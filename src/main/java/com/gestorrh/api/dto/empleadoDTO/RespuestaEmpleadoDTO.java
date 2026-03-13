package com.gestorrh.api.dto.empleadoDTO;

import com.gestorrh.api.entity.enums.RolEmpleado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO puro para devolver los datos de un empleado en listados o consultas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaEmpleadoDTO {

    private Long idEmpleado;
    private String email;
    private String nombre;
    private String apellidos;
    private String telefono;
    private String puesto;
    private String departamento;
    private RolEmpleado rol;
    private Boolean activo;
    private LocalDate fechaBajaContrato;
}
