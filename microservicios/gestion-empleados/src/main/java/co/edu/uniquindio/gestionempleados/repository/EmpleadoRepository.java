package co.edu.uniquindio.gestionempleados.repository;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpleadoRepository
        extends JpaRepository<Empleado, String> {

    boolean existsByEmailIgnoreCase(
            String email
    );

    boolean existsByNumeroEmpleadoIgnoreCase(
            String numeroEmpleado
    );

    List<Empleado> findByEstado(EstadoEmpleado estado);
}