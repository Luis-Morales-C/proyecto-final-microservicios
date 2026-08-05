package co.edu.uniquindio.gestionempleados.repository;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EmpleadoRepository {

    private final Map<String, Empleado> empleados = new ConcurrentHashMap<>();

    public Empleado save(Empleado empleado) {
        empleados.put(empleado.id(), empleado);
        return empleado;
    }

    public Optional<Empleado> findById(String id) {
        return Optional.ofNullable(empleados.get(id));
    }

    public boolean existsByEmail(String email) {
        return empleados.values().stream()
                .anyMatch(e -> e.email().equalsIgnoreCase(email));
    }

    public boolean existsByNumeroEmpleado(String numeroEmpleado) {
        return empleados.values().stream()
                .anyMatch(e -> e.numeroEmpleado().equalsIgnoreCase(numeroEmpleado));
    }
}