using System.Text.Json;
using Npgsql;

namespace AuthService;

/// <summary>
/// Ciclo de vida de la cuenta dirigido por eventos.
///
/// Garantía: la deduplicación, el cambio de estado y la publicación del
/// evento de salida van en una sola transacción. Se publica ANTES del commit:
/// si el commit fallara, el broker reentrega y se vuelve a publicar
/// (al-menos-una-vez). Preferible a perder el evento de salida; el Inbox/Outbox
/// formal llega en el Reto 29.
/// </summary>
public sealed class AccountEventHandler(
    NpgsqlDataSource dataSource,
    UserRepository users,
    TokenService tokens,
    EventPublisher publisher,
    IConfiguration configuration,
    ILogger<AccountEventHandler> logger)
{
    public static readonly string[] HandledTypes =
    [
        "empleado.creado",
        "empleado.retirado",
        "vacaciones.iniciadas",
        "vacaciones.finalizadas"
    ];

    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    private TimeSpan ActivationLifetime =>
        TimeSpan.FromMinutes(configuration.GetValue("ACTIVATION_TOKEN_MINUTES", 60));

    /// <returns>true si el evento era duplicado y se descartó.</returns>
    public async Task<bool> HandleAsync(EventEnvelope envelope, CancellationToken ct)
    {
        var data = envelope.Data.ValueKind == JsonValueKind.Object
            ? envelope.Data.Deserialize<EventData>(JsonOptions)
            : null;

        if (data is null || string.IsNullOrWhiteSpace(data.EmpleadoId))
        {
            throw new InvalidDataException(
                $"El evento {envelope.Type} ({envelope.Id}) no contiene data.empleadoId");
        }

        await using var conn = await dataSource.OpenConnectionAsync(ct);
        await using var tx = await conn.BeginTransactionAsync(ct);

        if (!await users.TryMarkProcessedAsync(conn, tx, envelope.Id, ct))
        {
            await tx.RollbackAsync(ct);
            return true;
        }

        switch (envelope.Type)
        {
            case "empleado.creado":
                await OnEmpleadoCreadoAsync(conn, tx, envelope, data, ct);
                break;

            case "empleado.retirado":
                await OnEmpleadoRetiradoAsync(conn, tx, envelope, data, ct);
                break;

            case "vacaciones.iniciadas":
                await OnVacacionesIniciadasAsync(conn, tx, envelope, data, ct);
                break;

            case "vacaciones.finalizadas":
                await OnVacacionesFinalizadasAsync(conn, tx, envelope, data, ct);
                break;
        }

        await tx.CommitAsync(ct);
        return false;
    }

    // empleado.creado -> cuenta INACTIVA + usuario.creado
    private async Task OnEmpleadoCreadoAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        EventEnvelope envelope, EventData data, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(data.Email))
        {
            throw new InvalidDataException(
                $"empleado.creado ({envelope.Id}) no contiene data.email");
        }

        var user = new Usuario(
            data.EmpleadoId!,
            data.Email.Trim().ToLowerInvariant(),
            PasswordHash: null,
            Roles.User,
            EstadoCuenta.Inactiva);

        if (!await users.InsertIfAbsentAsync(conn, tx, user, ct))
        {
            logger.LogWarning(
                "empleado.creado {EventId}: ya existe una cuenta para " +
                "{EmpleadoId} o el email {Email}; no se crea otra",
                envelope.Id, user.EmpleadoId, user.Email);
            return;
        }

        var (token, expiraEn) = tokens.CreateResetToken(user, ActivationLifetime);

        publisher.Publish(
            "usuario.creado",
            new UsuarioCreadoData(user.EmpleadoId, user.Email, token, expiraEn));

        logger.LogInformation(
            "Cuenta creada (INACTIVA) para {EmpleadoId}. Evento={EventId}",
            user.EmpleadoId, envelope.Id);
    }

    // empleado.retirado -> DESACTIVADA_PERMANENTE + cuenta.desactivada (RETIRO)
    private async Task OnEmpleadoRetiradoAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        EventEnvelope envelope, EventData data, CancellationToken ct)
    {
        var user = await users.GetForUpdateAsync(conn, tx, data.EmpleadoId!, ct);

        if (user is null)
        {
            logger.LogWarning(
                "empleado.retirado {EventId}: no hay cuenta para {EmpleadoId}",
                envelope.Id, data.EmpleadoId);
            return;
        }

        if (user.Estado == EstadoCuenta.DesactivadaPermanente)
        {
            logger.LogInformation(
                "empleado.retirado {EventId}: {EmpleadoId} ya estaba " +
                "desactivada permanentemente", envelope.Id, user.EmpleadoId);
            return;
        }

        await users.SetEstadoAsync(
            conn, tx, user.EmpleadoId, EstadoCuenta.DesactivadaPermanente, ct);

        publisher.Publish(
            "cuenta.desactivada",
            new CuentaDesactivadaData(
                user.EmpleadoId, user.Email, "RETIRO", Permanente: true));

        logger.LogInformation(
            "Cuenta {EmpleadoId} DESACTIVADA_PERMANENTE (estado previo {Previo}). " +
            "Evento={EventId}", user.EmpleadoId, user.Estado, envelope.Id);
    }

    // vacaciones.iniciadas -> SUSPENDIDA_TEMPORAL + cuenta.desactivada (VACACIONES)
    private async Task OnVacacionesIniciadasAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        EventEnvelope envelope, EventData data, CancellationToken ct)
    {
        var user = await users.GetForUpdateAsync(conn, tx, data.EmpleadoId!, ct);

        if (user is null)
        {
            logger.LogWarning(
                "vacaciones.iniciadas {EventId}: no hay cuenta para {EmpleadoId}",
                envelope.Id, data.EmpleadoId);
            return;
        }

        // Solo se suspende una cuenta ACTIVA. Una INACTIVA aún no puede
        // autenticarse y una PERMANENTE no debe degradarse a temporal.
        if (user.Estado != EstadoCuenta.Activa)
        {
            logger.LogInformation(
                "vacaciones.iniciadas {EventId}: {EmpleadoId} en estado " +
                "{Estado}; no se suspende", envelope.Id, user.EmpleadoId, user.Estado);
            return;
        }

        await users.SetEstadoAsync(
            conn, tx, user.EmpleadoId, EstadoCuenta.SuspendidaTemporal, ct);

        publisher.Publish(
            "cuenta.desactivada",
            new CuentaDesactivadaData(
                user.EmpleadoId, user.Email, "VACACIONES", Permanente: false));

        logger.LogInformation(
            "Cuenta {EmpleadoId} SUSPENDIDA_TEMPORAL por vacaciones. Evento={EventId}",
            user.EmpleadoId, envelope.Id);
    }

    // vacaciones.finalizadas -> reactiva SOLO si estaba SUSPENDIDA_TEMPORAL
    private async Task OnVacacionesFinalizadasAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        EventEnvelope envelope, EventData data, CancellationToken ct)
    {
        var user = await users.GetForUpdateAsync(conn, tx, data.EmpleadoId!, ct);

        if (user is null)
        {
            logger.LogWarning(
                "vacaciones.finalizadas {EventId}: no hay cuenta para {EmpleadoId}",
                envelope.Id, data.EmpleadoId);
            return;
        }

        // CASO BORDE: retirado durante las vacaciones => DESACTIVADA_PERMANENTE.
        // Aquí NO se reactiva y no se publica cuenta.activada.
        if (user.Estado != EstadoCuenta.SuspendidaTemporal)
        {
            logger.LogWarning(
                "vacaciones.finalizadas {EventId}: {EmpleadoId} está en estado " +
                "{Estado}; NO se reactiva la cuenta",
                envelope.Id, user.EmpleadoId, user.Estado);
            return;
        }

        await users.SetEstadoAsync(
            conn, tx, user.EmpleadoId, EstadoCuenta.Activa, ct);

        publisher.Publish(
            "cuenta.activada",
            new CuentaActivadaData(user.EmpleadoId, user.Email, "FIN_VACACIONES"));

        logger.LogInformation(
            "Cuenta {EmpleadoId} reactivada por fin de vacaciones. Evento={EventId}",
            user.EmpleadoId, envelope.Id);
    }
}
