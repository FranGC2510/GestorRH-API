package com.gestorrh.api.dto.error;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO que estandariza las respuestas de error de la API.
 */
@Getter
@Builder
public class RespuestaErrorDTO {
    private OffsetDateTime timestamp;
    private int status;
    private String errorCode;
    private String message;
    private String path;
    private List<String> details;
}
