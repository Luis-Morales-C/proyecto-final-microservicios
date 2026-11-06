using System.Text;
using System.Text.Json;
using RabbitMQ.Client;
using System.Diagnostics;

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

    public bool PublishScheduled(Vacacion vacation) => Publish("vacaciones.programadas", vacation);
    public bool PublishStarted(Vacacion vacation)   => Publish("vacaciones.iniciadas", vacation);
    public bool PublishFinished(Vacacion vacation)  => Publish("vacaciones.finalizadas", vacation);

    private bool Publish(string routingKey, Vacacion vacation)
    {
        try
        {
            lock (_sync)
            {
                EnsureConnection();

                var envelope = new EventoEnvelope<VacacionesProgramadasData>(
                    Guid.NewGuid().ToString(),
                    routingKey,
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

                properties.Headers ??= new Dictionary<string, object>();

                var activity = Activity.Current;

                if (activity is not null)
                {
                    var traceParent =
                        $"00-{activity.TraceId}-{activity.SpanId}-{(activity.ActivityTraceFlags.HasFlag(ActivityTraceFlags.Recorded) ? "01" : "00")}";

                    properties.Headers["traceparent"] =
                        Encoding.UTF8.GetBytes(traceParent);
                }

                _channel.BasicPublish(Exchange, routingKey, mandatory: false,
                    basicProperties: properties, body: body);
            }

            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "No fue posible publicar {RoutingKey} para la vacación {Id}",
                routingKey, vacation.Id);
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
