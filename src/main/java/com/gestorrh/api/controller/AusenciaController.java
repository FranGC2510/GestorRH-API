package com.gestorrh.api.controller;

import com.gestorrh.api.dto.ausenciaDTO.PeticionAusenciaDTO;
import com.gestorrh.api.dto.ausenciaDTO.PeticionRevisionAusenciaDTO;
import com.gestorrh.api.dto.ausenciaDTO.RespuestaAusenciaDTO;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.TipoAusencia;
import com.gestorrh.api.service.AusenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Controlador REST para la gestión de Ausencias (Épica E6).
 */
@RestController
@RequestMapping("/api/ausencias")
@RequiredArgsConstructor
public class AusenciaController {

    private final AusenciaService ausenciaService;

    // ENDPOINTS DE DICCIONARIO

    @GetMapping("/tipos")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<List<TipoAusencia>> obtenerTiposAusencia() {
        return ResponseEntity.ok(Arrays.asList(TipoAusencia.values()));
    }

    @GetMapping("/estados")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<List<EstadoAusencia>> obtenerEstadosAusencia() {
        return ResponseEntity.ok(Arrays.asList(EstadoAusencia.values()));
    }

    // ENDPOINTS DEL EMPLEADO

    @PostMapping
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<RespuestaAusenciaDTO> crearAusencia(@Valid @RequestBody PeticionAusenciaDTO peticion) {
        RespuestaAusenciaDTO respuesta = ausenciaService.crearAusencia(peticion);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<List<RespuestaAusenciaDTO>> obtenerMisAusencias(
            @RequestParam(value = "estado", required = false) EstadoAusencia estado) {
        List<RespuestaAusenciaDTO> lista = ausenciaService.obtenerMisAusencias(estado);
        return ResponseEntity.ok(lista);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<RespuestaAusenciaDTO> actualizarMiAusencia(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionAusenciaDTO peticion) {
        RespuestaAusenciaDTO respuesta = ausenciaService.actualizarMiAusencia(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLEADO')")
    public ResponseEntity<Void> eliminarMiAusencia(@PathVariable("id") Long id) {
        ausenciaService.eliminarMiAusencia(id);
        return ResponseEntity.noContent().build();
    }

    // ENDPOINTS DE REVISIÓN

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<List<RespuestaAusenciaDTO>> listarAusenciasPermitidas(
            @RequestParam(value = "estado", required = false) EstadoAusencia estado) {
        List<RespuestaAusenciaDTO> lista = ausenciaService.obtenerAusenciasPermitidas(estado);
        return ResponseEntity.ok(lista);
    }

    @PutMapping("/{id}/revision")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    public ResponseEntity<RespuestaAusenciaDTO> revisarAusencia(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionRevisionAusenciaDTO peticion) {
        RespuestaAusenciaDTO respuesta = ausenciaService.revisarAusencia(id, peticion);
        return ResponseEntity.ok(respuesta);
    }
}
