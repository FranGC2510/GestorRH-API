package com.gestorrh.api.dto.fichaje;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO para la respuesta con los detalles de un fichaje.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaFichajeDTO {

    private Long idFichaje;
    private Long idEmpleado;
    private String nombreEmpleado;

    private Long idAsignacion;
    private String descripcionTurno;

    private LocalDate fecha;
    private OffsetDateTime horaEntrada;
    private OffsetDateTime horaSalida;

    private String incidencias;
}
