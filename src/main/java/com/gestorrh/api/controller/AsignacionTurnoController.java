package com.gestorrh.api.controller;

import com.gestorrh.api.annotation.ApiErroresAccion;
import com.gestorrh.api.annotation.ApiErroresEscritura;
import com.gestorrh.api.annotation.ApiErroresLectura;
import com.gestorrh.api.dto.asignacion.PeticionAsignacionTurnoDTO;
import com.gestorrh.api.dto.asignacion.RespuestaAsignacionTurnoDTO;
import com.gestorrh.api.entity.enums.ModalidadTurno;
import com.gestorrh.api.service.AsignacionTurnoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Controlador REST para la gestión de asignaciones de turnos de trabajo a empleados.
 * <p>
 * Permite la planificación de horarios, la consulta de asignaciones propias y generales,
 * así como la modificación y eliminación de estas por parte de usuarios autorizados.
 * </p>
 */
@RestController
@RequestMapping("/api/asignaciones")
@RequiredArgsConstructor
@Tag(
        name = "5. Asignaciones de Turnos",
        description = "Planificación horaria. Vincula a los empleados con los turnos. " +
                "Nota: La creación y modificación está reservada a EMPRESA y SUPERVISOR. Los EMPLEADOS rasos solo pueden consultar " +
                "sus propias asignaciones."
)
public class AsignacionTurnoController {

    private final AsignacionTurnoService asignacionService;

    /**
     * Registra una nueva asignación de turno para un empleado específico.
     * <p>
     * Este endpoint es utilizado por perfiles con capacidad de planificación (Empresa o Supervisores).
     * </p>
     * <p>
     * URL de acceso: {@code POST http://localhost:8080/api/asignaciones}
     * </p>
     *
     * @param peticion DTO con los detalles de la asignación (empleado, turno, fecha, modalidad).
     * @return ResponseEntity con el {@link RespuestaAsignacionTurnoDTO} creado y estado 201 (Created).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    @Operation(
            summary = "Crear asignación de turno",
            description = "Requiere Token de EMPRESA o SUPERVISOR. Asigna un turno específico a un empleado en una fecha y modalidad determinada.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Asignación de turno creada con éxito")
            }
    )
    @ApiErroresEscritura
    public ResponseEntity<RespuestaAsignacionTurnoDTO> crearAsignacion(@Valid @RequestBody PeticionAsignacionTurnoDTO peticion) {
        RespuestaAsignacionTurnoDTO respuesta = asignacionService.crearAsignacion(peticion);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Obtiene el listado de asignaciones de turno según los permisos del usuario.
     * <p>
     * Las organizaciones con rol 'EMPRESA' visualizan todas las asignaciones, mientras
     * que los supervisores acceden a las de su ámbito de responsabilidad.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/asignaciones}
     * </p>
     *
     * @return ResponseEntity con la lista de {@link RespuestaAsignacionTurnoDTO} permitidas.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    @Operation(
            summary = "Listar asignaciones (Global/Equipo)",
            description = "Requiere Token de EMPRESA o SUPERVISOR. La EMPRESA visualiza toda la planificación; el SUPERVISOR " +
                    "solo accede a las asignaciones de su departamento."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RespuestaAsignacionTurnoDTO>> listarAsignaciones() {
        List<RespuestaAsignacionTurnoDTO> lista = asignacionService.obtenerAsignacionesPermitidas();
        return ResponseEntity.ok(lista);
    }

    /**
     * Recupera el listado exclusivo de asignaciones de turno para el empleado autenticado.
     * <p>
     * Permite al trabajador conocer su planificación horaria personal.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/asignaciones/me}
     * </p>
     *
     * @return ResponseEntity con la lista de {@link RespuestaAsignacionTurnoDTO} del usuario logueado.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO', 'SUPERVISOR')")
    @Operation(
            summary = "Mis asignaciones (Perfil propio)",
            description = "Requiere Token de EMPLEADO (o SUPERVISOR). Recupera el listado exclusivo de la planificación " +
                    "horaria personal del usuario logueado."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RespuestaAsignacionTurnoDTO>> obtenerMisAsignaciones() {
        List<RespuestaAsignacionTurnoDTO> lista = asignacionService.obtenerMisAsignaciones();
        return ResponseEntity.ok(lista);
    }

    /**
     * Modifica una asignación de turno previamente registrada.
     * <p>
     * Requiere la especificación de un motivo para el cambio con fines de auditoría.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/asignaciones/{id}}
     * </p>
     *
     * @param id Identificador único de la asignación a modificar.
     * @param peticion DTO con los nuevos datos de la asignación y el motivo del cambio.
     * @return ResponseEntity con el {@link RespuestaAsignacionTurnoDTO} actualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    @Operation(
            summary = "Actualizar asignación",
            description = "Requiere Token de EMPRESA o SUPERVISOR. Modifica una asignación existente. " +
                    "Es obligatorio especificar un motivo de cambio para la auditoría."
    )
    @ApiErroresAccion
    public ResponseEntity<RespuestaAsignacionTurnoDTO> actualizarAsignacion(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionAsignacionTurnoDTO peticion) {
        RespuestaAsignacionTurnoDTO respuesta = asignacionService.actualizarAsignacion(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Elimina de forma permanente una asignación de turno del sistema.
     * <p>
     * Esta operación es destructiva y debe usarse para correcciones de planificación.
     * </p>
     * <p>
     * URL de acceso: {@code DELETE http://localhost:8080/api/asignaciones/{id}}
     * </p>
     *
     * @param id Identificador único de la asignación a eliminar.
     * @return ResponseEntity con cuerpo vacío y estado 204 (No Content).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    @Operation(
            summary = "Eliminar asignación",
            description = "Requiere Token de EMPRESA o SUPERVISOR. Borra de forma permanente una asignación de turno del " +
                    "sistema por correcciones de planificación.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Asignación eliminada con éxito")
            }
    )
    @ApiErroresAccion
    public ResponseEntity<Void> eliminarAsignacion(@PathVariable("id") Long id) {
        asignacionService.eliminarAsignacion(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Proporciona el listado de modalidades de turno disponibles (Presencial, Teletrabajo, etc.).
     * <p>
     * Sirve como endpoint de diccionario para la interfaz de usuario.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/asignaciones/modalidades}
     * </p>
     *
     * @return ResponseEntity con una lista de los valores del enum {@link ModalidadTurno}.
     */
    @GetMapping("/modalidades")
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR', 'EMPLEADO')")
    @Operation(
            summary = "Listar modalidades disponibles",
            description = "Requiere Token de EMPRESA, SUPERVISOR o EMPLEADO. Endpoint de diccionario que devuelve los " +
                    "valores posibles (ej: Presencial, Teletrabajo)."
    )
    @ApiErroresLectura
    public ResponseEntity<List<ModalidadTurno>> obtenerModalidades() {
        return ResponseEntity.ok(Arrays.asList(ModalidadTurno.values()));
    }
}
