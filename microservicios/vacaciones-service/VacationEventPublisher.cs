using System.Text;
using System.Text.Json;
using RabbitMQ.Client;

namespace VacacionesService;

public sealed class VacationEventPublisher : IDisposable
{
    private const string Exchange = "rrhh.events";
    private readonly IConfiguration _configuration;
    private readonly ILogger<VacationEventPublisher> _logger;
    private readonly object _sync = new();
    private IConnection? _connection;
    private IModel? _channel;

    public VacationEventPublisher(IConfiguration configuration, ILogger<VacationEventPublisher> logger)
    {
        _configuration = configuration;
        _logger = logger;
    }

    public bool PublishScheduled(Vacacion vacation)
    {
        try
        {
            lock (_sync)
            {
                EnsureConnection();

                var envelope = new EventoEnvelope<VacacionesProgramadasData>(
                    Guid.NewGuid().ToString(),
                    "vacaciones.programadas",
                    1,
                    DateTimeOffset.UtcNow,
                    "vacaciones-service",
                    new VacacionesProgramadasData(
                        vacation.Id,
                        vacation.EmpleadoId,
                        vacation.FechaInicio,
                        vacation.FechaFin
                    )
                );

                var body = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(envelope, new JsonSerializerOptions
                {
                    PropertyNamingPolicy = JsonNamingPolicy.CamelCase
                }));
                var properties = _channel!.CreateBasicProperties();
                properties.Persistent = true;
                properties.ContentType = "application/json";

                _channel.BasicPublish(
                    Exchange,
                    "vacaciones.programadas",
                    mandatory: false,
                    basicProperties: properties,
                    body: body);
            }

            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex,
                "La vacación fue persistida, pero no fue posible publicar vacaciones.programadas");
            ResetConnection();
            return false;
        }
    }

    private void EnsureConnection()
    {
        if (_connection?.IsOpen == true && _channel?.IsOpen == true)
            return;

        ResetConnection();
        var factory = new ConnectionFactory
        {
            HostName = _configuration["RABBITMQ_HOST"] ?? "rabbitmq",
            Port = int.Parse(_configuration["RABBITMQ_PORT"] ?? "5672"),
            UserName = _configuration["RABBITMQ_USER"],
            Password = _configuration["RABBITMQ_PASSWORD"],
            AutomaticRecoveryEnabled = true,
            ClientProvidedName = "vacaciones-service-publisher"
        };
        _connection = factory.CreateConnection();
        _channel = _connection.CreateModel();
    }

    private void ResetConnection()
    {
        try { _channel?.Close(); } catch { }
        try { _connection?.Close(); } catch { }
        _channel?.Dispose();
        _connection?.Dispose();
        _channel = null;
        _connection = null;
    }

    public void Dispose() => ResetConnection();
}
