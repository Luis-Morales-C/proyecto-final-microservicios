namespace VacacionesService;

/// <summary>
/// Reglas de dominio puras de vacaciones. No dependen de HTTP, PostgreSQL,
/// RabbitMQ ni Docker; por eso pueden probarse como verdaderas unitarias.
/// </summary>
public static class VacationRules
{
    public static string? ValidarFechas(
        DateOnly fechaInicio,
        DateOnly fechaFin,
        DateOnly hoy)
    {
        if (fechaFin < fechaInicio)
            return "fechaFin no puede ser anterior a fechaInicio";

        if (fechaInicio < hoy)
            return "fechaInicio no puede estar en el pasado";

        return null;
    }

    public static bool EmpleadoPuedeProgramar(EmpleadoReplica? empleado) =>
        empleado is { Activo: true };

    /// <summary>
    /// Los rangos son inclusivos. Si uno termina exactamente el día en que
    /// el otro comienza, existe solapamiento de ese día.
    /// </summary>
    public static bool SeSolapan(
        DateOnly inicioA,
        DateOnly finA,
        DateOnly inicioB,
        DateOnly finB) =>
        inicioA <= finB && finA >= inicioB;

    public static int CalcularDiasInclusivos(DateOnly inicio, DateOnly fin)
    {
        if (fin < inicio)
            throw new ArgumentException("fechaFin no puede ser anterior a fechaInicio");

        return fin.DayNumber - inicio.DayNumber + 1;
    }
}
