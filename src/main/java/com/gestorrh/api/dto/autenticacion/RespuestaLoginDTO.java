package com.gestorrh.api.dto.autenticacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) para enviar la respuesta de un login exitoso.
 * Devuelve el token, el rol, los datos básicos del usuario y el nombre de la empresa asociada.
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
    private String nombreEmpresa;
}
