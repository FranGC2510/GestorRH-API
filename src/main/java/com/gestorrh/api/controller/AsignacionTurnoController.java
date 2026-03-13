package com.gestorrh.api.controller;

import com.gestorrh.api.dto.PeticionAsignacionTurnoDTO;
import com.gestorrh.api.dto.RespuestaAsignacionTurnoDTO;
import com.gestorrh.api.service.AsignacionTurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de Asignaciones de Turnos (Épica E5).
 */
@RestController
@RequestMapping("/api/asignaciones")
@RequiredArgsConstructor
public class AsignacionTurnoController {

    private final AsignacionTurnoService asignacionService;

    /**
     * Crea una nueva asignación de turno.
     * Solo para EMPRESA o SUPERVISOR (usamos validación programática en el Service para el rol exacto de empleado).
     * URL: POST http://localhost:8080/api/asignaciones
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<RespuestaAsignacionTurnoDTO> crearAsignacion(@Valid @RequestBody PeticionAsignacionTurnoDTO peticion) {
        RespuestaAsignacionTurnoDTO respuesta = asignacionService.crearAsignacion(peticion);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Obtiene el listado de asignaciones.
     * La EMPRESA ve todas. El SUPERVISOR ve las de su departamento.
     * URL: GET http://localhost:8080/api/asignaciones
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<List<RespuestaAsignacionTurnoDTO>> listarAsignaciones() {
        List<RespuestaAsignacionTurnoDTO> lista = asignacionService.obtenerAsignacionesPermitidas();
        return ResponseEntity.ok(lista);
    }

    /**
     * Obtiene el listado de asignaciones exclusivas del empleado logueado.
     * URL: GET http://localhost:8080/api/asignaciones/me
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<List<RespuestaAsignacionTurnoDTO>> obtenerMisAsignaciones() {
        List<RespuestaAsignacionTurnoDTO> lista = asignacionService.obtenerMisAsignaciones();
        return ResponseEntity.ok(lista);
    }

    /**
     * Modifica una asignación existente, requiriendo motivo de cambio (Auditoría).
     * URL: PUT http://localhost:8080/api/asignaciones/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<RespuestaAsignacionTurnoDTO> actualizarAsignacion(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionAsignacionTurnoDTO peticion) {
        RespuestaAsignacionTurnoDTO respuesta = asignacionService.actualizarAsignacion(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Elimina físicamente una asignación.
     * URL: DELETE http://localhost:8080/api/asignaciones/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<Void> eliminarAsignacion(@PathVariable("id") Long id) {
        asignacionService.eliminarAsignacion(id);
        return ResponseEntity.noContent().build();
    }
}
