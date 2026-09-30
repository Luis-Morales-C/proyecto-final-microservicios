using System.Security.Cryptography;
using System.Text;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;

namespace AuthService;

public sealed record ResetTokenClaims(string Sub, string Fingerprint);

/// <summary>
/// Emite y valida los dos tipos de token (HMAC SHA256, clave simétrica):
///  - Access JWT:  sub, role, email, iat, exp
///  - Reset JWT:   sub, type=RESET_PASSWORD, pv, iat, exp  (Opción A del reto)
/// </summary>
public sealed class TokenService
{
    public const string ResetType = "RESET_PASSWORD";

    private readonly JsonWebTokenHandler _handler = new();
    private readonly SymmetricSecurityKey _key;
    private readonly string _issuer;

    public TokenValidationParameters ValidationParameters { get; }

    public TokenService(IConfiguration configuration)
    {
        var secret = configuration["JWT_SECRET"];

        if (string.IsNullOrWhiteSpace(secret) ||
            Encoding.UTF8.GetByteCount(secret) < 32)
        {
            throw new InvalidOperationException(
                "JWT_SECRET debe estar definido y tener al menos " +
                "32 caracteres (HMAC SHA256).");
        }

        _issuer = configuration["JWT_ISSUER"] ?? "auth-service";
        _key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret));

        ValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = _issuer,
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = _key,
            ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
            ClockSkew = TimeSpan.FromSeconds(30),
            NameClaimType = "sub",
            RoleClaimType = "role"
        };
    }

    public static DateTime NowUtcSeconds()
    {
        var now = DateTime.UtcNow;
        return new DateTime(
            now.Ticks - (now.Ticks % TimeSpan.TicksPerSecond),
            DateTimeKind.Utc);
    }

    public static string FormatUtc(DateTime utc) =>
        utc.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /// <summary>Huella corta del hash actual: invalida el token de reset tras usarlo.</summary>
    public static string Fingerprint(string? passwordHash)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(passwordHash ?? ""));
        return Convert.ToHexString(bytes)[..16];
    }

    public (string Token, int ExpiresInSeconds) CreateAccessToken(
        Usuario user, TimeSpan lifetime)
    {
        var now = NowUtcSeconds();

        var token = _handler.CreateToken(new SecurityTokenDescriptor
        {
            Issuer = _issuer,
            IssuedAt = now,
            NotBefore = now,
            Expires = now.Add(lifetime),
            SigningCredentials = new SigningCredentials(
                _key, SecurityAlgorithms.HmacSha256),
            Claims = new Dictionary<string, object>
            {
                ["sub"] = user.EmpleadoId,
                ["role"] = user.Rol,
                ["email"] = user.Email
            }
        });

        return (token, (int)lifetime.TotalSeconds);
    }

    public (string Token, string ExpiraEn) CreateResetToken(
        Usuario user, TimeSpan lifetime)
    {
        var now = NowUtcSeconds();
        var expires = now.Add(lifetime);

        var token = _handler.CreateToken(new SecurityTokenDescriptor
        {
            Issuer = _issuer,
            IssuedAt = now,
            NotBefore = now,
            Expires = expires,
            SigningCredentials = new SigningCredentials(
                _key, SecurityAlgorithms.HmacSha256),
            Claims = new Dictionary<string, object>
            {
                ["sub"] = user.EmpleadoId,
                ["type"] = ResetType,
                ["pv"] = Fingerprint(user.PasswordHash)
            }
        });

        return (token, FormatUtc(expires));
    }

    /// <summary>Devuelve null si el token es inválido, expiró o no es de tipo reset.</summary>
    public async Task<ResetTokenClaims?> ValidateResetTokenAsync(string? token)
    {
        if (string.IsNullOrWhiteSpace(token))
            return null;

        var result = await _handler.ValidateTokenAsync(token, ValidationParameters);

        if (!result.IsValid)
            return null;

        if (!result.Claims.TryGetValue("type", out var type) ||
            type?.ToString() != ResetType)
            return null;

        if (!result.Claims.TryGetValue("sub", out var sub) ||
            string.IsNullOrWhiteSpace(sub?.ToString()))
            return null;

        result.Claims.TryGetValue("pv", out var pv);

        return new ResetTokenClaims(sub.ToString()!, pv?.ToString() ?? "");
    }
}
