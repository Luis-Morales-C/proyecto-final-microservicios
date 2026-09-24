const notificacionRepository = require(
    "../repositories/notificacionRepository"
);

function construirNotificacion(evento) {
    const datos = evento.data;

    if (!datos) {
        throw new Error(
            `El evento ${evento.id} no contiene data`
        );
    }

    const empleadoId = datos.id;
    const destinatario = datos.email;
    const nombre = datos.nombre || "empleado";

    if (!empleadoId || !destinatario) {
        throw new Error(
            `El evento ${evento.id} no contiene id o email del empleado`
        );
    }

    switch (evento.type) {
        case "empleado.creado":
            return {
                tipo: "BIENVENIDA",
                destinatario,
                empleadoId,
                mensaje: `Bienvenido/a ${nombre} al sistema de RRHH.`
            };

        case "empleado.retirado":
            return {
                tipo: "DESVINCULACION",
                destinatario,
                empleadoId,
                mensaje: `Hola ${nombre}, se ha registrado tu desvinculación de la empresa.`
            };

        default:
            return null;
    }
}

async function procesarEvento(evento) {
    if (!evento.id || !evento.type) {
        throw new Error(
            "El evento debe contener id y type"
        );
    }

    const notificacion = construirNotificacion(evento);

    if (!notificacion) {
        console.log(
            `Evento no gestionado: ${evento.type}`
        );

        return;
    }

    const resultado =
        await notificacionRepository.registrarDesdeEvento(
            evento.id,
            notificacion
        );

    if (resultado.duplicado) {
        console.log(
            `Evento duplicado descartado: ${evento.id}`
        );

        return;
    }

    console.log(
        JSON.stringify({
            accion: "NOTIFICACION_SIMULADA",
            tipo: resultado.notificacion.tipo,
            destinatario: resultado.notificacion.destinatario,
            mensaje: resultado.notificacion.mensaje,
            empleadoId: resultado.notificacion.empleadoId
        })
    );
}

module.exports = {
    procesarEvento
};