package co.edu.uniquindio.gestionempleados.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Entity
@Table(
        name = "empleados",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_empleados_email",
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = "uk_empleados_numero",
                        columnNames = "numero_empleado"
                )
        }
)
@Schema(
        description = "Modelo completo de un empleado"
)
public class Empleado {

        @Id
        @NotBlank(message = "El id es obligatorio")
        @Schema(
                description = "Identificador único del empleado",
                example = "E001"
        )
        private String id;

        @NotBlank(message = "El nombre es obligatorio")
        @Schema(example = "Juan")
        private String nombre;

        @NotBlank(message = "El apellido es obligatorio")
        @Schema(example = "Pérez")
        private String apellido;

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe tener un formato válido")
        @Column(
                nullable = false,
                unique = true
        )
        @Schema(example = "juan.perez@empresa.com")
        private String email;

        @NotBlank(message = "El número de empleado es obligatorio")
        @Pattern(
                regexp = "^[A-Z0-9-]+$",
                message = (
                        "El número de empleado solo puede contener "
                                + "letras mayúsculas, números y guiones"
                )
        )
        @Column(
                name = "numero_empleado",
                nullable = false,
                unique = true
        )
        @Schema(example = "EMP-2026-001")
        private String numeroEmpleado;

        @NotBlank(message = "El cargo es obligatorio")
        @Schema(example = "Desarrollador Senior")
        private String cargo;

        @NotBlank(message = "El área es obligatoria")
        @Schema(example = "Tecnología")
        private String area;

        @NotBlank(message = "El departamento es obligatorio")
        @Schema(example = "IT")
        private String departamentoId;

        @NotNull(message = "La fecha de ingreso es obligatoria")
        @PastOrPresent(
                message = "La fecha de ingreso no puede ser una fecha futura"
        )
        @Schema(example = "2026-02-10")
        private LocalDate fechaIngreso;

        @Enumerated(EnumType.STRING)
        @Column(
                nullable = false
        )
        @Schema(
                description = "Estado del empleado. En Reto 2 siempre es ACTIVO",
                example = "ACTIVO"
        )
        private EstadoEmpleado estado;

        public Empleado() {
        }

        public Empleado(
                String id,
                String nombre,
                String apellido,
                String email,
                String numeroEmpleado,
                String cargo,
                String area,
                String departamentoId,
                LocalDate fechaIngreso,
                EstadoEmpleado estado
        ) {
                this.id = id;
                this.nombre = nombre;
                this.apellido = apellido;
                this.email = email;
                this.numeroEmpleado = numeroEmpleado;
                this.cargo = cargo;
                this.area = area;
                this.departamentoId = departamentoId;
                this.fechaIngreso = fechaIngreso;
                this.estado = estado;
        }

        public String getId() {
                return id;
        }

        public void setId(String id) {
                this.id = id;
        }

        public String getNombre() {
                return nombre;
        }

        public void setNombre(String nombre) {
                this.nombre = nombre;
        }

        public String getApellido() {
                return apellido;
        }

        public void setApellido(String apellido) {
                this.apellido = apellido;
        }

        public String getEmail() {
                return email;
        }

        public void setEmail(String email) {
                this.email = email;
        }

        public String getNumeroEmpleado() {
                return numeroEmpleado;
        }

        public void setNumeroEmpleado(String numeroEmpleado) {
                this.numeroEmpleado = numeroEmpleado;
        }

        public String getCargo() {
                return cargo;
        }

        public void setCargo(String cargo) {
                this.cargo = cargo;
        }

        public String getArea() {
                return area;
        }

        public void setArea(String area) {
                this.area = area;
        }

        public String getDepartamentoId() {
                return departamentoId;
        }

        public void setDepartamentoId(String departamentoId) {
                this.departamentoId = departamentoId;
        }

        public LocalDate getFechaIngreso() {
                return fechaIngreso;
        }

        public void setFechaIngreso(LocalDate fechaIngreso) {
                this.fechaIngreso = fechaIngreso;
        }

        public EstadoEmpleado getEstado() {
                return estado;
        }

        public void setEstado(EstadoEmpleado estado) {
                this.estado = estado;
        }
}