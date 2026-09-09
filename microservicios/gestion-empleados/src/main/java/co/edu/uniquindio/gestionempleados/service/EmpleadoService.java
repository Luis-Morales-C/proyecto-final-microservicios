package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmpleadoService {

    private final EmpleadoRepository repository;
    private final DepartamentoClient departamentoClient;

    public EmpleadoService(
            EmpleadoRepository repository,
            DepartamentoClient departamentoClient
    ) {
        this.repository = repository;
        this.departamentoClient = departamentoClient;
    }

    public Empleado registrar(Empleado empleado) {

        validarEmailUnico(empleado.getEmail());

        validarNumeroEmpleadoUnico(
                empleado.getNumeroEmpleado()
        );

        departamentoClient.consultarDepartamento(
                empleado.getDepartamentoId()
        );

        empleado.setEstado(
                EstadoEmpleado.ACTIVO
        );

        try {

            return repository.saveAndFlush(empleado);

        } catch (DataIntegrityViolationException ex) {

            throw new EmpleadoDuplicadoException(
                    "El email o el número de empleado "
                            + "ya está registrado"
            );
        }
    }

    private void validarEmailUnico(String email) {

        if (repository.existsByEmailIgnoreCase(email)) {

            throw new EmpleadoDuplicadoException(
                    "El email "
                            + email
                            + " ya está registrado"
            );
        }
    }

    private void validarNumeroEmpleadoUnico(
            String numeroEmpleado
    ) {

        if (repository.existsByNumeroEmpleadoIgnoreCase(
                numeroEmpleado
        )) {

            throw new EmpleadoDuplicadoException(
                    "El número de empleado "
                            + numeroEmpleado
                            + " ya está registrado"
            );
        }
    }

    public Empleado consultar(String id) {

        return repository.findById(id)
                .orElseThrow(
                        () -> new EmpleadoNoEncontradoException(id)
                );
    }

    public List<Empleado> consultarTodos() {

        return repository.findAll();
    }
}