package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    @Mock
    private EmpleadoRepository repository;

    @Mock
    private DepartamentoClient departamentoClient;

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
    }

    @Test
    void debeRegistrarEmpleado() {

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        when(repository.saveAndFlush(
                any(Empleado.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Empleado resultado =
                service.registrar(empleado);

        assertNotNull(resultado);
        assertEquals(
                EstadoEmpleado.ACTIVO,
                resultado.getEstado()
        );

        verify(
                departamentoClient
        ).consultarDepartamento("IT");

        verify(repository).saveAndFlush(
                empleado
        );
    }

    @Test
    void debeRechazarEmailDuplicado() {

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(true);

        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );

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

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(true);

        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );

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

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        doThrow(
                new DepartamentoNoEncontradoException("IT")
        ).when(
                departamentoClient
        ).consultarDepartamento("IT");

        assertThrows(
                DepartamentoNoEncontradoException.class,
                () -> service.registrar(empleado)
        );

        verify(
                repository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void debeRechazarDepartamentoNoDisponible() {

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        doThrow(
                new DepartamentoNoDisponibleException()
        ).when(
                departamentoClient
        ).consultarDepartamento("IT");

        assertThrows(
                DepartamentoNoDisponibleException.class,
                () -> service.registrar(empleado)
        );

        verify(
                repository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void debeCapturarCarreraDeUnicidad() {

        when(repository.existsByEmailIgnoreCase(
                empleado.getEmail()
        )).thenReturn(false);

        when(repository.existsByNumeroEmpleadoIgnoreCase(
                empleado.getNumeroEmpleado()
        )).thenReturn(false);

        when(repository.saveAndFlush(
                any(Empleado.class)
        )).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate key"
                )
        );

        assertThrows(
                EmpleadoDuplicadoException.class,
                () -> service.registrar(empleado)
        );
    }

    @Test
    void debeConsultarEmpleado() {

        when(repository.findById("E001"))
                .thenReturn(
                        Optional.of(empleado)
                );

        Empleado resultado =
                service.consultar("E001");

        assertEquals(
                "E001",
                resultado.getId()
        );
    }

    @Test
    void debeLanzarExcepcionCuandoEmpleadoNoExiste() {

        when(repository.findById("E999"))
                .thenReturn(
                        Optional.empty()
                );

        assertThrows(
                EmpleadoNoEncontradoException.class,
                () -> service.consultar("E999")
        );
    }

    @Test
    void debeConsultarTodosLosEmpleados() {

        when(repository.findAll())
                .thenReturn(
                        List.of(empleado)
                );

        List<Empleado> resultado =
                service.consultarTodos();

        assertEquals(
                1,
                resultado.size()
        );
    }
}