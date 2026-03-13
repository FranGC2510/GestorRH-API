package com.gestorrh.api.dto.empleadoDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la petición de cambio de contraseña por parte del empleado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeticionCambiarPasswordDTO {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres para ser segura")
    private String nuevaPassword;
}
