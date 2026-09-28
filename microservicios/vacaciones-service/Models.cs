namespace VacacionesService;

public static class EstadosVacaciones
{
    public const string Programada = "PROGRAMADA";
    public const string EnCurso = "EN_CURSO";
    public const string Finalizada = "FINALIZADA";
    public const string Cancelada = "CANCELADA";
}

public sealed record ProgramarVacacionesRequest(
    string EmpleadoId,
    DateOnly FechaInicio,
    DateOnly FechaFin
);

public sealed record Vacacion(
    string Id,
    string EmpleadoId,
    DateOnly FechaInicio,
    DateOnly FechaFin,
    string Estado,
    DateTimeOffset FechaCreacion
);

public sealed record EmpleadoReplica(
    string Id,
    bool Activo
);

// Contrato compatible con empleado.creado/actualizado/retirado.
// Vacaciones solo necesita empleadoId/estado y consume creado + retirado.
public sealed record EmpleadoEventData(
    string EmpleadoId,
    string? NumeroEmpleado,
    string? Nombre,
    string? Apellido,
    string? Email,
    string? DepartamentoId,
    string? Estado,
    string? FechaRetiro
);

public sealed record EmpleadoEventEnvelope(
    string Id,
    string Type,
    int Version,
    DateTimeOffset OccurredAt,
    string Producer,
    EmpleadoEventData Data
);

// Catálogo v1.0: vacaciones.programadas lleva únicamente estos cuatro campos.
public sealed record VacacionesProgramadasData(
    string VacacionId,
    string EmpleadoId,
    DateOnly FechaInicio,
    DateOnly FechaFin
);

public sealed record EventoEnvelope<T>(
    string Id,
    string Type,
    int Version,
    DateTimeOffset OccurredAt,
    string Producer,
    T Data
);

public sealed record ErrorResponse(
    bool Error,
    string Mensaje,
    object? Conflicto = null
);
