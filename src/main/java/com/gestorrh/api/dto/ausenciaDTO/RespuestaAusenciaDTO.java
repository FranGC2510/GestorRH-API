package com.gestorrh.api.dto.ausenciaDTO;

import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.TipoAusencia;
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
public class RespuestaAusenciaDTO {

    private Long idAusencia;
    private Long idEmpleado;
    private String nombreCompletoEmpleado;
    private TipoAusencia tipo;
    private String descripcion;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String justificante;
    private EstadoAusencia estado;

    private String responsableRevision;
    private String observacionesRevision;
}
