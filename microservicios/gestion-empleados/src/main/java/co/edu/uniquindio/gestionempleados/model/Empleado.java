package co.edu.uniquindio.gestionempleados.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Entity
@Table(name = "empleados")
public class Empleado {

        @Id
        private String id;

        @NotBlank(message = "El nombre es obligatorio")
        private String nombre;

        @NotBlank(message = "El apellido es obligatorio")
        private String apellido;

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email debe tener un formato válido")
        private String email;

        @NotBlank(message = "El número de empleado es obligatorio")
        @Pattern(
                regexp = "^[A-Z0-9-]+$",
                message = "El número de empleado solo puede contener letras mayúsculas, números y guiones"
        )
        private String numeroEmpleado;

        @NotBlank(message = "El cargo es obligatorio")
        private String cargo;

        @NotBlank(message = "El área es obligatoria")
        private String area;

        @NotBlank(message = "El departamento es obligatorio")
        private String departamentoId;

        @NotNull(message = "La fecha de ingreso es obligatoria")
        @PastOrPresent(message = "La fecha de ingreso no puede ser una fecha futura")
        private LocalDate fechaIngreso;

        private EstadoEmpleado estado;

        // Constructor vacío requerido por JPA
        public Empleado() {
        }

        // Constructor completo
        public Empleado(String id, String nombre, String apellido, String email,
                        String numeroEmpleado, String cargo, String area,
                        String departamentoId, LocalDate fechaIngreso,
                        EstadoEmpleado estado) {

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