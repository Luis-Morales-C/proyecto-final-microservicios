package co.edu.uniquindio.gestionempleados.exception;

public class DepartamentoNoEncontradoException extends RuntimeException {

    public DepartamentoNoEncontradoException(String id) {
        super("El departamento con id " + id + " no existe");
    }
}