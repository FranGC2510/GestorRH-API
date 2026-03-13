package com.gestorrh.api.service;

import com.gestorrh.api.dto.asignacionDTO.PeticionAsignacionTurnoDTO;
import com.gestorrh.api.dto.asignacionDTO.RespuestaAsignacionTurnoDTO;
import com.gestorrh.api.entity.*;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio principal para la Épica E5: Gestión de Asignaciones de Turnos.
 */
@Service
@RequiredArgsConstructor
public class AsignacionTurnoService {

    private final AsignacionTurnoRepository asignacionRepository;
    private final EmpleadoRepository empleadoRepository;
    private final TurnoRepository turnoRepository;
    private final EmpresaRepository empresaRepository;
    private final AusenciaRepository ausenciaRepository;

    private static final long MAX_MINUTOS_JORNADA = 8 * 60;

    @Transactional
    public RespuestaAsignacionTurnoDTO crearAsignacion(PeticionAsignacionTurnoDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        Empleado empleadoDestino = empleadoRepository.findById(peticion.getIdEmpleado())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        Turno turno = turnoRepository.findById(peticion.getIdTurno())
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

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

        return mapearARespuesta(nuevaAsignacion);
    }

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
     * Endpoint exclusivo para el rol EMPLEADO.
     * Devuelve únicamente las asignaciones del usuario que hace la petición.
     */
    @Transactional(readOnly = true)
    public List<RespuestaAsignacionTurnoDTO> obtenerMisAsignaciones() {
        String emailAuth = SecurityContextHolder.getContext().getAuthentication().getName();

        Empleado empleado = empleadoRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        List<AsignacionTurno> misAsignaciones = asignacionRepository.findByEmpleadoIdEmpleado(empleado.getIdEmpleado());

        return misAsignaciones.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Modifica una asignación existente. Registra la auditoría del cambio.
     */
    @Transactional
    public RespuestaAsignacionTurnoDTO actualizarAsignacion(Long idAsignacion, PeticionAsignacionTurnoDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        AsignacionTurno asignacionExistente = asignacionRepository.findById(idAsignacion)
                .orElseThrow(() -> new RuntimeException("Asignación no encontrada"));

        Turno nuevoTurno = turnoRepository.findById(peticion.getIdTurno())
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));

        validarPrivilegiosAsignacion(emailAuth, esEmpresa, asignacionExistente.getEmpleado(), nuevoTurno);

        if (!asignacionExistente.getTurno().getIdTurno().equals(nuevoTurno.getIdTurno()) ||
                !asignacionExistente.getFecha().equals(peticion.getFecha())) {

            long minutosTurnoViejo = Duration.between(asignacionExistente.getTurno().getHoraInicio(), asignacionExistente.getTurno().getHoraFin()).toMinutes();

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
        asignacionExistente.setFechaCambio(LocalDateTime.now());
        asignacionExistente.setResponsableCambio(emailAuth);

        asignacionExistente = asignacionRepository.save(asignacionExistente);

        return mapearARespuesta(asignacionExistente);
    }

    /**
     * Elimina una asignación (ej. si se asignó por error).
     */
    @Transactional
    public void eliminarAsignacion(Long idAsignacion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        AsignacionTurno asignacion = asignacionRepository.findById(idAsignacion)
                .orElseThrow(() -> new RuntimeException("Asignación no encontrada"));

        validarPrivilegiosAsignacion(emailAuth, esEmpresa, asignacion.getEmpleado(), asignacion.getTurno());

        asignacionRepository.delete(asignacion);
    }

    // MÉTODOS PRIVADOS DE REGLAS DE NEGOCIO

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
            throw new RuntimeException("Acceso denegado: Violación de seguridad Multi-Tenant.");
        }
    }

    private void validarLimiteHorasDiarias(Long idEmpleado, java.time.LocalDate fecha, Turno nuevoTurno) {
        List<AsignacionTurno> turnosDelDia = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(idEmpleado, fecha);

        long minutosTotales = 0;
        for (AsignacionTurno asig : turnosDelDia) {
            minutosTotales += Duration.between(asig.getTurno().getHoraInicio(), asig.getTurno().getHoraFin()).toMinutes();
        }

        long minutosNuevoTurno = Duration.between(nuevoTurno.getHoraInicio(), nuevoTurno.getHoraFin()).toMinutes();

        if ((minutosTotales + minutosNuevoTurno) > MAX_MINUTOS_JORNADA) {
            throw new RuntimeException("Regla de negocio violada: El empleado no puede exceder las " + (MAX_MINUTOS_JORNADA / 60) + " horas de jornada en un mismo día.");
        }
    }

    private void validarLimiteHorasDiariasConDescuento(Long idEmpleado, java.time.LocalDate fecha, Turno nuevoTurno, long minutosADescontar) {
        List<AsignacionTurno> turnosDelDia = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(idEmpleado, fecha);

        long minutosTotales = 0;
        for (AsignacionTurno asig : turnosDelDia) {
            minutosTotales += Duration.between(asig.getTurno().getHoraInicio(), asig.getTurno().getHoraFin()).toMinutes();
        }

        long minutosNuevoTurno = Duration.between(nuevoTurno.getHoraInicio(), nuevoTurno.getHoraFin()).toMinutes();

        if ((minutosTotales - minutosADescontar + minutosNuevoTurno) > MAX_MINUTOS_JORNADA) {
            throw new RuntimeException("Regla de negocio violada: El empleado no puede exceder las " + (MAX_MINUTOS_JORNADA / 60) + " horas de jornada en un mismo día.");
        }
    }

    private void validarQueNoEsteDeVacaciones(Long idEmpleado, java.time.LocalDate fechaTurno) {
        if (ausenciaRepository.tieneAusenciaAprobadaEnFecha(idEmpleado, EstadoAusencia.APROBADA, fechaTurno)) {
            throw new RuntimeException("Regla de negocio violada: No se puede asignar un turno. El empleado tiene una ausencia APROBADA en esa fecha.");
        }
    }

    private RespuestaAsignacionTurnoDTO mapearARespuesta(AsignacionTurno asig) {
        return RespuestaAsignacionTurnoDTO.builder()
                .idAsignacion(asig.getIdAsignacion())
                .idEmpleado(asig.getEmpleado().getIdEmpleado())
                .nombreCompletoEmpleado(asig.getEmpleado().getNombre() + " " + asig.getEmpleado().getApellidos())
                .idTurno(asig.getTurno().getIdTurno())
                .descripcionTurno(asig.getTurno().getDescripcion())
                .fecha(asig.getFecha())
                .modalidad(asig.getModalidad())
                .motivoCambio(asig.getMotivoCambio())
                .fechaCambio(asig.getFechaCambio())
                .responsableCambio(asig.getResponsableCambio())
                .build();
    }
}
