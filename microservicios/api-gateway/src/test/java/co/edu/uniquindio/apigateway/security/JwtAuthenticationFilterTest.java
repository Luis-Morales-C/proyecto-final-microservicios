package co.edu.uniquindio.apigateway.security;

import co.edu.uniquindio.apigateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "clave-academica-reto5-cambiar-en-produccion-0123456789";
    private static final String ISSUER = "auth-service";

    private JwtAuthenticationFilter filter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer(ISSUER);
        filter = new JwtAuthenticationFilter(properties);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void healthEsPublico() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/health", null);
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, chain(forwarded)))
                .verifyComplete();

        assertNotNull(forwarded.get());
    }

    @Test
    void loginEsPublico() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/auth/login", null);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void documentacionGetEsPublica() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados/docs/index.html", null);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void optionsCorsNoRequiereToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.OPTIONS, "/empleados", null);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void rutaProtegidaSinTokenRetorna401() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados", null);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseStatusException.class, error);
                    assertEquals(401, ((ResponseStatusException) error).getStatusCode().value());
                })
                .verify();
    }

    @Test
    void tokenInvalidoRetorna401() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados", "Bearer token-invalido");

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        401, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void tokenExpiradoRetorna401() {
        String token = Jwts.builder()
                .subject("E001")
                .claim("role", "USER")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados", "Bearer " + token);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        401, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void tokenResetNoSirveComoTokenAcceso() {
        String token = token("E001", "USER", "RESET_PASSWORD", ISSUER);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados", "Bearer " + token);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        401, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void tokenSinRolRetorna401() {
        String token = Jwts.builder()
                .subject("E001")
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/empleados", "Bearer " + token);

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        401, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void adminPuedeEscribirYPropagaIdentidad() {
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = exchange(
                HttpMethod.DELETE,
                "/empleados/E001",
                "Bearer " + token("admin", "ADMIN", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(forwarded))).verifyComplete();

        assertEquals("admin", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ADMIN", forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void userPuedeLeer() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.GET, "/departamentos", "Bearer " + token("E001", "USER", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void userNoPuedeEliminar() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.DELETE, "/empleados/E001", "Bearer " + token("E001", "USER", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        403, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void userPuedeCambiarSuPassword() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/auth/change-password", "Bearer " + token("E001", "USER", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void userPuedeActualizarSuPerfil() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.PUT, "/perfiles/E001", "Bearer " + token("E001", "USER", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .verifyComplete();
    }

    @Test
    void userNoPuedeActualizarPerfilAjeno() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.PUT, "/perfiles/E002", "Bearer " + token("E001", "USER", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        403, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void rolDesconocidoSeDeniega() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/departamentos", "Bearer " + token("E001", "OTRO", null, ISSUER));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        403, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void issuerIncorrectoRetorna401() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.GET, "/empleados", "Bearer " + token("E001", "USER", null, "otro-issuer"));

        StepVerifier.create(filter.filter(exchange, chain(new AtomicReference<>())))
                .expectErrorSatisfies(error -> assertEquals(
                        401, ((ResponseStatusException) error).getStatusCode().value()))
                .verify();
    }

    @Test
    void ordenEsMenosCien() {
        assertEquals(-100, filter.getOrder());
    }

    private String token(String sub, String role, String type, String issuer) {
        var builder = Jwts.builder()
                .subject(sub)
                .claim("role", role)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)));
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.signWith(key).compact();
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String authorization) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.method(method, path);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static GatewayFilterChain chain(AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}
