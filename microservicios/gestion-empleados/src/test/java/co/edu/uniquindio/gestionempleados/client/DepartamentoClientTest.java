package co.edu.uniquindio.gestionempleados.client;

import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoServicioException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del cliente HTTP encargado de validar departamentos.
 *
 * Se utiliza un servidor HTTP local para simular las respuestas del
 * microservicio gestion-departamentos sin levantar Docker ni realizar
 * llamadas a servicios externos reales.
 *
 * También se simula el Circuit Breaker para verificar el comportamiento
 * cuando el circuito se encuentra abierto.
 */
@ExtendWith(MockitoExtension.class)
class DepartamentoClientTest {

    @Mock
    private CircuitBreaker circuitBreaker;

    private HttpServer servidor;

    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {

        /*
         * Se crea un pequeño servidor HTTP local utilizando una
         * herramienta incluida en el propio JDK.
         *
         * El puerto 0 indica que el sistema operativo debe elegir
         * automáticamente un puerto disponible.
         */
        servidor = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        servidor.createContext(
                "/departamentos",
                this::manejarPeticion
        );

        servidor.start();

        int puerto =
                servidor.getAddress().getPort();

        baseUrl =
                "http://localhost:" + puerto;

        /*
         * Por defecto hacemos que el Circuit Breaker permita
         * ejecutar la operación recibida.
         *
         * Esto permite probar realmente la lógica HTTP interna
         * de DepartamentoClient.
         */
        lenient().doAnswer(invocation -> {

            Runnable operacion =
                    invocation.getArgument(0);

            operacion.run();

            return null;

        }).when(circuitBreaker)
                .executeRunnable(any(Runnable.class));
    }

    @AfterEach
    void tearDown() {

        if (servidor != null) {

            servidor.stop(0);
        }
    }

    /**
     * Servidor HTTP simulado.
     *
     * La respuesta depende del identificador enviado en la URL:
     *
     * /departamentos/IT          -> 200
     * /departamentos/NO-EXISTE   -> 404
     * /departamentos/PROHIBIDO   -> 403
     */
    private void manejarPeticion(
            HttpExchange exchange
    ) throws IOException {

        String ruta =
                exchange
                        .getRequestURI()
                        .getPath();

        int status;

        if (ruta.endsWith("/NO-EXISTE")) {

            status = 404;

        } else if (ruta.endsWith("/PROHIBIDO")) {

            status = 403;

        } else {

            status = 200;
        }

        exchange.sendResponseHeaders(
                status,
                -1
        );

        exchange.close();
    }

    // =========================================================
    // CONSULTA EXITOSA
    // =========================================================

    @Test
    void debeConsultarDepartamentoCorrectamente() {

        // ARRANGE
        DepartamentoClient client =
                new DepartamentoClient(
                        baseUrl,
                        circuitBreaker
                );

        /*
         * ACT + ASSERT
         *
         * Si el departamento responde 200,
         * no debe producirse ninguna excepción.
         */
        assertDoesNotThrow(
                () -> client.consultarDepartamento(
                        "IT"
                )
        );

        // VERIFY
        verify(circuitBreaker)
                .executeRunnable(
                        any(Runnable.class)
                );
    }

    // =========================================================
    // DEPARTAMENTO NO ENCONTRADO
    // =========================================================

    @Test
    void debeLanzarExcepcionCuandoDepartamentoNoExiste() {

        // ARRANGE
        DepartamentoClient client =
                new DepartamentoClient(
                        baseUrl,
                        circuitBreaker
                );

        /*
         * ACT + ASSERT
         *
         * Una respuesta HTTP 404 debe traducirse a la
         * excepción propia del dominio.
         */
        assertThrows(
                DepartamentoNoEncontradoException.class,
                () -> client.consultarDepartamento(
                        "NO-EXISTE"
                )
        );
    }

    // =========================================================
    // OTRO ERROR 4XX
    // =========================================================

    @Test
    void debeLanzarExcepcionCuandoServicioRechazaSolicitud() {

        // ARRANGE
        DepartamentoClient client =
                new DepartamentoClient(
                        baseUrl,
                        circuitBreaker
                );

        /*
         * ACT + ASSERT
         *
         * Un error 4xx diferente de 404 representa una
         * solicitud rechazada por gestion-departamentos.
         */
        assertThrows(
                DepartamentoServicioException.class,
                () -> client.consultarDepartamento(
                        "PROHIBIDO"
                )
        );
    }

    // =========================================================
    // CIRCUIT BREAKER ABIERTO
    // =========================================================

    @Test
    void debeLanzarNoDisponibleCuandoCircuitoEstaAbierto() {

        // ARRANGE:
        // Se crea un Circuit Breaker real para evitar depender
        // de métodos internos que Mockito no tiene configurados.
        CircuitBreaker circuitoReal =
                CircuitBreaker.ofDefaults(
                        "departamentos-test"
                );

        // Se fuerza manualmente el estado OPEN.
        // En este estado el Circuit Breaker bloquea la llamada
        // antes de ejecutar la operación HTTP.
        circuitoReal.transitionToOpenState();

        DepartamentoClient client =
                new DepartamentoClient(
                        baseUrl,
                        circuitoReal
                );

        // ACT + ASSERT:
        // DepartamentoClient debe traducir CallNotPermittedException
        // a la excepción propia DepartamentoNoDisponibleException.
        assertThrows(
                DepartamentoNoDisponibleException.class,
                () -> client.consultarDepartamento(
                        "IT"
                )
        );
    }
}