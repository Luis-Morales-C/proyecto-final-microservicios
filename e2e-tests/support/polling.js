export async function esperarHastaQue(
    condicion,
    {
        maxIntentos = Number(process.env.POLL_MAX_ATTEMPTS ?? 15),
        intervaloMs = Number(process.env.POLL_INTERVAL_MS ?? 1000),
        descripcion = "condición asíncrona"
    } = {}
) {
    let ultimoError = null;

    for (let intento = 1; intento <= maxIntentos; intento++) {
        try {
            const resultado = await condicion();
            if (resultado) return resultado;
        } catch (error) {
            ultimoError = error;
        }

        if (intento < maxIntentos) {
            await new Promise(resolve => setTimeout(resolve, intervaloMs));
        }
    }

    const detalle = ultimoError ? ` Último error: ${ultimoError.message}` : "";
    throw new Error(
        `Timeout esperando ${descripcion} después de ${maxIntentos} intentos.${detalle}`
    );
}
