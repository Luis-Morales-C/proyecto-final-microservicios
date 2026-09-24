package co.edu.uniquindio.gestionempleados.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ActualizarEmpleadoDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        String apellido,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe tener un formato válido")
        String email,

        @NotBlank(message = "El cargo es obligatorio")
        String cargo,

        @NotBlank(message = "El área es obligatoria")
        String area,

        @NotBlank(message = "El departamento es obligatorio")
        String departamentoId
) {
}