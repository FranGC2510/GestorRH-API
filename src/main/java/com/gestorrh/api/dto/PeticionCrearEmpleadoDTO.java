package com.gestorrh.api.dto;

import com.gestorrh.api.entity.RolEmpleado;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeticionCrearEmpleadoDTO {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El formato del correo no es válido")
    private String email;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    private String telefono;
    private String puesto;
    private String departamento;

    @NotNull(message = "El rol es obligatorio")
    private RolEmpleado rol;
}
