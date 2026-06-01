package com.gestorrh.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entidad que registra las entradas y salidas diarias de un empleado.
 */
@Entity
@Table(name = "fichaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fichaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fichaje")
    private Long idFichaje;

    @NotNull(message = "El empleado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id")
    private AsignacionTurno asignacion;

    @NotNull(message = "La fecha del fichaje es obligatoria")
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @NotNull(message = "La hora de entrada es obligatoria")
    @Column(name = "hora_entrada", nullable = false)
    private OffsetDateTime horaEntrada;

    @Column(name = "latitud_entrada")
    private Double latitudEntrada;

    @Column(name = "longitud_entrada")
    private Double longitudEntrada;

    @Column(name = "hora_salida")
    private OffsetDateTime horaSalida;

    @Column(name = "latitud_salida")
    private Double latitudSalida;

    @Column(name = "longitud_salida")
    private Double longitudSalida;

    @Column(name = "incidencias", length = 500)
    private String incidencias;
}
