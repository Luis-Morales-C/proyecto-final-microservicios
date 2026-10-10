package co.edu.uniquindio.gestionempleados.integration;

import co.edu.uniquindio.gestionempleados.model.Empleado;
import co.edu.uniquindio.gestionempleados.model.EstadoEmpleado;
import co.edu.uniquindio.gestionempleados.repository.EmpleadoRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración del repositorio de empleados.
 *
 * A diferencia de las pruebas unitarias, estas pruebas utilizan
 * un PostgreSQL real levantado automáticamente con Testcontainers.
 *
 * Se verifica:
 * - persistencia real,
 * - lectura desde base de datos,
 * - restricciones UNIQUE del esquema.
 */
@Testcontainers
@DataJpaTest
class EmpleadoRepositoryIntegrationTest {

    /**
     * PostgreSQL real utilizado únicamente durante las pruebas.
     *
     * Testcontainers crea el contenedor antes de ejecutar los tests
     * y lo elimina automáticamente al terminar.
     */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("empleados_test")
                    .withUsername("test")
                    .withPassword("test");

    /**
     * Spring recibe dinámicamente los datos de conexión
     * del contenedor PostgreSQL creado por Testcontainers.
     */
    @DynamicPropertySource
    static void configurarPostgres(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );
    }

    @Autowired
    private EmpleadoRepository repository;

    // =========================================================
    // PERSISTENCIA REAL
    // =========================================================

    @Test
    void debeGuardarYConsultarEmpleadoEnPostgreSQL() {

        // ARRANGE
        Empleado empleado =
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

        // ACT
        repository.saveAndFlush(empleado);

        Optional<Empleado> encontrado =
                repository.findById("E001");

        // ASSERT
        assertTrue(
                encontrado.isPresent()
        );

        assertEquals(
                "Juan",
                encontrado.get().getNombre()
        );

        assertEquals(
                "juan@empresa.com",
                encontrado.get().getEmail()
        );

        assertEquals(
                EstadoEmpleado.ACTIVO,
                encontrado.get().getEstado()
        );
    }

    // =========================================================
    // RESTRICCIÓN UNIQUE - EMAIL
    // =========================================================

    @Test
    void debeRechazarEmailDuplicadoEnPostgreSQL() {

        // ARRANGE
        Empleado empleado1 =
                new Empleado(
                        "E001",
                        "Juan",
                        "Perez",
                        "duplicado@empresa.com",
                        "EMP-001",
                        "Desarrollador",
                        "Tecnologia",
                        "IT",
                        LocalDate.now(),
                        EstadoEmpleado.ACTIVO
                );

        Empleado empleado2 =
                new Empleado(
                        "E002",
                        "Maria",
                        "Gomez",
                        "duplicado@empresa.com",
                        "EMP-002",
                        "Analista",
                        "Tecnologia",
                        "IT",
                        LocalDate.now(),
                        EstadoEmpleado.ACTIVO
                );

        repository.saveAndFlush(
                empleado1
        );

        // ACT + ASSERT
        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(
                        empleado2
                )
        );
    }

    // =========================================================
    // RESTRICCIÓN UNIQUE - NÚMERO DE EMPLEADO
    // =========================================================

    @Test
    void debeRechazarNumeroEmpleadoDuplicadoEnPostgreSQL() {

        // ARRANGE
        Empleado empleado1 =
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

        Empleado empleado2 =
                new Empleado(
                        "E002",
                        "Maria",
                        "Gomez",
                        "maria@empresa.com",
                        "EMP-001",
                        "Analista",
                        "Tecnologia",
                        "IT",
                        LocalDate.now(),
                        EstadoEmpleado.ACTIVO
                );

        repository.saveAndFlush(
                empleado1
        );

        // ACT + ASSERT
        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(
                        empleado2
                )
        );
    }
}