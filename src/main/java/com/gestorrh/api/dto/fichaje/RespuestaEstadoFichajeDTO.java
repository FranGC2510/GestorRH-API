package com.gestorrh.api.dto.fichaje;

import com.gestorrh.api.entity.enums.ModalidadTurno;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO que consolida el estado actual de la jornada laboral de un empleado.
 * <p>
 * Diseñado bajo el patrón BFF (Backend For Frontend) para proporcionar en una sola llamada
 * toda la información necesaria para renderizar el Dashboard principal de la aplicación móvil.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado consolidado de la jornada actual del empleado (BFF)")
public class RespuestaEstadoFichajeDTO {

    @Schema(description = "Indica si el empleado tiene un fichaje abierto actualmente", example = "true")
    private Boolean trabajandoActualmente;

    @Schema(description = "ID del fichaje abierto hoy, null si no está trabajando", example = "105")
    private Long idFichajeAbierto;

    @Schema(description = "Hora exacta en la que el empleado registró su entrada hoy", example = "2026-04-15T08:30:00Z")
    private OffsetDateTime horaEntrada;

    @Schema(description = "Indica si el empleado tiene un turno planificado para el día de hoy", example = "true")
    private Boolean tieneTurnoHoy;

    @Schema(description = "Modalidad del turno de hoy (PRESENCIAL o TELETRABAJO)", example = "TELETRABAJO")
    private ModalidadTurno modalidadHoy;
}
