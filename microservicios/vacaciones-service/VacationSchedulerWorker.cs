namespace VacacionesService;

public sealed class VacationSchedulerWorker(
    VacationRepository repository,
    VacationEventPublisher publisher,
    IConfiguration configuration,
    ILogger<VacationSchedulerWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var segundos = int.TryParse(
            configuration["VACACIONES_SCHEDULER_INTERVALO_SEGUNDOS"], out var s) && s > 0 ? s : 60;

        logger.LogInformation("Scheduler de vacaciones activo cada {Segundos}s", segundos);

        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(segundos));
        try
        {
            do { await EjecutarCicloAsync(stoppingToken); }
            while (await timer.WaitForNextTickAsync(stoppingToken));
        }
        catch (OperationCanceledException) { /* apagado normal */ }
    }

    private async Task EjecutarCicloAsync(CancellationToken ct)
    {
        try
        {
            // Misma referencia de "hoy" que usa Program.cs al validar (UTC)
            var hoy = DateOnly.FromDateTime(DateTime.UtcNow);

            foreach (var v in await repository.ObtenerProgramadasParaIniciarAsync(hoy, ct))
                await IniciarAsync(v, ct);

            foreach (var v in await repository.ObtenerEnCursoParaFinalizarAsync(hoy, ct))
                await FinalizarAsync(v, ct);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            // Un fallo (BD, broker) no debe matar el scheduler: se reintenta en el siguiente tick
            logger.LogError(ex, "Error en el ciclo del scheduler de vacaciones");
        }
    }

    public async Task<bool> IniciarAsync(Vacacion v, CancellationToken ct)
    {
        // Publicar primero: si falla, el estado no cambia y se reintenta.
        // Si publica pero falla el UPDATE, se republica y el consumidor deduplica (Reto 4).
        if (!publisher.PublishStarted(v))
        {
            logger.LogWarning("vacaciones.iniciadas no publicado para {Id}; se reintentará", v.Id);
            return false;
        }

        var ok = await repository.TransicionarEstadoAsync(
            v.Id, EstadosVacaciones.Programada, EstadosVacaciones.EnCurso, ct);
        if (ok) logger.LogInformation("Vacación {Id} PROGRAMADA -> EN_CURSO", v.Id);
        return ok;
    }

    public async Task<bool> FinalizarAsync(Vacacion v, CancellationToken ct)
    {
        if (!publisher.PublishFinished(v))
        {
            logger.LogWarning("vacaciones.finalizadas no publicado para {Id}; se reintentará", v.Id);
            return false;
        }

        var ok = await repository.TransicionarEstadoAsync(
            v.Id, EstadosVacaciones.EnCurso, EstadosVacaciones.Finalizada, ct);
        if (ok) logger.LogInformation("Vacación {Id} EN_CURSO -> FINALIZADA", v.Id);
        return ok;
    }
}