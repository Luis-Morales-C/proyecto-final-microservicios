package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmpleadoService {

    private final EmpleadoRepository repository;

    public EmpleadoService(EmpleadoRepository repository) {
        this.repository = repository;
    }

    public Empleado registrar(Empleado empleado) {
        if (repository.existsByEmail(empleado.email())) {
            throw new EmpleadoDuplicadoException("El email " + empleado.email() + " ya está registrado");
        }
        if (repository.existsByNumeroEmpleado(empleado.numeroEmpleado())) {
            throw new EmpleadoDuplicadoException("El número de empleado " + empleado.numeroEmpleado() + " ya está registrado");
        }

        String idFinal = UUID.randomUUID().toString();

        Empleado empleadoFinal = new Empleado(
                idFinal,
                empleado.nombre(),
                empleado.apellido(),
                empleado.email(),
                empleado.numeroEmpleado(),
                empleado.cargo(),
                empleado.area(),
                empleado.departamentoId(),
                empleado.fechaIngreso(),
                empleado.estado() == null ? EstadoEmpleado.ACTIVO : empleado.estado()
        );

        return repository.save(empleadoFinal);
    }

    public Empleado consultar(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EmpleadoNoEncontradoException(id));
    }
}