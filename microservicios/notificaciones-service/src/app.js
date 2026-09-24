require("dotenv").config();

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

app.use(express.json());

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

        await iniciarConsumidor();

        app.listen(PORT, "0.0.0.0", () => {
            console.log(
                `Notificaciones iniciado en el puerto ${PORT}`
            );
        });
    } catch (error) {
        console.error(
            "No fue posible iniciar Notificaciones:",
            error
        );

        process.exit(1);
    }
}

iniciarAplicacion();