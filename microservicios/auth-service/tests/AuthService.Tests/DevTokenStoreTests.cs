using AuthService;
using Xunit;

namespace AuthService.Tests;

public sealed class DevTokenStoreTests
{
    [Fact]
    public void Guarda_Consulta_Y_Elimina_De_Forma_CaseInsensitive()
    {
        var store = new DevTokenStore();
        store.Save(" User@Empresa.COM ", "token-1", "2026-11-07T12:00:00Z");

        var saved = store.Get("user@empresa.com");
        Assert.NotNull(saved);
        Assert.Equal("user@empresa.com", saved!.Email);
        Assert.Equal("token-1", saved.Token);

        store.Remove("USER@EMPRESA.COM");
        Assert.Null(store.Get("user@empresa.com"));
    }

    [Fact]
    public void Get_Y_Remove_Toleran_Entrada_Vacia()
    {
        var store = new DevTokenStore();
        Assert.Null(store.Get("  "));
        store.Remove("");
        Assert.Null(store.Get("nadie@empresa.com"));
    }

    [Fact]
    public void Save_Reemplaza_El_Ultimo_Token_Del_Mismo_Email()
    {
        var store = new DevTokenStore();
        store.Save("user@empresa.com", "primero", "1");
        store.Save("user@empresa.com", "segundo", "2");

        Assert.Equal("segundo", store.Get("user@empresa.com")!.Token);
    }
}
