using System.Text.Json;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;

namespace AuthService;

/// <summary>
/// Consume la cola auth.eventos (empleado.creado, empleado.retirado,
/// vacaciones.iniciadas, vacaciones.finalizadas) y delega en
/// <see cref="AccountEventHandler"/>. Sigue el mismo patrón que el
/// EmployeeEventWorker de vacaciones-service.
/// </summary>
public sealed class AuthEventWorker(
    AccountEventHandler handler,
    IConfiguration configuration,
    ILogger<AuthEventWorker> logger) : BackgroundService
{
    private const string QueueName = "auth.eventos";

    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
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
                    "Falló el consumidor de auth. Reintentando en 5 segundos");

                await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
            }
        }
    }

    private async Task ConsumeAsync(CancellationToken stoppingToken)
    {
        var factory = new ConnectionFactory
        {
            HostName = configuration["RABBITMQ_HOST"] ?? "rabbitmq",
            Port = int.Parse(configuration["RABBITMQ_PORT"] ?? "5672"),
            UserName = configuration["RABBITMQ_USER"],
            Password = configuration["RABBITMQ_PASSWORD"],
            DispatchConsumersAsync = true,
            AutomaticRecoveryEnabled = true,
            NetworkRecoveryInterval = TimeSpan.FromSeconds(5),
            ClientProvidedName = "auth-service-consumer"
        };

        using var connection = factory.CreateConnection();
        using var channel = connection.CreateModel();

        channel.BasicQos(prefetchSize: 0, prefetchCount: 1, global: false);

        channel.QueueDeclare(
            queue: QueueName,
            durable: true,
            exclusive: false,
            autoDelete: false,
            arguments: null);

        var consumer = new AsyncEventingBasicConsumer(channel);

        consumer.Received += async (_, args) =>
        {
            try
            {
                var envelope = JsonSerializer.Deserialize<EventEnvelope>(
                    args.Body.Span, JsonOptions);

                if (envelope is null || string.IsNullOrWhiteSpace(envelope.Id))
                {
                    logger.LogWarning("Evento inválido descartado (sin id)");
                    channel.BasicAck(args.DeliveryTag, multiple: false);
                    return;
                }

                if (!AccountEventHandler.HandledTypes.Contains(envelope.Type))
                {
                    logger.LogDebug(
                        "Evento {Type} ignorado por auth", envelope.Type);
                    channel.BasicAck(args.DeliveryTag, multiple: false);
                    return;
                }

                var duplicate = await handler.HandleAsync(
                    envelope, stoppingToken);

                if (duplicate)
                {
                    logger.LogInformation(
                        "Evento duplicado descartado en auth: {EventId} ({Type})",
                        envelope.Id, envelope.Type);
                }

                channel.BasicAck(args.DeliveryTag, multiple: false);
            }
            catch (Exception ex) when (
                ex is JsonException or InvalidDataException)
            {
                // Mensaje venenoso: reintentar no lo arregla. Se descarta.
                logger.LogError(ex, "Evento malformado descartado en auth");
                channel.BasicAck(args.DeliveryTag, multiple: false);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error procesando evento en auth; se reencola");
                channel.BasicNack(args.DeliveryTag, multiple: false, requeue: true);
            }
        };

        channel.BasicConsume(
            queue: QueueName,
            autoAck: false,
            consumerTag: "",
            noLocal: false,
            exclusive: false,
            arguments: null,
            consumer: consumer);

        logger.LogInformation("Auth escuchando cola {Queue}", QueueName);

        var closed = new TaskCompletionSource<bool>(
            TaskCreationOptions.RunContinuationsAsynchronously);

        connection.ConnectionShutdown += (_, _) => closed.TrySetResult(true);

        using var registration = stoppingToken.Register(
            () => closed.TrySetCanceled(stoppingToken));

        await closed.Task;
    }
}
