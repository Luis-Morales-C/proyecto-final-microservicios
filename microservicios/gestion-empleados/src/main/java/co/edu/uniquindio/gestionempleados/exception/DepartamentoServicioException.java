package co.edu.uniquindio.gestionempleados.exception;

public class DepartamentoServicioException
        extends RuntimeException {

    private final int statusCode;

    public DepartamentoServicioException(
            int statusCode,
            String mensaje
    ) {
        super(mensaje);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}