package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;

@Service
public class EmpleadoService {

    private final EmpleadoRepository repository;

    public EmpleadoService(EmpleadoRepository repository) {
        this.repository = repository;
    }

    public Empleado registrar(Empleado empleado) {

        if (repository.existsByEmailIgnoreCase(empleado.getEmail())) {
            throw new EmpleadoDuplicadoException(
                    "El email " + empleado.getEmail() + " ya está registrado"
            );
        }

        if (repository.existsByNumeroEmpleadoIgnoreCase(empleado.getNumeroEmpleado())) {
            throw new EmpleadoDuplicadoException(
                    "El número de empleado " + empleado.getNumeroEmpleado() + " ya está registrado"
            );
        }

        Empleado empleadoFinal = new Empleado(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getApellido(),
                empleado.getEmail(),
                empleado.getNumeroEmpleado(),
                empleado.getCargo(),
                empleado.getArea(),
                empleado.getDepartamentoId(),
                empleado.getFechaIngreso(),
                empleado.getEstado() == null
                        ? EstadoEmpleado.ACTIVO
                        : empleado.getEstado()
        );

        return repository.save(empleadoFinal);
    }

    public Empleado consultar(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EmpleadoNoEncontradoException(id));
    }
}