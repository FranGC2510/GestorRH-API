package com.gestorrh.api.dto;

import com.gestorrh.api.entity.ModalidadTurno;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RespuestaAsignacionTurnoDTO {

    private Long idAsignacion;
    private Long idEmpleado;
    private String nombreCompletoEmpleado;
    private Long idTurno;
    private String descripcionTurno;
    private LocalDate fecha;
    private ModalidadTurno modalidad;

    private String motivoCambio;
    private LocalDateTime fechaCambio;
    private String responsableCambio;
}
