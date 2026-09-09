package co.edu.uniquindio.gestionempleados.exception;

import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<
            MensajeDTO<Map<String, String>>
            > handleValidacion(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> errores =
                new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errores.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        new MensajeDTO<>(
                                true,
                                errores
                        )
                );
    }

    @ExceptionHandler(
            ConstraintViolationException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleConstraintViolation(
            ConstraintViolationException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        new MensajeDTO<>(
                                true,
                                "Los parámetros enviados "
                                        + "no son válidos"
                        )
                );
    }

    @ExceptionHandler(
            HttpMessageNotReadableException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleJsonInvalido(
            HttpMessageNotReadableException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        new MensajeDTO<>(
                                true,
                                "El cuerpo de la petición "
                                        + "no contiene un JSON válido "
                                        + "o tiene tipos de datos incorrectos"
                        )
                );
    }

    @ExceptionHandler(
            HttpMediaTypeNotSupportedException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleMediaType(
            HttpMediaTypeNotSupportedException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(
                        new MensajeDTO<>(
                                true,
                                "El tipo de contenido "
                                        + "enviado no es compatible"
                        )
                );
    }

    @ExceptionHandler(
            EmpleadoDuplicadoException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDuplicado(
            EmpleadoDuplicadoException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        new MensajeDTO<>(
                                true,
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            DataIntegrityViolationException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDataIntegrity(
            DataIntegrityViolationException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        new MensajeDTO<>(
                                true,
                                "No fue posible guardar el empleado "
                                        + "porque uno de sus valores "
                                        + "debe ser único"
                        )
                );
    }

    @ExceptionHandler(
            EmpleadoNoEncontradoException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleNoEncontrado(
            EmpleadoNoEncontradoException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        new MensajeDTO<>(
                                true,
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            DepartamentoNoEncontradoException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDepartamentoNoEncontrado(
            DepartamentoNoEncontradoException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        new MensajeDTO<>(
                                true,
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            DepartamentoNoDisponibleException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDepartamentoNoDisponible(
            DepartamentoNoDisponibleException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.SERVICE_UNAVAILABLE
                )
                .body(
                        new MensajeDTO<>(
                                true,
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            DepartamentoServicioException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDepartamentoServicio(
            DepartamentoServicioException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.BAD_GATEWAY
                )
                .body(
                        new MensajeDTO<>(
                                true,
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(
            NoHandlerFoundException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleRutaNoEncontrada(
            NoHandlerFoundException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        new MensajeDTO<>(
                                true,
                                "Recurso no encontrado"
                        )
                );
    }

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleMetodoNoSoportado(
            HttpRequestMethodNotSupportedException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(
                        new MensajeDTO<>(
                                true,
                                "El método HTTP utilizado "
                                        + "no está permitido para "
                                        + "este recurso"
                        )
                );
    }

    @ExceptionHandler(
            org.springframework.dao.DataAccessException.class
    )
    public ResponseEntity<MensajeDTO<String>>
    handleDatabase(
            org.springframework.dao.DataAccessException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.SERVICE_UNAVAILABLE
                )
                .body(
                        new MensajeDTO<>(
                                true,
                                "La base de datos "
                                        + "no está disponible"
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeDTO<String>>
    handleGeneral(Exception ex) {

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        new MensajeDTO<>(
                                true,
                                "Ocurrió un error interno "
                                        + "en el servidor"
                        )
                );
    }
}