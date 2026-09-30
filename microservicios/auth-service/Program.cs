using System.Security.Claims;
using AuthService;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.OpenApi.Models;
using Npgsql;
using Swashbuckle.AspNetCore.SwaggerGen;

var builder = WebApplication.CreateBuilder(args);
var config = builder.Configuration;

builder.WebHost.UseUrls($"http://+:{config["PORT"] ?? "8086"}");

// ---------- Base de datos ----------
var connectionString = new NpgsqlConnectionStringBuilder
{
    Host = config["DB_HOST"] ?? "localhost",
    Port = int.Parse(config["DB_PORT"] ?? "5432"),
    Database = config["DB_NAME"],
    Username = config["DB_USER"],
    Password = config["DB_PASSWORD"]
}.ConnectionString;

builder.Services.AddSingleton(NpgsqlDataSource.Create(connectionString));

// ---------- Servicios ----------
// TokenService valida JWT_SECRET al construirse: si falta, el servicio no arranca.
var tokenService = new TokenService(config);

builder.Services.AddSingleton(tokenService);
builder.Services.AddSingleton<PasswordService>();
builder.Services.AddSingleton<UserRepository>();
builder.Services.AddSingleton<EventPublisher>();
builder.Services.AddSingleton<AccountService>();
builder.Services.AddSingleton<AccountEventHandler>();
builder.Services.AddHostedService<AuthEventWorker>();

// ---------- Autenticación / autorización (solo para /auth/change-password) ----------
builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false; // conserva "sub" y "role" tal cual
        options.TokenValidationParameters = tokenService.ValidationParameters;
    });

builder.Services.AddAuthorization(options =>
{
    // Solo tokens de ACCESO: un token de reset (claim "type") no sirve aquí.
    options.AddPolicy("AccessToken", policy => policy
        .RequireAuthenticatedUser()
        .RequireClaim("role")
        .RequireAssertion(ctx => !ctx.User.HasClaim(c => c.Type == "type")));
});

// ---------- Swagger con esquema BearerAuth ----------
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Auth Service",
        Version = "v1",
        Description =
            "Proveedor de identidad. Flujo: POST /auth/login -> copie " +
            "accessToken -> botón Authorize -> pegue el token (sin 'Bearer')."
    });

    options.AddSecurityDefinition("BearerAuth", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header,
        Name = "Authorization",
        Description = "JWT obtenido en /auth/login."
    });

    options.OperationFilter<BearerSecurityOperationFilter>();
});

var app = builder.Build();

// ---------- Inicialización ----------
var startupLogger = app.Services.GetRequiredService<ILogger<Program>>();
var dataSource = app.Services.GetRequiredService<NpgsqlDataSource>();

await DatabaseInitializer.InitializeAsync(dataSource);
await DatabaseInitializer.SeedAdminAsync(
    dataSource,
    app.Services.GetRequiredService<PasswordService>(),
    config,
    startupLogger);

app.UseSwagger();
app.UseSwaggerUI(o => o.SwaggerEndpoint("/swagger/v1/swagger.json", "Auth Service v1"));

app.UseAuthentication();
app.UseAuthorization();

// ---------- Endpoints ----------
app.MapGet("/health", () => Results.Ok(new { status = "UP" }))
    .ExcludeFromDescription();

var auth = app.MapGroup("/auth").WithTags("Auth");

auth.MapPost("/login",
        (LoginRequest request, AccountService svc, CancellationToken ct) =>
            svc.LoginAsync(request, ct))
    .WithSummary("Autentica con email y contraseña y retorna un JWT de acceso")
    .Produces<LoginResponse>(StatusCodes.Status200OK)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized);

auth.MapPost("/recover-password",
        (RecoverPasswordRequest request, AccountService svc, CancellationToken ct) =>
            svc.RecoverAsync(request, ct))
    .WithSummary("Inicia la recuperación: publica usuario.recuperacion")
    .Produces<MessageResponse>(StatusCodes.Status202Accepted);

auth.MapPost("/reset-password",
        (ResetPasswordRequest request, AccountService svc, CancellationToken ct) =>
            svc.ResetAsync(request, ct))
    .WithSummary("Establece la contraseña con el token de activación/recuperación")
    .Produces<MessageResponse>(StatusCodes.Status200OK)
    .ProducesProblem(StatusCodes.Status400BadRequest);

auth.MapPost("/change-password",
        async (ChangePasswordRequest request, ClaimsPrincipal user,
            AccountService svc, CancellationToken ct) =>
        {
            var empleadoId = user.FindFirst("sub")?.Value;

            if (string.IsNullOrEmpty(empleadoId))
                return Results.Unauthorized();

            return await svc.ChangeAsync(empleadoId, request, ct);
        })
    .RequireAuthorization("AccessToken")
    .WithSummary("Cambia la contraseña del usuario autenticado")
    .Produces<MessageResponse>(StatusCodes.Status200OK)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized)
    .ProducesProblem(StatusCodes.Status403Forbidden);

app.Run();

/// <summary>Marca con candado en Swagger los endpoints que exigen JWT.</summary>
public sealed class BearerSecurityOperationFilter : IOperationFilter
{
    public void Apply(OpenApiOperation operation, OperationFilterContext context)
    {
        var requiresAuth = context.ApiDescription.ActionDescriptor
            .EndpointMetadata.OfType<IAuthorizeData>().Any();

        if (!requiresAuth) return;

        operation.Security =
        [
            new OpenApiSecurityRequirement
            {
                [new OpenApiSecurityScheme
                {
                    Reference = new OpenApiReference
                    {
                        Type = ReferenceType.SecurityScheme,
                        Id = "BearerAuth"
                    }
                }] = Array.Empty<string>()
            }
        ];
    }
}