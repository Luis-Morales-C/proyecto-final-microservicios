package co.edu.uniquindio.gestionempleados.client;

import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoServicioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class DepartamentoClient {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DepartamentoClient.class
            );

    private static final int MAX_INTENTOS = 4;

    private static final long[] ESPERAS = {
            1000,
            2000,
            4000
    };

    private final RestClient restClient;

    public DepartamentoClient(
            @Value("${DEPARTAMENTOS_URL}") String departamentosUrl
    ) {

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(2)
                        )
                        .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(2)
        );

        this.restClient =
                RestClient.builder()
                        .baseUrl(departamentosUrl)
                        .requestFactory(requestFactory)
                        .build();
    }

    public void consultarDepartamento(
            String departamentoId
    ) {

        for (
                int intento = 1;
                intento <= MAX_INTENTOS;
                intento++
        ) {

            try {

                log.info(
                        "Consultando departamento {}. Intento {}/{}",
                        departamentoId,
                        intento,
                        MAX_INTENTOS
                );

                restClient.get()
                        .uri(
                                "/departamentos/{id}",
                                departamentoId
                        )
                        .retrieve()
                        .toBodilessEntity();

                log.info(
                        "Departamento {} validado correctamente",
                        departamentoId
                );

                return;

            } catch (
                    RestClientResponseException ex
            ) {

                int statusCode =
                        ex.getStatusCode().value();

                if (statusCode == 404) {

                    throw new DepartamentoNoEncontradoException(
                            departamentoId
                    );
                }

                if (
                        ex.getStatusCode()
                                .is4xxClientError()
                ) {

                    throw new DepartamentoServicioException(
                            statusCode,
                            "El servicio de departamentos "
                                    + "rechazó la solicitud"
                    );
                }

                log.warn(
                        "Error {} al consultar departamentos. "
                                + "Intento {}/{}",
                        statusCode,
                        intento,
                        MAX_INTENTOS
                );

                if (
                        intento == MAX_INTENTOS
                ) {

                    throw new DepartamentoNoDisponibleException();
                }

            } catch (
                    ResourceAccessException ex
            ) {

                log.warn(
                        "Timeout o error de conexión con "
                                + "departamentos. Intento {}/{}",
                        intento,
                        MAX_INTENTOS
                );

                if (
                        intento == MAX_INTENTOS
                ) {

                    throw new DepartamentoNoDisponibleException();
                }
            }

            esperar(intento);
        }
    }

    private void esperar(int intento) {

        try {

            Thread.sleep(
                    ESPERAS[intento - 1]
            );

        } catch (
                InterruptedException ex
        ) {

            Thread.currentThread().interrupt();

            throw new DepartamentoNoDisponibleException();
        }
    }
}