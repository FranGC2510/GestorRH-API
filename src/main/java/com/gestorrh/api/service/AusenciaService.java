package com.gestorrh.api.service;

import com.gestorrh.api.dto.ausencia.PeticionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.PeticionRevisionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.RespuestaAusenciaDTO;
import com.gestorrh.api.entity.*;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio encargado de gestionar el flujo completo de ausencias y vacaciones de los empleados.
 * <p>
 * Cubre el ciclo de vida de la ausencia: desde la solicitud inicial por parte del empleado 
 * (incluyendo la gestión de justificantes en el sistema de ficheros), hasta la revisión,
 * aprobación o rechazo por parte de los responsables correspondientes.
 * </p>
 * <p>
 * El sistema integra validaciones automáticas de solapamientos temporales y gestiona 
 * la limpieza automática del calendario laboral al aprobar periodos de descanso.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AusenciaService {

    private final AusenciaRepository ausenciaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpresaRepository empresaRepository;
    private final AsignacionTurnoRepository asignacionRepository;
    private final FileStorageService fileStorageService;
    private final FichajeRepository fichajeRepository;

    /**
     * Permite a un empleado registrar una nueva solicitud de ausencia.
     * Valida que no existan solapamientos con otras ausencias activas y almacena el archivo justificante si se adjunta.
     *
     * @param peticion DTO con las fechas y el tipo de ausencia.
     * @param archivo Archivo opcional (justificante médico, etc.) en formato Multipart.
     * @return {@link RespuestaAusenciaDTO} con la solicitud recién creada en estado PENDIENTE.
     */
    @Transactional
    public RespuestaAusenciaDTO crearAusencia(PeticionAusenciaDTO peticion, org.springframework.web.multipart.MultipartFile archivo) {
        Empleado empleadoLogueado = obtenerEmpleadoAutenticado();
        validarFechas(peticion.getFechaInicio(), peticion.getFechaFin());
        validarSolapamientoAusencias(empleadoLogueado.getIdEmpleado(), peticion.getFechaInicio(), peticion.getFechaFin(), null);
        validarQueNoHayFichajesEnRango(empleadoLogueado.getIdEmpleado(), peticion.getFechaInicio(), peticion.getFechaFin());

        String nombreArchivoGenerado = null;
        if (archivo != null && !archivo.isEmpty()) {
            nombreArchivoGenerado = fileStorageService.guardarArchivo(archivo);
        }

        Ausencia nuevaAusencia = Ausencia.builder()
                .empleado(empleadoLogueado)
                .tipo(peticion.getTipo())
                .descripcion(peticion.getDescripcion())
                .fechaInicio(peticion.getFechaInicio())
                .fechaFin(peticion.getFechaFin())
                .justificante(nombreArchivoGenerado)
                .estado(EstadoAusencia.SOLICITADA)
                .build();

        nuevaAusencia = ausenciaRepository.save(nuevaAusencia);

        log.info("NUEVA AUSENCIA: El empleado '{}' ha solicitado una ausencia de tipo [{}] del {} al {}.",
                empleadoLogueado.getEmail(), peticion.getTipo(), peticion.getFechaInicio(), peticion.getFechaFin());

        return mapearARespuesta(nuevaAusencia);
    }

    /**
     * Recupera el historial de ausencias del empleado autenticado.
     * Permite el filtrado por estado de la solicitud.
     *
     * @param estadoFiltro Estado opcional (SOLICITADA, APROBADA, RECHAZADA) para filtrar la búsqueda.
     * @return List de {@link RespuestaAusenciaDTO} con las solicitudes propias del empleado.
     */
    @Transactional(readOnly = true)
    public List<RespuestaAusenciaDTO> obtenerMisAusencias(EstadoAusencia estadoFiltro) {
        Empleado empleadoLogueado = obtenerEmpleadoAutenticado();
        List<Ausencia> misAusencias;

        if (estadoFiltro != null) {
            misAusencias = ausenciaRepository.findByEmpleadoIdEmpleadoAndEstado(empleadoLogueado.getIdEmpleado(), estadoFiltro);
        } else {
            misAusencias = ausenciaRepository.findByEmpleadoIdEmpleado(empleadoLogueado.getIdEmpleado());
        }

        return misAusencias.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Permite al empleado modificar una solicitud de ausencia que todavía no ha sido procesada.
     * Si la solicitud ya ha sido aprobada o rechazada, la modificación queda prohibida.
     * <p>
     * La gestión del justificante sigue esta lógica de precedencia:
     * </p>
     * <ol>
     *   <li>Si {@code eliminarJustificante} es {@code true} en la petición, el justificante
     *       actual se elimina del almacenamiento y el campo queda a {@code null},
     *       independientemente de si se adjunta un archivo nuevo.</li>
     *   <li>Si se adjunta un {@code archivoNuevo}, el justificante anterior se reemplaza por el nuevo.</li>
     *   <li>Si no se indica ninguna de las anteriores, el justificante actual se conserva sin cambios.</li>
     * </ol>
     *
     * @param idAusencia Identificador de la ausencia a editar.
     * @param peticion Nuevos datos para la solicitud. El campo {@code eliminarJustificante}
     *                 permite eliminar el justificante actual sin adjuntar uno nuevo.
     * @param archivoNuevo Nuevo archivo justificante opcional que reemplazará al anterior si se proporciona
     *                     y {@code eliminarJustificante} no es {@code true}.
     * @return {@link RespuestaAusenciaDTO} con la ausencia actualizada.
     */
    @Transactional
    public RespuestaAusenciaDTO actualizarMiAusencia(Long idAusencia, PeticionAusenciaDTO peticion, org.springframework.web.multipart.MultipartFile archivoNuevo) {
        Empleado empleadoLogueado = obtenerEmpleadoAutenticado();
        Ausencia ausencia = obtenerAusenciaPropia(idAusencia, empleadoLogueado);

        if (ausencia.getEstado() != EstadoAusencia.SOLICITADA) {
            log.warn("Operación DENEGADA: El empleado '{}' intentó modificar la ausencia ID {} que ya no está en estado SOLICITADA.", empleadoLogueado.getEmail(), idAusencia);
            throw new RuntimeException("Solo puedes modificar una ausencia que esté en estado SOLICITADA.");
        }

        validarFechas(peticion.getFechaInicio(), peticion.getFechaFin());
        validarSolapamientoAusencias(empleadoLogueado.getIdEmpleado(), peticion.getFechaInicio(), peticion.getFechaFin(), idAusencia);
        validarQueNoHayFichajesEnRango(empleadoLogueado.getIdEmpleado(), peticion.getFechaInicio(), peticion.getFechaFin());

        boolean deseaEliminar = Boolean.TRUE.equals(peticion.getEliminarJustificante());

        if (deseaEliminar) {
            if (ausencia.getJustificante() != null) {
                fileStorageService.eliminarArchivo(ausencia.getJustificante());
            }
            ausencia.setJustificante(null);
        } else if (archivoNuevo != null && !archivoNuevo.isEmpty()) {
            if (ausencia.getJustificante() != null) {
                fileStorageService.eliminarArchivo(ausencia.getJustificante());
            }
            String nombreArchivoGenerado = fileStorageService.guardarArchivo(archivoNuevo);
            ausencia.setJustificante(nombreArchivoGenerado);
        }

        ausencia.setTipo(peticion.getTipo());
        ausencia.setDescripcion(peticion.getDescripcion());
        ausencia.setFechaInicio(peticion.getFechaInicio());
        ausencia.setFechaFin(peticion.getFechaFin());

        ausencia = ausenciaRepository.save(ausencia);

        log.info("AUSENCIA MODIFICADA: El empleado '{}' ha actualizado su solicitud de ausencia ID {}.", empleadoLogueado.getEmail(), idAusencia);

        return mapearARespuesta(ausencia);
    }

    /**
     * Elimina definitivamente una solicitud de ausencia en estado pendiente.
     *
     * @param idAusencia Identificador de la solicitud a cancelar.
     */
    @Transactional
    public void eliminarMiAusencia(Long idAusencia) {
        Empleado empleadoLogueado = obtenerEmpleadoAutenticado();
        Ausencia ausencia = obtenerAusenciaPropia(idAusencia, empleadoLogueado);

        if (ausencia.getEstado() != EstadoAusencia.SOLICITADA) {
            log.warn("Operación DENEGADA: El empleado '{}' intentó cancelar la ausencia ID {} que ya no está en estado SOLICITADA.", empleadoLogueado.getEmail(), idAusencia);
            throw new RuntimeException("Solo puedes cancelar una ausencia que esté en estado SOLICITADA.");
        }

        if (ausencia.getJustificante() != null) {
            fileStorageService.eliminarArchivo(ausencia.getJustificante());
        }

        ausenciaRepository.delete(ausencia);
        log.info("AUSENCIA CANCELADA: El empleado '{}' ha eliminado su solicitud de ausencia ID {}.", empleadoLogueado.getEmail(), idAusencia);
    }

    /**
     * Recupera la lista de ausencias que el usuario autenticado tiene permiso para revisar.
     * - EMPRESA: Ve todas las ausencias de sus empleados.
     * - SUPERVISOR: Ve ausencias de los empleados de su mismo departamento (exceptuando la suya).
     *
     * @param estadoFiltro Estado opcional para la consulta.
     * @return List de {@link RespuestaAusenciaDTO} con las solicitudes pendientes de gestión.
     */
    @Transactional(readOnly = true)
    public List<RespuestaAusenciaDTO> obtenerAusenciasPermitidas(EstadoAusencia estadoFiltro) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        List<Ausencia> ausencias;

        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));
            if (estadoFiltro != null) {
                ausencias = ausenciaRepository.findByEmpleadoEmpresaIdEmpresaAndEstado(empresa.getIdEmpresa(), estadoFiltro);
            } else {
                ausencias = ausenciaRepository.findByEmpleadoEmpresaIdEmpresa(empresa.getIdEmpresa());
            }
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Supervisor no encontrado"));
            if (estadoFiltro != null) {
                ausencias = ausenciaRepository.findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCaseAndEstado(
                        supervisor.getEmpresa().getIdEmpresa(), supervisor.getDepartamento(), estadoFiltro);
            } else {
                ausencias = ausenciaRepository.findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(
                        supervisor.getEmpresa().getIdEmpresa(), supervisor.getDepartamento());
            }

            ausencias = ausencias.stream()
                    .filter(a -> !a.getEmpleado().getIdEmpleado().equals(supervisor.getIdEmpleado()))
                    .collect(Collectors.toList());
        }

        return ausencias.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Procesa la revisión de una solicitud de ausencia (Aprobar o Rechazar).
     * <p>
     * <b>Lógica de Negocio al Aprobar:</b>
     * Si la ausencia es aprobada, el sistema localiza y elimina automáticamente cualquier 
     * asignación de turno que el empleado tuviera planificada en el rango de fechas de la ausencia,
     * garantizando la coherencia del calendario operativo.
     * </p>
     * <p>
     * <b>Requisito de Rechazo:</b>
     * Si el responsable decide rechazar la solicitud, es obligatorio proporcionar una descripción
     * en el campo de observaciones con los motivos de la denegación.
     * </p>
     *
     * @param idAusencia Identificador único de la solicitud de ausencia a gestionar.
     * @param peticion Objeto {@link PeticionRevisionAusenciaDTO} con el nuevo estado y comentarios.
     * @return {@link RespuestaAusenciaDTO} con el resultado final de la revisión persistido.
     * @throws RuntimeException Si falta justificación en un rechazo o si el revisor no tiene autoridad suficiente.
     * @throws EntityNotFoundException Si la ausencia especificada no existe en la base de datos.
     */
    @Transactional
    public RespuestaAusenciaDTO revisarAusencia(Long idAusencia, PeticionRevisionAusenciaDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        Ausencia ausencia = ausenciaRepository.findById(idAusencia)
                .orElseThrow(() -> new EntityNotFoundException("Ausencia no encontrada"));

        validarPrivilegiosRevision(emailAuth, esEmpresa, ausencia.getEmpleado());

        if (peticion.getEstado() == EstadoAusencia.RECHAZADA &&
                (peticion.getObservacionesRevision() == null || peticion.getObservacionesRevision().trim().isEmpty())) {
            log.warn("Revisión DENEGADA: El usuario '{}' intentó rechazar la ausencia ID {} sin justificar los motivos.", emailAuth, idAusencia);
            throw new RuntimeException("Debes proporcionar observaciones si rechazas la ausencia.");
        }

        ausencia.setEstado(peticion.getEstado());
        ausencia.setResponsableRevision(emailAuth);
        ausencia.setObservacionesRevision(peticion.getObservacionesRevision());

        if (peticion.getEstado() == EstadoAusencia.APROBADA) {
            List<AsignacionTurno> turnosSolapados = asignacionRepository.findByEmpleadoIdEmpleadoAndFechaBetween(
                    ausencia.getEmpleado().getIdEmpleado(),
                    ausencia.getFechaInicio(),
                    ausencia.getFechaFin()
            );
            if (!turnosSolapados.isEmpty()) {
                List<Long> idsConFichajes = fichajeRepository.findIdAsignacionesConFichajes(
                        turnosSolapados.stream()
                                .map(AsignacionTurno::getIdAsignacion)
                                .collect(Collectors.toList())
                );
                List<AsignacionTurno> eliminables = turnosSolapados.stream()
                        .filter(a -> !idsConFichajes.contains(a.getIdAsignacion()))
                        .collect(Collectors.toList());
                if (!eliminables.isEmpty()) {
                    asignacionRepository.deleteAll(eliminables);
                    log.info("SISTEMA: Eliminados {} turnos planificados para el empleado ID {} debido a la aprobación de la ausencia ID {}.",
                            eliminables.size(), ausencia.getEmpleado().getIdEmpleado(), idAusencia);
                }
            }
        }

        ausencia = ausenciaRepository.save(ausencia);

        log.info("REVISIÓN DE AUSENCIA: La ausencia ID {} ha sido marcada como {} por el usuario '{}'.",
                idAusencia, peticion.getEstado(), emailAuth);

        return mapearARespuesta(ausencia);
    }

    /**
     * Obtiene el empleado autenticado a partir del contexto de seguridad.
     *
     * @throws EntityNotFoundException Si el empleado especificado no existe en la base de datos.
     * @return Empleado que realiza la acción.
     */
    private Empleado obtenerEmpleadoAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return empleadoRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));
    }

    /**
     * Valida que una ausencia exista y pertenezca efectivamente al empleado que la consulta.
     *
     * @param idAusencia Identificador de la ausencia.
     * @param empleadoLogueado Empleado que realiza la consulta.
     * @throws EntityNotFoundException Si la ausencia especificada no existe en la base de datos.
     * @return {@link Ausencia} Entidad recuperada.
     */
    private Ausencia obtenerAusenciaPropia(Long idAusencia, Empleado empleadoLogueado) {
        Ausencia ausencia = ausenciaRepository.findById(idAusencia)
                .orElseThrow(() -> new EntityNotFoundException("Ausencia no encontrada"));

        if (!ausencia.getEmpleado().getIdEmpleado().equals(empleadoLogueado.getIdEmpleado())) {
            throw new RuntimeException("Acceso denegado: Esta ausencia no te pertenece.");
        }
        return ausencia;
    }

    /**
     * Comprueba la coherencia cronológica entre la fecha de inicio y la de fin.
     *
     * @param inicio Fecha inicial.
     * @param fin Fecha final.
     */
    private void validarFechas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        if (inicio.isAfter(fin)) {
            throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
    }

    /**
     * Verifica que el revisor tenga los permisos necesarios para gestionar la ausencia de un empleado.
     * Controla la jurisdicción multi-empresa y multi-departamento.
     *
     * @param emailAuth Email del revisor.
     * @param esEmpresa Booleano indicando si el revisor tiene rol de empresa.
     * @param empleadoDestino Empleado cuya ausencia se está revisando.
     */
    private void validarPrivilegiosRevision(String emailAuth, boolean esEmpresa, Empleado empleadoDestino) {
        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));
            if (!empleadoDestino.getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa())) {
                log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó revisar una ausencia del empleado ID {}, que pertenece a otra empresa.", emailAuth, empleadoDestino.getIdEmpleado());
                throw new RuntimeException("El empleado no pertenece a tu empresa.");
            }
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Supervisor no encontrado"));
            if (!supervisor.getEmpresa().getIdEmpresa().equals(empleadoDestino.getEmpresa().getIdEmpresa())) {
                throw new RuntimeException("El empleado no pertenece a tu empresa.");
            }
            if (!supervisor.getDepartamento().equalsIgnoreCase(empleadoDestino.getDepartamento())) {
                log.warn("VIOLACIÓN DE SEGURIDAD: El supervisor '{}' intentó revisar una ausencia de un empleado fuera de su departamento ({}).", emailAuth, empleadoDestino.getDepartamento());
                throw new RuntimeException("Solo puedes revisar ausencias de tu departamento.");
            }
            if (supervisor.getIdEmpleado().equals(empleadoDestino.getIdEmpleado())) {
                log.warn("DENEGADO: El supervisor '{}' intentó auto-aprobarse una ausencia.", emailAuth);
                throw new RuntimeException("No puedes aprobar o rechazar tus propias ausencias. Debe hacerlo la Empresa.");
            }
        }
    }

    /**
     * Asegura que no existan solicitudes de ausencia activas (Pendientes o Aprobadas) que colisionen en el tiempo.
     *
     * @param idEmpleado ID del trabajador.
     * @param inicio Fecha de inicio de la nueva solicitud.
     * @param fin Fecha de fin de la nueva solicitud.
     * @param idAusenciaExcluida ID para excluir de la validación (usado en actualizaciones).
     */
    private void validarSolapamientoAusencias(Long idEmpleado, java.time.LocalDate inicio, java.time.LocalDate fin, Long idAusenciaExcluida) {
        List<EstadoAusencia> estadosActivos = List.of(EstadoAusencia.SOLICITADA, EstadoAusencia.APROBADA);

        List<Ausencia> solapadas = ausenciaRepository.findAusenciasSolapadas(idEmpleado, estadosActivos, inicio, fin);

        if (idAusenciaExcluida != null) {
            solapadas = solapadas.stream()
                    .filter(a -> !a.getIdAusencia().equals(idAusenciaExcluida))
                    .collect(Collectors.toList());
        }

        if (!solapadas.isEmpty()) {
            throw new RuntimeException("No puedes solicitar esta ausencia: ya tienes otra solicitud (Pendiente o Aprobada) que coincide con estas fechas.");
        }
    }

    /**
     * Verifica que el empleado no tenga fichajes registrados en el rango de fechas solicitado.
     * Si existe al menos uno, la solicitud de ausencia se rechaza para evitar inconsistencias
     * entre los registros de jornada y las ausencias.
     *
     * @param idEmpleado ID del trabajador.
     * @param inicio Fecha de inicio del rango.
     * @param fin Fecha de fin del rango.
     */
    private void validarQueNoHayFichajesEnRango(Long idEmpleado, java.time.LocalDate inicio, java.time.LocalDate fin) {
        List<com.gestorrh.api.entity.Fichaje> fichajesExistentes =
                fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(idEmpleado, inicio, fin);

        if (!fichajesExistentes.isEmpty()) {
            throw new RuntimeException("No puedes solicitar una ausencia en fechas en las que ya has fichado.");
        }
    }

    /**
     * Convierte la entidad de base de datos {@link Ausencia} a su correspondiente DTO de respuesta.
     *
     * @param a Entidad a mapear.
     * @return {@link RespuestaAusenciaDTO} formateado.
     */
    private RespuestaAusenciaDTO mapearARespuesta(Ausencia a) {
        return RespuestaAusenciaDTO.builder()
                .idAusencia(a.getIdAusencia())
                .idEmpleado(a.getEmpleado().getIdEmpleado())
                .nombreCompletoEmpleado(a.getEmpleado().getNombre() + " " + a.getEmpleado().getApellidos())
                .tipo(a.getTipo())
                .descripcion(a.getDescripcion())
                .fechaInicio(a.getFechaInicio())
                .fechaFin(a.getFechaFin())
                .justificante(a.getJustificante())
                .estado(a.getEstado())
                .responsableRevision(a.getResponsableRevision())
                .observacionesRevision(a.getObservacionesRevision())
                .build();
    }
}
