package com.gestorrh.api.dto.empleadoDTO;

import com.gestorrh.api.entity.enums.RolEmpleado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaCrearEmpleadoDTO {

    private Long idEmpleado;
    private String nombre;
    private String apellidos;
    private String email;
    private RolEmpleado rol;
    private String passwordGenerada;
}