const notificacionRepository = require(
    "../repositories/notificacionRepository"
);

// Base del enlace de activación/recuperación (simulado).
const RESET_URL =
    process.env.APP_RESET_URL || "https://app.empresa.com/reset";

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
        case "vacaciones.iniciadas":
        case "vacaciones.finalizadas":
            resultado = await procesarVacaciones(evento);
            break;

        case "usuario.creado":
        case "usuario.recuperacion":
            resultado = await procesarToken(evento);
            break;

        case "cuenta.activada":
        case "cuenta.desactivada":
            resultado = await procesarCuenta(evento);
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

    if (!resultado.notificacion) {
        // Eventos que solo dejan traza (no envían correo).
        console.log(
            `[TRAZA] ${evento.type} | empleadoId: ${
                (evento.data && evento.data.empleadoId) || "-"
            } | evento: ${evento.id}`
        );
        return;
    }

    imprimirNotificacion(
        resultado.notificacion,
        resultado.mensajeConsola
    );
}

/**
 * Simula el envío del correo. Formato pedido por el Reto 5.
 * mensajeConsola puede incluir el token; el mensaje persistido nunca.
 */
function imprimirNotificacion(notificacion, mensajeConsola) {
    console.log(
        `[NOTIFICACIÓN] Tipo: ${notificacion.tipo} | ` +
        `Para: ${notificacion.destinatario} | ` +
        `Mensaje: "${mensajeConsola || notificacion.mensaje}"`
    );
}

// empleado.creado / empleado.retirado: solo sincronizan el contacto local
// y dejan traza. El correo de bienvenida sale con usuario.creado y el de
// despedida con cuenta.desactivada (Catálogo 3.1 y 3.3).
async function procesarEmpleado(evento, activo) {
    const datos = evento.data || {};
    const empleadoId = datos.empleadoId || datos.id;
    const destinatario = datos.email;

    validarContacto(evento, empleadoId, destinatario);

    return notificacionRepository.registrarEmpleadoDesdeEvento(
        evento.id,
        {
            empleadoId,
            // empleado.retirado no trae nombre: null conserva el existente.
            nombre: datos.nombre || null,
            email: destinatario,
            activo
        }
    );
}

async function procesarVacaciones(evento) {
    const datos = evento.data || {};
    const empleadoId = datos.empleadoId;

    if (!empleadoId) {
        throw new Error(
            `El evento ${evento.id} no contiene empleadoId (${evento.type})`
        );
    }

    if (evento.type === "vacaciones.programadas") {
        if (!datos.fechaInicio || !datos.fechaFin) {
            throw new Error(
                `El evento ${evento.id} no cumple el contrato de vacaciones.programadas`
            );
        }
    }

    const contacto = await notificacionRepository
        .obtenerContacto(empleadoId);

    const destinatario = datos.email || (contacto && contacto.email);

    if (!destinatario) {
        throw new Error(
            `No hay email para el empleado ${empleadoId} (evento ${evento.id})`
        );
    }

    const nombre = (contacto && contacto.nombre) || "empleado";

    let mensaje;

    switch (evento.type) {
        case "vacaciones.programadas":
            mensaje =
                `Hola ${nombre}, tus vacaciones quedaron programadas ` +
                `del ${datos.fechaInicio} al ${datos.fechaFin}.`;
            break;

        case "vacaciones.iniciadas":
            mensaje =
                `Hola ${nombre}, tu período de vacaciones inició. ` +
                `Tu acceso al sistema estará suspendido hasta el ${datos.fechaFin}.`;
            break;

        default: // vacaciones.finalizadas
            mensaje =
                `Hola ${nombre}, tu período de vacaciones finalizó.`;
    }

    return notificacionRepository.registrarDesdeEvento(
        evento.id,
        {
            tipo: "VACACIONES",
            destinatario,
            empleadoId,
            mensaje
        }
    );
}

// usuario.creado / usuario.recuperacion
async function procesarToken(evento) {
    const datos = evento.data || {};
    const esCreacion = evento.type === "usuario.creado";

    const destinatario = datos.email;
    const token = esCreacion
        ? datos.tokenActivacion
        : datos.tokenRecuperacion;

    if (!destinatario || !token) {
        throw new Error(
            `El evento ${evento.id} no cumple el contrato de ${evento.type}`
        );
    }

    const contacto = await buscarContacto(datos.empleadoId, destinatario);
    const nombre = (contacto && contacto.nombre) || null;
    const empleadoId =
        datos.empleadoId || (contacto && contacto.empleadoId) || null;

    const enlace = `${RESET_URL}?token=${token}`;
    const vence = datos.expiraEn ? ` (vence ${datos.expiraEn})` : "";

    const saludo = nombre ? `Hola ${nombre}. ` : "";
    const intro = esCreacion
        ? "Bienvenido/a al sistema de RRHH. Para establecer su contraseña"
        : "Para restablecer su contraseña";

    // Mensaje que se ve en consola (simula el correo, incluye el enlace).
    const mensajeConsola =
        `${saludo}${intro} use el siguiente enlace${vence}: ${enlace}`;

    // Mensaje que se guarda en BD: SIN token. GET /notificaciones lo
    // expone a cualquier usuario autenticado; un token ahí permitiría
    // tomar la cuenta de otro.
    const mensaje =
        `${saludo}${intro} se envió un enlace de un solo uso${vence}.`;

    const resultado = await notificacionRepository.registrarDesdeEvento(
        evento.id,
        {
            tipo: "SEGURIDAD",
            destinatario,
            empleadoId,
            mensaje
        }
    );

    return { ...resultado, mensajeConsola };
}

// cuenta.activada / cuenta.desactivada
async function procesarCuenta(evento) {
    const datos = evento.data || {};
    const destinatario = datos.email;

    if (!destinatario || !datos.motivo) {
        throw new Error(
            `El evento ${evento.id} no cumple el contrato de ${evento.type}`
        );
    }

    const contacto = await buscarContacto(datos.empleadoId, destinatario);
    const nombre = (contacto && contacto.nombre) || "empleado";

    let mensaje;

    if (evento.type === "cuenta.desactivada") {
        mensaje = datos.permanente
            ? `Hola ${nombre}, su cuenta fue desactivada de forma ` +
            `permanente por su retiro de la empresa.`
            : `Hola ${nombre}, su cuenta fue desactivada temporalmente ` +
            `por vacaciones. Se reactivará al finalizar el período.`;
    } else {
        mensaje = datos.motivo === "FIN_VACACIONES"
            ? `Bienvenido de regreso, ${nombre}. Su cuenta fue reactivada.`
            : `Hola ${nombre}, su cuenta fue activada. Ya puede iniciar sesión.`;
    }

    return notificacionRepository.registrarDesdeEvento(
        evento.id,
        {
            tipo: "CUENTA",
            destinatario,
            empleadoId: datos.empleadoId || null,
            mensaje
        }
    );
}

async function buscarContacto(empleadoId, email) {
    if (empleadoId) {
        const porId = await notificacionRepository
            .obtenerContacto(empleadoId);

        if (porId) {
            return porId;
        }
    }

    return notificacionRepository.obtenerContactoPorEmail(email);
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
