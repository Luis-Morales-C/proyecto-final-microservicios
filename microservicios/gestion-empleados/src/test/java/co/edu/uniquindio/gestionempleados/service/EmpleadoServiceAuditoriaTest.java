package co.edu.uniquindio.gestionempleados.service;

import co.edu.uniquindio.gestionempleados.client.DepartamentoClient;
import co.edu.uniquindio.gestionempleados.event.EmpleadoEventPublisher;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la consulta de auditoría de empleados retirados.
 *
 * Se prueban todas las combinaciones posibles de filtros de fecha:
 * - sin fechas,
 * - únicamente fecha inicial,
 * - únicamente fecha final,
 * - intervalo completo,
 * - intervalo inválido.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoServiceAuditoriaTest {

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
                EstadoEmpleado.RETIRADO
        );
    }

    @Test
    void debeConsultarAuditoriaSinFiltrosDeFecha() {

        // ARRANGE
        when(repository
                .findByEstadoOrderByFechaRetiroDesc(
                        EstadoEmpleado.RETIRADO
                ))
                .thenReturn(
                        List.of(empleado)
                );

        // ACT
        List<Empleado> resultado =
                service.consultarAuditoria(
                        null,
                        null
                );

        // ASSERT
        assertEquals(
                1,
                resultado.size()
        );

        // VERIFY
        verify(repository)
                .findByEstadoOrderByFechaRetiroDesc(
                        EstadoEmpleado.RETIRADO
                );
    }

    @Test
    void debeConsultarAuditoriaDesdeUnaFecha() {

        // ARRANGE
        LocalDateTime desde =
                LocalDateTime.now()
                        .minusDays(10);

        when(repository.buscarRetiradosDesde(
                EstadoEmpleado.RETIRADO,
                desde
        )).thenReturn(
                List.of(empleado)
        );

        // ACT
        List<Empleado> resultado =
                service.consultarAuditoria(
                        desde,
                        null
                );

        // ASSERT
        assertEquals(
                1,
                resultado.size()
        );

        // VERIFY
        verify(repository)
                .buscarRetiradosDesde(
                        EstadoEmpleado.RETIRADO,
                        desde
                );
    }

    @Test
    void debeConsultarAuditoriaHastaUnaFecha() {

        // ARRANGE
        LocalDateTime hasta =
                LocalDateTime.now();

        when(repository.buscarRetiradosHasta(
                EstadoEmpleado.RETIRADO,
                hasta
        )).thenReturn(
                List.of(empleado)
        );

        // ACT
        List<Empleado> resultado =
                service.consultarAuditoria(
                        null,
                        hasta
                );

        // ASSERT
        assertEquals(
                1,
                resultado.size()
        );

        // VERIFY
        verify(repository)
                .buscarRetiradosHasta(
                        EstadoEmpleado.RETIRADO,
                        hasta
                );
    }

    @Test
    void debeConsultarAuditoriaEntreFechas() {

        // ARRANGE
        LocalDateTime desde =
                LocalDateTime.now()
                        .minusDays(10);

        LocalDateTime hasta =
                LocalDateTime.now();

        when(repository.buscarRetiradosEntre(
                EstadoEmpleado.RETIRADO,
                desde,
                hasta
        )).thenReturn(
                List.of(empleado)
        );

        // ACT
        List<Empleado> resultado =
                service.consultarAuditoria(
                        desde,
                        hasta
                );

        // ASSERT
        assertEquals(
                1,
                resultado.size()
        );

        // VERIFY
        verify(repository)
                .buscarRetiradosEntre(
                        EstadoEmpleado.RETIRADO,
                        desde,
                        hasta
                );
    }

    @Test
    void debeRechazarAuditoriaConRangoDeFechasInvalido() {

        // ARRANGE
        LocalDateTime desde =
                LocalDateTime.now();

        LocalDateTime hasta =
                desde.minusDays(1);

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> service.consultarAuditoria(
                        desde,
                        hasta
                )
        );

        // VERIFY:
        // No debe consultarse la base de datos si el rango es inválido.
        verifyNoInteractions(repository);
    }
}