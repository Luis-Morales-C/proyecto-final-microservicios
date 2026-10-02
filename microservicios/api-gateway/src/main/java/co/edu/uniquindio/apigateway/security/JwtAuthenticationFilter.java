package co.edu.uniquindio.apigateway.security;

import co.edu.uniquindio.apigateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Valida el JWT emitido por auth-service (HMAC-SHA256) y aplica las reglas
 * de autorización del ecosistema:
 *
 *  - Sin token en una ruta protegida            -> 401
 *  - Token inválido, expirado o mal firmado     -> 401
 *  - Token de tipo RESET_PASSWORD (claim "type")-> 401 (no sirve para acceder a recursos)
 *  - Rol ADMIN                                  -> acceso total
 *  - Rol USER, método de lectura (GET/HEAD)     -> permitido
 *  - Rol USER, método de escritura              -> 403, salvo:
 *      - POST /auth/change-password             -> permitido (el propio auth-service
 *                                                   valida que el usuario cambia su clave)
 *      - PUT  /perfiles/{empleadoId}             -> permitido solo si empleadoId == sub (ABAC)
 *
 * Se ejecuta antes que el enrutamiento (getOrder() muy negativo) para que
 * ninguna petición no autorizada llegue a tocar un microservicio.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RESET_TYPE_CLAIM = "type";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    /** Rutas que no requieren token: todavía no hay sesión en ese punto del flujo. */
    private static final List<RouteRule> RUTAS_PUBLICAS = List.of(
            new RouteRule(HttpMethod.POST, "/auth/login"),
            new RouteRule(HttpMethod.POST, "/auth/recover-password"),
            new RouteRule(HttpMethod.POST, "/auth/reset-password")
    );

    /** Métodos de solo lectura: permitidos para cualquier usuario autenticado. */
    private static final Set<HttpMethod> METODOS_LECTURA =
            Set.of(HttpMethod.GET, HttpMethod.HEAD);

    /** Excepción puntual: cualquier usuario autenticado puede cambiar su propia clave. */
    private static final RouteRule CAMBIO_PASSWORD_PROPIO =
            new RouteRule(HttpMethod.POST, "/auth/change-password");

    /** Excepción ABAC: PUT /perfiles/{empleadoId}, solo si el id coincide con el sub del token. */
    private static final Pattern PERFIL_PROPIO =
            Pattern.compile("^/perfiles/([^/]+)$");

    private final SecretKey secretKey;
    private final String issuerEsperado;

    public JwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
        this.issuerEsperado = jwtProperties.getIssuer();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Preflight CORS: nunca lleva Authorization, no se valida.
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        if (esRutaPublica(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return Mono.error(new ResponseStatusException(
                    UNAUTHORIZED,
                    "Falta el token JWT en la cabecera Authorization (esquema Bearer)"
            ));
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuerEsperado)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException ex) {
            return Mono.error(new ResponseStatusException(
                    UNAUTHORIZED, "El token JWT expiró"
            ));
        } catch (JwtException ex) {
            log.warn("Token JWT inválido: {}", ex.getMessage());
            return Mono.error(new ResponseStatusException(
                    UNAUTHORIZED, "El token JWT es inválido"
            ));
        }

        // Un token de reset de contraseña no es un token de acceso.
        if (claims.get(RESET_TYPE_CLAIM) != null) {
            return Mono.error(new ResponseStatusException(
                    UNAUTHORIZED, "El token proporcionado no es un token de acceso"
            ));
        }

        String sub = claims.getSubject();
        String rol = claims.get("role", String.class);

        if (sub == null || rol == null) {
            return Mono.error(new ResponseStatusException(
                    UNAUTHORIZED, "El token no contiene los claims requeridos"
            ));
        }

        if (!autorizado(rol, sub, path, method)) {
            return Mono.error(new ResponseStatusException(
                    FORBIDDEN,
                    "El rol " + rol + " no tiene permiso para " + method + " " + path
            ));
        }

        // Se propaga la identidad a los microservicios internos como cabeceras.
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", sub)
                .header("X-User-Role", rol)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean esRutaPublica(String path, HttpMethod method) {
        return RUTAS_PUBLICAS.stream()
                .anyMatch(regla -> regla.matches(path, method));
    }

    /**
     * si rol == ADMIN                                  -> permitir
     * si rol == USER y método es de lectura             -> permitir
     * si rol == USER y cambia su propia contraseña       -> permitir
     * si rol == USER y recurso.empleadoId == token.sub   -> permitir
     * en cualquier otro caso                            -> denegar (403)
     */
    private boolean autorizado(String rol, String sub, String path, HttpMethod method) {

        if (ROLE_ADMIN.equals(rol)) {
            return true;
        }

        if (!ROLE_USER.equals(rol)) {
            return false; // rol desconocido: se deniega por seguridad
        }

        if (METODOS_LECTURA.contains(method)) {
            return true;
        }

        if (CAMBIO_PASSWORD_PROPIO.matches(path, method)) {
            return true;
        }

        return esActualizacionDePerfilPropio(sub, path, method);
    }

    private boolean esActualizacionDePerfilPropio(
            String sub, String path, HttpMethod method
    ) {
        if (!HttpMethod.PUT.equals(method)) {
            return false;
        }

        Matcher matcher = PERFIL_PROPIO.matcher(path);

        return matcher.matches() && matcher.group(1).equals(sub);
    }

    @Override
    public int getOrder() {
        // Debe ejecutarse antes del enrutamiento hacia el microservicio destino.
        return -100;
    }

    private record RouteRule(HttpMethod method, String path) {
        boolean matches(String requestPath, HttpMethod requestMethod) {
            return method.equals(requestMethod) && path.equals(requestPath);
        }
    }
}