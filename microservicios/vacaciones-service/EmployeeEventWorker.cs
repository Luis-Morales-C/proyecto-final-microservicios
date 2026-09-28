using System.Text.Json;
using Npgsql;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;

namespace VacacionesService;

public sealed class EmployeeEventWorker(
    NpgsqlDataSource dataSource,
    IConfiguration configuration,
    ILogger<EmployeeEventWorker> logger) : BackgroundService
{
    private const string QueueName = "vacaciones.empleados";

    protected override async Task ExecuteAsync(
        CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await ConsumeAsync(stoppingToken);
            }
            catch (OperationCanceledException)
                when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception ex)
            {
                logger.LogError(
                    ex,
                    "Falló el consumidor de empleados de vacaciones. " +
                    "Reintentando en 5 segundos"
                );

                await Task.Delay(
                    TimeSpan.FromSeconds(5),
                    stoppingToken
                );
            }
        }
    }

    private async Task ConsumeAsync(
        CancellationToken stoppingToken)
    {
        var factory = new ConnectionFactory
        {
            HostName =
                configuration["RABBITMQ_HOST"] ?? "rabbitmq",

            Port = int.Parse(
                configuration["RABBITMQ_PORT"] ?? "5672"
            ),

            UserName =
                configuration["RABBITMQ_USER"],

            Password =
                configuration["RABBITMQ_PASSWORD"],

            DispatchConsumersAsync = true,
            AutomaticRecoveryEnabled = true,

            NetworkRecoveryInterval =
                TimeSpan.FromSeconds(5),

            ClientProvidedName =
                "vacaciones-service-consumer"
        };

        using var connection =
            factory.CreateConnection();

        using var channel =
            connection.CreateModel();

        channel.BasicQos(
            prefetchSize: 0,
            prefetchCount: 1,
            global: false
        );

        channel.QueueDeclare(
            queue: QueueName,
            durable: true,
            exclusive: false,
            autoDelete: false,
            arguments: null
        );

        var consumer =
            new AsyncEventingBasicConsumer(channel);

        consumer.Received += async (_, args) =>
        {
            try
            {
                var envelope =
                    JsonSerializer.Deserialize<EmpleadoEventEnvelope>(
                        args.Body.Span,
                        new JsonSerializerOptions
                        {
                            PropertyNameCaseInsensitive = true
                        }
                    );

                if (
                    envelope is null ||
                    string.IsNullOrWhiteSpace(envelope.Id)
                )
                {
                    logger.LogWarning(
                        "Evento de empleado inválido descartado"
                    );

                    channel.BasicAck(
                        args.DeliveryTag,
                        multiple: false
                    );

                    return;
                }

                if (
                    envelope.Type is not
                    ("empleado.creado" or "empleado.retirado")
                )
                {
                    channel.BasicAck(
                        args.DeliveryTag,
                        multiple: false
                    );

                    return;
                }

                var duplicate =
                    await ProcessEventAsync(
                        envelope,
                        stoppingToken
                    );

                if (duplicate)
                {
                    logger.LogInformation(
                        "Evento duplicado descartado " +
                        "en vacaciones: {EventId}",
                        envelope.Id
                    );
                }

                channel.BasicAck(
                    args.DeliveryTag,
                    multiple: false
                );
            }
            catch (Exception ex)
            {
                logger.LogError(
                    ex,
                    "Error procesando evento de empleado " +
                    "en vacaciones"
                );

                channel.BasicNack(
                    args.DeliveryTag,
                    multiple: false,
                    requeue: true
                );
            }
        };

        channel.BasicConsume(
            queue: QueueName,
            autoAck: false,
            consumerTag: "",
            noLocal: false,
            exclusive: false,
            arguments: null,
            consumer: consumer
        );

        logger.LogInformation(
            "Vacaciones escuchando cola {Queue}",
            QueueName
        );

        var closed =
            new TaskCompletionSource<bool>(
                TaskCreationOptions.RunContinuationsAsynchronously
            );

        connection.ConnectionShutdown +=
            (_, _) => closed.TrySetResult(true);

        using var registration =
            stoppingToken.Register(
                () => closed.TrySetCanceled(stoppingToken)
            );

        await closed.Task;
    }

    private async Task<bool> ProcessEventAsync(
        EmpleadoEventEnvelope envelope,
        CancellationToken cancellationToken)
    {
        await using var connection =
            await dataSource.OpenConnectionAsync(
                cancellationToken
            );

        await using var transaction =
            await connection.BeginTransactionAsync(
                cancellationToken
            );

        /*
         * Deduplicación:
         * se intenta registrar el ID del evento.
         *
         * Si ya existe, significa que el evento
         * fue procesado anteriormente.
         */
        await using var insertProcessed =
            new NpgsqlCommand(
                """
                INSERT INTO eventos_procesados (id)
                VALUES (@id)
                ON CONFLICT (id) DO NOTHING
                RETURNING id
                """,
                connection,
                transaction
            );

        insertProcessed.Parameters.AddWithValue(
            "id",
            envelope.Id
        );

        var inserted =
            await insertProcessed.ExecuteScalarAsync(
                cancellationToken
            );

        if (inserted is null)
        {
            await transaction.RollbackAsync(
                cancellationToken
            );

            return true;
        }

        if (
            string.IsNullOrWhiteSpace(
                envelope.Data.EmpleadoId
            )
        )
        {
            throw new InvalidOperationException(
                "El evento de empleado no contiene " +
                "data.empleadoId"
            );
        }

        switch (envelope.Type)
        {
            case "empleado.creado":
            {
                /*
                 * Un empleado recién creado se almacena
                 * en la réplica local.
                 *
                 * Vacaciones no necesita consultar
                 * gestion-empleados por REST.
                 */
                var active =
                    !string.Equals(
                        envelope.Data.Estado,
                        "RETIRADO",
                        StringComparison.OrdinalIgnoreCase
                    )
                    &&
                    !string.Equals(
                        envelope.Data.Estado,
                        "RECHAZADO",
                        StringComparison.OrdinalIgnoreCase
                    );

                await using var upsert =
                    new NpgsqlCommand(
                        """
                        INSERT INTO empleados_validos (
                            id,
                            activo,
                            actualizado_en
                        )
                        VALUES (
                            @id,
                            @activo,
                            NOW()
                        )
                        ON CONFLICT (id)
                        DO UPDATE
                        SET
                            activo = EXCLUDED.activo,
                            actualizado_en = NOW()
                        """,
                        connection,
                        transaction
                    );

                upsert.Parameters.AddWithValue(
                    "id",
                    envelope.Data.EmpleadoId
                );

                upsert.Parameters.AddWithValue(
                    "activo",
                    active
                );

                await upsert.ExecuteNonQueryAsync(
                    cancellationToken
                );

                logger.LogInformation(
                    "Empleado {EmpleadoId} sincronizado " +
                    "en Vacaciones. Activo={Activo}",
                    envelope.Data.EmpleadoId,
                    active
                );

                break;
            }

            case "empleado.retirado":
            {
                /*
                 * Primero se marca al empleado como inactivo
                 * dentro de la réplica local.
                 */
                await using var retire =
                    new NpgsqlCommand(
                        """
                        INSERT INTO empleados_validos (
                            id,
                            activo,
                            actualizado_en
                        )
                        VALUES (
                            @id,
                            FALSE,
                            NOW()
                        )
                        ON CONFLICT (id)
                        DO UPDATE
                        SET
                            activo = FALSE,
                            actualizado_en = NOW()
                        """,
                        connection,
                        transaction
                    );

                retire.Parameters.AddWithValue(
                    "id",
                    envelope.Data.EmpleadoId
                );

                await retire.ExecuteNonQueryAsync(
                    cancellationToken
                );

                /*
                 * NUEVO:
                 *
                 * Un empleado retirado ya no debe conservar
                 * períodos de vacaciones activos.
                 *
                 * No eliminamos las vacaciones físicamente
                 * porque queremos preservar su historial.
                 *
                 * PROGRAMADA / EN_CURSO → CANCELADA
                 *
                 * FINALIZADA y CANCELADA permanecen intactas.
                 */
                await using var cancelVacations =
                    new NpgsqlCommand(
                        """
                        UPDATE vacaciones
                        SET estado = 'CANCELADA'
                        WHERE empleado_id = @empleadoId
                          AND estado IN (
                              'PROGRAMADA',
                              'EN_CURSO'
                          )
                        """,
                        connection,
                        transaction
                    );

                cancelVacations.Parameters.AddWithValue(
                    "empleadoId",
                    envelope.Data.EmpleadoId
                );

                var cancelledCount =
                    await cancelVacations.ExecuteNonQueryAsync(
                        cancellationToken
                    );

                logger.LogInformation(
                    "Empleado {EmpleadoId} retirado. " +
                    "Vacaciones activas canceladas: {Cantidad}",
                    envelope.Data.EmpleadoId,
                    cancelledCount
                );

                break;
            }
        }

        /*
         * La actualización de la réplica del empleado
         * y la cancelación de vacaciones ocurren dentro
         * de la MISMA transacción.
         *
         * Si algo falla, no queda el dominio a medias.
         */
        await transaction.CommitAsync(
            cancellationToken
        );

        return false;
    }
}