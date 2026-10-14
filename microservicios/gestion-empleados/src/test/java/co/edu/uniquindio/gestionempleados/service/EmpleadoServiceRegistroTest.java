package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias relacionadas con el registro de empleados.
 *
 * Se utilizan mocks para aislar EmpleadoService de:
 * - PostgreSQL, mediante EmpleadoRepository.
 * - gestion-departamentos, mediante DepartamentoClient.
 * - RabbitMQ, mediante EmpleadoEventPublisher.
 *
 * Estas pruebas no requieren Docker ni infraestructura externa.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceRegistroTest {

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

        // Empleado válido reutilizado como base en los escenarios de registro.
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
    }

    @Test
    void debeRegistrarEmpleado() {

        // ARRANGE: email y número de empleado disponibles.
        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        Empleado resultado = service.registrar(empleado);

        // ASSERT
        assertNotNull(resultado);
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
                .publicarCreado(empleado);
    }

    @Test
    void debeRechazarEmailDuplicado() {

        // ARRANGE: el repositorio informa que el email ya existe.
        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(true);

        // ACT + ASSERT
        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );

        // VERIFY: no debe continuar con el registro.
        verify(
                departamentoClient,
                never()
        ).consultarDepartamento(any());

        verify(
                repository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void debeRechazarNumeroDuplicado() {

        // ARRANGE
        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(true);

        // ACT + ASSERT
        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );

        // VERIFY
        verify(
                departamentoClient,
                never()
        ).consultarDepartamento(any());

        verify(
                repository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void debeRechazarDepartamentoInexistente() {

        // ARRANGE
        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        doThrow(
                new DepartamentoNoEncontradoException("IT")
        ).when(departamentoClient)
                .consultarDepartamento("IT");

        // ACT + ASSERT
        assertThrows(
                DepartamentoNoEncontradoException.class,
                () -> service.registrar(empleado)
        );

        // VERIFY
        verify(
                repository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void debeRegistrarPendienteCuandoDepartamentoNoDisponible() {

        // ARRANGE:
        // Se simula que gestion-departamentos está temporalmente caído.
        doThrow(
                new DepartamentoNoDisponibleException()
        ).when(departamentoClient)
                .consultarDepartamento("IT");

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        Empleado resultado = service.registrar(empleado);

        // ASSERT:
        // El empleado se conserva, pero queda pendiente de validación.
        assertEquals(
                EstadoEmpleado.PENDIENTE_VALIDACION,
                resultado.getEstado()
        );

        // VERIFY
        verify(repository)
                .saveAndFlush(empleado);

        verify(eventPublisher)
                .publicarCreado(empleado);
    }

    @Test
    void debeCapturarCarreraDeUnicidad() {

        // ARRANGE:
        // Las validaciones previas no detectan duplicados,
        // pero la base de datos rechaza el guardado.
        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate key"
                        )
                );

        // ACT + ASSERT
        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );

        // VERIFY:
        // Si el guardado falla, no debe publicarse el evento.
        verify(eventPublisher, never())
                .publicarCreado(any());
    }
}