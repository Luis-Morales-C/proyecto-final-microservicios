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
                    CHECK (fecha_fin >= fecha_inicio)
            );

            /*
             * MIGRACIÓN:
             *
             * CREATE TABLE IF NOT EXISTS no modifica una tabla
             * que ya existe. Si la base se creó con la regla
             * anterior (fecha_fin > fecha_inicio), se reemplaza
             * por >= para permitir períodos de un solo día.
             * Es idempotente: se puede ejecutar en cada arranque.
             */
            ALTER TABLE vacaciones
                DROP CONSTRAINT IF EXISTS ck_vacaciones_fechas;

            ALTER TABLE vacaciones
                ADD CONSTRAINT ck_vacaciones_fechas
                CHECK (fecha_fin >= fecha_inicio);

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
             * vacaciones PROGRAMADA, se cancelan
             * automáticamente al iniciar el servicio.
             *
             * Las vacaciones EN_CURSO NO se tocan: siguen su
             * curso hasta que el scheduler publique
             * vacaciones.finalizadas (caso borde del Reto 5:
             * auth-service debe ignorarlo si la cuenta fue
             * desactivada de forma permanente).
             */
            UPDATE vacaciones AS v
            SET estado = 'CANCELADA'
            FROM empleados_validos AS e
            WHERE v.empleado_id = e.id
              AND e.activo = FALSE
              AND v.estado = 'PROGRAMADA';
            """;

        await using var command =
            dataSource.CreateCommand(sql);

        await command.ExecuteNonQueryAsync(
            cancellationToken
        );
    }
}