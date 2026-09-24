package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
import co.edu.uniquindio.gestionempleados.dto.ActualizarEmpleadoDTO;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoRetiradoException;
import java.util.List;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmpleadoService {

    private static final Logger log =
            LoggerFactory.getLogger(EmpleadoService.class);

    private final EmpleadoRepository repository;
    private final DepartamentoClient departamentoClient;
    private final EmpleadoEventPublisher eventPublisher;

    public EmpleadoService(
            EmpleadoRepository repository,
            DepartamentoClient departamentoClient, EmpleadoEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.departamentoClient = departamentoClient;
        this.eventPublisher = eventPublisher;
    }

    public Empleado registrar(Empleado empleado) {

        validarEmailUnico(empleado.getEmail());

        validarNumeroEmpleadoUnico(
                empleado.getNumeroEmpleado()
        );

        try {

            departamentoClient.consultarDepartamento(
                    empleado.getDepartamentoId()
            );

            empleado.setEstado(
                    EstadoEmpleado.ACTIVO
            );

        } catch (DepartamentoNoDisponibleException ex) {

            // FALLBACK: departamentos caído o circuito abierto.
            // Se prioriza la disponibilidad: el empleado se registra
            // y queda pendiente de validar su departamento.
            // DepartamentoNoEncontradoException NO se captura aquí:
            // sigue su curso normal y produce el 400.
            log.warn(
                    "Fallback: empleado {} queda PENDIENTE_VALIDACION",
                    empleado.getNumeroEmpleado()
            );

            empleado.setEstado(
                    EstadoEmpleado.PENDIENTE_VALIDACION
            );
        }

        try {

            Empleado guardado = repository.saveAndFlush(empleado);
            eventPublisher.publicarCreado(guardado);
            return guardado;

        } catch (DataIntegrityViolationException ex) {

            throw new EmpleadoDuplicadoException(
                    "El email o el número de empleado "
                            + "ya está registrado"
            );
        }
    }

    /**
     * Reconciliación: revalida los empleados PENDIENTE_VALIDACION.
     * Retorna los que siguen pendientes.
     */
    public List<Empleado> reconciliarPendientes() {

        List<Empleado> pendientes =
                repository.findByEstado(
                        EstadoEmpleado.PENDIENTE_VALIDACION
                );

        for (Empleado empleado : pendientes) {

            try {

                departamentoClient.consultarDepartamento(
                        empleado.getDepartamentoId()
                );

                empleado.setEstado(EstadoEmpleado.ACTIVO);

            } catch (DepartamentoNoEncontradoException ex) {

                empleado.setEstado(EstadoEmpleado.RECHAZADO);

            } catch (DepartamentoNoDisponibleException ex) {

                log.warn(
                        "Reconciliación detenida: departamentos "
                                + "sigue sin estar disponible"
                );

                break;
            }

            repository.save(empleado);
        }

        return repository.findByEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        );
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

    public Empleado actualizar(String id, ActualizarEmpleadoDTO cambios) {
        Empleado existente = consultar(id);

        if (existente.getEstado() == EstadoEmpleado.RETIRADO) {
            throw new EmpleadoRetiradoException(
                    "No se puede actualizar un empleado retirado"
            );        }

        if (!existente.getEmail().equalsIgnoreCase(cambios.email())) {
            validarEmailUnico(cambios.email());
        }

        try {
            departamentoClient.consultarDepartamento(
                    cambios.departamentoId()
            );

            if (existente.getEstado() == EstadoEmpleado.PENDIENTE_VALIDACION) {
                existente.setEstado(EstadoEmpleado.ACTIVO);
            }

        } catch (DepartamentoNoDisponibleException ex) {
            log.warn(
                    "Fallback: actualización de empleado {} queda PENDIENTE_VALIDACION",
                    existente.getNumeroEmpleado()
            );

            existente.setEstado(EstadoEmpleado.PENDIENTE_VALIDACION);
        }

        existente.setNombre(cambios.nombre());
        existente.setApellido(cambios.apellido());
        existente.setEmail(cambios.email());
        existente.setCargo(cambios.cargo());
        existente.setArea(cambios.area());
        existente.setDepartamentoId(cambios.departamentoId());

        try {
            Empleado guardado = repository.saveAndFlush(existente);
            eventPublisher.publicarActualizado(guardado);
            return guardado;

        } catch (DataIntegrityViolationException ex) {
            throw new EmpleadoDuplicadoException(
                    "El email ya está registrado"
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

    public List<Empleado> consultarPorEstado(EstadoEmpleado estado) {
        return repository.findByEstado(estado);
    }

    public Empleado retirar(String id) {
        Empleado existente = consultar(id);

        if (existente.getEstado() == EstadoEmpleado.RETIRADO) {
            throw new EmpleadoRetiradoException(
                    "El empleado ya se encuentra retirado"
            );
        }

        existente.setEstado(EstadoEmpleado.RETIRADO);
        existente.setFechaRetiro(LocalDateTime.now());

        Empleado guardado = repository.saveAndFlush(existente);
        eventPublisher.publicarRetirado(guardado);

        return guardado;
    }

    public List<Empleado> consultarAuditoria(
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                    "La fecha desde no puede ser posterior a la fecha hasta"
            );
        }

        EstadoEmpleado estado = EstadoEmpleado.RETIRADO;

        if (desde == null && hasta == null) {
            return repository.findByEstadoOrderByFechaRetiroDesc(estado);
        }

        if (desde != null && hasta == null) {
            return repository.buscarRetiradosDesde(estado, desde);
        }

        if (desde == null) {
            return repository.buscarRetiradosHasta(estado, hasta);
        }

        return repository.buscarRetiradosEntre(
                estado,
                desde,
                hasta
        );
    }
}