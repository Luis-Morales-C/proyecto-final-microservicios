package co.edu.uniquindio.gestionempleados.model;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record Empleado(

        String id,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        String apellido,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe tener un formato válido")
        String email,

        @NotBlank(message = "El número de empleado es obligatorio")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "El número de empleado solo puede contener letras mayúsculas, números y guiones")
        String numeroEmpleado,

        @NotBlank(message = "El cargo es obligatorio")
        String cargo,

        @NotBlank(message = "El área es obligatoria")
        String area,

        @NotBlank(message = "El departamento es obligatorio")
        String departamentoId,

        @NotNull(message = "La fecha de ingreso es obligatoria")
        @PastOrPresent(message = "La fecha de ingreso no puede ser una fecha futura")
        LocalDate fechaIngreso,

        EstadoEmpleado estado
) {
}