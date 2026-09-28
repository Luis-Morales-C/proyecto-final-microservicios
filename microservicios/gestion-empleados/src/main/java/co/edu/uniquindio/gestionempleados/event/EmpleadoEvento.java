package co.edu.uniquindio.gestionempleados.event;

import co.edu.uniquindio.gestionempleados.model.Empleado;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Envelope canónico de eventos de empleado del Reto 4.
 *
 * El campo data usa contratos explícitos para no publicar accidentalmente
 * campos internos de la entidad JPA. Los nombres y cargas útiles forman
 * parte del contrato entre microservicios.
 */
public record EmpleadoEvento(
        String id,
        String type,
        int version,
        Instant occurredAt,
        String producer,
        Object data
) {

    public static EmpleadoEvento creado(Empleado empleado) {
        return nuevo(
                "empleado.creado",
                EmpleadoData.desde(empleado)
        );
    }

    public static EmpleadoEvento actualizado(Empleado empleado) {
        return nuevo(
                "empleado.actualizado",
                EmpleadoData.desde(empleado)
        );
    }

    public static EmpleadoEvento retirado(Empleado empleado) {
        return nuevo(
                "empleado.retirado",
                EmpleadoRetiradoData.desde(empleado)
        );
    }

    private static EmpleadoEvento nuevo(String type, Object data) {
        return new EmpleadoEvento(
                UUID.randomUUID().toString(),
                type,
                1,
                Instant.now(),
                "gestion-empleados",
                data
        );
    }

    /**
     * Contrato de data para empleado.creado y empleado.actualizado.
     */
    public record EmpleadoData(
            String empleadoId,
            String numeroEmpleado,
            String nombre,
            String apellido,
            String email,
            String departamentoId,
            String estado
    ) {
        static EmpleadoData desde(Empleado empleado) {
            return new EmpleadoData(
                    empleado.getId(),
                    empleado.getNumeroEmpleado(),
                    empleado.getNombre(),
                    empleado.getApellido(),
                    empleado.getEmail(),
                    empleado.getDepartamentoId(),
                    empleado.getEstado() == null
                            ? null
                            : empleado.getEstado().name()
            );
        }
    }

    /**
     * Contrato de data para empleado.retirado.
     */
    public record EmpleadoRetiradoData(
            String empleadoId,
            String numeroEmpleado,
            String nombre,
            String apellido,
            String email,
            String estado,
            LocalDateTime fechaRetiro
    ) {
        static EmpleadoRetiradoData desde(Empleado empleado) {
            return new EmpleadoRetiradoData(
                    empleado.getId(),
                    empleado.getNumeroEmpleado(),
                    empleado.getNombre(),
                    empleado.getApellido(),
                    empleado.getEmail(),
                    empleado.getEstado() == null
                            ? null
                            : empleado.getEstado().name(),
                    empleado.getFechaRetiro()
            );
        }
    }
}