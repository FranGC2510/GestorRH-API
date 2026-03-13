package com.gestorrh.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que cruza a un Empleado con un Turno en una fecha concreta.
 * Épica E5 - Gestión de Asignaciones.
 */
@Entity
@Table(name = "asignacion_turno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsignacionTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Long idAsignacion;

    @NotNull(message = "El empleado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @NotNull(message = "El turno es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @NotNull(message = "La fecha de la asignación es obligatoria")
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @NotNull(message = "La modalidad es obligatoria")
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false)
    private ModalidadTurno modalidad;

    @Column(name = "motivo_cambio", length = 255)
    private String motivoCambio;

    @Column(name = "fecha_cambio")
    private LocalDateTime fechaCambio;

    @Column(name = "responsable_cambio", length = 100)
    private String responsableCambio;

    @Version
    @Column(name = "version")
    private Long version;
}
