package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de las operaciones de consulta de EmpleadoService.
 *
 * Se verifica la consulta individual, consulta general y filtrado por estado.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceConsultaTest {

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
    }

    @Test
    void debeConsultarEmpleado() {

        // ARRANGE
        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        // ACT
        Empleado resultado =
                service.consultar("E001");

        // ASSERT
        assertEquals(
                "E001",
                resultado.getId()
        );
    }

    @Test
    void debeLanzarExcepcionCuandoEmpleadoNoExiste() {

        // ARRANGE
        when(repository.findById("E999"))
                .thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(
                EmpleadoNoEncontradoException.class,
                () -> service.consultar("E999")
        );
    }

    @Test
    void debeConsultarTodosLosEmpleados() {

        // ARRANGE
        when(repository.findAll())
                .thenReturn(List.of(empleado));

        // ACT
        List<Empleado> resultado =
                service.consultarTodos();

        // ASSERT
        assertEquals(
                1,
                resultado.size()
        );
    }

    @Test
    void debeConsultarEmpleadosPorEstado() {

        // ARRANGE
        empleado.setEstado(EstadoEmpleado.ACTIVO);

        when(repository.findByEstado(
                EstadoEmpleado.ACTIVO
        )).thenReturn(List.of(empleado));

        // ACT
        List<Empleado> resultado =
                service.consultarPorEstado(
                        EstadoEmpleado.ACTIVO
                );

        // ASSERT
        assertEquals(1, resultado.size());

        assertEquals(
                EstadoEmpleado.ACTIVO,
                resultado.get(0).getEstado()
        );

        // VERIFY
        verify(repository)
                .findByEstado(
                        EstadoEmpleado.ACTIVO
                );
    }
}