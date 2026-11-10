package co.edu.uniquindio.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler();

    @Test
    void respeta404DeSpring() {
        var exchange = exchange("/no-existe");
        StepVerifier.create(handler.handle(exchange,
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "no route")))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("La ruta solicitada no existe"));
    }

    @Test
    void conexionCaidaSeConvierteEn503() {
        var exchange = exchange("/empleados");
        StepVerifier.create(handler.handle(exchange, new RuntimeException(new ConnectException("refused"))))
                .verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("no está disponible"));
    }

    @Test
    void timeoutSeConvierteEn503() {
        var exchange = exchange("/perfiles");
        StepVerifier.create(handler.handle(exchange, new TimeoutException("timeout")))
                .verifyComplete();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void unauthorizedConservaElMotivo() {
        var exchange = exchange("/empleados");
        StepVerifier.create(handler.handle(exchange,
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token inválido")))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("token inválido"));
    }

    @Test
    void forbiddenSinMotivoUsaMensajeSeguro() {
        var exchange = exchange("/empleados");
        StepVerifier.create(handler.handle(exchange,
                        new ResponseStatusException(HttpStatus.FORBIDDEN, null)))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("No tiene permisos"));
    }

    @Test
    void errorGenericoEs500() {
        var exchange = exchange("/empleados");
        StepVerifier.create(handler.handle(exchange, new IllegalStateException("boom")))
                .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        assertTrue(body(exchange).contains("error inesperado"));
    }

    @Test
    void respuestaYaComprometidaPropagaExcepcion() {
        var exchange = exchange("/empleados");
        exchange.getResponse().setComplete().block();
        IllegalStateException expected = new IllegalStateException("boom");

        StepVerifier.create(handler.handle(exchange, expected))
                .expectErrorMatches(error -> error == expected)
                .verify();
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }


    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
