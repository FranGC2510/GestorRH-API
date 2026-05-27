package com.gestorrh.api.service;

import com.gestorrh.api.dto.fichaje.*;
import com.gestorrh.api.entity.AsignacionTurno;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Fichaje;
import com.gestorrh.api.entity.enums.ModalidadTurno;
import com.gestorrh.api.repository.AsignacionTurnoRepository;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.FichajeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio central para la gestión de fichajes y registros de jornada de los empleados.
 * <p>
 * Gestiona el ciclo completo de la jornada laboral: desde el registro de entrada con validación 
 * de geolocalización (geovallado) y detección de retrasos, hasta el registro de salida 
 * con cálculo automático de incidencias horarias.
 * </p>
 * <p>
 * Proporciona además funcionalidades críticas de auditoría para la modificación manual de fichajes
 * por parte de gestores, garantizando la trazabilidad de cualquier alteración en el registro de tiempos.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FichajeService {

    private final FichajeRepository fichajeRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpresaRepository empresaRepository;
    private final AsignacionTurnoRepository asignacionRepository;
    private final GeofencingService geofencingService;

    private static final int MINUTOS_CORTESIA = 15;

    /**
     * Registra un nuevo inicio de jornada (fichaje de entrada) para el empleado autenticado.
     * <p>
     * El flujo de ejecución realiza las siguientes validaciones críticas:
     * </p>
     * <ol>
     * <li><b>Configuración de Sede:</b> Verifica que la empresa haya configurado sus coordenadas GPS y radio de validez.</li>
     * <li><b>Fichaje Duplicado:</b> Impide abrir una nueva jornada si ya existe un registro abierto (sin hora de salida) para hoy.</li>
     * <li><b>Geovallado:</b> Si el turno asignado es de modalidad presencial, valida que la ubicación GPS recibida esté dentro del radio permitido respecto a la sede.</li>
     * <li><b>Control de Puntualidad:</b> Compara la hora actual con el inicio del turno asignado (incluyendo un margen de cortesía de 15 minutos) para detectar posibles retrasos.</li>
     * </ol>
     *
     * @param peticion Objeto {@link PeticionFichajeEntradaDTO} con las coordenadas GPS (latitud y longitud) del empleado.
     * @return {@link RespuestaFichajeDTO} con los detalles del registro creado satisfactoriamente.
     * @throws RuntimeException Si se detecta una violación de geolocalización o si el estado del empleado/empresa es inválido para operar.
     */
    @Transactional
    public RespuestaFichajeDTO ficharEntrada(PeticionFichajeEntradaDTO peticion) {
        Empleado empleado = obtenerEmpleadoAutenticado();
        validarSedeConfigurada(empleado.getEmpresa());
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Fichaje> abiertos = fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(empleado.getIdEmpleado(), hoy);
        if (!abiertos.isEmpty()) {
            log.warn("Fichaje DENEGADO (Entrada): El empleado '{}' intentó abrir un turno, pero ya tiene uno abierto sin cerrar.", empleado.getEmail());
            throw new RuntimeException("No puedes fichar la entrada: ya tienes un turno abierto sin registrar la salida.");
        }

        List<AsignacionTurno> asignacionesHoy = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(empleado.getIdEmpleado(), hoy);
        AsignacionTurno asignacionActual = asignacionesHoy.isEmpty() ? null : asignacionesHoy.getFirst();

        validarGeovalladoSiEsPresencial(empleado, asignacionActual, peticion.getLatitud(), peticion.getLongitud());

        String incidencias = evaluarRetrasoEntrada(asignacionActual, ahora, empleado);

        Fichaje nuevoFichaje = Fichaje.builder()
                .empleado(empleado)
                .asignacion(asignacionActual)
                .fecha(hoy)
                .horaEntrada(ahora)
                .latitudEntrada(peticion.getLatitud())
                .longitudEntrada(peticion.getLongitud())
                .incidencias(incidencias.isEmpty() ? null : incidencias)
                .build();

        nuevoFichaje = fichajeRepository.save(nuevoFichaje);
        return mapearARespuesta(nuevoFichaje);
    }

    /**
     * Finaliza la jornada laboral actual registrando el fichaje de salida.
     * <p>
     * Calcula posibles incidencias por salida anticipada si el empleado cierra el turno antes de la hora prevista.
     * </p>
     *
     * @param peticion DTO con las coordenadas GPS del momento de salida.
     * @return {@link RespuestaFichajeDTO} con la información actualizada incluyendo la hora de salida.
     * @throws RuntimeException Si no hay ninguna entrada abierta para el día de hoy.
     */
    @Transactional
    public RespuestaFichajeDTO ficharSalida(PeticionFichajeSalidaDTO peticion) {
        Empleado empleado = obtenerEmpleadoAutenticado();
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Fichaje> abiertos = fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(empleado.getIdEmpleado(), hoy);
        if (abiertos.isEmpty()) {
            log.warn("Fichaje DENEGADO (Salida): El empleado '{}' intentó fichar la salida sin tener una entrada abierta.", empleado.getEmail());
            throw new RuntimeException("No puedes fichar la salida: no tienes ninguna entrada abierta para el día de hoy.");
        }

        Fichaje fichajeAbierto = abiertos.getFirst();

        validarGeovalladoSiEsPresencial(empleado, fichajeAbierto.getAsignacion(), peticion.getLatitud(), peticion.getLongitud());

        fichajeAbierto.setHoraSalida(ahora);
        fichajeAbierto.setLatitudSalida(peticion.getLatitud());
        fichajeAbierto.setLongitudSalida(peticion.getLongitud());

        String incidenciasActualizadas = evaluarSalidaAnticipada(fichajeAbierto.getAsignacion(), ahora, empleado, fichajeAbierto.getIncidencias());
        fichajeAbierto.setIncidencias(incidenciasActualizadas.isEmpty() ? null : incidenciasActualizadas);

        fichajeAbierto = fichajeRepository.save(fichajeAbierto);
        return mapearARespuesta(fichajeAbierto);
    }

    /**
     * Consulta el historial de fichajes bajo un rango de fechas y filtros opcionales.
     * <p>
     * La visibilidad de los datos depende del rol:
     * - EMPRESA: Ve todos los fichajes de su plantilla.
     * - SUPERVISOR: Ve fichajes de su departamento.
     * - EMPLEADO: Ve únicamente sus propios registros.
     * </p>
     *
     * @param fechaInicio Fecha de inicio de la búsqueda (si es null, se usa el día 1 del mes actual).
     * @param fechaFin Fecha de fin de la búsqueda (si es null, se usa la fecha de hoy).
     * @param empleadoIdFiltro Identificador opcional para filtrar por un empleado concreto.
     * @return List de {@link RespuestaFichajeDTO} con los registros localizados.
     */
    @Transactional(readOnly = true)
    public List<RespuestaFichajeDTO> consultarFichajes(LocalDate fechaInicio, LocalDate fechaFin, Long empleadoIdFiltro) {
        if (fechaInicio == null) fechaInicio = LocalDate.now().withDayOfMonth(1);
        if (fechaFin == null) fechaFin = LocalDate.now();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));
        boolean esSupervisor = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));

        List<Fichaje> fichajes;

        if (esEmpresa) {
            fichajes = consultarFichajesComoEmpresa(emailAuth, fechaInicio, fechaFin, empleadoIdFiltro);
        } else if (esSupervisor) {
            fichajes = consultarFichajesComoSupervisor(emailAuth, fechaInicio, fechaFin, empleadoIdFiltro);
        } else {
            fichajes = consultarFichajesComoEmpleado(emailAuth, fechaInicio, fechaFin);
        }

        return fichajes.stream().map(this::mapearARespuesta).collect(Collectors.toList());
    }

    /**
     * Permite a una empresa o supervisor modificar manualmente las horas de un fichaje ya registrado.
     * <p>
     * Esta operación está estrictamente auditada y requiere un motivo justificado. Los supervisores
     * no pueden modificar sus propios fichajes.
     * </p>
     *
     * @param idFichaje Identificador del registro a modificar.
     * @param peticion DTO con las nuevas horas y el motivo de la modificación.
     * @return {@link RespuestaFichajeDTO} con el registro actualizado y la marca de auditoría en incidencias.
     * @throws RuntimeException Si la modificación viola reglas de seguridad o jurisdicción de roles.
     */
    @Transactional
    public RespuestaFichajeDTO modificarFichajeManual(Long idFichaje, PeticionModificacionFichajeDTO peticion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        Fichaje fichaje = fichajeRepository.findById(idFichaje)
                .orElseThrow(() -> new EntityNotFoundException("Fichaje no encontrado"));

        validarPermisosModificacion(fichaje, emailAuth, esEmpresa);

        if (peticion.getNuevaHoraEntrada() == null && peticion.getNuevaHoraSalida() == null) {
            throw new RuntimeException("Debes proporcionar al menos una nueva hora de entrada o de salida para modificar el fichaje.");
        }

        if (peticion.getNuevaHoraEntrada() != null) {
            fichaje.setHoraEntrada(peticion.getNuevaHoraEntrada());
        }
        if (peticion.getNuevaHoraSalida() != null) {
            fichaje.setHoraSalida(peticion.getNuevaHoraSalida());
        }

        String marcaAuditoria = String.format(" | [MODIFICADO MANUALMENTE por %s. Motivo: %s]", emailAuth, peticion.getMotivoModificacion());
        String incidenciasActuales = fichaje.getIncidencias() == null ? "" : fichaje.getIncidencias();

        if (incidenciasActuales.isEmpty()) {
            fichaje.setIncidencias(marcaAuditoria.replace(" | ", "").trim());
        } else {
            fichaje.setIncidencias(incidenciasActuales + marcaAuditoria);
        }

        fichaje = fichajeRepository.save(fichaje);

        log.info("AUDITORÍA: El fichaje ID {} ha sido MODIFICADO MANUALMENTE por '{}'. Motivo: {}", idFichaje, emailAuth, peticion.getMotivoModificacion());

        return mapearARespuesta(fichaje);
    }

    /**
     * Obtiene un resumen consolidado del estado de la jornada para el empleado autenticado.
     * <p>
     * Este método es fundamental para la experiencia de usuario en la app móvil, ya que permite
     * determinar qué acciones (fichar entrada o salida) están disponibles al arrancar la aplicación.
     * </p>
     *
     * @return {@link RespuestaEstadoFichajeDTO} con la información de fichajes y turnos del día actual.
     */
    @Transactional(readOnly = true)
    public RespuestaEstadoFichajeDTO obtenerEstadoActual() {
        Empleado empleado = obtenerEmpleadoAutenticado();
        LocalDate hoy = LocalDate.now();

        List<Fichaje> abiertos = fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(empleado.getIdEmpleado(), hoy);
        Fichaje fichajeActivo = abiertos.isEmpty() ? null : abiertos.getFirst();

        List<AsignacionTurno> asignaciones = asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(empleado.getIdEmpleado(), hoy);
        AsignacionTurno turnoHoy = asignaciones.isEmpty() ? null : asignaciones.getFirst();

        return RespuestaEstadoFichajeDTO.builder()
                .trabajandoActualmente(fichajeActivo != null)
                .idFichajeAbierto(fichajeActivo != null ? fichajeActivo.getIdFichaje() : null)
                .horaEntrada(fichajeActivo != null ? fichajeActivo.getHoraEntrada() : null)
                .tieneTurnoHoy(turnoHoy != null)
                .modalidadHoy(turnoHoy != null ? turnoHoy.getModalidad() : null)
                .build();
    }

    // MÉTODOS PRIVADOS DE REFACTORIZACIÓN (CLEAN CODE)

    private Empleado obtenerEmpleadoAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return empleadoRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));
    }

    private void validarSedeConfigurada(Empresa empresa) {
        if (empresa.getLatitudSede() == null || empresa.getLongitudSede() == null || empresa.getRadioValidez() == null) {
            throw new RuntimeException("Operación denegada: La empresa debe configurar la ubicación de su sede en el perfil antes de permitir registrar fichajes.");
        }
    }

    private void validarGeovalladoSiEsPresencial(Empleado empleado, AsignacionTurno asignacionActual, Double latitud, Double longitud) {
        if (asignacionActual != null && asignacionActual.getModalidad() == ModalidadTurno.PRESENCIAL) {

            if (latitud == null || longitud == null) {
                log.warn("Fichaje DENEGADO (GPS Requerido): El empleado '{}' intentó fichar PRESENCIAL sin enviar coordenadas.", empleado.getEmail());
                throw new RuntimeException("Para el turno presencial es obligatorio activar el GPS y enviar la ubicación.");
            }

            Empresa empresa = empleado.getEmpresa();
            boolean gpsValido = geofencingService.esFichajeValido(
                    latitud, longitud,
                    empresa.getLatitudSede(), empresa.getLongitudSede(), empresa.getRadioValidez()
            );

            if (!gpsValido) {
                log.warn("Fichaje DENEGADO (Geovallado): El empleado '{}' intentó fichar PRESENCIAL fuera del radio permitido.", empleado.getEmail());
                throw new RuntimeException("Fichaje denegado: Estás fuera del radio permitido de la oficina para tu turno presencial.");
            }
        }
    }

    private String evaluarRetrasoEntrada(AsignacionTurno asignacion, LocalDateTime ahora, Empleado empleado) {
        if (asignacion != null) {
            LocalDateTime horaInicioTurno = LocalDate.now().atTime(asignacion.getTurno().getHoraInicio());
            if (ahora.isAfter(horaInicioTurno.plusMinutes(MINUTOS_CORTESIA))) {
                log.info("Fichaje ENTRADA (CON RETRASO): El empleado '{}' ha fichado a las {}.", empleado.getEmail(), ahora.toLocalTime());
                return "Retraso en la entrada. Fichó a las " + ahora.toLocalTime() + ". ";
            } else {
                log.info("Fichaje ENTRADA: El empleado '{}' ha fichado correctamente.", empleado.getEmail());
                return "";
            }
        } else {
            log.info("Fichaje ENTRADA (SIN TURNO): El empleado '{}' ha fichado sin tener turno planificado.", empleado.getEmail());
            return "Fichaje registrado sin turno asignado para el día de hoy. ";
        }
    }

    private String evaluarSalidaAnticipada(AsignacionTurno asignacion, LocalDateTime ahora, Empleado empleado, String incidenciasPrevias) {
        String incidenciasBase = (incidenciasPrevias == null) ? "" : incidenciasPrevias;

        if (asignacion != null) {
            LocalDateTime horaFinTurno = LocalDate.now().atTime(asignacion.getTurno().getHoraFin());

            if (ahora.isBefore(horaFinTurno.minusMinutes(MINUTOS_CORTESIA))) {
                String nuevaIncidencia = "Salida anticipada a las " + ahora.toLocalTime() + ". ";
                String union = incidenciasBase.isEmpty() ? "" : " | ";
                log.info("Fichaje SALIDA (ANTICIPADA): El empleado '{}' ha salido antes de tiempo a las {}.", empleado.getEmail(), ahora.toLocalTime());
                return incidenciasBase + union + nuevaIncidencia;
            } else {
                log.info("Fichaje SALIDA: El empleado '{}' ha cerrado su turno correctamente.", empleado.getEmail());
                return incidenciasBase;
            }
        } else {
            log.info("Fichaje SALIDA: El empleado '{}' ha cerrado su turno (no planificado).", empleado.getEmail());
            return incidenciasBase;
        }
    }

    private List<Fichaje> consultarFichajesComoEmpresa(String emailAuth, LocalDate fechaInicio, LocalDate fechaFin, Long empleadoIdFiltro) {
        Empresa empresa = empresaRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new EntityNotFoundException("Error crítico: Empresa no encontrada en el sistema"));
        List<Fichaje> fichajes = fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(empresa.getIdEmpresa(), fechaInicio, fechaFin);

        if (empleadoIdFiltro != null) {
            return fichajes.stream().filter(f -> f.getEmpleado().getIdEmpleado().equals(empleadoIdFiltro)).collect(Collectors.toList());
        }
        return fichajes;
    }

    private List<Fichaje> consultarFichajesComoSupervisor(String emailAuth, LocalDate fechaInicio, LocalDate fechaFin, Long empleadoIdFiltro) {
        Empleado supervisor = empleadoRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new EntityNotFoundException("Error crítico: Supervisor no encontrado en el sistema"));

        List<Fichaje> fichajesDepartamento = fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(supervisor.getEmpresa().getIdEmpresa(), fechaInicio, fechaFin);
        List<Fichaje> fichajes = fichajesDepartamento.stream()
                .filter(f -> f.getEmpleado().getDepartamento().equalsIgnoreCase(supervisor.getDepartamento()))
                .collect(Collectors.toList());

        if (empleadoIdFiltro != null) {
            boolean esElPropiaSupervisor = supervisor.getIdEmpleado().equals(empleadoIdFiltro);
            boolean perteneceAlDepartamento = empleadoRepository.findById(empleadoIdFiltro)
                    .map(e -> e.getEmpresa().getIdEmpresa().equals(supervisor.getEmpresa().getIdEmpresa())
                            && e.getDepartamento() != null
                            && e.getDepartamento().equalsIgnoreCase(supervisor.getDepartamento()))
                    .orElse(false);

            if (!esElPropiaSupervisor && !perteneceAlDepartamento) {
                throw new RuntimeException("Acceso denegado: Solo puedes ver los fichajes de los empleados de tu departamento (" + supervisor.getDepartamento() + ").");
            }
            return fichajes.stream().filter(f -> f.getEmpleado().getIdEmpleado().equals(empleadoIdFiltro)).collect(Collectors.toList());
        }
        return fichajes;
    }

    private List<Fichaje> consultarFichajesComoEmpleado(String emailAuth, LocalDate fechaInicio, LocalDate fechaFin) {
        Empleado empleadoAuth = empleadoRepository.findByEmail(emailAuth)
                .orElseThrow(() -> new EntityNotFoundException("Error crítico: Empleado no encontrado en el sistema"));
        return fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(empleadoAuth.getIdEmpleado(), fechaInicio, fechaFin);
    }

    private void validarPermisosModificacion(Fichaje fichaje, String emailAuth, boolean esEmpresa) {
        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Error crítico: Empresa no encontrada en el sistema"));
            if (!fichaje.getEmpleado().getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa())) {
                log.warn("VIOLACIÓN DE SEGURIDAD: La empresa '{}' intentó modificar el fichaje ID {} de otra empresa.", emailAuth, fichaje.getIdFichaje());
                throw new RuntimeException("Acceso denegado: El fichaje pertenece a otra empresa.");
            }
        } else {
            Empleado supervisor = empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Error crítico: Supervisor no encontrado en el sistema"));

            if (fichaje.getEmpleado().getIdEmpleado().equals(supervisor.getIdEmpleado())) {
                log.warn("DENEGADO: El supervisor '{}' intentó modificar su propio fichaje ID {}.", emailAuth, fichaje.getIdFichaje());
                throw new RuntimeException("Conflicto de intereses: Como supervisor no puedes modificar tus propios fichajes. Debe hacerlo la empresa u otro supervisor.");
            }

            if (!fichaje.getEmpleado().getEmpresa().getIdEmpresa().equals(supervisor.getEmpresa().getIdEmpresa()) ||
                    !fichaje.getEmpleado().getDepartamento().equalsIgnoreCase(supervisor.getDepartamento())) {
                log.warn("VIOLACIÓN DE SEGURIDAD: El supervisor '{}' intentó modificar el fichaje ID {} fuera de su jurisdicción.", emailAuth, fichaje.getIdFichaje());
                throw new RuntimeException("Acceso denegado: Solo puedes modificar fichajes de los empleados de tu departamento.");
            }
        }
    }

    private RespuestaFichajeDTO mapearARespuesta(Fichaje f) {
        return RespuestaFichajeDTO.builder()
                .idFichaje(f.getIdFichaje())
                .idEmpleado(f.getEmpleado().getIdEmpleado())
                .nombreEmpleado(f.getEmpleado().getNombre() + " " + f.getEmpleado().getApellidos())
                .idAsignacion(f.getAsignacion() != null ? f.getAsignacion().getIdAsignacion() : null)
                .descripcionTurno(f.getAsignacion() != null ? f.getAsignacion().getTurno().getDescripcion() : "Sin turno asignado")
                .fecha(f.getFecha())
                .horaEntrada(f.getHoraEntrada())
                .horaSalida(f.getHoraSalida())
                .incidencias(f.getIncidencias())
                .build();
    }
}
