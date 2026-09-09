package co.edu.uniquindio.gestionempleados.client;

import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
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

    private final RestClient restClient;

    private static final int MAX_INTENTOS = 4;
    private static final long[] ESPERAS = {1000, 2000, 4000};

    public DepartamentoClient(
            @Value("${DEPARTAMENTOS_URL}") String departamentosUrl) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .baseUrl(departamentosUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public void consultarDepartamento(String departamentoId) {

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {

            try {

                restClient.get()
                        .uri("/departamentos/{id}", departamentoId)
                        .retrieve()
                        .toBodilessEntity();

                return;

            } catch (RestClientResponseException ex) {

                if (ex.getStatusCode().value() == 404) {
                    throw new DepartamentoNoEncontradoException(departamentoId);
                }

                if (ex.getStatusCode().is4xxClientError()) {
                    throw ex;
                }

                if (intento == MAX_INTENTOS) {
                    throw new DepartamentoNoDisponibleException();
                }

            } catch (ResourceAccessException ex) {

                if (intento == MAX_INTENTOS) {
                    throw new DepartamentoNoDisponibleException();
                }
            }

            esperar(intento);
        }
    }

    private void esperar(int intento) {

        try {
            Thread.sleep(ESPERAS[intento - 1]);

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();
            throw new DepartamentoNoDisponibleException();
        }
    }
}