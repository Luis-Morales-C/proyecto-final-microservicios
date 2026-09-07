package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.dto.MensajeDTO;
import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.service.EmpleadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empleados")
public class EmpleadoController {

    private final EmpleadoService service;

    public EmpleadoController(EmpleadoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MensajeDTO<Empleado>> registrar(@Valid @RequestBody Empleado empleado) {
        Empleado creado = service.registrar(empleado);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MensajeDTO<>(false, creado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MensajeDTO<Empleado>> consultar(@PathVariable String id) {
        return ResponseEntity.ok(new MensajeDTO<>(false, service.consultar(id)));
    }
}