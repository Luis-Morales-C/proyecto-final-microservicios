package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.dto.EmpleadoAuditoriaDTO;
import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.service.EmpleadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import co.edu.uniquindio.gestionempleados.dto.ActualizarEmpleadoDTO;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;

@RestController
@RequestMapping("/empleados")
@Tag(
        name = "Empleados",
        description = "Operaciones de gestión de empleados"
)
public class EmpleadoController {
    private final EmpleadoService service;

    public EmpleadoController(
            EmpleadoService service
    ) {
        this.service = service;
    }

    @Operation(
            summary = "Registrar empleado",
            description = (
                    "Registra un empleado validando "
                            + "email, número de empleado y "
                            + "existencia del departamento."
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Empleado creado correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o duplicados",
                    content = @Content(
                            schema = @Schema(
                                    implementation = MensajeDTO.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = (
                            "El servicio de departamentos "
                                    + "no está disponible"
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno"
            )
    })
    @PostMapping
    public ResponseEntity<MensajeDTO<Empleado>> registrar(
            @Valid @RequestBody Empleado empleado
    ) {

        Empleado creado =
                service.registrar(empleado);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        new MensajeDTO<>(
                                false,
                                creado
                        )
                );
    }

    @Operation(
            summary = "Actualizar empleado",
            description = "Actualiza nombre, apellido, email, cargo, área y departamento. "
                    + "Conserva el ID, número de empleado y fecha de ingreso."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Empleado actualizado correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o email duplicado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Empleado no encontrado"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "No se puede actualizar un empleado retirado"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<MensajeDTO<Empleado>> actualizar(
            @PathVariable String id,
            @Valid @RequestBody ActualizarEmpleadoDTO cambios
    ) {
        Empleado actualizado = service.actualizar(id, cambios);

        return ResponseEntity.ok(
                new MensajeDTO<>(false, actualizado)
        );
    }

    @Operation(
            summary = "Consultar empleado",
            description = (
                    "Obtiene un empleado por "
                            + "su identificador."
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Empleado encontrado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Empleado no encontrado"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno"
            )
    })

    @GetMapping("/{id}")
    public ResponseEntity<MensajeDTO<Empleado>> consultar(
            @PathVariable String id
    ) {

        return ResponseEntity.ok(
                new MensajeDTO<>(
                        false,
                        service.consultar(id)
                )
        );
    }

    @Operation(
            summary = "Listar empleados",
            description = "Lista todos los empleados o filtra por estado."
    )
    @GetMapping(params = {"!desde", "!hasta", "!estado"})
    public ResponseEntity<MensajeDTO<List<Empleado>>> consultarTodos() {
        return ResponseEntity.ok(
                new MensajeDTO<>(false, service.consultarTodos())
        );
    }

    @Operation(
            summary = "Consultar empleados por estado",
            description = "Para RETIRADO devuelve los campos de auditoría "
                    + "y permite filtrar por fechas yyyy-MM-dd."
    )
    @GetMapping(params = "estado")
    public ResponseEntity<MensajeDTO<?>> consultarPorEstado(
            @RequestParam EstadoEmpleado estado,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate desde,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate hasta
    ) {
        if (estado != EstadoEmpleado.RETIRADO) {

            if (desde != null || hasta != null) {
                throw new IllegalArgumentException(
                        "Los filtros de fecha solo aplican "
                                + "cuando estado=RETIRADO"
                );
            }

            return ResponseEntity.ok(
                    new MensajeDTO<>(
                            false,
                            service.consultarPorEstado(estado)
                    )
            );
        }

        LocalDateTime fechaDesde =
                desde != null ? desde.atStartOfDay() : null;

        LocalDateTime fechaHasta =
                hasta != null ? hasta.atTime(23, 59, 59, 999999999) : null;

        List<EmpleadoAuditoriaDTO> auditoria =
                service.consultarAuditoria(fechaDesde, fechaHasta)
                        .stream()
                        .map(EmpleadoAuditoriaDTO::desde)
                        .toList();

        return ResponseEntity.ok(
                new MensajeDTO<>(false, auditoria)
        );
    }

    @Operation(
            summary = "Reconciliar empleados pendientes",
            description = (
                    "Revalida el departamento de los empleados en "
                            + "PENDIENTE_VALIDACION. Pasan a ACTIVO, "
                            + "o a RECHAZADO si el departamento no existe. "
                            + "Retorna los que siguen pendientes."
            )
    )
    @PostMapping("/reconciliar")
    public ResponseEntity<MensajeDTO<List<Empleado>>> reconciliar() {

        return ResponseEntity.ok(
                new MensajeDTO<>(
                        false,
                        service.reconciliarPendientes()
                )
        );
    }

    @Operation(
            summary = "Retirar empleado",
            description = "Realiza el retiro lógico del empleado sin eliminar su registro, además registra la fecha y hora del retiro"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Empleado retirado correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Empleado no encontrado"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El empleado ya se encuentra retirado"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<MensajeDTO<Empleado>> retirar(
            @PathVariable String id
    ) {
        Empleado retirado = service.retirar(id);

        return ResponseEntity.ok(
                new MensajeDTO<>(
                        false,
                        retirado
                )
        );
    }

    @Operation(
            summary = "Consultar auditoría de retiros",
            description = "Lista los empleados retirados con los campos "
                    + "de auditoría y permite filtrar por fecha y hora."
    )
    @GetMapping("/auditoria")
    public ResponseEntity<MensajeDTO<List<EmpleadoAuditoriaDTO>>> consultarAuditoria(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime desde,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime hasta
    ) {
        List<EmpleadoAuditoriaDTO> auditoria =
                service.consultarAuditoria(desde, hasta)
                        .stream()
                        .map(EmpleadoAuditoriaDTO::desde)
                        .toList();

        return ResponseEntity.ok(
                new MensajeDTO<>(false, auditoria)
        );
    }
}