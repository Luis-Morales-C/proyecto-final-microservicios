package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.dto.ActualizarEmpleadoDTO;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoRetiradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la actualización de empleados.
 *
 * Se cubren:
 * - actualización normal,
 * - fallback por indisponibilidad de departamentos,
 * - restricción sobre empleados retirados,
 * - reactivación de empleados pendientes,
 * - duplicados detectados por la base de datos.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceActualizacionTest {

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

        empleado.setEstado(EstadoEmpleado.ACTIVO);
    }

    @Test
    void debeActualizarEmpleado() {

        // ARRANGE
        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan Actualizado",
                        "Perez",
                        "juan.nuevo@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        when(repository.existsByEmailIgnoreCase(
                "juan.nuevo@empresa.com"
        )).thenReturn(false);

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(
                        invocation -> invocation.getArgument(0)
                );

        // ACT
        Empleado resultado =
                service.actualizar(
                        "E001",
                        cambios
                );

        // ASSERT
        assertEquals(
                "Juan Actualizado",
                resultado.getNombre()
        );

        assertEquals(
                "juan.nuevo@empresa.com",
                resultado.getEmail()
        );

        assertEquals(
                "Arquitecto",
                resultado.getCargo()
        );

        // VERIFY
        verify(departamentoClient)
                .consultarDepartamento("IT");

        verify(repository)
                .saveAndFlush(empleado);

        verify(eventPublisher)
                .publicarActualizado(empleado);
    }

    @Test
    void debeDejarPendienteCuandoDepartamentoNoDisponibleAlActualizar() {

        // ARRANGE
        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan",
                        "Perez",
                        "juan@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        doThrow(
                new DepartamentoNoDisponibleException()
        ).when(departamentoClient)
                .consultarDepartamento("IT");

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(
                        invocation -> invocation.getArgument(0)
                );

        // ACT
        Empleado resultado =
                service.actualizar(
                        "E001",
                        cambios
                );

        // ASSERT
        assertEquals(
                EstadoEmpleado.PENDIENTE_VALIDACION,
                resultado.getEstado()
        );

        // VERIFY
        verify(repository)
                .saveAndFlush(empleado);

        verify(eventPublisher)
                .publicarActualizado(empleado);
    }

    @Test
    void debeRechazarActualizacionDeEmpleadoRetirado() {

        // ARRANGE
        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan",
                        "Perez",
                        "juan@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        // ACT + ASSERT
        EmpleadoRetiradoException excepcion =
                assertThrows(
                        EmpleadoRetiradoException.class,
                        () -> service.actualizar(
                                "E001",
                                cambios
                        )
                );

        assertEquals(
                "No se puede actualizar un empleado retirado",
                excepcion.getMessage()
        );

        // VERIFY:
        // La operación debe detenerse antes de consultar otras dependencias.
        verifyNoInteractions(
                departamentoClient
        );

        verify(
                repository,
                never()
        ).saveAndFlush(any());

        verify(
                eventPublisher,
                never()
        ).publicarActualizado(any());
    }

    @Test
    void debeActivarEmpleadoPendienteAlActualizarCuandoDepartamentoEstaDisponible() {

        // ARRANGE:
        // El empleado había quedado pendiente porque departamentos estaba caído.
        empleado.setEstado(
                EstadoEmpleado.PENDIENTE_VALIDACION
        );

        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan Actualizado",
                        "Perez",
                        "juan@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        // No se lanza excepción:
        // significa que el departamento nuevamente está disponible.
        doNothing()
                .when(departamentoClient)
                .consultarDepartamento("IT");

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(
                        invocation -> invocation.getArgument(0)
                );

        // ACT
        Empleado resultado =
                service.actualizar(
                        "E001",
                        cambios
                );

        // ASSERT
        assertEquals(
                EstadoEmpleado.ACTIVO,
                resultado.getEstado()
        );

        // VERIFY
        verify(departamentoClient)
                .consultarDepartamento("IT");

        verify(repository)
                .saveAndFlush(empleado);

        verify(eventPublisher)
                .publicarActualizado(empleado);
    }

    @Test
    void debeCapturarDuplicadoAlActualizarEmpleado() {

        // ARRANGE:
        // La validación previa considera el email disponible,
        // pero PostgreSQL rechaza el guardado por duplicidad.
        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan",
                        "Perez",
                        "juan.nuevo@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        when(repository.existsByEmailIgnoreCase(
                "juan.nuevo@empresa.com"
        )).thenReturn(false);

        doNothing()
                .when(departamentoClient)
                .consultarDepartamento("IT");

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Email duplicado"
                        )
                );

        // ACT + ASSERT
        EmpleadoDuplicadoException excepcion =
                assertThrows(
                        EmpleadoDuplicadoException.class,
                        () -> service.actualizar(
                                "E001",
                                cambios
                        )
                );

        assertEquals(
                "El email ya está registrado",
                excepcion.getMessage()
        );

        // VERIFY
        verify(repository)
                .saveAndFlush(empleado);

        verify(
                eventPublisher,
                never()
        ).publicarActualizado(any());
    }
}