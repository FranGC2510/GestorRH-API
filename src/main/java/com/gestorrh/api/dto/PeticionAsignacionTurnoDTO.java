package com.gestorrh.api.dto;

import com.gestorrh.api.entity.ModalidadTurno;
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
public class PeticionAsignacionTurnoDTO {

    @NotNull(message = "El ID del empleado es obligatorio")
    private Long idEmpleado;

    @NotNull(message = "El ID del turno es obligatorio")
    private Long idTurno;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La modalidad es obligatoria")
    private ModalidadTurno modalidad;

    private String motivoCambio;
}
