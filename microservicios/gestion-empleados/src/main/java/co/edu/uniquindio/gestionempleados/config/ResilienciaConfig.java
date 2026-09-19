package co.edu.uniquindio.gestionempleados.config;

import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienciaConfig {

    private static final Logger log =
            LoggerFactory.getLogger(ResilienciaConfig.class);

    @Bean
    public CircuitBreaker departamentosCircuitBreaker(
            @Value("${circuit-breaker.departamentos.umbral-fallos:3}")
            int umbralFallos,
            @Value("${circuit-breaker.departamentos.espera-abierto-segundos:30}")
            int esperaAbiertoSegundos
    ) {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                )
                .slidingWindowSize(umbralFallos)
                .minimumNumberOfCalls(umbralFallos)
                .failureRateThreshold(100)
                .waitDurationInOpenState(
                        Duration.ofSeconds(esperaAbiertoSegundos)
                )
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .permittedNumberOfCallsInHalfOpenState(1)
                // Solo "departamentos caído" cuenta como fallo.
                // Un 404 o un 4xx es una respuesta válida: el servicio está vivo.
                .recordExceptions(DepartamentoNoDisponibleException.class)
                .build();

        CircuitBreaker circuitBreaker =
                CircuitBreaker.of("departamentos", config);

        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn(
                        "CIRCUIT BREAKER 'departamentos': {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                )
        );

        return circuitBreaker;
    }
}
