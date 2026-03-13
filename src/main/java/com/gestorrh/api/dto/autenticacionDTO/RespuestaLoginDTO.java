package com.gestorrh.api.dto.autenticacionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) para enviar la respuesta de un login exitoso.
 * Devuelve el token y el rol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaLoginDTO {

    private String token;
    private String rol;

    private Long id;
    private String nombre;
}
