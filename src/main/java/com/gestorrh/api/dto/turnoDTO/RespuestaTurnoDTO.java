package com.gestorrh.api.dto.turnoDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * DTO para enviar los datos de un turno al cliente de forma segura.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RespuestaTurnoDTO {

    private Long idTurno;
    private String descripcion;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    // NOTA: No devolvemos la Empresa aquí porque el cliente (la Empresa logueada) ya sabe quién es.
}
