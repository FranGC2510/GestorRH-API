package com.gestorrh.api.dto.ausencia;

import com.gestorrh.api.entity.enums.TipoAusencia;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO para la petición de una nueva ausencia.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeticionAusenciaDTO {

    @NotNull(message = "El tipo de ausencia es obligatorio")
    private TipoAusencia tipo;

    private String descripcion;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;

    @Builder.Default
    private Boolean eliminarJustificante = false;
}
