package com.gestorrh.api.dto.empleadoDTO;

import com.gestorrh.api.entity.enums.RolEmpleado;
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
public class PeticionActualizarEmpleadoDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    private String telefono;
    private String puesto;
    private String departamento;

    @NotNull(message = "El rol es obligatorio")
    private RolEmpleado rol;

    @NotNull(message = "El estado (activo/inactivo) es obligatorio")
    private Boolean activo;
}