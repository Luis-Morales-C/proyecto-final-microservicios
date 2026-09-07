package co.edu.uniquindio.gestionempleados.exception;

import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Errores de validación de campos (@NotBlank, @Email, @Pattern, etc.)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeDTO<Map<String, String>>> handleValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new MensajeDTO<>(true, errores));
    }

    @ExceptionHandler(EmpleadoDuplicadoException.class)
    public ResponseEntity<MensajeDTO<String>> handleDuplicado(EmpleadoDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }

    @ExceptionHandler(EmpleadoNoEncontradoException.class)
    public ResponseEntity<MensajeDTO<String>> handleNoEncontrado(EmpleadoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeDTO<String>> handleGeneral(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new MensajeDTO<>(true, "Ocurrió un error inesperado: " + ex.getMessage()));
    }
}