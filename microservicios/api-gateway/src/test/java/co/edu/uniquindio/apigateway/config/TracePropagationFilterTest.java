package co.edu.uniquindio.apigateway.config;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TracePropagationFilterTest {

    @Test
    void dejaContinuarLaPeticionConOpenTelemetryNoop() {
        TracePropagationFilter filter = new TracePropagationFilter(OpenTelemetry.noop());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health").build());
        AtomicBoolean invoked = new AtomicBoolean(false);
        GatewayFilterChain chain = forwarded -> {
            invoked.set(true);
            assertEquals("/health", forwarded.getRequest().getPath().value());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(invoked.get());
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
    }
}
