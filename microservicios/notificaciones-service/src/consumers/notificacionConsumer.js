const amqp = require("amqplib");

const { procesarEvento } = require(
    "../services/notificacionService"
);

const COLAS = [
    {
        nombre: "notificaciones.empleados",
        tipos: new Set([
            "empleado.creado",
            "empleado.retirado"
        ])
    },
    {
        nombre: "notificaciones.vacaciones",
        tipos: new Set([
            "vacaciones.programadas",
            "vacaciones.iniciadas",
            "vacaciones.finalizadas"
        ])
    },
    {
        // Eventos emitidos por el auth-service (Reto 5)
        nombre: "notificaciones.seguridad",
        tipos: new Set([
            "usuario.creado",
            "usuario.recuperacion",
            "cuenta.activada",
            "cuenta.desactivada"
        ])
    }
];

async function iniciarConsumidor() {
    const conexion = await amqp.connect({
        hostname: process.env.RABBITMQ_HOST || "rabbitmq",
        port: Number(process.env.RABBITMQ_PORT || 5672),
        username: process.env.RABBITMQ_USER,
        password: process.env.RABBITMQ_PASSWORD
    });

    const canal = await conexion.createChannel();
    await canal.prefetch(1);

    for (const configuracion of COLAS) {
        await canal.assertQueue(configuracion.nombre, {
            durable: true
        });

        await canal.consume(
            configuracion.nombre,
            async (mensaje) => {
                if (!mensaje) {
                    return;
                }

                try {
                    const evento = JSON.parse(
                        mensaje.content.toString("utf8")
                    );

                    if (!configuracion.tipos.has(evento.type)) {
                        console.log(
                            `Evento no gestionado en ${configuracion.nombre}: ${evento.type}`
                        );
                        canal.ack(mensaje);
                        return;
                    }

                    await procesarEvento(evento);
                    canal.ack(mensaje);
                } catch (error) {
                    console.error(
                        `Error procesando evento de ${configuracion.nombre}:`,
                        error
                    );

                    // Se reencola para permitir reintento. La tabla
                    // eventos_procesados hace que el efecto sea idempotente.
                    canal.nack(mensaje, false, true);
                }
            },
            { noAck: false }
        );

        console.log(
            `Escuchando cola: ${configuracion.nombre}`
        );
    }

    conexion.on("error", (error) => {
        console.error(
            "Error de conexión con RabbitMQ:",
            error
        );
    });

    conexion.on("close", () => {
        console.error(
            "Se cerró la conexión con RabbitMQ"
        );
        process.exit(1);
    });

    return conexion;
}

module.exports = {
    iniciarConsumidor
};
