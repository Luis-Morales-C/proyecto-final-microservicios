package co.edu.uniquindio.gestionempleados.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/empleados/circuit-breaker")
@Tag(
        name = "Circuit Breaker",
        description = "Estado del circuito hacia el servicio de departamentos"
)
public class CircuitBreakerController {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerController(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Operation(
            summary = "Estado del Circuit Breaker",
            description = "Retorna CLOSED, OPEN o HALF_OPEN."
    )
    @GetMapping
    public Map<String, Object> estado() {

        CircuitBreaker.Metrics metricas = circuitBreaker.getMetrics();

        return Map.of(
                "nombre", circuitBreaker.getName(),
                "estado", circuitBreaker.getState().name(),
                "llamadasFallidasEnVentana", metricas.getNumberOfFailedCalls(),
                "llamadasBloqueadas", metricas.getNumberOfNotPermittedCalls()
        );
    }
}
