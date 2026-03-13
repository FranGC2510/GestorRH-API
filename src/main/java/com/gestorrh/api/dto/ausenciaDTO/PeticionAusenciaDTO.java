package com.gestorrh.api.dto.ausenciaDTO;

import com.gestorrh.api.entity.enums.TipoAusencia;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

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

    private String justificante; // Opcional por ahora, será un String simulando la URL
}
