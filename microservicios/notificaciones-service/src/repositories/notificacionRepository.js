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

async function obtenerContacto(empleadoId) {
    const resultado = await pool.query(
        `
      SELECT
        empleado_id AS "empleadoId",
        nombre,
        email,
        activo
      FROM empleados_contacto
      WHERE empleado_id = $1
    `,
        [empleadoId]
    );

    return resultado.rows[0] || null;
}

async function obtenerContactoPorEmail(email) {
    const resultado = await pool.query(
        `
      SELECT
        empleado_id AS "empleadoId",
        nombre,
        email,
        activo
      FROM empleados_contacto
      WHERE LOWER(email) = LOWER($1)
      LIMIT 1
    `,
        [email]
    );

    return resultado.rows[0] || null;
}

/**
 * empleado.creado / empleado.retirado: sincroniza el contacto local y
 * deja constancia del evento, SIN generar notificación (los correos
 * salen con usuario.creado y cuenta.desactivada).
 * Si contacto.nombre es null (retirado) se conserva el nombre existente.
 */
async function registrarEmpleadoDesdeEvento(eventoId, contacto) {
    const cliente = await pool.connect();

    try {
        await cliente.query("BEGIN");

        const duplicado = await intentarRegistrarEvento(
            cliente,
            eventoId
        );

        if (duplicado) {
            await cliente.query("ROLLBACK");
            return {
                duplicado: true,
                notificacion: null
            };
        }

        await cliente.query(
            `
        INSERT INTO empleados_contacto (
          empleado_id,
          nombre,
          email,
          activo,
          actualizado_en
        )
        VALUES ($1, COALESCE($2::text, 'empleado'), $3, $4, NOW())
        ON CONFLICT (empleado_id) DO UPDATE
        SET nombre = COALESCE($2::text, empleados_contacto.nombre),
            email = EXCLUDED.email,
            activo = EXCLUDED.activo,
            actualizado_en = NOW()
      `,
            [
                contacto.empleadoId,
                contacto.nombre,
                contacto.email,
                contacto.activo
            ]
        );

        await cliente.query("COMMIT");

        return {
            duplicado: false,
            notificacion: null
        };
    } catch (error) {
        await cliente.query("ROLLBACK");
        throw error;
    } finally {
        cliente.release();
    }
}

async function registrarDesdeEvento(eventoId, notificacion) {
    const cliente = await pool.connect();

    try {
        await cliente.query("BEGIN");

        const duplicado = await intentarRegistrarEvento(
            cliente,
            eventoId
        );

        if (duplicado) {
            await cliente.query("ROLLBACK");

            return {
                duplicado: true,
                notificacion: null
            };
        }

        const guardada = await insertarNotificacion(
            cliente,
            notificacion
        );

        await cliente.query("COMMIT");

        return {
            duplicado: false,
            notificacion: guardada
        };
    } catch (error) {
        await cliente.query("ROLLBACK");
        throw error;
    } finally {
        cliente.release();
    }
}

async function intentarRegistrarEvento(cliente, eventoId) {
    const eventoInsertado = await cliente.query(
        `
      INSERT INTO eventos_procesados (id)
      VALUES ($1)
      ON CONFLICT (id) DO NOTHING
      RETURNING id
    `,
        [eventoId]
    );

    return eventoInsertado.rowCount === 0;
}

async function insertarNotificacion(cliente, notificacion) {
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
            notificacion.empleadoId || null
        ]
    );

    return resultado.rows[0];
}

module.exports = {
    listarTodas,
    listarPorEmpleado,
    obtenerContacto,
    obtenerContactoPorEmail,
    registrarEmpleadoDesdeEvento,
    registrarDesdeEvento
};
