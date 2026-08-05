package co.edu.uniquindio.gestionempleados.model;

import java.time.LocalDate;

public record Empleado(
        String id,
        String nombre,
        String apellido,
        String email,
        String numeroEmpleado,
        String cargo,
        String area,
        String departamentoId,
        LocalDate fechaIngreso,
        EstadoEmpleado estado
) {
}