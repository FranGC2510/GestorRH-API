package com.gestorrh.api.dto.asignacion;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gestorrh.api.entity.enums.ModalidadTurno;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;

/**
 * DTO para la respuesta con los datos de una asignación de turno.
 */
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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaInicio;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horaFin;

    private LocalDate fecha;
    private ModalidadTurno modalidad;

    private String motivoCambio;
    private OffsetDateTime fechaCambio;
    private String responsableCambio;
}
