package co.edu.uniquindio.gestionempleados.dto;

public record MensajeDTO<T>(
        boolean error,
        T respuesta
) {
}
