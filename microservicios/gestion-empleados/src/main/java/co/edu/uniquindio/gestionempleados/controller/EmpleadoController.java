package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import co.edu.uniquindio.gestionempleados.model.Empleado;
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
            description = "Obtiene todos los empleados registrados."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista obtenida correctamente"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno"
            )
    })
    @GetMapping
    public ResponseEntity<MensajeDTO<List<Empleado>>>
    consultarTodos() {

        return ResponseEntity.ok(
                new MensajeDTO<>(
                        false,
                        service.consultarTodos()
                )
        );
    }
}