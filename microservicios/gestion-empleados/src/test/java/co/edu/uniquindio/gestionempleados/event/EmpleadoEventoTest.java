package co.edu.uniquindio.gestionempleados.event;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del contrato de eventos de empleados.
 *
 * Estas pruebas verifican que los eventos publicados hacia RabbitMQ
 * mantengan la estructura definida para la comunicación entre
 * microservicios.
 *
 * Se validan:
 * - tipo del evento,
 * - versión,
 * - productor,
 * - identificador único,
 * - fecha de ocurrencia,
 * - contenido del payload.
 */
class EmpleadoEventoTest {

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
                EstadoEmpleado.ACTIVO
        );
    }

    // =========================================================
    // EVENTO EMPLEADO.CREADO
    // =========================================================

    @Test
    void debeCrearEventoEmpleadoCreado() {

        // ACT
        EmpleadoEvento evento =
                EmpleadoEvento.creado(empleado);

        // ASSERT - envelope
        assertNotNull(evento);
        assertNotNull(evento.id());
        assertFalse(evento.id().isBlank());

        assertEquals(
                "empleado.creado",
                evento.type()
        );

        assertEquals(
                1,
                evento.version()
        );

        assertEquals(
                "gestion-empleados",
                evento.producer()
        );

        assertNotNull(
                evento.occurredAt()
        );

        // ASSERT - payload
        assertInstanceOf(
                EmpleadoEvento.EmpleadoData.class,
                evento.data()
        );

        EmpleadoEvento.EmpleadoData data =
                (EmpleadoEvento.EmpleadoData)
                        evento.data();

        assertEquals(
                "E001",
                data.empleadoId()
        );

        assertEquals(
                "EMP-001",
                data.numeroEmpleado()
        );

        assertEquals(
                "Juan",
                data.nombre()
        );

        assertEquals(
                "Perez",
                data.apellido()
        );

        assertEquals(
                "juan@empresa.com",
                data.email()
        );

        assertEquals(
                "IT",
                data.departamentoId()
        );

        assertEquals(
                "ACTIVO",
                data.estado()
        );
    }

    // =========================================================
    // EVENTO EMPLEADO.ACTUALIZADO
    // =========================================================

    @Test
    void debeCrearEventoEmpleadoActualizado() {

        // ARRANGE
        empleado.setNombre(
                "Juan Actualizado"
        );

        empleado.setEmail(
                "juan.nuevo@empresa.com"
        );

        // ACT
        EmpleadoEvento evento =
                EmpleadoEvento.actualizado(
                        empleado
                );

        // ASSERT
        assertEquals(
                "empleado.actualizado",
                evento.type()
        );

        assertEquals(
                1,
                evento.version()
        );

        assertEquals(
                "gestion-empleados",
                evento.producer()
        );

        assertInstanceOf(
                EmpleadoEvento.EmpleadoData.class,
                evento.data()
        );

        EmpleadoEvento.EmpleadoData data =
                (EmpleadoEvento.EmpleadoData)
                        evento.data();

        assertEquals(
                "Juan Actualizado",
                data.nombre()
        );

        assertEquals(
                "juan.nuevo@empresa.com",
                data.email()
        );

        assertEquals(
                "ACTIVO",
                data.estado()
        );
    }

    // =========================================================
    // EVENTO EMPLEADO.RETIRADO
    // =========================================================

    @Test
    void debeCrearEventoEmpleadoRetirado() {

        // ARRANGE
        LocalDateTime fechaRetiro =
                LocalDateTime.now();

        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        empleado.setFechaRetiro(
                fechaRetiro
        );

        // ACT
        EmpleadoEvento evento =
                EmpleadoEvento.retirado(
                        empleado
                );

        // ASSERT - envelope
        assertEquals(
                "empleado.retirado",
                evento.type()
        );

        assertEquals(
                1,
                evento.version()
        );

        assertEquals(
                "gestion-empleados",
                evento.producer()
        );

        assertNotNull(
                evento.id()
        );

        assertNotNull(
                evento.occurredAt()
        );

        // ASSERT - payload específico de retiro
        assertInstanceOf(
                EmpleadoEvento
                        .EmpleadoRetiradoData.class,
                evento.data()
        );

        EmpleadoEvento.EmpleadoRetiradoData data =
                (EmpleadoEvento.EmpleadoRetiradoData)
                        evento.data();

        assertEquals(
                "E001",
                data.empleadoId()
        );

        assertEquals(
                "EMP-001",
                data.numeroEmpleado()
        );

        assertEquals(
                "Juan",
                data.nombre()
        );

        assertEquals(
                "Perez",
                data.apellido()
        );

        assertEquals(
                "juan@empresa.com",
                data.email()
        );

        assertEquals(
                "RETIRADO",
                data.estado()
        );

        assertEquals(
                fechaRetiro,
                data.fechaRetiro()
        );
    }

    // =========================================================
    // ESTADO NULL EN EMPLEADO DATA
    // =========================================================

    @Test
    void debePermitirEstadoNuloEnEventoCreado() {

        // ARRANGE
        empleado.setEstado(null);

        // ACT
        EmpleadoEvento evento =
                EmpleadoEvento.creado(
                        empleado
                );

        EmpleadoEvento.EmpleadoData data =
                (EmpleadoEvento.EmpleadoData)
                        evento.data();

        // ASSERT
        assertNull(
                data.estado()
        );
    }

    // =========================================================
    // ESTADO NULL EN EVENTO RETIRADO
    // =========================================================

    @Test
    void debePermitirEstadoNuloEnEventoRetirado() {

        // ARRANGE
        empleado.setEstado(null);

        // ACT
        EmpleadoEvento evento =
                EmpleadoEvento.retirado(
                        empleado
                );

        EmpleadoEvento.EmpleadoRetiradoData data =
                (EmpleadoEvento.EmpleadoRetiradoData)
                        evento.data();

        // ASSERT
        assertNull(
                data.estado()
        );
    }

    // =========================================================
    // ID ÚNICO
    // =========================================================

    @Test
    void debeGenerarIdentificadorDistintoParaCadaEvento() {

        // ACT
        EmpleadoEvento evento1 =
                EmpleadoEvento.creado(
                        empleado
                );

        EmpleadoEvento evento2 =
                EmpleadoEvento.creado(
                        empleado
                );

        // ASSERT
        assertNotEquals(
                evento1.id(),
                evento2.id()
        );
    }
}