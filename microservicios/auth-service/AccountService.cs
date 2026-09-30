using Npgsql;

namespace AuthService;

/// <summary>Lógica de los cuatro endpoints /auth/*.</summary>
public sealed class AccountService(
    UserRepository users,
    TokenService tokens,
    PasswordService passwords,
    EventPublisher publisher,
    NpgsqlDataSource dataSource,
    IConfiguration configuration,
    ILogger<AccountService> logger)
{
    private TimeSpan AccessLifetime =>
        TimeSpan.FromMinutes(configuration.GetValue("ACCESS_TOKEN_MINUTES", 60));

    private TimeSpan RecoveryLifetime =>
        TimeSpan.FromMinutes(configuration.GetValue("RECOVERY_TOKEN_MINUTES", 15));

    private static IResult InvalidToken() =>
        Results.Problem(
            statusCode: StatusCodes.Status400BadRequest,
            title: "Token inválido",
            detail: "El token es inválido, expiró o ya fue utilizado.");

    // ---------- POST /auth/login ----------

    public async Task<IResult> LoginAsync(LoginRequest request, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrEmpty(request.Password))
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Solicitud inválida",
                detail: "email y password son obligatorios.");
        }

        var email = request.Email.Trim().ToLowerInvariant();
        var user = await users.GetByEmailAsync(email, ct);

        // Verifica siempre (incluso sin usuario) para igualar tiempos.
        var passwordOk = passwords.Verify(request.Password, user?.PasswordHash);

        if (user is null || !passwordOk)
        {
            logger.LogInformation("Login rechazado: credenciales inválidas");
            return Unauthorized();
        }

        if (user.Estado != EstadoCuenta.Activa)
        {
            // El motivo real queda solo en el log; al cliente no se le revela.
            logger.LogInformation(
                "Login rechazado para {EmpleadoId}: cuenta en estado {Estado}",
                user.EmpleadoId, user.Estado);
            return Unauthorized();
        }

        var (token, expiresIn) = tokens.CreateAccessToken(user, AccessLifetime);

        logger.LogInformation("Login exitoso: {EmpleadoId}", user.EmpleadoId);

        return Results.Ok(new LoginResponse(token, "Bearer", expiresIn));
    }

    private static IResult Unauthorized() =>
        Results.Problem(
            statusCode: StatusCodes.Status401Unauthorized,
            title: "No autorizado",
            detail: "Credenciales inválidas o cuenta no habilitada.");

    // ---------- POST /auth/recover-password ----------

    public async Task<IResult> RecoverAsync(
        RecoverPasswordRequest request, CancellationToken ct)
    {
        // Respuesta idéntica exista o no el correo (evita enumerar cuentas).
        var accepted = Results.Accepted(value: new MessageResponse(
            "Si el correo está registrado, recibirá instrucciones " +
            "para restablecer su contraseña."));

        if (string.IsNullOrWhiteSpace(request.Email))
            return accepted;

        var user = await users.GetByEmailAsync(
            request.Email.Trim().ToLowerInvariant(), ct);

        if (user is null || user.Estado == EstadoCuenta.DesactivadaPermanente)
            return accepted;

        try
        {
            var (token, expiraEn) = tokens.CreateResetToken(user, RecoveryLifetime);

            publisher.Publish(
                "usuario.recuperacion",
                new UsuarioRecuperacionData(user.Email, token, expiraEn));
        }
        catch (Exception ex)
        {
            logger.LogError(ex,
                "No se pudo publicar usuario.recuperacion para {EmpleadoId}",
                user.EmpleadoId);
        }

        return accepted;
    }

    // ---------- POST /auth/reset-password ----------

    public async Task<IResult> ResetAsync(
        ResetPasswordRequest request, CancellationToken ct)
    {
        var claims = await tokens.ValidateResetTokenAsync(request.Token);
        if (claims is null)
            return InvalidToken();

        var policyError = passwords.ValidatePolicy(request.NewPassword);
        if (policyError is not null)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Contraseña no válida",
                detail: policyError);
        }

        await using var conn = await dataSource.OpenConnectionAsync(ct);
        await using var tx = await conn.BeginTransactionAsync(ct);

        var user = await users.GetForUpdateAsync(conn, tx, claims.Sub, ct);

        // Token de un solo uso: si la contraseña cambió desde que se emitió,
        // la huella ya no coincide y el token queda invalidado.
        if (user is null ||
            user.Estado == EstadoCuenta.DesactivadaPermanente ||
            TokenService.Fingerprint(user.PasswordHash) != claims.Fingerprint)
        {
            return InvalidToken();
        }

        var activate = user.Estado == EstadoCuenta.Inactiva;

        await users.SetPasswordAsync(
            conn, tx, user.EmpleadoId,
            passwords.Hash(request.NewPassword!), activate, ct);

        if (activate)
        {
            publisher.Publish(
                "cuenta.activada",
                new CuentaActivadaData(
                    user.EmpleadoId, user.Email, "ACTIVACION_INICIAL"));
        }

        await tx.CommitAsync(ct);

        logger.LogInformation(
            "Contraseña establecida para {EmpleadoId} (activación inicial: {Activate})",
            user.EmpleadoId, activate);

        return Results.Ok(new MessageResponse("Contraseña establecida correctamente."));
    }

    // ---------- POST /auth/change-password (requiere JWT) ----------

    public async Task<IResult> ChangeAsync(
        string empleadoId, ChangePasswordRequest request, CancellationToken ct)
    {
        if (string.IsNullOrEmpty(request.CurrentPassword))
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Solicitud inválida",
                detail: "currentPassword es obligatoria.");
        }

        var user = await users.GetByIdAsync(empleadoId, ct);

        if (user is null || user.Estado != EstadoCuenta.Activa)
            return Results.Problem(
                statusCode: StatusCodes.Status403Forbidden,
                title: "Prohibido",
                detail: "La cuenta no está habilitada.");

        if (!passwords.Verify(request.CurrentPassword, user.PasswordHash))
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Contraseña incorrecta",
                detail: "La contraseña actual no es correcta.");
        }

        var policyError = passwords.ValidatePolicy(request.NewPassword);
        if (policyError is not null)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Contraseña no válida",
                detail: policyError);
        }

        if (request.NewPassword == request.CurrentPassword)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Contraseña no válida",
                detail: "La nueva contraseña debe ser distinta de la actual.");
        }

        await using var conn = await dataSource.OpenConnectionAsync(ct);
        await users.SetPasswordAsync(
            conn, null, user.EmpleadoId,
            passwords.Hash(request.NewPassword!), activate: false, ct);

        logger.LogInformation("Contraseña cambiada: {EmpleadoId}", user.EmpleadoId);

        return Results.Ok(new MessageResponse("Contraseña actualizada correctamente."));
    }
}
