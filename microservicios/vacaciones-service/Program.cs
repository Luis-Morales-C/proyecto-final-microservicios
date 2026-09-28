using Microsoft.OpenApi.Models;
using Npgsql;
using VacacionesService;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "API de Gestión de Vacaciones",
        Version = "1.0.0",
        Description = "Programa, consulta y cancela períodos de vacaciones. Publica vacaciones.programadas y valida empleados mediante una réplica local alimentada por eventos."
    });
});

var dbHost = builder.Configuration["DB_HOST"] ?? "database-vacaciones";
var dbPort = builder.Configuration["DB_PORT"] ?? "5432";
var dbName = builder.Configuration["DB_NAME"] ?? "vacaciones";
var dbUser = builder.Configuration["DB_USER"] ?? "vacaciones";
var dbPassword = builder.Configuration["DB_PASSWORD"] ?? "vacaciones";
var connectionString = $"Host={dbHost};Port={dbPort};Database={dbName};Username={dbUser};Password={dbPassword};Pooling=true";

builder.Services.AddSingleton(NpgsqlDataSource.Create(connectionString));
builder.Services.AddSingleton<VacationRepository>();
builder.Services.AddSingleton<VacationEventPublisher>();
builder.Services.AddHostedService<EmployeeEventWorker>();

var app = builder.Build();

var dataSource = app.Services.GetRequiredService<NpgsqlDataSource>();
await DatabaseInitializer.InitializeAsync(dataSource);

app.UseSwagger(options =>
{
    options.RouteTemplate = "vacaciones/swagger/{documentName}/swagger.json";
});
app.UseSwaggerUI(options =>
{
    options.RoutePrefix = "vacaciones/docs";
    options.SwaggerEndpoint("/vacaciones/swagger/v1/swagger.json", "Vacaciones API v1");
});

app.MapGet("/health", async (VacationRepository repository, CancellationToken ct) =>
{
    return await repository.PingAsync(ct)
        ? Results.Ok(new { estado = "OK", servicio = "vacaciones-service" })
        : Results.Json(new { estado = "ERROR", servicio = "vacaciones-service" }, statusCode: 503);
}).ExcludeFromDescription();

app.MapPost("/vacaciones", async (
    ProgramarVacacionesRequest request,
    VacationRepository repository,
    VacationEventPublisher publisher,
    CancellationToken ct) =>
{
    if (string.IsNullOrWhiteSpace(request.EmpleadoId))
        return Results.BadRequest(new ErrorResponse(true, "empleadoId es obligatorio"));

    if (request.FechaFin <= request.FechaInicio)
        return Results.BadRequest(new ErrorResponse(true, "fechaFin debe ser posterior a fechaInicio"));

    var today = DateOnly.FromDateTime(DateTime.UtcNow);
    if (request.FechaInicio < today)
        return Results.BadRequest(new ErrorResponse(true, "fechaInicio no puede estar en el pasado"));

    var employee = await repository.ObtenerEmpleadoActivoAsync(request.EmpleadoId, ct);
    if (employee is null)
        return Results.BadRequest(new ErrorResponse(true, "El empleado no existe o se encuentra retirado"));

    var conflict = await repository.BuscarSolapamientoAsync(
        request.EmpleadoId, request.FechaInicio, request.FechaFin, ct);
    if (conflict is not null)
        return Results.BadRequest(new ErrorResponse(
            true,
            "El período solicitado se solapa con otro período PROGRAMADA o EN_CURSO",
            conflict));

    var vacation = await repository.CrearAsync(
        request.EmpleadoId, request.FechaInicio, request.FechaFin, ct);

    publisher.PublishScheduled(vacation);
    return Results.Created($"/vacaciones/{vacation.Id}", vacation);
})
.WithName("ProgramarVacaciones")
.WithTags("Vacaciones")
.Produces<Vacacion>(StatusCodes.Status201Created)
.Produces<ErrorResponse>(StatusCodes.Status400BadRequest);

app.MapGet("/vacaciones/{id}", async (
    string id,
    VacationRepository repository,
    CancellationToken ct) =>
{
    var vacation = await repository.ConsultarAsync(id, ct);
    return vacation is null
        ? Results.NotFound(new ErrorResponse(true, $"El período {id} no existe"))
        : Results.Ok(vacation);
})
.WithName("ConsultarVacacion")
.WithTags("Vacaciones")
.Produces<Vacacion>()
.Produces<ErrorResponse>(StatusCodes.Status404NotFound);

app.MapGet("/vacaciones", async (
    string? empleadoId,
    VacationRepository repository,
    CancellationToken ct) =>
{
    var vacations = await repository.ListarAsync(empleadoId, ct);
    return Results.Ok(vacations);
})
.WithName("ListarVacaciones")
.WithTags("Vacaciones")
.Produces<IReadOnlyList<Vacacion>>();

app.MapDelete("/vacaciones/{id}", async (
    string id,
    VacationRepository repository,
    CancellationToken ct) =>
{
    var result = await repository.CancelarAsync(id, ct);
    return result.Error switch
    {
        "NO_ENCONTRADA" => Results.NotFound(new ErrorResponse(true, $"El período {id} no existe")),
        "YA_INICIO" => Results.BadRequest(new ErrorResponse(true, "No se puede cancelar un período que ya inició")),
        "ESTADO_INVALIDO" => Results.BadRequest(new ErrorResponse(true, "Solo puede cancelarse un período PROGRAMADA")),
        _ => Results.Ok(result.Vacation)
    };
})
.WithName("CancelarVacacion")
.WithTags("Vacaciones")
.Produces<Vacacion>()
.Produces<ErrorResponse>(StatusCodes.Status400BadRequest)
.Produces<ErrorResponse>(StatusCodes.Status404NotFound);

app.Run();
