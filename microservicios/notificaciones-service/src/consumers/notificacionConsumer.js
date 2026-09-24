const amqp = require("amqplib");

const { procesarEvento } = require(
    "../services/notificacionService"
);

const COLA_EMPLEADOS = "notificaciones.empleados";

async function iniciarConsumidor() {
    const conexion = await amqp.connect({
        hostname: process.env.RABBITMQ_HOST || "rabbitmq",
        port: Number(process.env.RABBITMQ_PORT || 5672),
        username: process.env.RABBITMQ_USER,
        password: process.env.RABBITMQ_PASSWORD
    });

    const canal = await conexion.createChannel();

    await canal.prefetch(1);

    await canal.assertQueue(COLA_EMPLEADOS, {
        durable: true
    });

    await canal.consume(
        COLA_EMPLEADOS,
        async (mensaje) => {
            if (!mensaje) {
                return;
            }

            try {
                const evento = JSON.parse(
                    mensaje.content.toString("utf8")
                );

                if (
                    evento.type !== "empleado.creado" &&
                    evento.type !== "empleado.retirado"
                ) {
                    console.log(
                        `Evento no gestionado: ${evento.type}`
                    );

                    canal.ack(mensaje);
                    return;
                }

                await procesarEvento(evento);

                canal.ack(mensaje);
            } catch (error) {
                console.error(
                    "Error procesando evento:",
                    error
                );

                // Cerramos el canal sin confirmar el mensaje.
                // RabbitMQ podrá reentregarlo cuando el servicio
                // vuelva a conectarse.
                try {
                    await canal.close();
                } finally {
                    await conexion.close();
                }

                process.exit(1);
            }
        },
        {
            noAck: false
        }
    );

    console.log(
        `Escuchando cola: ${COLA_EMPLEADOS}`
    );

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