package com.gestorrh.api.controller;

import com.gestorrh.api.dto.*;
import com.gestorrh.api.service.EmpleadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de Empleados.
 * Todos los endpoints de esta clase requerirán un token válido.
 */
@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    /**
     * Endpoint para dar de alta a un nuevo empleado.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: POST http://localhost:8080/api/empleados
     */
    @PostMapping
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<RespuestaCrearEmpleadoDTO> crearEmpleado(
            @Valid @RequestBody PeticionCrearEmpleadoDTO peticion) {

        RespuestaCrearEmpleadoDTO respuesta = empleadoService.crearEmpleado(peticion);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Endpoint para listar todos los empleados de la empresa logueada.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: GET http://localhost:8080/api/empleados
     */
    @GetMapping
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<List<RespuestaEmpleadoDTO>> listarEmpleados() {

        List<RespuestaEmpleadoDTO> lista = empleadoService.obtenerEmpleadosDeEmpresa();
        return ResponseEntity.ok(lista);
    }

    /**
     * Endpoint para actualizar los datos de un empleado existente.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: PUT http://localhost:8080/api/empleados/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<RespuestaEmpleadoDTO> actualizarEmpleado(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionActualizarEmpleadoDTO peticion) {

        RespuestaEmpleadoDTO respuesta = empleadoService.actualizarEmpleado(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/{id}/baja")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<Void> darDeBaja(
            @PathVariable Long id,
            @RequestBody @Valid PeticionBajaEmpleadoDTO peticion) {

        empleadoService.darDeBajaEmpleado(id, peticion.getFecha_baja_contrato());
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint para readmitir a un empleado que estaba de baja.
     * Elimina su fecha de baja y le genera una nueva contraseña.
     * URL: POST http://localhost:8080/api/empleados/{id}/readmitir
     */
    @PostMapping("/{id}/readmitir")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<RespuestaCrearEmpleadoDTO> readmitirEmpleado(@PathVariable("id") Long id) {

        RespuestaCrearEmpleadoDTO respuesta = empleadoService.readmitirEmpleado(id);

        return ResponseEntity.ok(respuesta);
    }
}
