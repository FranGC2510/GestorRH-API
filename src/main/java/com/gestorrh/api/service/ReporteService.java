package com.gestorrh.api.service;

import com.gestorrh.api.dto.reporte.ReporteDetalleDTO;
import com.gestorrh.api.dto.reporte.ReporteResumenDTO;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Fichaje;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.FichajeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio encargado de la generación de informes detallados y resumidos sobre la asistencia de los empleados.
 * <p>
 * Centraliza el procesamiento de datos de fichajes para proporcionar reportes precisos que pueden ser filtrados por 
 * fecha y por empleado. Estos informes son fundamentales para el cumplimiento de auditorías laborales y la gestión 
 * eficiente de los recursos humanos de la empresa.
 * </p>
 * <p>
 * Incluye lógica avanzada de cálculo para transformar registros de entrada/salida en métricas de tiempo trabajado,
 * tiempo teórico y detección automática de horas extraordinarias.
 * </p>
 *
 * @see com.gestorrh.api.entity.Fichaje
 * @see com.gestorrh.api.dto.reporte.ReporteDetalleDTO
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final FichajeRepository fichajeRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;

    private static final int MINUTOS_CORTESIA = 15;
    private static final DateTimeFormatter HORA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");


    /**
     * Obtiene el nombre de la empresa asociada al usuario autenticado que ha realizado la petición.
     * <p>
     * Determina si el usuario posee el rol de Empresa o de Empleado y recupera el nombre correspondiente
     * desde el repositorio adecuado. En caso de no encontrar la entidad, se devuelve "EMPRESA" como valor por defecto.
     * </p>
     *
     * @return El nombre de la empresa como cadena de texto.
     */
    public String obtenerNombreEmpresaAutenticada() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));

        if (esEmpresa) {
            return empresaRepository.findByEmail(email)
                    .map(Empresa::getNombre)
                    .orElse("EMPRESA");
        } else {
            return empleadoRepository.findByEmail(email)
                    .map(e -> e.getEmpresa().getNombre())
                    .orElse("EMPRESA");
        }
    }

    /**
     * Genera un reporte detallado de los fichajes realizados en un periodo temporal definido.
     * <p>
     * El reporte incluye un desglose pormenorizado por cada registro de entrada y salida, calculando el tiempo trabajado, 
     * el tiempo teórico según el turno asignado y el exceso de jornada (horas extra).
     * </p>
     * <p>
     * <b>Control de Acceso por Roles:</b>
     * </p>
     * <ul>
     *   <li>{@code ROLE_EMPRESA}: Acceso total a todos los empleados de la organización.</li>
     *   <li>{@code ROLE_SUPERVISOR}: Acceso restringido a los empleados de su propio departamento corporativo.</li>
     *   <li>{@code ROLE_EMPLEADO}: Acceso restringido exclusivamente a su historial de fichajes personal.</li>
     * </ul>
     *
     * @param fechaInicio Fecha de comienzo del periodo de reporte (inclusive).
     * @param fechaFin Fecha de finalización del periodo de reporte (inclusive).
     * @param idEmpleadoFiltro Identificador opcional de un empleado específico para filtrar los resultados de la consulta.
     * @return List de {@link ReporteDetalleDTO} con los datos de fichajes procesados y ordenados secuencialmente por empleado y fecha.
     * @throws RuntimeException Si la fecha de inicio es posterior a la de fin o si no se encuentran registros en el periodo.
     */
    @Transactional(readOnly = true)
    public List<ReporteDetalleDTO> obtenerReporteDetallado(LocalDate fechaInicio, LocalDate fechaFin, Long idEmpleadoFiltro) {
        if (fechaInicio.isAfter(fechaFin)) {
            throw new RuntimeException("Error: La fecha de inicio no puede ser posterior a la fecha de fin.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String emailAuth = auth.getName();
        boolean esEmpresa = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));
        boolean esSupervisor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));

        List<Fichaje> fichajes;

        if (esEmpresa) {
            Empresa empresa = empresaRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empresa no encontrada"));
            fichajes = fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(empresa.getIdEmpresa(), fechaInicio, fechaFin);

            if (idEmpleadoFiltro != null) {
                fichajes = fichajes.stream()
                        .filter(f -> f.getEmpleado().getIdEmpleado().equals(idEmpleadoFiltro)).collect(Collectors.toList());
            }
        } else {
            Empleado empleadoAuth = empleadoRepository.findByEmail(emailAuth)
                    .orElseThrow(() -> new EntityNotFoundException("Empleado no encontrado"));

            if (esSupervisor) {
                List<Fichaje> fichajesDepto = fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(empleadoAuth.getEmpresa().getIdEmpresa(), fechaInicio, fechaFin);
                fichajes = fichajesDepto.stream()
                        .filter(f -> f.getEmpleado().getDepartamento().equalsIgnoreCase(empleadoAuth.getDepartamento()))
                        .collect(Collectors.toList());

                if (idEmpleadoFiltro != null) {
                    boolean permitido = fichajes.stream().anyMatch(f -> f.getEmpleado().getIdEmpleado().equals(idEmpleadoFiltro)) || empleadoAuth.getIdEmpleado().equals(idEmpleadoFiltro);
                    if (!permitido) throw new RuntimeException("Acceso denegado: Solo puedes ver a los empleados de tu departamento.");
                    fichajes = fichajes.stream().filter(f -> f.getEmpleado().getIdEmpleado().equals(idEmpleadoFiltro)).collect(Collectors.toList());
                }
            } else {
                fichajes = fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(empleadoAuth.getIdEmpleado(), fechaInicio, fechaFin);
            }
        }

        if (fichajes.isEmpty()) {
            throw new RuntimeException("No hay datos de fichajes para el rango de fechas seleccionado.");
        }

        return fichajes.stream()
                .filter(f -> f.getHoraSalida() != null)
                .map(this::calcularFichaje)
                .sorted(Comparator.comparing(ReporteDetalleDTO::getNombreEmpleado)
                        .thenComparing(ReporteDetalleDTO::getFecha))
                .collect(Collectors.toList());
    }

    /**
     * Genera un reporte resumido que consolida los datos de asistencia agrupados por empleado.
     * <p>
     * Este método es ideal para obtener una visión ejecutiva del rendimiento de la plantilla, 
     * calculando totales de días laborados, tiempo total trabajado y el acumulado de minutos extraordinarios.
     * Los resultados son adecuados para ser presentados en resúmenes visuales o exportaciones PDF.
     * </p>
     *
     * @param fechaInicio Fecha inicial para el cálculo de los totales acumulados.
     * @param fechaFin Fecha final para el cálculo de los totales acumulados.
     * @param idEmpleadoFiltro Identificador opcional para realizar un resumen de un único trabajador.
     * @return List de {@link ReporteResumenDTO} con la información agregada y consolidada por cada empleado detectado.
     */
    @Transactional(readOnly = true)
    public List<ReporteResumenDTO> obtenerReporteResumen(LocalDate fechaInicio, LocalDate fechaFin, Long idEmpleadoFiltro) {

        List<ReporteDetalleDTO> detalle = obtenerReporteDetallado(fechaInicio, fechaFin, idEmpleadoFiltro);

        return detalle.stream()
                .collect(Collectors.groupingBy(ReporteDetalleDTO::getIdEmpleado))
                .values().stream()
                .map(listaFichajesEmpleado -> {
                    ReporteDetalleDTO primerFichaje = listaFichajesEmpleado.get(0);
                    return ReporteResumenDTO.builder()
                            .idEmpleado(primerFichaje.getIdEmpleado())
                            .nombreEmpleado(primerFichaje.getNombreEmpleado())
                            .departamento(primerFichaje.getDepartamento())
                            .diasTrabajados(listaFichajesEmpleado.size())
                            .totalTiempoTeoricoMinutos(listaFichajesEmpleado.stream().mapToLong(ReporteDetalleDTO::getTiempoTeoricoMinutos).sum())
                            .totalTiempoTotalMinutos(listaFichajesEmpleado.stream().mapToLong(ReporteDetalleDTO::getTiempoTotalMinutos).sum())
                            .totalMinutosExtra(listaFichajesEmpleado.stream().mapToLong(ReporteDetalleDTO::getMinutosExtra).sum())
                            .build();
                })
                .sorted(Comparator.comparing(ReporteResumenDTO::getNombreEmpleado))
                .collect(Collectors.toList());
    }

    /**
     * Realiza el cálculo matemático exhaustivo de un registro de fichaje individualizado.
     * <p>
     * Determina la duración de la jornada real efectuada frente a la duración teórica del turno que el empleado 
     * tenía planificado para ese día. Implementa una lógica de negocio de "minutos de cortesía" para la 
     * contabilización de las horas extra (sólo se cuentan si superan los 15 minutos de exceso).
     * </p>
     * <p>
     * En caso de que el empleado no tenga un turno asignado (Fichaje Fantasma), el tiempo trabajado se 
     * contabiliza íntegramente como tiempo total y extra.
     * </p>
     *
     * @param f La entidad {@link Fichaje} persistida que contiene las marcas horarias del empleado.
     * @return {@link ReporteDetalleDTO} con los cálculos de minutos teóricos, reales y extras completamente procesados.
     */
    private ReporteDetalleDTO calcularFichaje(Fichaje f) {
        long minutosTotales = Duration.between(f.getHoraEntrada(), f.getHoraSalida()).toMinutes();
        long minutosTeoricos = 0;
        long minutosExtra = 0;
        String descripcionTurno = "Sin turno asignado (Fichaje Fantasma)";

        if (f.getAsignacion() != null) {
            descripcionTurno = f.getAsignacion().getTurno().getDescripcion();
            LocalTime inicioTurno = f.getAsignacion().getTurno().getHoraInicio();
            LocalTime finTurno = f.getAsignacion().getTurno().getHoraFin();

            minutosTeoricos = TurnoService.calcularMinutosTurno(inicioTurno, finTurno);

            long minutosTotalesEfectivos = minutosTotales;
            LocalDateTime fechaHoraInicioTurno = f.getFecha().atTime(inicioTurno);

            if (f.getHoraEntrada().isBefore(fechaHoraInicioTurno)) {
                long minutosTemprano = Duration.between(f.getHoraEntrada(), fechaHoraInicioTurno).toMinutes();
                minutosTotalesEfectivos -= minutosTemprano;
            }

            long diferencia = minutosTotalesEfectivos - minutosTeoricos;

            if (diferencia > MINUTOS_CORTESIA) {
                minutosExtra = diferencia;
            }
        } else {
            minutosExtra = minutosTotales;
        }

        return ReporteDetalleDTO.builder()
                .idEmpleado(f.getEmpleado().getIdEmpleado())
                .nombreEmpleado(f.getEmpleado().getNombre() + " " + f.getEmpleado().getApellidos())
                .departamento(f.getEmpleado().getDepartamento() != null ? f.getEmpleado().getDepartamento() : "Sin departamento")
                .fecha(f.getFecha())
                .descripcionTurno(descripcionTurno)
                .horaEntradaReal(f.getHoraEntrada().format(HORA_FORMATTER))
                .horaSalidaReal(f.getHoraSalida().format(HORA_FORMATTER))
                .tiempoTotalMinutos(minutosTotales)
                .tiempoTeoricoMinutos(minutosTeoricos)
                .minutosExtra(minutosExtra)
                .incidencias(f.getIncidencias() != null ? f.getIncidencias() : "")
                .build();
    }
}
