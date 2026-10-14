using VacacionesService;
using Xunit;
namespace VacacionesService.Tests;

public sealed class VacationRulesTests
{
    private static readonly DateOnly Hoy = new(2026, 10, 13);

    [Fact]
    public void Rechaza_FechaFin_Anterior_A_FechaInicio()
    {
        var error = VacationRules.ValidarFechas(
            new DateOnly(2026, 10, 20),
            new DateOnly(2026, 10, 19),
            Hoy);

        Assert.Equal("fechaFin no puede ser anterior a fechaInicio", error);
    }

    [Fact]
    public void Rechaza_FechaInicio_En_El_Pasado()
    {
        var error = VacationRules.ValidarFechas(
            new DateOnly(2026, 10, 12),
            new DateOnly(2026, 10, 15),
            Hoy);

        Assert.Equal("fechaInicio no puede estar en el pasado", error);
    }

    [Fact]
    public void Permite_Periodo_De_Un_Solo_Dia()
    {
        var error = VacationRules.ValidarFechas(Hoy, Hoy, Hoy);

        Assert.Null(error);
    }

    [Fact]
    public void Rechaza_Empleado_Inexistente()
    {
        Assert.False(VacationRules.EmpleadoPuedeProgramar(null));
    }

    [Fact]
    public void Rechaza_Empleado_Retirado()
    {
        var empleado = new EmpleadoReplica("E001", false);

        Assert.False(VacationRules.EmpleadoPuedeProgramar(empleado));
    }

    [Fact]
    public void Acepta_Empleado_Activo()
    {
        var empleado = new EmpleadoReplica("E001", true);

        Assert.True(VacationRules.EmpleadoPuedeProgramar(empleado));
    }

    [Theory]
    [InlineData("2026-11-15", "2026-11-25", true)]   // inicia dentro
    [InlineData("2026-11-01", "2026-11-12", true)]   // termina dentro
    [InlineData("2026-11-01", "2026-11-30", true)]   // contiene al existente
    [InlineData("2026-11-10", "2026-11-20", true)]   // idéntico
    [InlineData("2026-11-01", "2026-11-10", true)]   // toca límite inicial
    [InlineData("2026-11-20", "2026-11-25", true)]   // toca límite final
    [InlineData("2026-11-21", "2026-11-25", false)]  // después, sin tocar
    [InlineData("2026-11-01", "2026-11-09", false)]  // antes, sin tocar
    public void Detecta_Solapamiento_Con_Casos_Borde(
        string inicioNuevo,
        string finNuevo,
        bool esperado)
    {
        var existenteInicio = new DateOnly(2026, 11, 10);
        var existenteFin = new DateOnly(2026, 11, 20);

        var actual = VacationRules.SeSolapan(
            DateOnly.Parse(inicioNuevo),
            DateOnly.Parse(finNuevo),
            existenteInicio,
            existenteFin);

        Assert.Equal(esperado, actual);
    }

    [Fact]
    public void Calcula_Dias_Inclusivos()
    {
        var dias = VacationRules.CalcularDiasInclusivos(
            new DateOnly(2026, 11, 10),
            new DateOnly(2026, 11, 20));

        Assert.Equal(11, dias);
    }
}
