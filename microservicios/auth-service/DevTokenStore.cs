using System.Collections.Concurrent;

namespace AuthService;

/// <summary>
/// Soporte exclusivo de desarrollo/E2E. Mantiene en memoria el último token
/// de activación/recuperación emitido por email para automatizar el Reto 6
/// sin exponer tokens en el historial de notificaciones.
///
/// El endpoint que lo consulta solo existe cuando ENABLE_DEV_ENDPOINTS=true
/// y exige rol ADMIN.
/// </summary>
public sealed class DevTokenStore
{
    private readonly ConcurrentDictionary<string, DevResetToken> tokens =
        new(StringComparer.OrdinalIgnoreCase);

    public void Save(string email, string token, string expiraEn)
    {
        tokens[email.Trim().ToLowerInvariant()] =
            new DevResetToken(email.Trim().ToLowerInvariant(), token, expiraEn);
    }

    public DevResetToken? Get(string email)
    {
        if (string.IsNullOrWhiteSpace(email)) return null;
        return tokens.TryGetValue(email.Trim().ToLowerInvariant(), out var value)
            ? value
            : null;
    }

    public void Remove(string email)
    {
        if (!string.IsNullOrWhiteSpace(email))
            tokens.TryRemove(email.Trim().ToLowerInvariant(), out _);
    }
}

public sealed record DevResetToken(string Email, string Token, string ExpiraEn);
