package com.gestorrh.api.controller;

import com.gestorrh.api.dto.turnoDTO.PeticionTurnoDTO;
import com.gestorrh.api.dto.turnoDTO.RespuestaTurnoDTO;
import com.gestorrh.api.service.TurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión del catálogo de Turnos.
 * Cumple con la Épica E4 del Anteproyecto.
 */
@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;

    /**
     * Endpoint para crear un nuevo tipo de turno.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: POST http://localhost:8080/api/turnos
     *
     * @param peticion DTO con los datos del turno (descripción, horaInicio, horaFin).
     * @return ResponseEntity con el DTO del turno creado y estado HTTP 201 (Created).
     */
    @PostMapping
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<RespuestaTurnoDTO> crearTurno(@Valid @RequestBody PeticionTurnoDTO peticion) {
        RespuestaTurnoDTO respuesta = turnoService.crearTurno(peticion);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Endpoint para listar todos los turnos configurados por la empresa logueada.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: GET http://localhost:8080/api/turnos
     *
     * @return ResponseEntity con la lista de turnos y estado HTTP 200 (OK).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    public ResponseEntity<List<RespuestaTurnoDTO>> listarTurnos() {
        List<RespuestaTurnoDTO> lista = turnoService.obtenerTurnosDeEmpresa();
        return ResponseEntity.ok(lista);
    }

    /**
     * Endpoint para modificar un turno existente.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: PUT http://localhost:8080/api/turnos/{id}
     *
     * @param id Identificador único del turno a modificar.
     * @param peticion DTO con los nuevos datos del turno.
     * @return ResponseEntity con el DTO del turno actualizado y estado HTTP 200 (OK).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<RespuestaTurnoDTO> actualizarTurno(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionTurnoDTO peticion) {
        RespuestaTurnoDTO respuesta = turnoService.actualizarTurno(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Endpoint para eliminar físicamente un turno.
     * SOLO accesible para usuarios con el rol EMPRESA.
     * URL: DELETE http://localhost:8080/api/turnos/{id}
     *
     * @param id Identificador único del turno a eliminar.
     * @return ResponseEntity vacío con estado HTTP 204 (No Content).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    public ResponseEntity<Void> eliminarTurno(@PathVariable("id") Long id) {
        turnoService.eliminarTurno(id);
        return ResponseEntity.noContent().build();
    }
}
