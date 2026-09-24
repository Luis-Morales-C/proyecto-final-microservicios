package co.edu.uniquindio.gestionempleados.dto;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmpleadoAuditoriaDTO(
        String id,
        String nombre,
        String cargo,
        LocalDate fechaIngreso,
        LocalDateTime fechaRetiro,
        EstadoEmpleado estado
) {
    public static EmpleadoAuditoriaDTO desde(Empleado empleado) {
        return new EmpleadoAuditoriaDTO(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getCargo(),
                empleado.getFechaIngreso(),
                empleado.getFechaRetiro(),
                empleado.getEstado()
        );
    }
}