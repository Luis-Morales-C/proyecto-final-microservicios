const notificacionRepository = require(
    "../repositories/notificacionRepository"
);

async function procesarEvento(evento) {
    validarEnvelope(evento);

    let resultado;

    switch (evento.type) {
        case "empleado.creado":
            resultado = await procesarEmpleado(evento, true);
            break;

        case "empleado.retirado":
            resultado = await procesarEmpleado(evento, false);
            break;

        case "vacaciones.programadas":
            resultado = await procesarVacaciones(evento);
            break;

        default:
            console.log(
                `Evento no gestionado: ${evento.type}`
            );
            return;
    }

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

async function procesarEmpleado(evento, activo) {
    const datos = evento.data || {};
    const empleadoId = datos.empleadoId || datos.id;
    const destinatario = datos.email;
    const nombre = datos.nombre || "empleado";

    validarContacto(evento, empleadoId, destinatario);

    const contacto = {
        empleadoId,
        nombre,
        email: destinatario,
        activo
    };

    const notificacion = activo
        ? {
            tipo: "BIENVENIDA",
            destinatario,
            empleadoId,
            mensaje: `Bienvenido/a ${nombre} al sistema de RRHH.`
        }
        : {
            tipo: "DESVINCULACION",
            destinatario,
            empleadoId,
            mensaje: `Hola ${nombre}, se ha registrado tu desvinculación de la empresa.`
        };

    return notificacionRepository
        .registrarEmpleadoYNotificacionDesdeEvento(
            evento.id,
            contacto,
            notificacion
        );
}

async function procesarVacaciones(evento) {
    const datos = evento.data || {};
    const empleadoId = datos.empleadoId;

    if (
        !empleadoId ||
        !datos.vacacionId ||
        !datos.fechaInicio ||
        !datos.fechaFin
    ) {
        throw new Error(
            `El evento ${evento.id} no cumple el contrato de vacaciones.programadas`
        );
    }

    const contacto = await notificacionRepository
        .obtenerContacto(empleadoId);

    if (!contacto) {
        throw new Error(
            `No existe contacto local para el empleado ${empleadoId}`
        );
    }

    const notificacion = {
        tipo: "VACACIONES",
        destinatario: contacto.email,
        empleadoId,
        mensaje: `Hola ${contacto.nombre}, tus vacaciones quedaron programadas del ${datos.fechaInicio} al ${datos.fechaFin}.`
    };

    return notificacionRepository.registrarDesdeEvento(
        evento.id,
        notificacion
    );
}

function validarEnvelope(evento) {
    if (
        !evento ||
        !evento.id ||
        !evento.type ||
        evento.version === undefined ||
        !evento.occurredAt ||
        !evento.producer ||
        !evento.data
    ) {
        throw new Error(
            "El evento no cumple el envelope canónico"
        );
    }
}

function validarContacto(evento, empleadoId, destinatario) {
    if (!empleadoId || !destinatario) {
        throw new Error(
            `El evento ${evento.id} no contiene empleadoId o email`
        );
    }
}

module.exports = {
    procesarEvento
};