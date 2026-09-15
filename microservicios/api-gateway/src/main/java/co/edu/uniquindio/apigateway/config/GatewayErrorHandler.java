package co.edu.uniquindio.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Manejador global de errores del Gateway.
 *
 * Distingue dos casos que antes se confundían bajo un mismo 503:
 *  - Ruta que no matchea ninguna definida en application.yml (ej. /ruta)
 *    -> debe responder 404, es un error del cliente, no del ecosistema.
 *  - Ruta válida, pero el servicio destino no responde (caído, timeout,
 *    host no resuelto dentro de la red de Docker)
 *    -> ahí sí responde 503 con el cuerpo JSON descriptivo.
 *
 * Se registra con la mayor precedencia (HIGHEST_PRECEDENCE) para que se
 * ejecute antes que el manejador de errores por defecto de WebFlux, pero
 * ahora respeta el status real de la excepción cuando existe uno.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String errorPhrase = status.getReasonPhrase();
        String message = buildMessage(status);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", errorPhrase);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + errorPhrase + "\"}")
                    .getBytes();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Si Spring ya trae un status explícito en la excepción (por ejemplo,
     * 404 cuando ninguna ruta matchea el path pedido), se respeta ese status
     * tal cual. Solo se fuerza 503 cuando la causa raíz es un fallo real de
     * conexión hacia el servicio destino.
     */
    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus statusCode = HttpStatus.resolve(rse.getStatusCode().value());
            if (statusCode != null) {
                return statusCode;
            }
        }

        if (isConnectionFailure(ex)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean isConnectionFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof TimeoutException
                    || current instanceof PrematureCloseException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildMessage(HttpStatus status) {
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "El servicio solicitado no está disponible en este momento. "
                    + "Intente nuevamente más tarde.";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "La ruta solicitada no existe en este API Gateway.";
        }
        return "Ocurrió un error inesperado al procesar la solicitud.";
    }
}