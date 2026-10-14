package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la reconciliación de empleados pendientes.
 *
 * Se cubren tres resultados posibles:
 * - el departamento existe y el empleado pasa a ACTIVO,
 * - el departamento no existe y el empleado pasa a RECHAZADO,
 * - el servicio de departamentos sigue caído y el empleado permanece pendiente.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceReconciliacionTest {

    @Mock
    private EmpleadoRepository repository;

    @Mock
    private DepartamentoClient departamentoClient;

    @Mock
    private EmpleadoEventPublisher eventPublisher;

    @InjectMocks
    private EmpleadoService service;

    private Empleado empleado;

    @BeforeEach
    void setUp() {

        empleado = new Empleado(
                "E001",
                "Juan",
                "Perez",
                "juan@empresa.com",
                "EMP-001",
                "Desarrollador",
                "Tecnologia",
                "IT",
                LocalDate.now(),
                null
        );

        empleado.setEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        );
    }

    @Test
    void debeActivarEmpleadoPendienteCuandoDepartamentoEsValido() {

        // ARRANGE:
        // Primera consulta: existe un empleado pendiente.
        // Segunda consulta: después de reconciliar ya no quedan pendientes.
        when(repository.findByEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        )).thenReturn(
                List.of(empleado),
                List.of()
        );

        // ACT
        List<Empleado> resultado =
                service.reconciliarPendientes();

        // ASSERT
        assertEquals(
                EstadoEmpleado.ACTIVO,
                empleado.getEstado()
        );

        assertTrue(
                resultado.isEmpty()
        );

        // VERIFY
        verify(departamentoClient)
                .consultarDepartamento("IT");

        verify(repository)
                .save(empleado);
    }

    @Test
    void debeRechazarEmpleadoPendienteSiDepartamentoNoExiste() {

        // ARRANGE
        when(repository.findByEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        )).thenReturn(
                List.of(empleado),
                List.of()
        );

        doThrow(
                new DepartamentoNoEncontradoException("IT")
        ).when(departamentoClient)
                .consultarDepartamento("IT");

        // ACT
        service.reconciliarPendientes();

        // ASSERT
        assertEquals(
                EstadoEmpleado.RECHAZADO,
                empleado.getEstado()
        );

        // VERIFY
        verify(repository)
                .save(empleado);
    }

    @Test
    void debeMantenerPendienteSiDepartamentoSigueNoDisponible() {

        // ARRANGE
        when(repository.findByEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        )).thenReturn(
                List.of(empleado),
                List.of(empleado)
        );

        doThrow(
                new DepartamentoNoDisponibleException()
        ).when(departamentoClient)
                .consultarDepartamento("IT");

        // ACT
        List<Empleado> resultado =
                service.reconciliarPendientes();

        // ASSERT
        assertEquals(
                EstadoEmpleado.PENDIENTE_VALIDACION,
                empleado.getEstado()
        );

        assertEquals(
                1,
                resultado.size()
        );

        // VERIFY:
        // El empleado no se modifica porque el proceso se detiene
        // mientras departamentos continúa sin estar disponible.
        verify(
                repository,
                never()
        ).save(empleado);
    }
}