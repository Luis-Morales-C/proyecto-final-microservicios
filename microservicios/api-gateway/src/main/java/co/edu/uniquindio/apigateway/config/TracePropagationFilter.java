package co.edu.uniquindio.apigateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TracePropagationFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TracePropagationFilter.class);

    private static final TextMapSetter<ServerHttpRequest.Builder> SETTER =
            (carrier, key, value) -> {
                if (carrier == null) {
                    return;
                }

                if ("traceparent".equalsIgnoreCase(key)) {
                    value = normalizeTraceParent(value);
                }

                carrier.header(key, value);
            };

    private final OpenTelemetry openTelemetry;

    public TracePropagationFilter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        ServerHttpRequest.Builder requestBuilder =
                exchange.getRequest().mutate();

        openTelemetry
                .getPropagators()
                .getTextMapPropagator()
                .inject(
                        Context.current(),
                        requestBuilder,
                        SETTER
                );

        ServerHttpRequest mutatedRequest = requestBuilder.build();

        String traceParent =
                mutatedRequest.getHeaders().getFirst("traceparent");

        String traceId = "";
        String spanId = "";

        if (traceParent != null) {
            String[] partes = traceParent.split("-");

            if (partes.length == 4) {
                traceId = partes[1];
                spanId = partes[2];
            }
        }

        LOGGER.info(
                "Petición HTTP {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                StructuredArguments.kv("traceId", traceId),
                StructuredArguments.kv("spanId", spanId),
                StructuredArguments.kv(
                        "method",
                        exchange.getRequest().getMethod().toString()
                ),
                StructuredArguments.kv(
                        "path",
                        exchange.getRequest().getURI().getPath()
                )
        );

        ServerWebExchange mutatedExchange =
                exchange.mutate()
                        .request(mutatedRequest)
                        .build();

        return chain.filter(mutatedExchange);
    }

    private static String normalizeTraceParent(String traceParent) {
        if (traceParent == null || traceParent.length() < 2) {
            return traceParent;
        }

        try {
            int lastDash = traceParent.lastIndexOf('-');

            if (lastDash < 0 ||
                    lastDash + 3 != traceParent.length()) {
                return traceParent;
            }

            String flagsHex =
                    traceParent.substring(lastDash + 1);

            int flags =
                    Integer.parseInt(flagsHex, 16);

            int compatibleFlags = flags & 0x01;

            return traceParent.substring(
                    0,
                    lastDash + 1
            ) + String.format(
                    "%02x",
                    compatibleFlags
            );

        } catch (NumberFormatException e) {
            return traceParent;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}