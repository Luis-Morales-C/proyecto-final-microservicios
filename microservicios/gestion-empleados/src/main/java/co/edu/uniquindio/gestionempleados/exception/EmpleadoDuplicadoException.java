package co.edu.uniquindio.gestionempleados.exception;

public class EmpleadoDuplicadoException
        extends RuntimeException {

    public EmpleadoDuplicadoException(
            String mensaje
    ) {
        super(mensaje);
    }
}