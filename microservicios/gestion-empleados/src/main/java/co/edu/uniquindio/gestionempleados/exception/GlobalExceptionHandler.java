package co.edu.uniquindio.gestionempleados.exception;

import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MensajeDTO<Map<String, String>>> handleValidacion(
            MethodArgumentNotValidException ex) {

        Map<String, String> errores = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errores.put(error.getField(), error.getDefaultMessage())
                );

        return ResponseEntity.badRequest()
                .body(new MensajeDTO<>(true, errores));
    }

    @ExceptionHandler(EmpleadoDuplicadoException.class)
    public ResponseEntity<MensajeDTO<String>> handleDuplicado(
            EmpleadoDuplicadoException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }

    @ExceptionHandler(EmpleadoNoEncontradoException.class)
    public ResponseEntity<MensajeDTO<String>> handleNoEncontrado(
            EmpleadoNoEncontradoException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<MensajeDTO<String>> handleRutaNoEncontrada(
            NoHandlerFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MensajeDTO<>(true, "Recurso no encontrado"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<MensajeDTO<String>> handleMetodoNoSoportado(
            HttpRequestMethodNotSupportedException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MensajeDTO<>(true, "Recurso no encontrado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeDTO<String>> handleGeneral(Exception ex) {

        return ResponseEntity.internalServerError()
                .body(new MensajeDTO<>(
                        true,
                        "Ocurrió un error inesperado: " + ex.getMessage()
                ));
    }

    @ExceptionHandler(DepartamentoNoEncontradoException.class)
    public ResponseEntity<MensajeDTO<String>> handleDepartamentoNoEncontrado(
            DepartamentoNoEncontradoException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }

    @ExceptionHandler(DepartamentoNoDisponibleException.class)
    public ResponseEntity<MensajeDTO<String>> handleDepartamentoNoDisponible(
            DepartamentoNoDisponibleException ex) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new MensajeDTO<>(true, ex.getMessage()));
    }
}
