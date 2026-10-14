using System.Text.Json;

namespace AuthService;

/// <summary>
/// Estado de la cuenta. No es un booleano: hay que distinguir
/// la suspensión temporal (vacaciones) de la baja permanente (retiro).
/// </summary>
public static class EstadoCuenta
{
    public const string Inactiva = "INACTIVA";                     // creada, aún sin contraseña
    public const string Activa = "ACTIVA";
    public const string SuspendidaTemporal = "SUSPENDIDA_TEMPORAL"; // vacaciones
    public const string DesactivadaPermanente = "DESACTIVADA_PERMANENTE"; // retiro
}

public static class Roles
{
    public const string Admin = "ADMIN";
    public const string User = "USER";
}

public sealed record Usuario(
    string EmpleadoId,
    string Email,
    string? PasswordHash,
    string Rol,
    string Estado);

// ---------- DTOs HTTP ----------

public sealed record LoginRequest(string? Email, string? Password);

public sealed record LoginResponse(string AccessToken, string TokenType, int ExpiresIn);

public sealed record RecoverPasswordRequest(string? Email);

public sealed record ResetPasswordRequest(string? Token, string? NewPassword);

public sealed record ChangePasswordRequest(string? CurrentPassword, string? NewPassword);

public sealed record MessageResponse(string Message);

// ---------- Eventos entrantes ----------

public sealed class EventEnvelope
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "";
    public int Version { get; set; }
    public string? OccurredAt { get; set; }
    public string? Producer { get; set; }
    public JsonElement Data { get; set; }
}

/// <summary>Unión de los campos que el auth-service lee de los eventos entrantes.</summary>
public sealed class EventData
{
    public string? EmpleadoId { get; set; }
    public string? Email { get; set; }
    public string? VacacionesId { get; set; }
}

// ---------- Eventos salientes (Catálogo, secciones 3.4 a 3.7) ----------

public sealed record UsuarioCreadoData(
    string EmpleadoId, string Email, string TokenActivacion, string ExpiraEn);

public sealed record UsuarioRecuperacionData(
    string Email, string TokenRecuperacion, string ExpiraEn);

public sealed record CuentaActivadaData(
    string EmpleadoId, string Email, string Motivo);

public sealed record CuentaDesactivadaData(
    string EmpleadoId, string Email, string Motivo, bool Permanente);
