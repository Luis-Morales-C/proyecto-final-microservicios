const { randomUUID } = require("crypto");
const { pool } = require("../config/database");

async function listarTodas() {
    const resultado = await pool.query(`
    SELECT
      id,
      tipo,
      destinatario,
      mensaje,
      fecha_envio AS "fechaEnvio",
      empleado_id AS "empleadoId"
    FROM notificaciones
    ORDER BY fecha_envio DESC
  `);

    return resultado.rows;
}

async function listarPorEmpleado(empleadoId) {
    const resultado = await pool.query(
        `
      SELECT
        id,
        tipo,
        destinatario,
        mensaje,
        fecha_envio AS "fechaEnvio",
        empleado_id AS "empleadoId"
      FROM notificaciones
      WHERE empleado_id = $1
      ORDER BY fecha_envio DESC
    `,
        [empleadoId]
    );

    return resultado.rows;
}

async function registrarDesdeEvento(eventoId, notificacion) {
    const cliente = await pool.connect();

    try {
        await cliente.query("BEGIN");

        const eventoInsertado = await cliente.query(
            `
        INSERT INTO eventos_procesados (id)
        VALUES ($1)
        ON CONFLICT (id) DO NOTHING
        RETURNING id
      `,
            [eventoId]
        );

        if (eventoInsertado.rowCount === 0) {
            await cliente.query("ROLLBACK");

            return {
                duplicado: true,
                notificacion: null
            };
        }

        const resultado = await cliente.query(
            `
        INSERT INTO notificaciones (
          id,
          tipo,
          destinatario,
          mensaje,
          empleado_id
        )
        VALUES ($1, $2, $3, $4, $5)
        RETURNING
          id,
          tipo,
          destinatario,
          mensaje,
          fecha_envio AS "fechaEnvio",
          empleado_id AS "empleadoId"
      `,
            [
                randomUUID(),
                notificacion.tipo,
                notificacion.destinatario,
                notificacion.mensaje,
                notificacion.empleadoId
            ]
        );

        await cliente.query("COMMIT");

        return {
            duplicado: false,
            notificacion: resultado.rows[0]
        };
    } catch (error) {
        await cliente.query("ROLLBACK");
        throw error;
    } finally {
        cliente.release();
    }
}

module.exports = {
    listarTodas,
    listarPorEmpleado,
    registrarDesdeEvento
};