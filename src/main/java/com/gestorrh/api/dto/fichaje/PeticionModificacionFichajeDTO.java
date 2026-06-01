package com.gestorrh.api.dto.fichaje;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO para solicitar la modificación de un fichaje existente.
 * Los clientes deben enviar las fechas con offset explícito (ej: 2026-05-18T17:35:00Z).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeticionModificacionFichajeDTO {

    private OffsetDateTime nuevaHoraEntrada;
    private OffsetDateTime nuevaHoraSalida;

    @NotBlank(message = "El motivo de la modificación es obligatorio por motivos de auditoría")
    private String motivoModificacion;
}
