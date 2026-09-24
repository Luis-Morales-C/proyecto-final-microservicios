package co.edu.uniquindio.gestionempleados.event;

import co.edu.uniquindio.gestionempleados.model.Empleado;

import java.time.Instant;
import java.util.UUID;

public record EmpleadoEvento(
        String id,
        String type,
        int version,
        Instant occurredAt,
        String producer,
        Empleado data
) {
    public static EmpleadoEvento creado(Empleado empleado) {
        return new EmpleadoEvento(
                UUID.randomUUID().toString(),
                "empleado.creado",
                1,
                Instant.now(),
                "gestion-empleados",
                empleado
        );
    }

    public static EmpleadoEvento actualizado(Empleado empleado) {
        return new EmpleadoEvento(
                UUID.randomUUID().toString(),
                "empleado.actualizado",
                1,
                Instant.now(),
                "gestion-empleados",
                empleado
        );
    }

    public static EmpleadoEvento retirado(Empleado empleado) {
        return new EmpleadoEvento(
                UUID.randomUUID().toString(),
                "empleado.retirado",
                1,
                Instant.now(),
                "gestion-empleados",
                empleado
        );
    }
}
