package com.gestorrh.api.dto.autenticacionDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) para recibir las credenciales de inicio de sesión.
 * Se usa tanto para Empresas como para Empleados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeticionLoginDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
