package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.dto.ActualizarEmpleadoDTO;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoRetiradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.service.EmpleadoService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Pruebas unitarias del controlador de empleados.
 *
 * Estas pruebas verifican:
 * - códigos HTTP,
 * - estructura de las respuestas,
 * - manejo de excepciones,
 * - invocación correcta de EmpleadoService.
 *
 * EmpleadoService se encuentra simulado con Mockito,
 * por lo que estas pruebas no requieren base de datos,
 * RabbitMQ ni otros microservicios.
 */
@ExtendWith(MockitoExtension.class)
class EmpleadoControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private EmpleadoService service;

    @InjectMocks
    private EmpleadoController controller;

    private Empleado empleado;

    @BeforeEach
    void setUp() {

        /*
         * MockMvc permite ejecutar peticiones HTTP simuladas
         * directamente contra el controlador.
         *
         * También se registra GlobalExceptionHandler para
         * comprobar los códigos HTTP producidos por excepciones.
         */
        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new co.edu.uniquindio
                                        .gestionempleados
                                        .exception
                                        .GlobalExceptionHandler()
                        )
                        .build();

        objectMapper =
                new ObjectMapper()
                        .registerModule(
                                new JavaTimeModule()
                        );

        empleado =
                new Empleado(
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
    // REGISTRO
    // =========================================================

    @Test
    void debeCrearEmpleado() throws Exception {

        // ARRANGE
        when(service.registrar(any()))
                .thenReturn(empleado);

        // ACT + ASSERT
        mockMvc.perform(
                        post("/empleados")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                empleado
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(false)
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.id"
                        ).value("E001")
                );

        // VERIFY
        verify(service)
                .registrar(any());
    }

    @Test
    void debeRechazarEmpleadoDuplicado() throws Exception {

        // ARRANGE
        when(service.registrar(any()))
                .thenThrow(
                        new EmpleadoDuplicadoException(
                                "El email ya está registrado"
                        )
                );

        // ACT + ASSERT
        mockMvc.perform(
                        post("/empleados")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                empleado
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void debeResponder400DepartamentoNoEncontrado()
            throws Exception {

        // ARRANGE
        when(service.registrar(any()))
                .thenThrow(
                        new DepartamentoNoEncontradoException(
                                "NO-EXISTE"
                        )
                );

        // ACT + ASSERT
        mockMvc.perform(
                        post("/empleados")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                empleado
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void debeResponder503DepartamentoNoDisponible()
            throws Exception {

        // ARRANGE
        when(service.registrar(any()))
                .thenThrow(
                        new DepartamentoNoDisponibleException()
                );

        // ACT + ASSERT
        mockMvc.perform(
                        post("/empleados")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                empleado
                                        )
                                )
                )
                .andExpect(
                        status().isServiceUnavailable()
                );
    }

    @Test
    void debeRechazarJsonInvalido()
            throws Exception {

        /*
         * JSON incompleto y mal formado.
         * Spring debe rechazarlo antes de llegar al service.
         */
        mockMvc.perform(
                        post("/empleados")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"id\":\"E001\""
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(service);
    }

    // =========================================================
    // CONSULTAS
    // =========================================================

    @Test
    void debeConsultarEmpleado()
            throws Exception {

        // ARRANGE
        when(service.consultar("E001"))
                .thenReturn(empleado);

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados/E001")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.id"
                        ).value("E001")
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.nombre"
                        ).value("Juan")
                );

        // VERIFY
        verify(service)
                .consultar("E001");
    }

    @Test
    void debeResponder404EmpleadoNoEncontrado()
            throws Exception {

        // ARRANGE
        when(service.consultar("E999"))
                .thenThrow(
                        new EmpleadoNoEncontradoException(
                                "E999"
                        )
                );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados/E999")
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void debeConsultarTodos()
            throws Exception {

        // ARRANGE
        when(service.consultarTodos())
                .thenReturn(
                        List.of(empleado)
                );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta[0].id"
                        ).value("E001")
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta[0].estado"
                        ).value("ACTIVO")
                );

        // VERIFY
        verify(service)
                .consultarTodos();
    }

    @Test
    void debeConsultarEmpleadosPorEstado()
            throws Exception {

        // ARRANGE
        when(service.consultarPorEstado(
                EstadoEmpleado.ACTIVO
        )).thenReturn(
                List.of(empleado)
        );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados")
                                .param(
                                        "estado",
                                        "ACTIVO"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta[0].estado"
                        ).value("ACTIVO")
                );

        // VERIFY
        verify(service)
                .consultarPorEstado(
                        EstadoEmpleado.ACTIVO
                );
    }

    // =========================================================
    // ACTUALIZACIÓN
    // =========================================================

    @Test
    void debeActualizarEmpleado()
            throws Exception {

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

        Empleado actualizado =
                new Empleado(
                        "E001",
                        "Juan Actualizado",
                        "Perez",
                        "juan.nuevo@empresa.com",
                        "EMP-001",
                        "Arquitecto",
                        "Tecnologia",
                        "IT",
                        LocalDate.now(),
                        EstadoEmpleado.ACTIVO
                );

        when(service.actualizar(
                eq("E001"),
                any(ActualizarEmpleadoDTO.class)
        )).thenReturn(actualizado);

        // ACT + ASSERT
        mockMvc.perform(
                        put("/empleados/E001")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                cambios
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.id"
                        ).value("E001")
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.nombre"
                        ).value(
                                "Juan Actualizado"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.cargo"
                        ).value(
                                "Arquitecto"
                        )
                );

        // VERIFY
        verify(service)
                .actualizar(
                        eq("E001"),
                        any(ActualizarEmpleadoDTO.class)
                );
    }


    @Test
    void debeRechazarEmailDuplicadoAlActualizar()
            throws Exception {

        // ARRANGE
        ActualizarEmpleadoDTO cambios =
                new ActualizarEmpleadoDTO(
                        "Juan",
                        "Perez",
                        "duplicado@empresa.com",
                        "Arquitecto",
                        "Tecnologia",
                        "IT"
                );

        when(service.actualizar(
                eq("E001"),
                any(ActualizarEmpleadoDTO.class)
        )).thenThrow(
                new EmpleadoDuplicadoException(
                        "El email ya está registrado"
                )
        );

        // ACT + ASSERT
        mockMvc.perform(
                        put("/empleados/E001")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                cambios
                                        )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    // =========================================================
    // RETIRO
    // =========================================================

    @Test
    void debeRetirarEmpleado()
            throws Exception {

        // ARRANGE
        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        empleado.setFechaRetiro(
                LocalDateTime.now()
        );

        when(service.retirar("E001"))
                .thenReturn(empleado);

        // ACT + ASSERT
        mockMvc.perform(
                        delete("/empleados/E001")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.id"
                        ).value("E001")
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta.estado"
                        ).value("RETIRADO")
                );

        // VERIFY
        verify(service)
                .retirar("E001");
    }

    @Test
    void debeResponder404AlRetirarEmpleadoInexistente()
            throws Exception {

        // ARRANGE
        when(service.retirar("E999"))
                .thenThrow(
                        new EmpleadoNoEncontradoException(
                                "E999"
                        )
                );

        // ACT + ASSERT
        mockMvc.perform(
                        delete("/empleados/E999")
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    // =========================================================
    // AUDITORÍA
    // =========================================================

    @Test
    void debeConsultarAuditoriaSinFechas()
            throws Exception {

        // ARRANGE
        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        empleado.setFechaRetiro(
                LocalDateTime.now()
        );

        when(service.consultarAuditoria(
                null,
                null
        )).thenReturn(
                List.of(empleado)
        );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados/auditoria")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.respuesta[0].estado"
                        ).value("RETIRADO")
                );

        // VERIFY
        verify(service)
                .consultarAuditoria(
                        null,
                        null
                );
    }

    @Test
    void debeConsultarAuditoriaConRangoDeFechas()
            throws Exception {

        // ARRANGE
        LocalDateTime desde =
                LocalDateTime.of(
                        2026,
                        10,
                        1,
                        0,
                        0
                );

        LocalDateTime hasta =
                LocalDateTime.of(
                        2026,
                        10,
                        31,
                        23,
                        59
                );

        empleado.setEstado(
                EstadoEmpleado.RETIRADO
        );

        when(service.consultarAuditoria(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(
                List.of(empleado)
        );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados/auditoria")
                                .param(
                                        "desde",
                                        desde.toString()
                                )
                                .param(
                                        "hasta",
                                        hasta.toString()
                                )
                )
                .andExpect(
                        status().isOk()
                );

        // VERIFY
        verify(service)
                .consultarAuditoria(
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                );
    }

    @Test
    void debeRechazarAuditoriaConRangoInvalido()
            throws Exception {

        // ARRANGE
        when(service.consultarAuditoria(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenThrow(
                new IllegalArgumentException(
                        "La fecha desde no puede ser posterior a la fecha hasta"
                )
        );

        // ACT + ASSERT
        mockMvc.perform(
                        get("/empleados/auditoria")
                                .param(
                                        "desde",
                                        "2026-10-31T00:00:00"
                                )
                                .param(
                                        "hasta",
                                        "2026-10-01T00:00:00"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }
}