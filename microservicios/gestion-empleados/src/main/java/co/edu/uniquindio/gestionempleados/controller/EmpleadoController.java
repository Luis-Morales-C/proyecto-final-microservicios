package co.edu.uniquindio.gestionempleados.controller;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.service.EmpleadoService;
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
    public ResponseEntity<Empleado> registrar(@RequestBody Empleado empleado) {
        Empleado creado = service.registrar(empleado);
        return ResponseEntity.ok(creado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Empleado> consultar(@PathVariable String id) {
        return ResponseEntity.ok(service.consultar(id));
    }
}