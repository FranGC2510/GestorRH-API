package com.gestorrh.api.controller;

import com.gestorrh.api.annotation.ApiErroresAccion;
import com.gestorrh.api.annotation.ApiErroresEscritura;
import com.gestorrh.api.annotation.ApiErroresLectura;
import com.gestorrh.api.dto.ausencia.PeticionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.PeticionRevisionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.RespuestaAusenciaDTO;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.TipoAusencia;
import com.gestorrh.api.service.AusenciaService;
import com.gestorrh.api.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Controlador REST para la gestión integral del ciclo de vida de las Ausencias.
 * <p>
 * Permite a los empleados solicitar ausencias y adjuntar justificantes, y a las
 * empresas o supervisores revisar, aprobar o denegar dichas solicitudes.
 * </p>
 */
@RestController
@RequestMapping("/api/ausencias")
@RequiredArgsConstructor
@Tag(
        name = "7. Ausencias",
        description = "Gestión de vacaciones, bajas y permisos. Soporta subida de archivos (justificantes médicos, etc.) " +
                "y flujo de aprobación por parte de supervisores o empresa."
)
public class AusenciaController {

    private final AusenciaService ausenciaService;
    private final FileStorageService fileStorageService;

    /**
     * Obtiene el listado de todos los tipos de ausencia configurados en el sistema.
     * <p>
     * Sirve como endpoint de consulta para cargar opciones en formularios de solicitud.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/ausencias/tipos}
     * </p>
     *
     * @return ResponseEntity con una lista de los valores del enum {@link TipoAusencia}.
     */
    @GetMapping("/tipos")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    @Operation(summary = "Listar tipos de ausencia", description = "Requiere Token de EMPRESA, SUPERVISOR o EMPLEADO. " +
            "Diccionario para formularios (ej: VACACIONES, BAJA_MEDICA).")
    @ApiErroresLectura
    public ResponseEntity<List<TipoAusencia>> obtenerTiposAusencia() {
        return ResponseEntity.ok(Arrays.asList(TipoAusencia.values()));
    }

    /**
     * Obtiene el listado de los posibles estados en los que puede encontrarse una ausencia.
     * <p>
     * Facilita el filtrado de solicitudes en las interfaces de usuario.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/ausencias/estados}
     * </p>
     *
     * @return ResponseEntity con una lista de los valores del enum {@link EstadoAusencia}.
     */
    @GetMapping("/estados")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    @Operation(summary = "Listar estados de ausencia", description = "Requiere Token de EMPRESA, SUPERVISOR o EMPLEADO. " +
            "Diccionario de estados (ej: PENDIENTE, APROBADA).")
    @ApiErroresLectura
    public ResponseEntity<List<EstadoAusencia>> obtenerEstadosAusencia() {
        return ResponseEntity.ok(Arrays.asList(EstadoAusencia.values()));
    }

    /**
     * Permite a un empleado crear una nueva solicitud de ausencia.
     * <p>
     * El endpoint soporta el envío de datos en formato JSON y un archivo (justificante) opcional.
     * </p>
     * <p>
     * URL de acceso: {@code POST http://localhost:8080/api/ausencias}
     * </p>
     *
     * @param peticion DTO con los detalles de la ausencia (fechas, tipo, motivo).
     * @param archivo Archivo opcional que sirve como justificante documental de la ausencia.
     * @return ResponseEntity con el {@link RespuestaAusenciaDTO} creado y estado 201 (Created).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLEADO')")
    @Operation(
            summary = "Solicitar nueva ausencia (con archivo opcional)",
            description = "Requiere Token de EMPLEADO (o SUPERVISOR). Permite enviar los datos de la ausencia (JSON) " +
                    "junto con un archivo PDF/Imagen opcional como justificante.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Solicitud de ausencia creada con éxito")
            }
    )
    @ApiErroresEscritura
    public ResponseEntity<RespuestaAusenciaDTO> crearAusencia(
            @Parameter(description = "Datos de la ausencia en formato JSON", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @RequestPart("datos") @Valid PeticionAusenciaDTO peticion,
            @Parameter(description = "Archivo físico del justificante (opcional)")
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        RespuestaAusenciaDTO respuesta = ausenciaService.crearAusencia(peticion, archivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Recupera el historial de ausencias solicitadas por el empleado que realiza la petición.
     * <p>
     * Permite el filtrado opcional por el estado de la solicitud.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/ausencias/me}
     * </p>
     *
     * @param estado Estado opcional por el cual filtrar las ausencias del empleado.
     * @return ResponseEntity con la lista de {@link RespuestaAusenciaDTO} del empleado autenticado.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLEADO')")
    @Operation(
            summary = "Mis solicitudes de ausencia",
            description = "Requiere Token de EMPLEADO (o SUPERVISOR). Recupera el historial de ausencias solicitadas " +
                    "por el usuario autenticado, filtrable por estado."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RespuestaAusenciaDTO>> obtenerMisAusencias(
            @RequestParam(value = "estado", required = false) EstadoAusencia estado) {
        List<RespuestaAusenciaDTO> lista = ausenciaService.obtenerMisAusencias(estado);
        return ResponseEntity.ok(lista);
    }

    /**
     * Permite a un empleado actualizar los datos o el justificante de una ausencia ya solicitada.
     * <p>
     * Únicamente se permiten cambios si la ausencia se encuentra en un estado que permita edición.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/ausencias/{id}}
     * </p>
     *
     * @param id Identificador único de la ausencia a modificar.
     * @param peticion DTO con los nuevos datos de la ausencia.
     * @param archivo Nuevo archivo justificante opcional que reemplazará al anterior si se proporciona.
     * @return ResponseEntity con el {@link RespuestaAusenciaDTO} actualizado.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLEADO')")
    @Operation(
            summary = "Actualizar mi solicitud de ausencia",
            description = "Requiere Token de EMPLEADO (o SUPERVISOR). Permite modificar datos o reemplazar el " +
                    "justificante de una ausencia que aún esté en estado SOLICITADA. " +
                    "Para gestionar el justificante, el objeto JSON de la parte 'datos' admite el campo " +
                    "'eliminarJustificante' (Boolean): " +
                    "si se envía a true, el justificante actual se elimina y el campo queda a null, " +
                    "con independencia de si se adjunta también un archivo nuevo. " +
                    "Si no se envía el campo o se envía a false, el comportamiento es el habitual: " +
                    "sin archivo el justificante se conserva, con archivo se reemplaza."
    )
    @ApiErroresAccion
    public ResponseEntity<RespuestaAusenciaDTO> actualizarMiAusencia(
            @PathVariable("id") Long id,
            @Parameter(description = "Datos de la ausencia en formato JSON", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @RequestPart("datos") @Valid PeticionAusenciaDTO peticion,
            @Parameter(description = "Nuevo archivo físico del justificante (opcional)")
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        RespuestaAusenciaDTO respuesta = ausenciaService.actualizarMiAusencia(id, peticion, archivo);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Elimina de forma permanente una solicitud de ausencia realizada por el empleado.
     * <p>
     * Solo es posible eliminar ausencias que aún no hayan sido procesadas definitivamente.
     * </p>
     * <p>
     * URL de acceso: {@code DELETE http://localhost:8080/api/ausencias/{id}}
     * </p>
     *
     * @param id Identificador único de la ausencia a eliminar.
     * @return ResponseEntity con cuerpo vacío y estado 204 (No Content).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLEADO')")
    @Operation(
            summary = "Cancelar mi solicitud de ausencia",
            description = "Requiere Token de EMPLEADO (o SUPERVISOR). Elimina una solicitud propia siempre que aún no " +
                    "haya sido procesada (siga PENDIENTE).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Solicitud de ausencia eliminada con éxito")
            }
    )
    @ApiErroresAccion
    public ResponseEntity<Void> eliminarMiAusencia(@PathVariable("id") Long id) {
        ausenciaService.eliminarMiAusencia(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista todas las solicitudes de ausencia que el usuario autenticado tiene permiso para visualizar.
     * <p>
     * Las empresas ven todas las de sus empleados, mientras que los supervisores ven las de su departamento.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/ausencias}
     * </p>
     *
     * @param estado Estado opcional para filtrar el listado de ausencias a revisar.
     * @return ResponseEntity con la lista de {@link RespuestaAusenciaDTO} permitidas.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    @Operation(
            summary = "Bandeja de revisión de ausencias",
            description = "Requiere Token de EMPRESA o SUPERVISOR. La EMPRESA ve todas las solicitudes; el SUPERVISOR " +
                    "ve las de su departamento. Permite filtrar por estado (ej: PENDIENTE)."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RespuestaAusenciaDTO>> listarAusenciasPermitidas(
            @RequestParam(value = "estado", required = false) EstadoAusencia estado) {
        List<RespuestaAusenciaDTO> lista = ausenciaService.obtenerAusenciasPermitidas(estado);
        return ResponseEntity.ok(lista);
    }

    /**
     * Procesa la revisión de una solicitud de ausencia, permitiendo su aprobación o denegación.
     * <p>
     * Se debe incluir un comentario de revisión que justifique la decisión tomada.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/ausencias/{id}/revision}
     * </p>
     *
     * @param id Identificador único de la ausencia a revisar.
     * @param peticion DTO con el nuevo estado (aprobado/denegado) y el comentario de revisión.
     * @return ResponseEntity con el {@link RespuestaAusenciaDTO} actualizado tras la revisión.
     */
    @PutMapping("/{id}/revision")
    @PreAuthorize("hasAnyRole('EMPRESA', 'EMPLEADO')")
    @Operation(
            summary = "Aprobar o Denegar ausencia",
            description = "Requiere Token de EMPRESA o SUPERVISOR. Procesa una solicitud cambiando su estado a " +
                    "APROBADA o DENEGADA y añade un comentario de revisión."
    )
    @ApiErroresAccion
    public ResponseEntity<RespuestaAusenciaDTO> revisarAusencia(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionRevisionAusenciaDTO peticion) {
        RespuestaAusenciaDTO respuesta = ausenciaService.revisarAusencia(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Permite la descarga del archivo justificante asociado a una solicitud de ausencia.
     * <p>
     * El archivo se retorna como un recurso descargable con las cabeceras HTTP apropiadas.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/ausencias/justificantes/{nombreArchivo}}
     * </p>
     *
     * @param nombreArchivo Nombre del archivo almacenado en el sistema que se desea descargar.
     * @return ResponseEntity que contiene el archivo como {@link Resource} y las cabeceras de descarga.
     */
    @GetMapping("/justificantes/{nombreArchivo}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR', 'EMPLEADO')")
    @Operation(
            summary = "Descargar justificante",
            description = "Requiere Token de EMPRESA, SUPERVISOR o EMPLEADO. Descarga el archivo físico del justificante " +
                    "asociado a una ausencia."
    )
    @ApiErroresLectura
    public ResponseEntity<Resource> descargarJustificante(@PathVariable String nombreArchivo) {
        Resource resource = fileStorageService.cargarArchivoComoRecurso(nombreArchivo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
