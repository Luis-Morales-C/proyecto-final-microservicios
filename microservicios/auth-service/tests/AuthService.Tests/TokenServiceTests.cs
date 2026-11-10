using AuthService;
using Microsoft.Extensions.Configuration;
using Microsoft.IdentityModel.JsonWebTokens;
using Xunit;

namespace AuthService.Tests;

public sealed class TokenServiceTests
{
    private const string Secret = "clave-academica-reto5-cambiar-en-produccion-0123456789";

    [Fact]
    public void Constructor_Rechaza_Secret_Ausente_O_Corto()
    {
        Assert.Throws<InvalidOperationException>(() => CreateService(secret: null));
        Assert.Throws<InvalidOperationException>(() => CreateService(secret: "demasiado-corto"));
    }

    [Fact]
    public void AccessToken_Contiene_Sub_Role_Email_Y_Expiracion()
    {
        var service = CreateService();
        var user = new Usuario("E001", "user@empresa.com", "hash", Roles.User, EstadoCuenta.Activa);

        var (token, expires) = service.CreateAccessToken(user, TimeSpan.FromMinutes(10));
        var jwt = new JsonWebToken(token);

        Assert.Equal(600, expires);
        Assert.Equal("E001", jwt.Subject);
        Assert.Equal("USER", jwt.GetClaim("role").Value);
        Assert.Equal("user@empresa.com", jwt.GetClaim("email").Value);
        Assert.Equal("auth-service", jwt.Issuer);
    }

    [Fact]
    public async Task ResetToken_Se_Valida_Y_Recupera_Huella()
    {
        var service = CreateService();
        var user = new Usuario("E002", "user2@empresa.com", "hash-actual", Roles.User, EstadoCuenta.Inactiva);

        var (token, expiraEn) = service.CreateResetToken(user, TimeSpan.FromMinutes(15));
        var claims = await service.ValidateResetTokenAsync(token);

        Assert.NotNull(claims);
        Assert.Equal("E002", claims!.Sub);
        Assert.Equal(TokenService.Fingerprint("hash-actual"), claims.Fingerprint);
        Assert.EndsWith("Z", expiraEn);
    }

    [Fact]
    public async Task AccessToken_No_Se_Acepta_Como_ResetToken()
    {
        var service = CreateService();
        var user = new Usuario("E003", "user3@empresa.com", "hash", Roles.User, EstadoCuenta.Activa);
        var (token, _) = service.CreateAccessToken(user, TimeSpan.FromMinutes(5));

        Assert.Null(await service.ValidateResetTokenAsync(token));
        Assert.Null(await service.ValidateResetTokenAsync(null));
        Assert.Null(await service.ValidateResetTokenAsync("token-invalido"));
    }

    [Fact]
    public void Fingerprint_Es_Determinista_Y_Cambia_Con_El_Hash()
    {
        var one = TokenService.Fingerprint("hash-1");
        var same = TokenService.Fingerprint("hash-1");
        var other = TokenService.Fingerprint("hash-2");

        Assert.Equal(16, one.Length);
        Assert.Equal(one, same);
        Assert.NotEqual(one, other);
        Assert.Equal(TokenService.Fingerprint(null), TokenService.Fingerprint(""));
    }

    [Fact]
    public void Helpers_UTC_Generan_Formato_Estable()
    {
        var now = TokenService.NowUtcSeconds();
        Assert.Equal(DateTimeKind.Utc, now.Kind);
        Assert.Equal(0, now.Ticks % TimeSpan.TicksPerSecond);
        Assert.Equal("2026-01-02T03:04:05Z",
            TokenService.FormatUtc(new DateTime(2026, 1, 2, 3, 4, 5, DateTimeKind.Utc)));
    }

    private static TokenService CreateService(string? secret = Secret)
    {
        var values = new Dictionary<string, string?>
        {
            ["JWT_SECRET"] = secret,
            ["JWT_ISSUER"] = "auth-service"
        };
        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();
        return new TokenService(configuration);
    }
}
