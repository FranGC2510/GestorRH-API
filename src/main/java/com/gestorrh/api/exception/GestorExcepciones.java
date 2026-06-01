package com.gestorrh.api.exception;

import com.gestorrh.api.dto.error.RespuestaErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Clase controladora de asesoramiento global para la gestión de excepciones en la API.
 * <p>
 * Centraliza la captura de errores lanzados desde cualquier capa del sistema (controladores, servicios, etc.)
 * y los transforma en una respuesta JSON estandarizada mediante {@link RespuestaErrorDTO}.
 * Utiliza la anotación {@code @RestControllerAdvice} para interceptar las excepciones de forma transversal.
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class GestorExcepciones {

    /**
     * Maneja las excepciones producidas cuando falla la validación de los argumentos de entrada (anotaciones {@code @Valid}).
     * <p>
     * Extrae todos los mensajes de error asociados a los campos del DTO que no han superado las restricciones
     * y genera una respuesta {@link HttpStatus#BAD_REQUEST} detallada.
     * </p>
     *
     * @param ex La excepción de validación capturada.
     * @param request La solicitud HTTP en la que se produjo el error, para extraer la URI.
     * @return Una respuesta estructurada con los detalles de los campos que fallaron la validación.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarValidaciones(MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> errores = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        log.error("Error de VALIDACIÓN en la ruta [{}]: {}", request.getRequestURI(), errores);

        RespuestaErrorDTO errorResponse = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_ERROR")
                .message("Error en la validación de los datos enviados")
                .path(request.getRequestURI())
                .details(errores)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja las excepciones genéricas de ejecución que representan violaciones de reglas de negocio.
     * <p>
     * Captura cualquier {@link RuntimeException} no manejada específicamente, registrando el error en el log
     * y retornando una respuesta {@link HttpStatus#BAD_REQUEST} con el mensaje descriptivo de la excepción.
     * </p>
     *
     * @param ex La excepción de negocio o error de ejecución capturado.
     * @param request La solicitud HTTP donde ocurrió la incidencia.
     * @return Una respuesta de error estandarizada con el mensaje de la excepción.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarExcepcionesDeNegocio(RuntimeException ex, HttpServletRequest request) {

        log.warn("Violación de regla de NEGOCIO en la ruta [{}]: {}", request.getRequestURI(), ex.getMessage());

        RespuestaErrorDTO errorResponse = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("BUSINESS_RULE_VIOLATION")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .details(List.of("Revisa las reglas de negocio o los parámetros enviados"))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Maneja las excepciones globales de tipo {@link Exception} que no han sido capturadas por otros manejadores.
     * <p>
     * Este es el último nivel de captura de errores, encargado de interceptar fallos críticos e inesperados
     * en el sistema. Registra la traza completa del error en los logs para su posterior análisis técnico
     * y retorna una respuesta {@link HttpStatus#INTERNAL_SERVER_ERROR} estandarizada.
     * </p>
     *
     * @param ex La excepción raíz capturada.
     * @param request La solicitud HTTP donde se produjo el fallo crítico.
     * @return Una respuesta de error genérica con código 500 y recomendaciones de contacto con soporte.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaErrorDTO> manejarExcepcionesGlobales(Exception ex, HttpServletRequest request) {

        log.error("Error CRÍTICO no controlado en la ruta [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);

        RespuestaErrorDTO errorResponse = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Ha ocurrido un error inesperado en el servidor")
                .path(request.getRequestURI())
                .details(List.of("Por favor, contacte con el soporte técnico"))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Maneja las excepciones producidas cuando no se encuentra un recurso solicitado en la base de datos.
     * <p>
     * Captura las excepciones de tipo {@link jakarta.persistence.EntityNotFoundException}, registrando una advertencia
     * en los logs y retornando una respuesta {@link HttpStatus#NOT_FOUND} (404). Esto ocurre típicamente al
     * intentar acceder, actualizar o eliminar un registro mediante un identificador inexistente.
     * </p>
     *
     * @param ex La excepción de entidad no encontrada.
     * @param request La solicitud HTTP donde se intentó acceder al recurso inexistente.
     * @return Una respuesta estructurada indicando que el recurso no está disponible.
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarRecursoNoEncontrado(jakarta.persistence.EntityNotFoundException ex, HttpServletRequest request) {

        log.warn("Recurso NO ENCONTRADO en la ruta [{}]: {}", request.getRequestURI(), ex.getMessage());

        RespuestaErrorDTO errorResponse = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.NOT_FOUND.value())
                .errorCode("NOT_FOUND")
                .message(ex.getMessage() != null ? ex.getMessage() : "El recurso solicitado no ha sido encontrado")
                .path(request.getRequestURI())
                .details(List.of("Verifique que el identificador o los parámetros de búsqueda sean correctos"))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja las excepciones de concurrencia producidas cuando dos usuarios intentan modificar el mismo registro simultáneamente.
     * <p>
     * Captura las excepciones de tipo {@link ObjectOptimisticLockingFailureException} lanzadas por el mecanismo de
     * control de concurrencia optimista de JPA (anotación {@code @Version}). Retorna una respuesta
     * {@link HttpStatus#CONFLICT} (409) informando al cliente que debe recargar los datos antes de reintentar la operación,
     * evitando así la corrupción de datos por "actualizaciones perdidas".
     * </p>
     *
     * @param ex La excepción de fallo de bloqueo optimista lanzada por Hibernate/JPA.
     * @param request La solicitud HTTP en la que se produjo la colisión de datos.
     * @return Una respuesta estructurada indicando el conflicto de concurrencia (409 Conflict).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarConcurrencia(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {

        log.warn("Conflicto de CONCURRENCIA en la ruta [{}]: {}", request.getRequestURI(), ex.getMessage());

        RespuestaErrorDTO errorResponse = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.CONFLICT.value())
                .errorCode("CONFLICT")
                .message("Los datos han sido modificados por otro usuario mientras los visualizabas. Por favor, recarga la página e inténtalo de nuevo.")
                .path(request.getRequestURI())
                .details(List.of("La versión del registro enviada no coincide con la versión actual en la base de datos"))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
}
