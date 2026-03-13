package com.gestorrh.api.dto.ausenciaDTO;

import com.gestorrh.api.entity.enums.EstadoAusencia;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeticionRevisionAusenciaDTO {

    @NotNull(message = "El nuevo estado es obligatorio (APROBADA o RECHAZADA)")
    private EstadoAusencia estado;

    private String observacionesRevision;
}
