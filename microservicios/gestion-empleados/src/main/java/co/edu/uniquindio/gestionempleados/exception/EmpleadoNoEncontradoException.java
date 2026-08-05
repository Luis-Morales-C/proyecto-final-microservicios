package co.edu.uniquindio.gestionempleados.exception;

public class EmpleadoNoEncontradoException extends RuntimeException {
    public EmpleadoNoEncontradoException(String id) {
        super("El empleado con id " + id + " no existe");
    }
}