using Npgsql;

namespace VacacionesService;

public sealed class VacationRepository(NpgsqlDataSource dataSource)
{
    public async Task<bool> PingAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using var command = dataSource.CreateCommand("SELECT 1");
            await command.ExecuteScalarAsync(cancellationToken);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public async Task<EmpleadoReplica?> ObtenerEmpleadoActivoAsync(
        string empleadoId,
        CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT id, activo
            FROM empleados_validos
            WHERE id = @empleadoId AND activo = TRUE
            """);
        command.Parameters.AddWithValue("empleadoId", empleadoId);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            return null;

        return new EmpleadoReplica(
            reader.GetString(0),
            reader.GetBoolean(1)
        );
    }

    public async Task<Vacacion?> BuscarSolapamientoAsync(
        string empleadoId,
        DateOnly fechaInicio,
        DateOnly fechaFin,
        CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion
            FROM vacaciones
            WHERE empleado_id = @empleadoId
              AND estado IN ('PROGRAMADA', 'EN_CURSO')
              AND fecha_inicio <= @fechaFin
              AND fecha_fin >= @fechaInicio
            ORDER BY fecha_inicio
            LIMIT 1
            """);
        command.Parameters.AddWithValue("empleadoId", empleadoId);
        command.Parameters.AddWithValue("fechaInicio", fechaInicio);
        command.Parameters.AddWithValue("fechaFin", fechaFin);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadVacation(reader) : null;
    }

    public async Task<Vacacion> CrearAsync(
        string empleadoId,
        DateOnly fechaInicio,
        DateOnly fechaFin,
        CancellationToken cancellationToken)
    {
        var id = $"V-{DateTime.UtcNow.Year}-{Guid.NewGuid().ToString("N")[..8].ToUpperInvariant()}";
        var now = DateTimeOffset.UtcNow;

        await using var command = dataSource.CreateCommand("""
            INSERT INTO vacaciones (id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion)
            VALUES (@id, @empleadoId, @fechaInicio, @fechaFin, @estado, @fechaCreacion)
            """);
        command.Parameters.AddWithValue("id", id);
        command.Parameters.AddWithValue("empleadoId", empleadoId);
        command.Parameters.AddWithValue("fechaInicio", fechaInicio);
        command.Parameters.AddWithValue("fechaFin", fechaFin);
        command.Parameters.AddWithValue("estado", EstadosVacaciones.Programada);
        command.Parameters.AddWithValue("fechaCreacion", now);
        await command.ExecuteNonQueryAsync(cancellationToken);

        return new Vacacion(id, empleadoId, fechaInicio, fechaFin, EstadosVacaciones.Programada, now);
    }

    public async Task<Vacacion?> ConsultarAsync(string id, CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion
            FROM vacaciones
            WHERE id = @id
            """);
        command.Parameters.AddWithValue("id", id);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadVacation(reader) : null;
    }

    public async Task<IReadOnlyList<Vacacion>> ListarAsync(
        string? empleadoId,
        CancellationToken cancellationToken)
    {
        var sql = """
            SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion
            FROM vacaciones
            """;
        if (!string.IsNullOrWhiteSpace(empleadoId))
            sql += " WHERE empleado_id = @empleadoId";
        sql += " ORDER BY fecha_creacion DESC";

        await using var command = dataSource.CreateCommand(sql);
        if (!string.IsNullOrWhiteSpace(empleadoId))
            command.Parameters.AddWithValue("empleadoId", empleadoId);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var results = new List<Vacacion>();
        while (await reader.ReadAsync(cancellationToken))
            results.Add(ReadVacation(reader));
        return results;
    }

    public async Task<(Vacacion? Vacation, string? Error)> CancelarAsync(
        string id,
        CancellationToken cancellationToken)
    {
        var vacation = await ConsultarAsync(id, cancellationToken);
        if (vacation is null)
            return (null, "NO_ENCONTRADA");
        if (vacation.Estado != EstadosVacaciones.Programada)
            return (vacation, "ESTADO_INVALIDO");

        var today = DateOnly.FromDateTime(DateTime.UtcNow);
        if (vacation.FechaInicio <= today)
            return (vacation, "YA_INICIO");

        await using var command = dataSource.CreateCommand("""
            UPDATE vacaciones
            SET estado = 'CANCELADA'
            WHERE id = @id AND estado = 'PROGRAMADA'
            """);
        command.Parameters.AddWithValue("id", id);
        await command.ExecuteNonQueryAsync(cancellationToken);

        return (vacation with { Estado = EstadosVacaciones.Cancelada }, null);
    }

    private static Vacacion ReadVacation(NpgsqlDataReader reader) => new(
        reader.GetString(0),
        reader.GetString(1),
        reader.GetFieldValue<DateOnly>(2),
        reader.GetFieldValue<DateOnly>(3),
        reader.GetString(4),
        reader.GetFieldValue<DateTimeOffset>(5)
    );

    public async Task<IReadOnlyList<Vacacion>> ObtenerProgramadasParaIniciarAsync(
        DateOnly hoy, CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion
            FROM vacaciones
            WHERE estado = 'PROGRAMADA' AND fecha_inicio <= @hoy
            ORDER BY fecha_inicio
            """);
        command.Parameters.AddWithValue("hoy", hoy);
        return await LeerListaAsync(command, cancellationToken);
    }

    public async Task<IReadOnlyList<Vacacion>> ObtenerEnCursoParaFinalizarAsync(
        DateOnly hoy, CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, fecha_creacion
            FROM vacaciones
            WHERE estado = 'EN_CURSO' AND fecha_fin < @hoy
            ORDER BY fecha_fin
            """);
        command.Parameters.AddWithValue("hoy", hoy);
        return await LeerListaAsync(command, cancellationToken);
    }

    // UPDATE condicionado al estado de origen: la transición es atómica y no pisa cambios concurrentes.
    public async Task<bool> TransicionarEstadoAsync(
        string id, string desde, string hacia, CancellationToken cancellationToken)
    {
        await using var command = dataSource.CreateCommand("""
            UPDATE vacaciones
            SET estado = @hacia
            WHERE id = @id AND estado = @desde
            """);
        command.Parameters.AddWithValue("id", id);
        command.Parameters.AddWithValue("desde", desde);
        command.Parameters.AddWithValue("hacia", hacia);
        return await command.ExecuteNonQueryAsync(cancellationToken) == 1;
    }

    private static async Task<IReadOnlyList<Vacacion>> LeerListaAsync(
        NpgsqlCommand command, CancellationToken cancellationToken)
    {
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var results = new List<Vacacion>();
        while (await reader.ReadAsync(cancellationToken))
            results.Add(ReadVacation(reader));
        return results;
    }
}
