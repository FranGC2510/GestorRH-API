package com.gestorrh.api.controller;

import com.gestorrh.api.annotation.ApiErroresAccion;
import com.gestorrh.api.annotation.ApiErroresEscritura;
import com.gestorrh.api.annotation.ApiErroresLectura;
import com.gestorrh.api.dto.empleado.*;
import com.gestorrh.api.entity.enums.RolEmpleado;
import com.gestorrh.api.service.EmpleadoService;
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
 * Controlador REST para la gestión integral de Empleados dentro de una organización.
 * <p>
 * Proporciona endpoints para el alta, listado, actualización, baja y readmisión de empleados,
 * así como la gestión del perfil propio por parte de los mismos.
 * </p>
 * <p>
 * Todas las operaciones en este controlador requieren un token de autenticación válido
 * y los permisos correspondientes según el rol del usuario (Empresa o Empleado).
 * </p>
 */
@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
@Tag(name = "3. Empleados",
        description = "Gestión integral de los trabajadores. Altas, bajas, listados y gestión del perfil propio. " +
                "Nota: El rol SUPERVISOR del empleado tiene acceso a los mismos endpoints de perfil propio (/me) que el EMPLEADO.")
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    /**
     * Endpoint para dar de alta un nuevo empleado en la empresa autenticada.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPRESA'.
     * Tras el alta, el sistema genera automáticamente una contraseña para el empleado.
     * </p>
     * <p>
     * URL de acceso: {@code POST http://localhost:8080/api/empleados}
     * </p>
     *
     * @param peticion DTO con los datos personales y contractuales del nuevo empleado.
     * @return ResponseEntity con el objeto {@link RespuestaCrearEmpleadoDTO} que incluye la contraseña generada y estado 201 (Created).
     */
    @PostMapping
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(
            summary = "Dar de alta un empleado",
            description = "Requiere Token de EMPRESA. Crea un nuevo empleado y el sistema le genera una contraseña automáticamente.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Empleado creado con éxito")
            }
    )
    @ApiErroresEscritura
    public ResponseEntity<RespuestaCrearEmpleadoDTO> crearEmpleado(
            @Valid @RequestBody PeticionCrearEmpleadoDTO peticion) {

        RespuestaCrearEmpleadoDTO respuesta = empleadoService.crearEmpleado(peticion);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Obtiene el listado completo de empleados pertenecientes a la empresa autenticada.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPRESA'.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/empleados}
     * </p>
     *
     * @return ResponseEntity con una lista de {@link RespuestaEmpleadoDTO} y estado 200 (OK).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR')")
    @Operation(
            summary = "Listar empleados",
            description = "Requiere Token de EMPRESA o SUPERVISOR. " +
                    "La EMPRESA obtiene el catálogo completo de todos los empleados de la organización. " +
                    "El SUPERVISOR obtiene únicamente los empleados de su mismo departamento, " +
                    "filtrado automáticamente desde el JWT sin necesidad de parámetros adicionales."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RespuestaEmpleadoDTO>> listarEmpleados() {

        List<RespuestaEmpleadoDTO> lista = empleadoService.obtenerEmpleadosDeEmpresa();
        return ResponseEntity.ok(lista);
    }

    /**
     * Actualiza la información de un empleado existente identificado por su ID.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPRESA'.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/empleados/{id}}
     * </p>
     *
     * @param id Identificador único del empleado a modificar.
     * @param peticion DTO con los nuevos datos a actualizar en la ficha del empleado.
     * @return ResponseEntity con el {@link RespuestaEmpleadoDTO} actualizado y estado 200 (OK).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(
            summary = "Actualizar datos de un empleado",
            description = "Requiere Token de EMPRESA. Modifica la información personal y contractual de un empleado específico."
    )
    @ApiErroresAccion
    public ResponseEntity<RespuestaEmpleadoDTO> actualizarEmpleado(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionActualizarEmpleadoDTO peticion) {

        RespuestaEmpleadoDTO respuesta = empleadoService.actualizarEmpleado(id, peticion);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Tramita la baja contractual de un empleado en una fecha determinada.
     * <p>
     * El empleado dejará de tener acceso al sistema a partir de dicha fecha.
     * </p>
     * <p>
     * URL de acceso: {@code POST http://localhost:8080/api/empleados/{id}/baja}
     * </p>
     *
     * @param id Identificador único del empleado que causa baja.
     * @param peticion DTO que contiene la fecha efectiva de la baja del contrato.
     * @return ResponseEntity con cuerpo vacío y estado 204 (No Content) tras procesar la baja.
     */
    @PostMapping("/{id}/baja")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(
            summary = "Tramitar baja de empleado",
            description = "Requiere Token de EMPRESA. Establece la fecha de baja de un empleado, revocando su acceso al sistema a partir de ese día.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Empleado dado de baja con éxito")
            }
    )
    @ApiErroresAccion
    public ResponseEntity<Void> darDeBaja(
            @PathVariable Long id,
            @RequestBody @Valid PeticionBajaEmpleadoDTO peticion) {

        empleadoService.darDeBajaEmpleado(id, peticion.getFechaBajaContrato());
        return ResponseEntity.noContent().build();
    }

    /**
     * Reinstaura a un empleado que se encontraba previamente de baja.
     * <p>
     * Elimina la restricción de acceso, limpia la fecha de baja y genera una nueva contraseña.
     * </p>
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPRESA'.
     * </p>
     * <p>
     * URL de acceso: {@code POST http://localhost:8080/api/empleados/{id}/readmitir}
     * </p>
     *
     * @param id Identificador único del empleado a readmitir.
     * @return ResponseEntity con el objeto {@link RespuestaCrearEmpleadoDTO} y la nueva contraseña generada.
     */
    @PostMapping("/{id}/readmitir")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(
            summary = "Readmitir empleado dado de baja",
            description = "Requiere Token de EMPRESA. Reinstaura a un empleado previamente dado de baja y le genera una nueva contraseña de acceso.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Empleado readmitido con éxito")
            }
    )
    @ApiErroresAccion
    public ResponseEntity<RespuestaCrearEmpleadoDTO> readmitirEmpleado(@PathVariable("id") Long id) {

        RespuestaCrearEmpleadoDTO respuesta = empleadoService.readmitirEmpleado(id);

        return ResponseEntity.ok(respuesta);
    }

    /**
     * Restablece la contraseña de un empleado cuando este la ha olvidado.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPRESA'. La política interna prohíbe la
     * auto-recuperación por correo, por lo que esta operación administrativa centraliza el reseteo
     * en el personal de RRHH. No se exige la contraseña anterior: se sustituye por la nueva,
     * que se almacena cifrada con BCrypt y nunca se expone en la respuesta.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/empleados/{id}/reset-password}
     * </p>
     *
     * @param id Identificador del empleado cuya contraseña se va a restablecer.
     * @param peticion DTO con la nueva contraseña (mínimo 8 caracteres).
     * @return ResponseEntity con el {@link RespuestaEmpleadoDTO} actualizado y estado 200 (OK).
     */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('EMPRESA')")
    @Operation(
            summary = "Restablecer contraseña de empleado (RRHH)",
            description = "Requiere Token de EMPRESA. Permite al personal de RRHH establecer una nueva " +
                    "contraseña para un empleado que la ha olvidado. La contraseña nunca se devuelve en la respuesta."
    )
    @ApiErroresEscritura
    public ResponseEntity<RespuestaEmpleadoDTO> resetPasswordEmpleado(
            @PathVariable("id") Long id,
            @Valid @RequestBody PeticionResetPasswordDTO peticion) {

        RespuestaEmpleadoDTO respuesta = empleadoService.resetPassword(id, peticion.getNuevaPassword());
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Permite al empleado autenticado consultar su propia información de perfil.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPLEADO'.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/empleados/me}
     * </p>
     *
     * @return ResponseEntity con el {@link RespuestaEmpleadoDTO} que representa el perfil del usuario logueado.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLEADO', 'SUPERVISOR')")
    @Operation(
            summary = "Obtener mi perfil (Empleado)",
            description = "Requiere Token de EMPLEADO o SUPERVISOR. Devuelve la información personal y contractual del empleado autenticado."
    )
    @ApiErroresLectura
    public ResponseEntity<RespuestaEmpleadoDTO> obtenerMiPerfil() {

        RespuestaEmpleadoDTO miPerfil = empleadoService.obtenerMiPerfil();
        return ResponseEntity.ok(miPerfil);
    }

    /**
     * Permite al empleado autenticado actualizar su contraseña de acceso personal.
     * <p>
     * Únicamente accesible para usuarios con el rol de 'EMPLEADO'.
     * </p>
     * <p>
     * URL de acceso: {@code PUT http://localhost:8080/api/empleados/me/contrasena}
     * </p>
     *
     * @param peticion DTO que incluye la contraseña actual y la nueva contraseña elegida.
     * @return ResponseEntity con cuerpo vacío y estado 204 (No Content) tras el cambio exitoso.
     */
    @PutMapping("/me/contrasena")
    @PreAuthorize("hasAnyRole('EMPLEADO', 'SUPERVISOR')")
    @Operation(
            summary = "Cambiar mi contraseña (Empleado)",
            description = "Requiere Token de EMPLEADO o SUPERVISOR. Permite al empleado autenticado cambiar su contraseña de acceso personal.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Contraseña cambiada con éxito")
            }
    )
    @ApiErroresEscritura
    public ResponseEntity<Void> cambiarMiContrasena(
            @Valid @RequestBody PeticionCambiarPasswordDTO peticion) {

        empleadoService.cambiarMiContrasena(peticion);

        return ResponseEntity.noContent().build();
    }

    /**
     * Proporciona el listado de todos los roles de empleado disponibles en el sistema.
     * <p>
     * Sirve como endpoint de diccionario para facilitar la selección de roles en interfaces de usuario.
     * </p>
     * <p>
     * URL de acceso: {@code GET http://localhost:8080/api/empleados/roles}
     * </p>
     *
     * @return ResponseEntity con una lista de los posibles valores del enum {@link RolEmpleado}.
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('EMPRESA', 'SUPERVISOR', 'EMPLEADO')")
    @Operation(
            summary = "Listar roles disponibles",
            description = "Requiere Token de EMPRESA o SUPERVISOR. Sirve como diccionario para rellenar desplegables en el Frontend."
    )
    @ApiErroresLectura
    public ResponseEntity<List<RolEmpleado>> obtenerRoles() {
        return ResponseEntity.ok(Arrays.asList(RolEmpleado.values()));
    }
}
