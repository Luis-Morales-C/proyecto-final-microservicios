using Npgsql;

namespace AuthService;

public static class DatabaseInitializer
{
    public static async Task InitializeAsync(
        NpgsqlDataSource dataSource,
        CancellationToken cancellationToken = default)
    {
        const string sql =
            """
            CREATE TABLE IF NOT EXISTS usuarios (
                empleado_id VARCHAR(255) PRIMARY KEY,
                email VARCHAR(255) NOT NULL,
                password_hash VARCHAR(255),
                rol VARCHAR(20) NOT NULL,
                estado VARCHAR(30) NOT NULL,
                creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                CONSTRAINT ck_usuarios_rol
                    CHECK (rol IN ('ADMIN', 'USER')),

                CONSTRAINT ck_usuarios_estado
                    CHECK (estado IN (
                        'INACTIVA',
                        'ACTIVA',
                        'SUSPENDIDA_TEMPORAL',
                        'DESACTIVADA_PERMANENTE'
                    ))
            );

            CREATE UNIQUE INDEX IF NOT EXISTS ux_usuarios_email
                ON usuarios (LOWER(email));

            CREATE TABLE IF NOT EXISTS eventos_procesados (
                id VARCHAR(255) PRIMARY KEY,
                procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );
            """;

        await using var command = dataSource.CreateCommand(sql);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    /// <summary>
    /// Crea el administrador "semilla" a partir de variables de entorno.
    /// Si ya existe no se toca (no pisa una contraseña cambiada).
    /// </summary>
    public static async Task SeedAdminAsync(
        NpgsqlDataSource dataSource,
        PasswordService passwords,
        IConfiguration configuration,
        ILogger logger,
        CancellationToken cancellationToken = default)
    {
        var empleadoId = configuration["ADMIN_EMPLEADO_ID"] ?? "admin";
        var email = configuration["ADMIN_EMAIL"];
        var password = configuration["ADMIN_PASSWORD"];

        if (string.IsNullOrWhiteSpace(email) ||
            string.IsNullOrWhiteSpace(password))
        {
            logger.LogWarning(
                "ADMIN_EMAIL / ADMIN_PASSWORD no definidos: " +
                "no se crea el administrador semilla");
            return;
        }

        await using var command = dataSource.CreateCommand(
            """
            INSERT INTO usuarios
                (empleado_id, email, password_hash, rol, estado)
            VALUES
                (@id, @email, @hash, 'ADMIN', 'ACTIVA')
            ON CONFLICT DO NOTHING
            """);

        command.Parameters.AddWithValue("id", empleadoId);
        command.Parameters.AddWithValue("email", email.Trim().ToLowerInvariant());
        command.Parameters.AddWithValue("hash", passwords.Hash(password));

        var inserted = await command.ExecuteNonQueryAsync(cancellationToken);

        logger.LogInformation(
            inserted > 0
                ? "Administrador semilla creado: {EmpleadoId}"
                : "Administrador semilla ya existe: {EmpleadoId}",
            empleadoId);
    }
}
