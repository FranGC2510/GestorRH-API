package com.gestorrh.api.entity;

import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.TipoAusencia;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Entidad que representa una solicitud de ausencia o permiso de un empleado.
 * Épica E6 - Gestión de Ausencias.
 */
@Entity
@Table(name = "ausencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ausencia")
    private Long idAusencia;

    @NotNull(message = "El empleado es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @NotNull(message = "El tipo de ausencia es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoAusencia tipo;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    // TODO (Deuda Técnica): Actualmente recibe un String. En el futuro se implementará subida de ficheros (MultipartFile).
    @Column(name = "justificante", length = 500)
    private String justificante;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoAusencia estado;

    @Column(name = "responsable_revision", length = 100)
    private String responsableRevision;

    @Column(name = "observaciones_revision", length = 255)
    private String observacionesRevision;

    @Version
    @Column(name = "version")
    private Long version;
}
