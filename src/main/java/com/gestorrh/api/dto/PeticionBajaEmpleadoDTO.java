package com.gestorrh.api.dto;

import lombok.Data;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;

@Data
public class PeticionBajaEmpleadoDTO {
    @NotNull(message = "La fecha de baja es obligatoria")
    private LocalDate fecha_baja_contrato;
}
