package co.edu.uniquindio.gestionempleados.event;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmpleadoEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(EmpleadoEventPublisher.class);

    private static final String EXCHANGE = "rrhh.events";

    private final RabbitTemplate rabbitTemplate;

    public EmpleadoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarCreado(Empleado empleado) {
        publicar(EmpleadoEvento.creado(empleado));
    }

    public void publicarActualizado(Empleado empleado) {
        publicar(EmpleadoEvento.actualizado(empleado));
    }

    public void publicarRetirado(Empleado empleado) {
        publicar(EmpleadoEvento.retirado(empleado));
    }

    /**
     * Reto 4: la operación de base de datos ya fue confirmada antes de
     * publicar el evento. Si RabbitMQ no está disponible, se registra el
     * error pero no se revierte ni se transforma en fallo la operación REST.
     */
    private void publicar(EmpleadoEvento evento) {
        try {
            rabbitTemplate.convertAndSend(
                    EXCHANGE,
                    evento.type(),
                    evento
            );

            log.info(
                    "Evento {} publicado. id={}",
                    evento.type(),
                    evento.id()
            );

        } catch (RuntimeException ex) {
            log.error(
                    "La operación de empleado ya fue persistida, "
                            + "pero no fue posible publicar {}. id={}",
                    evento.type(),
                    evento.id(),
                    ex
            );
        }
    }
}