package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.exception.DepartamentoNoDisponibleException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoDuplicadoException;
import co.edu.uniquindio.gestionempleados.exception.EmpleadoNoEncontradoException;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.service.EmpleadoService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
                new ObjectMapper();

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

    @Test
    void debeCrearEmpleado() throws Exception {

        when(service.registrar(any()))
                .thenReturn(empleado);

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
    }

    @Test
    void debeConsultarEmpleado() throws Exception {

        when(service.consultar("E001"))
                .thenReturn(empleado);

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
                );
    }

    @Test
    void debeConsultarTodos() throws Exception {

        when(service.consultarTodos())
                .thenReturn(
                        List.of(empleado)
                );

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
                );
    }

    @Test
    void debeRechazarEmpleadoDuplicado() throws Exception {

        when(service.registrar(any()))
                .thenThrow(
                        new EmpleadoDuplicadoException(
                                "El email ya está registrado"
                        )
                );

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
    void debeResponder404EmpleadoNoEncontrado()
            throws Exception {

        when(service.consultar("E999"))
                .thenThrow(
                        new EmpleadoNoEncontradoException(
                                "E999"
                        )
                );

        mockMvc.perform(
                        get("/empleados/E999")
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void debeResponder400DepartamentoNoEncontrado()
            throws Exception {

        when(service.registrar(any()))
                .thenThrow(
                        new DepartamentoNoEncontradoException(
                                "NO-EXISTE"
                        )
                );

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

        when(service.registrar(any()))
                .thenThrow(
                        new DepartamentoNoDisponibleException()
                );

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
    }
}