const express = require("express");

const {
    listarTodas,
    listarPorEmpleado
} = require("../controllers/notificacionController");

const router = express.Router();

router.get("/", listarTodas);

router.get("/:empleadoId", listarPorEmpleado);

module.exports = router;