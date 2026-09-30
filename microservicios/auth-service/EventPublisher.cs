using System.Text.Json;
using RabbitMQ.Client;

namespace AuthService;

/// <summary>
/// Publica eventos en el exchange rrhh.events usando el sobre del
/// Catálogo de Eventos (sección 2). La routing key es el nombre del evento.
/// Conexión propia y canal con confirmaciones; el acceso se serializa
/// porque los canales de RabbitMQ no son thread-safe.
/// </summary>
public sealed class EventPublisher(
    IConfiguration configuration,
    ILogger<EventPublisher> logger) : IDisposable
{
    public const string Exchange = "rrhh.events";
    private const string Producer = "auth-service";

    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    private readonly object _lock = new();
    private IConnection? _connection;
    private IModel? _channel;

    public void Publish<T>(string type, T data)
    {
        var id = Guid.NewGuid().ToString();

        var envelope = new
        {
            id,
            type,
            version = 1,
            occurredAt = TokenService.FormatUtc(DateTime.UtcNow),
            producer = Producer,
            data
        };

        var body = JsonSerializer.SerializeToUtf8Bytes(envelope, JsonOptions);

        lock (_lock)
        {
            var channel = GetChannel();

            var props = channel.CreateBasicProperties();
            props.Persistent = true;
            props.ContentType = "application/json";
            props.MessageId = id;
            props.Type = type;

            channel.BasicPublish(
                exchange: Exchange,
                routingKey: type,
                mandatory: false,
                basicProperties: props,
                body: body);

            channel.WaitForConfirmsOrDie(TimeSpan.FromSeconds(5));
        }

        logger.LogInformation(
            "Evento publicado {Type} id={EventId}", type, id);
    }

    private IModel GetChannel()
    {
        if (_channel is { IsOpen: true })
            return _channel;

        CloseQuietly();

        var factory = new ConnectionFactory
        {
            HostName = configuration["RABBITMQ_HOST"] ?? "rabbitmq",
            Port = int.Parse(configuration["RABBITMQ_PORT"] ?? "5672"),
            UserName = configuration["RABBITMQ_USER"],
            Password = configuration["RABBITMQ_PASSWORD"],
            AutomaticRecoveryEnabled = true,
            NetworkRecoveryInterval = TimeSpan.FromSeconds(5),
            ClientProvidedName = "auth-service-publisher"
        };

        _connection = factory.CreateConnection();
        _channel = _connection.CreateModel();
        _channel.ConfirmSelect();

        return _channel;
    }

    private void CloseQuietly()
    {
        try { _channel?.Dispose(); } catch { /* ignorado */ }
        try { _connection?.Dispose(); } catch { /* ignorado */ }
        _channel = null;
        _connection = null;
    }

    public void Dispose()
    {
        lock (_lock) CloseQuietly();
    }
}
