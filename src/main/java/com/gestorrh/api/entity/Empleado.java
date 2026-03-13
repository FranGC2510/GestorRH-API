package com.gestorrh.api.entity;

import com.gestorrh.api.entity.enums.RolEmpleado;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa a la Plantilla (Empleado/Supervisor) en la base de datos.
 * Usuarios operativos que usarán la App Móvil.
 * Tiene una relación directa (N:1) con la Empresa a la que pertenecen.
 */
@Entity
@Table(name = "empleado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Long idEmpleado;

    // --- RELACIONES ---
    @NotNull(message = "El empleado debe pertenecer a una empresa")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // --- DATOS DE ACCESO ---
    @NotBlank(message = "El email no puede estar vacío")
    @Email(message = "El formato del email no es válido")
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // --- DATOS PERSONALES ---
    @NotBlank(message = "El nombre es obligatorio")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Column(name = "apellidos", nullable = false, length = 150)
    private String apellidos;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "puesto", length = 100)
    private String puesto;

    @Column(name = "departamento", length = 100)
    private String departamento;

    @NotNull(message = "El rol es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private RolEmpleado rol;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_baja_contrato")
    private java.time.LocalDate fechaBajaContrato;
}
