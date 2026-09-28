using Npgsql;

namespace VacacionesService;

public static class DatabaseInitializer
{
    public static async Task InitializeAsync(
        NpgsqlDataSource dataSource,
        CancellationToken cancellationToken = default)
    {
        const string sql =
            """
            CREATE TABLE IF NOT EXISTS vacaciones (
                id VARCHAR(255) PRIMARY KEY,
                empleado_id VARCHAR(255) NOT NULL,
                fecha_inicio DATE NOT NULL,
                fecha_fin DATE NOT NULL,
                estado VARCHAR(30) NOT NULL,
                fecha_creacion TIMESTAMPTZ NOT NULL
                    DEFAULT NOW(),

                CONSTRAINT ck_vacaciones_fechas
                    CHECK (fecha_fin > fecha_inicio)
            );

            CREATE INDEX IF NOT EXISTS
                idx_vacaciones_empleado
            ON vacaciones (empleado_id);

            CREATE INDEX IF NOT EXISTS
                idx_vacaciones_solapamiento
            ON vacaciones (
                empleado_id,
                fecha_inicio,
                fecha_fin,
                estado
            );

            CREATE TABLE IF NOT EXISTS empleados_validos (
                id VARCHAR(255) PRIMARY KEY,
                activo BOOLEAN NOT NULL DEFAULT TRUE,
                actualizado_en TIMESTAMPTZ
                    NOT NULL DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS eventos_procesados (
                id VARCHAR(255) PRIMARY KEY,
                procesado_en TIMESTAMPTZ
                    NOT NULL DEFAULT NOW()
            );

            /*
             * RECONCILIACIÓN:
             *
             * Si existen empleados que ya están marcados
             * como retirados/inactivos pero conservan
             * vacaciones PROGRAMADA o EN_CURSO,
             * se corrigen automáticamente al iniciar
             * el servicio.
             */
            UPDATE vacaciones AS v
            SET estado = 'CANCELADA'
            FROM empleados_validos AS e
            WHERE v.empleado_id = e.id
              AND e.activo = FALSE
              AND v.estado IN (
                  'PROGRAMADA',
                  'EN_CURSO'
              );
            """;

        await using var command =
            dataSource.CreateCommand(sql);

        await command.ExecuteNonQueryAsync(
            cancellationToken
        );
    }
}