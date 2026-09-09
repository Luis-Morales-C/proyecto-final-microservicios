package co.edu.uniquindio.gestionempleados.exception;

public class DepartamentoNoDisponibleException extends RuntimeException {

    public DepartamentoNoDisponibleException() {
        super("El servicio de departamentos no está disponible");
    }
}