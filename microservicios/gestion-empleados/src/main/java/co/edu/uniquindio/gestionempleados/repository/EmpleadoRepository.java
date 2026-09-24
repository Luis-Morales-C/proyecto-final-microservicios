package co.edu.uniquindio.gestionempleados.repository;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, String> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNumeroEmpleadoIgnoreCase(String numeroEmpleado);

    List<Empleado> findByEstado(EstadoEmpleado estado);

    // Auditoría sin filtros de fecha
    List<Empleado> findByEstadoOrderByFechaRetiroDesc(
            EstadoEmpleado estado
    );

    // Auditoría con fecha inicial
    @Query("""
        SELECT e
        FROM Empleado e
        WHERE e.estado = :estado
          AND e.fechaRetiro >= :desde
        ORDER BY e.fechaRetiro DESC
        """)
    List<Empleado> buscarRetiradosDesde(
            @Param("estado") EstadoEmpleado estado,
            @Param("desde") LocalDateTime desde
    );

    // Auditoría con fecha final
    @Query("""
        SELECT e
        FROM Empleado e
        WHERE e.estado = :estado
          AND e.fechaRetiro <= :hasta
        ORDER BY e.fechaRetiro DESC
        """)
    List<Empleado> buscarRetiradosHasta(
            @Param("estado") EstadoEmpleado estado,
            @Param("hasta") LocalDateTime hasta
    );

    // Auditoría dentro de un intervalo
    @Query("""
        SELECT e
        FROM Empleado e
        WHERE e.estado = :estado
          AND e.fechaRetiro >= :desde
          AND e.fechaRetiro <= :hasta
        ORDER BY e.fechaRetiro DESC
        """)
    List<Empleado> buscarRetiradosEntre(
            @Param("estado") EstadoEmpleado estado,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );
}