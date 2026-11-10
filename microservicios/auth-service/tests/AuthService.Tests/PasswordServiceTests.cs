using AuthService;
using Xunit;

namespace AuthService.Tests;

public sealed class PasswordServiceTests
{
    private readonly PasswordService service = new();

    [Fact]
    public void Hash_Y_Verify_Aceptan_Password_Correcta()
    {
        var hash = service.Hash("ClaveSegura1");
        Assert.NotEqual("ClaveSegura1", hash);
        Assert.True(service.Verify("ClaveSegura1", hash));
        Assert.False(service.Verify("OtraClave2", hash));
    }

    [Fact]
    public void Verify_Con_Hash_Nulo_O_Invalido_Retorna_False()
    {
        Assert.False(service.Verify("ClaveSegura1", null));
        Assert.False(service.Verify("ClaveSegura1", "hash-invalido"));
    }

    [Theory]
    [InlineData(null, "La contraseña es obligatoria.")]
    [InlineData("", "La contraseña es obligatoria.")]
    [InlineData("Abc1", "La contraseña debe tener al menos 8 caracteres.")]
    [InlineData("abcdefgh1", "La contraseña debe incluir al menos una mayúscula.")]
    [InlineData("ABCDEFGH1", "La contraseña debe incluir al menos una minúscula.")]
    [InlineData("Abcdefgh", "La contraseña debe incluir al menos un número.")]
    public void ValidatePolicy_Rechaza_Passwords_Invalidas(string? password, string expected)
    {
        Assert.Equal(expected, service.ValidatePolicy(password));
    }

    [Fact]
    public void ValidatePolicy_Rechaza_Mas_De_72_Caracteres()
    {
        var password = "A1" + new string('a', 71);
        Assert.Equal("La contraseña no puede superar 72 caracteres.", service.ValidatePolicy(password));
    }

    [Fact]
    public void ValidatePolicy_Acepta_Password_Valida()
    {
        Assert.Null(service.ValidatePolicy("ClaveSegura1"));
    }
}
