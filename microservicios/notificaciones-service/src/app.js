require("dotenv").config();

const client = require("@prometheus-io/client");
const {
    context,
    trace
} = require("@opentelemetry/api");

const express = require("express");
const swaggerUi = require("swagger-ui-express");

const swaggerDocument = require("./swagger");

const {
    pool,
    inicializarBaseDeDatos
} = require("./config/database");

const {
    iniciarConsumidor
} = require("./consumers/notificacionConsumer");

const notificacionRoutes = require(
    "./routes/notificacionRoutes"
);

const app = express();

const PORT = Number(process.env.PORT || 8083);

const register = new client.Registry();

client.collectDefaultMetrics({
    register
});

const httpRequestsTotal = new client.Counter({
    name: "http_requests_total",
    help: "Número total de peticiones HTTP recibidas",
    labelNames: ["method", "path", "status"],
    registers: [register]
});

const httpRequestDuration = new client.Histogram({
    name: "http_request_duration_seconds",
    help: "Duración de peticiones HTTP en segundos",
    labelNames: ["method", "path"],
    registers: [register]
});

app.use(express.json());

app.use((req, res, next) => {
    const inicio = process.hrtime.bigint();

    res.on("finish", () => {
        const fin = process.hrtime.bigint();
        const duracion =
            Number(fin - inicio) / 1_000_000_000;

        const span = trace.getSpan(context.active());
        const spanContext = span?.spanContext();

        const traceId =
            spanContext?.traceId || "";

        const spanId =
            spanContext?.spanId || "";

        httpRequestsTotal.inc({
            method: req.method,
            path: req.route?.path || req.path,
            status: String(res.statusCode)
        });

        httpRequestDuration.observe(
            {
                method: req.method,
                path: req.route?.path || req.path
            },
            duracion
        );

        console.log(JSON.stringify({
            timestamp: new Date().toISOString(),
            level: "INFO",
            service: "notificaciones-service",
            message:
                `${req.method} ${req.path} status=${res.statusCode}`,
            traceId,
            spanId,
            method: req.method,
            path: req.path,
            status: res.statusCode
        }));
    });

    next();
});

app.get("/metrics", async (req, res) => {
    res.set(
        "Content-Type",
        register.contentType
    );

    res.end(
        await register.metrics()
    );
});

app.get("/health", async (req, res) => {
    try {
        await pool.query("SELECT 1");

        res.status(200).json({
            estado: "OK",
            servicio: "notificaciones-service"
        });
    } catch (error) {
        res.status(503).json({
            estado: "ERROR",
            servicio: "notificaciones-service"
        });
    }
});

app.use(
    "/notificaciones/docs",
    swaggerUi.serve,
    swaggerUi.setup(swaggerDocument, {
        explorer: true,
        swaggerOptions: {
            url: "/notificaciones/openapi.json"
        }
    })
);

app.get("/notificaciones/openapi.json", (req, res) => {
    res.json(swaggerDocument);
});

app.use(
    "/notificaciones",
    notificacionRoutes
);

async function iniciarAplicacion() {
    try {
        await inicializarBaseDeDatos();

        // Iniciar el servidor Express PRIMERO para que el healthcheck responda
        app.listen(PORT, "0.0.0.0", () => {
            console.log(
                `Notificaciones iniciado en el puerto ${PORT}`
            );
        });

        // Luego conectar a RabbitMQ (puede tardar más)
        await iniciarConsumidor();
    } catch (error) {
        console.error(
            "No fue posible iniciar Notificaciones:",
            error
        );

        process.exit(1);
    }
}

iniciarAplicacion();