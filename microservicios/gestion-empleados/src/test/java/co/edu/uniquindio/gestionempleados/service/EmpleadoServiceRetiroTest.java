package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del proceso de retiro de empleados.
 *
 * Se verifica la transición a RETIRADO, el registro de fechaRetiro
 * y la protección contra un segundo retiro.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceRetiroTest {

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
    void debeRetirarEmpleado() {

        // ARRANGE
        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        when(repository.saveAndFlush(any(Empleado.class)))
                .thenAnswer(
                        invocation -> invocation.getArgument(0)
                );

        // ACT
        Empleado resultado =
                service.retirar("E001");

        // ASSERT
        assertNotNull(resultado);

        assertEquals(
                EstadoEmpleado.RETIRADO,
                resultado.getEstado()
        );

        assertNotNull(
                resultado.getFechaRetiro()
        );

        // VERIFY
        verify(repository)
                .saveAndFlush(empleado);

        verify(eventPublisher)
                .publicarRetirado(empleado);
    }

    @Test
    void debeRechazarRetiroDeEmpleadoYaRetirado() {

        // ARRANGE
        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        when(repository.findById("E001"))
                .thenReturn(Optional.of(empleado));

        // ACT + ASSERT
        assertThrows(
                EmpleadoRetiradoException.class,
                () -> service.retirar("E001")
        );

        // VERIFY:
        // Un segundo retiro no debe persistirse ni publicar eventos.
        verify(
                repository,
                never()
        ).saveAndFlush(any());

        verify(
                eventPublisher,
                never()
        ).publicarRetirado(any());
    }
}