using Npgsql;

namespace AuthService;

public sealed class UserRepository(NpgsqlDataSource dataSource)
{
    private const string Columns =
        "empleado_id, email, password_hash, rol, estado";

    private static Usuario Map(NpgsqlDataReader r) => new(
        r.GetString(0),
        r.GetString(1),
        r.IsDBNull(2) ? null : r.GetString(2),
        r.GetString(3),
        r.GetString(4));

    // ---------- Lecturas simples ----------

    public async Task<Usuario?> GetByEmailAsync(
        string email, CancellationToken ct)
    {
        await using var cmd = dataSource.CreateCommand(
            $"SELECT {Columns} FROM usuarios WHERE LOWER(email) = LOWER(@email)");
        cmd.Parameters.AddWithValue("email", email);

        await using var reader = await cmd.ExecuteReaderAsync(ct);
        return await reader.ReadAsync(ct) ? Map(reader) : null;
    }

    public async Task<Usuario?> GetByIdAsync(
        string empleadoId, CancellationToken ct)
    {
        await using var cmd = dataSource.CreateCommand(
            $"SELECT {Columns} FROM usuarios WHERE empleado_id = @id");
        cmd.Parameters.AddWithValue("id", empleadoId);

        await using var reader = await cmd.ExecuteReaderAsync(ct);
        return await reader.ReadAsync(ct) ? Map(reader) : null;
    }

    // ---------- Operaciones transaccionales ----------

    /// <summary>Lee la cuenta bloqueando la fila hasta el fin de la transacción.</summary>
    public async Task<Usuario?> GetForUpdateAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        string empleadoId, CancellationToken ct)
    {
        await using var cmd = new NpgsqlCommand(
            $"SELECT {Columns} FROM usuarios WHERE empleado_id = @id FOR UPDATE",
            conn, tx);
        cmd.Parameters.AddWithValue("id", empleadoId);

        await using var reader = await cmd.ExecuteReaderAsync(ct);
        return await reader.ReadAsync(ct) ? Map(reader) : null;
    }

    /// <summary>Inserta la cuenta; devuelve false si ya existía (id o email).</summary>
    public async Task<bool> InsertIfAbsentAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        Usuario user, CancellationToken ct)
    {
        await using var cmd = new NpgsqlCommand(
            """
            INSERT INTO usuarios
                (empleado_id, email, password_hash, rol, estado)
            VALUES
                (@id, @email, @hash, @rol, @estado)
            ON CONFLICT DO NOTHING
            """,
            conn, tx);

        cmd.Parameters.AddWithValue("id", user.EmpleadoId);
        cmd.Parameters.AddWithValue("email", user.Email);
        cmd.Parameters.AddWithValue("hash", (object?)user.PasswordHash ?? DBNull.Value);
        cmd.Parameters.AddWithValue("rol", user.Rol);
        cmd.Parameters.AddWithValue("estado", user.Estado);

        return await cmd.ExecuteNonQueryAsync(ct) > 0;
    }

    public async Task SetEstadoAsync(
        NpgsqlConnection conn, NpgsqlTransaction? tx,
        string empleadoId, string estado, CancellationToken ct)
    {
        await using var cmd = new NpgsqlCommand(
            """
            UPDATE usuarios
            SET estado = @estado, actualizado_en = NOW()
            WHERE empleado_id = @id
            """,
            conn, tx);

        cmd.Parameters.AddWithValue("estado", estado);
        cmd.Parameters.AddWithValue("id", empleadoId);
        await cmd.ExecuteNonQueryAsync(ct);
    }

    /// <summary>
    /// Guarda el nuevo hash. Si <paramref name="activate"/> es true, además
    /// pasa la cuenta de INACTIVA a ACTIVA (activación inicial).
    /// </summary>
    public async Task SetPasswordAsync(
        NpgsqlConnection conn, NpgsqlTransaction? tx,
        string empleadoId, string hash, bool activate, CancellationToken ct)
    {
        await using var cmd = new NpgsqlCommand(
            """
            UPDATE usuarios
            SET password_hash = @hash,
                estado = CASE WHEN @activate THEN 'ACTIVA' ELSE estado END,
                actualizado_en = NOW()
            WHERE empleado_id = @id
            """,
            conn, tx);

        cmd.Parameters.AddWithValue("hash", hash);
        cmd.Parameters.AddWithValue("activate", activate);
        cmd.Parameters.AddWithValue("id", empleadoId);
        await cmd.ExecuteNonQueryAsync(ct);
    }

    /// <summary>
    /// Deduplicación (Catálogo 2.1). Devuelve false si el evento ya se procesó.
    /// </summary>
    public async Task<bool> TryMarkProcessedAsync(
        NpgsqlConnection conn, NpgsqlTransaction tx,
        string eventId, CancellationToken ct)
    {
        await using var cmd = new NpgsqlCommand(
            """
            INSERT INTO eventos_procesados (id)
            VALUES (@id)
            ON CONFLICT (id) DO NOTHING
            RETURNING id
            """,
            conn, tx);

        cmd.Parameters.AddWithValue("id", eventId);
        return await cmd.ExecuteScalarAsync(ct) is not null;
    }
}
