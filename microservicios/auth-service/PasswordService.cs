namespace AuthService;

public sealed class PasswordService
{
    private const int WorkFactor = 11;

    // Hash de relleno: se verifica contra él cuando el usuario no existe,
    // para que el tiempo de respuesta no revele qué correos están registrados.
    private readonly string _dummyHash =
        BCrypt.Net.BCrypt.HashPassword("relleno-para-igualar-tiempos", WorkFactor);

    public string Hash(string password) =>
        BCrypt.Net.BCrypt.HashPassword(password, WorkFactor);

    public bool Verify(string password, string? hash)
    {
        if (hash is null)
        {
            BCrypt.Net.BCrypt.Verify(password, _dummyHash);
            return false;
        }

        try
        {
            return BCrypt.Net.BCrypt.Verify(password, hash);
        }
        catch (Exception)
        {
            return false;
        }
    }

    /// <summary>
    /// Política: 8 a 72 caracteres (BCrypt ignora lo que pase de 72 bytes),
    /// con mayúscula, minúscula y dígito. Devuelve null si es válida.
    /// </summary>
    public string? ValidatePolicy(string? password)
    {
        if (string.IsNullOrEmpty(password))
            return "La contraseña es obligatoria.";

        if (password.Length < 8)
            return "La contraseña debe tener al menos 8 caracteres.";

        if (password.Length > 72)
            return "La contraseña no puede superar 72 caracteres.";

        if (!password.Any(char.IsUpper))
            return "La contraseña debe incluir al menos una mayúscula.";

        if (!password.Any(char.IsLower))
            return "La contraseña debe incluir al menos una minúscula.";

        if (!password.Any(char.IsDigit))
            return "La contraseña debe incluir al menos un número.";

        return null;
    }
}
