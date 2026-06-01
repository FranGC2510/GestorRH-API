package com.gestorrh.api.service;

import com.gestorrh.api.dto.asignacion.PeticionAsignacionTurnoDTO;
import com.gestorrh.api.dto.asignacion.RespuestaAsignacionTurnoDTO;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la planificación operativa y gestión de turnos de trabajo.
 * <p>
 * Este servicio centraliza el calendario laboral de los empleados, permitiendo 
 * la asignación de turnos (mañana, tarde, etc.) bajo diferentes modalidades 
 * (presencial o teletrabajo). 
 * </p>
 * <p>
 * Implementa un motor de reglas de negocio avanzado para garantizar:
 * </p>
 * <ul>
 *   <li><b>Jornada Máxima:</b> Impide exceder los límites diarios de tiempo de trabajo permitidos.</li>
 *   <li><b>Consistencia:</b> Valida la configuración de la sede física de la empresa.</li>
 *   <li><b>Disponibilidad:</b> Previene el solapamiento con periodos de vacaciones o ausencias aprobadas.</li>
 *   <li><b>Trazabilidad:</b> Registra metadatos de auditoría para cada modificación manual de la planificación.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsignacionTurnoService {

    private final AsignacionTurnoRepository asignacionRepository;
    private final EmpleadoRepository empleadoRepository;
    private final TurnoRepository turnoRepository;
    private final EmpresaRepository empresaRepository;
    private final AusenciaRepository ausenciaRepository;

    private static final long MAX_MINUTOS_JORNADA = 8 * 60;

    /**
     * Crea y persiste una nueva asignación de turno en el sistema.
     * <p>
     * El flujo de creación incluye verificaciones de seguridad multi-tenant (para asegurar 
     * que el responsable, el empleado y el turno pertenecen a la misma empresa) y 
     * validaciones de negocio contra el límite de horas diarias y periodos de vacaciones.
     * </p>
     *
     * @param peticion Objeto {@link PeticionAsignacionTurnoDTO} con los detalles de la planificación propuesta.
     * @return {@link RespuestaAsignacionTurnoDTO} con los datos de la asignación confirmada satisfactoriamente.
     * @throws EntityNotFoundException Si el empleado o el turno especificados no existen en la base de datos.
     */
    @Transactional
    public RespuestaAsignacionTurnoDTO crearAsignacion(PeticionAsignacionTurnoDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        Empleado empleadoDestino = empleadoRepository.findById(peticion.getIdEmpleado())
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));

        validarSedeConfigurada(empleadoDestino.getEmpresa());

        Turno turno = turnoRepository.findById(peticion.getIdTurno())
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        validarPrivilegiosAsignacion(emailAuth, esEmpresa, empleadoDestino, turno);

        validarLimiteHorasDiarias(empleadoDestino.getIdEmpleado(), peticion.getFecha(), turno);

        validarQueNoEsteDeVacaciones(empleadoDestino.getIdEmpleado(), peticion.getFecha());

        AsignacionTurno nuevaAsignacion = AsignacionTurno.builder()
                .empleado(empleadoDestino)
                .turno(turno)
                .fecha(peticion.getFecha())
                .modalidad(peticion.getModalidad())
                .build();

        nuevaAsignacion = asignacionRepository.save(nuevaAsignacion);

        log.info("NUEVA ASIGNACIÓN: El usuario '{}' ha asignado el turno ID {} al empleado ID {} para la fecha {}.",
                emailAuth, turno.getIdTurno(), empleadoDestino.getIdEmpleado(), peticion.getFecha());

        return mapearARespuesta(nuevaAsignacion);
    }

    /**
     * Recupera todas las asignaciones de turnos que el usuario autenticado tiene permiso para visualizar.
     * - EMPRESA: Acceso total a las asignaciones de su plantilla.
     * - SUPERVISOR: Acceso limitado a los empleados de su propio departamento.
     *
     * @return List de {@link RespuestaAsignacionTurnoDTO} con la planificación consultada.
     */
    @Transactional(readOnly = true)
    public List<RespuestaAsignacionTurnoDTO> obtenerAsignacionesPermitidas() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        List<AsignacionTurno> asignaciones;

        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth).orElseThrow();
            asignaciones = asignacionRepository.findByEmpleadoEmpresaIdEmpresa(empresa.getIdEmpresa());
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth).orElseThrow();
            asignaciones = asignacionRepository.findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(
                    supervisor.getEmpresa().getIdEmpresa(), supervisor.getDepartamento());
        }

        return asignaciones.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Obtiene el calendario de turnos personal del empleado autenticado.
     *
     * @throws EntityNotFoundException Si el empleado o el turno especificados no existen en la base de datos.
     * @return List de {@link RespuestaAsignacionTurnoDTO} con las asignaciones propias.
     */
    @Transactional(readOnly = true)
    public List<RespuestaAsignacionTurnoDTO> obtenerMisAsignaciones() {
        String emailAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empleado empleado = empleadoRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));

        List<AsignacionTurno> misAsignaciones = asignacionRepository.findByEmpleadoIdEmpleado(empleado.getIdEmpleado());

        return misAsignaciones.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Actualiza una asignación de turno existente.
     * Es obligatorio registrar el motivo del cambio para mantener la trazabilidad de la auditoría.
     * Se vuelven a validar las restricciones de jornada máxima y ausencias.
     *
     * @param idAsignacion Identificador único de la asignación a modificar.
     * @param peticion DTO con los nuevos parámetros del turno y el motivo del cambio.
     * @return {@link RespuestaAsignacionTurnoDTO} con los datos actualizados y metadatos de auditoría.
     * @throws EntityNotFoundException Si el empleado o el turno especificados no existen en la base de datos.
     * @throws RuntimeException Si se violan los límites de jornada, hay solapamiento de vacaciones o faltan permisos de seguridad.
     */
    @Transactional
    public RespuestaAsignacionTurnoDTO actualizarAsignacion(Long idAsignacion, PeticionAsignacionTurnoDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        AsignacionTurno asignacionExistente = asignacionRepository.findById(idAsignacion)
                .orElseThrow(() -> new EntityNotFoundException("Asignación no encontrada"));

        Turno nuevoTurno = turnoRepository.findById(peticion.getIdTurno())
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        validarPrivilegiosAsignacion(emailAuth, esEmpresa, asignacionExistente.getEmpleado(), nuevoTurno);

        if (!asignacionExistente.getTurno().getIdTurno().equals(nuevoTurno.getIdTurno()) ||
                !asignacionExistente.getFecha().equals(peticion.getFecha())) {

            long minutosTurnoViejo = TurnoService.calcularMinutosTurno(asignacionExistente.getTurno().getHoraInicio(), asignacionExistente.getTurno().getHoraFin());

            validarLimiteHorasDiariasConDescuento(asignacionExistente.getEmpleado().getIdEmpleado(), peticion.getFecha(), nuevoTurno, minutosTurnoViejo);
        }

        validarQueNoEsteDeVacaciones(asignacionExistente.getEmpleado().getIdEmpleado(), peticion.getFecha());

        if (peticion.getMotivoCambio() == null || peticion.getMotivoCambio().trim().isEmpty()) {
            throw new RuntimeException("El motivo del cambio es obligatorio para auditar la modificación.");
        }

        asignacionExistente.setTurno(nuevoTurno);
        asignacionExistente.setFecha(peticion.getFecha());
        asignacionExistente.setModalidad(peticion.getModalidad());

        asignacionExistente.setMotivoCambio(peticion.getMotivoCambio());
        asignacionExistente.setFechaCambio(OffsetDateTime.now(ZoneOffset.UTC));
        asignacionExistente.setResponsableCambio(emailAuth);

        asignacionExistente = asignacionRepository.save(asignacionExistente);

        log.info("ASIGNACIÓN MODIFICADA: El usuario '{}' ha modificado la asignación ID {} (Detalles guardados en BD).", emailAuth, idAsignacion);

        return mapearARespuesta(asignacionExistente);
    }

    /**
     * Elimina permanentemente una asignación de turno del sistema.
     *
     * @throws EntityNotFoundException Si la asignación especificada no existe en la base de datos.
     * @param idAsignacion Identificador de la asignación a borrar.
     */
    @Transactional
    public void eliminarAsignacion(Long idAsignacion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        AsignacionTurno asignacion = asignacionRepository.findById(idAsignacion)
                .orElseThrow(() -> new EntityNotFoundException("Asignación no encontrada"));

        validarPrivilegiosAsignacion(emailAuth, esEmpresa, asignacion.getEmpleado(), asignacion.getTurno());

        asignacionRepository.delete(asignacion);

        log.warn("ASIGNACIÓN ELIMINADA: El usuario '{}' ha borrado permanentemente la asignación ID {}.", emailAuth, idAsignacion);
    }

    /**
     * Verifica que el usuario tenga permisos legales para gestionar la jornada de un empleado y turno.
     * Implementa lógica multi-tenant y de supervisión por departamento.
     *
     * @param emailAuth Email del usuario que realiza la operación.
     * @param esEmpresa Indica si es un perfil de empresa.
     * @param empleadoDestino Empleado cuya jornada se gestiona.
     * @param turno {@link Turno} que se pretende asignar.
     */
    private void validarPrivilegiosAsignacion(String emailAuth, boolean esEmpresa, Empleado empleadoDestino, Turno turno) {
        Long idEmpresaContexto;

        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth).orElseThrow();
            idEmpresaContexto = empresa.getIdEmpresa();
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth).orElseThrow();
            idEmpresaContexto = supervisor.getEmpresa().getIdEmpresa();

            if (!supervisor.getDepartamento().equalsIgnoreCase(empleadoDestino.getDepartamento())) {
                throw new RuntimeException("Como supervisor, solo puedes gestionar turnos del departamento: " + supervisor.getDepartamento());
            }
        }

        if (!empleadoDestino.getEmpresa().getIdEmpresa().equals(idEmpresaContexto) ||
                !turno.getEmpresa().getIdEmpresa().equals(idEmpresaContexto)) {
            log.warn("VIOLACIÓN DE SEGURIDAD: El usuario '{}' intentó gestionar una asignación fuera de su jurisdicción.", emailAuth);
            throw new RuntimeException("Acceso denegado: Violación de seguridad Multi-Tenant.");
        }
    }

    /**
     * Asegura que el empleado no supere el máximo de minutos permitidos por jornada diaria (ej. 8 horas).
     *
     * @param idEmpleado ID del trabajador.
     * @param fecha Día de la jornada.
     * @param nuevoTurno {@link Turno} que se desea añadir.
     */
    private void validarLimiteHorasDiarias(Long idEmpleado, java.time.LocalDate fecha, Turno nuevoTurno) {
        List<AsignacionTurno> turnosDelDia = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(idEmpleado, fecha);

        long minutosTotales = 0;
        for (AsignacionTurno asig : turnosDelDia) {
            minutosTotales += TurnoService.calcularMinutosTurno(asig.getTurno().getHoraInicio(), asig.getTurno().getHoraFin());
        }

        long minutosNuevoTurno = TurnoService.calcularMinutosTurno(nuevoTurno.getHoraInicio(), nuevoTurno.getHoraFin());

        if ((minutosTotales + minutosNuevoTurno) > MAX_MINUTOS_JORNADA) {
            throw new RuntimeException("Regla de negocio violada: El empleado no puede exceder las " + (MAX_MINUTOS_JORNADA / 60) + " horas de jornada en un mismo día.");
        }
    }

    /**
     * Variante de validación de jornada que tiene en cuenta el reemplazo de un turno existente.
     *
     * @param idEmpleado ID del trabajador.
     * @param fecha Día de la jornada.
     * @param nuevoTurno Nuevo turno propuesto.
     * @param minutosADescontar Minutos del turno que será reemplazado.
     */
    private void validarLimiteHorasDiariasConDescuento(Long idEmpleado, java.time.LocalDate fecha, Turno nuevoTurno, long minutosADescontar) {
        List<AsignacionTurno> turnosDelDia = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(idEmpleado, fecha);

        long minutosTotales = 0;
        for (AsignacionTurno asig : turnosDelDia) {
            minutosTotales += TurnoService.calcularMinutosTurno(asig.getTurno().getHoraInicio(), asig.getTurno().getHoraFin());
        }

        long minutosNuevoTurno = TurnoService.calcularMinutosTurno(nuevoTurno.getHoraInicio(), nuevoTurno.getHoraFin());

        if ((minutosTotales - minutosADescontar + minutosNuevoTurno) > MAX_MINUTOS_JORNADA) {
            throw new RuntimeException("Regla de negocio violada: El empleado no puede exceder las " + (MAX_MINUTOS_JORNADA / 60) + " horas de jornada en un mismo día.");
        }
    }

    /**
     * Comprueba si el empleado tiene una ausencia ya aprobada para la fecha en la que se le intenta asignar trabajo.
     *
     * @param idEmpleado ID del trabajador.
     * @param fechaTurno Fecha de la asignación.
     */
    private void validarQueNoEsteDeVacaciones(Long idEmpleado, java.time.LocalDate fechaTurno) {
        if (ausenciaRepository.tieneAusenciaAprobadaEnFecha(idEmpleado, EstadoAusencia.APROBADA, fechaTurno)) {
            throw new RuntimeException("Regla de negocio violada: No se puede asignar un turno. El empleado tiene una ausencia APROBADA en esa fecha.");
        }
    }

    /**
     * Mapea la entidad {@link AsignacionTurno} a su DTO de respuesta detallado.
     *
     * @param asig Entidad de base de datos.
     * @return {@link RespuestaAsignacionTurnoDTO} con toda la información, incluyendo auditoría.
     */
    private RespuestaAsignacionTurnoDTO mapearARespuesta(AsignacionTurno asig) {
        return RespuestaAsignacionTurnoDTO.builder()
                .idAsignacion(asig.getIdAsignacion())
                .idEmpleado(asig.getEmpleado().getIdEmpleado())
                .nombreCompletoEmpleado(asig.getEmpleado().getNombre() + " " + asig.getEmpleado().getApellidos())
                .idTurno(asig.getTurno().getIdTurno())
                .descripcionTurno(asig.getTurno().getDescripcion())
                .horaInicio(asig.getTurno().getHoraInicio())
                .horaFin(asig.getTurno().getHoraFin())
                .fecha(asig.getFecha())
                .modalidad(asig.getModalidad())
                .motivoCambio(asig.getMotivoCambio())
                .fechaCambio(asig.getFechaCambio())
                .responsableCambio(asig.getResponsableCambio())
                .build();
    }

    /**
     * Valida que la empresa tenga configurada su sede física para permitir asignaciones de turnos.
     *
     * @param empresa Entidad {@link Empresa} a validar.
     */
    private void validarSedeConfigurada(com.gestorrh.api.entity.Empresa empresa) {
        if (empresa.getLatitudSede() == null || empresa.getLongitudSede() == null || empresa.getRadioValidez() == null) {
            throw new RuntimeException("Operación denegada: La empresa debe configurar la ubicación de su sede (latitud, longitud y radio) en su perfil antes de poder asignar turnos a los empleados.");
        }
    }
}
