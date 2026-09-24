const notificacionRepository = require(
    "../repositories/notificacionRepository"
);

async function listarTodas(req, res) {
    try {
        const notificaciones =
            await notificacionRepository.listarTodas();

        res.status(200).json(notificaciones);
    } catch (error) {
        console.error(
            "Error consultando notificaciones:",
            error
        );

        res.status(500).json({
            mensaje: "Error al consultar las notificaciones"
        });
    }
}

async function listarPorEmpleado(req, res) {
    try {
        const { empleadoId } = req.params;

        const notificaciones =
            await notificacionRepository.listarPorEmpleado(
                empleadoId
            );

        res.status(200).json(notificaciones);
    } catch (error) {
        console.error(
            "Error consultando notificaciones del empleado:",
            error
        );

        res.status(500).json({
            mensaje: "Error al consultar las notificaciones del empleado"
        });
    }
}

module.exports = {
    listarTodas,
    listarPorEmpleado
};