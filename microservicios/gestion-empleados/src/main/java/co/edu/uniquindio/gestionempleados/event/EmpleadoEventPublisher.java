package co.edu.uniquindio.gestionempleados.event;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmpleadoEventPublisher {

    private static final String EXCHANGE = "rrhh.events";

    private final RabbitTemplate rabbitTemplate;

    public EmpleadoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarCreado(Empleado empleado) {
        EmpleadoEvento evento = EmpleadoEvento.creado(empleado);

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                evento.type(),
                evento
        );
    }

    public void publicarActualizado(Empleado empleado) {
        EmpleadoEvento evento = EmpleadoEvento.actualizado(empleado);

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                evento.type(),
                evento
        );
    }

    public void publicarRetirado(Empleado empleado) {
        EmpleadoEvento evento = EmpleadoEvento.retirado(empleado);

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                evento.type(),
                evento
        );
    }
}