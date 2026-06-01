package com.gestorrh.api.config;

import com.gestorrh.api.entity.*;
import com.gestorrh.api.entity.enums.*;
import com.gestorrh.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase encargada de inyectar el ecosistema de pruebas completo al arrancar la aplicación
 * si la base de datos está vacía. Genera un escenario multi-tenant realista con datos
 * del último mes hasta la fecha actual, incluyendo asignaciones futuras.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final TurnoRepository turnoRepository;
    private final AsignacionTurnoRepository asignacionRepository;
    private final FichajeRepository fichajeRepository;
    private final AusenciaRepository ausenciaRepository;
    private final PasswordEncoder passwordEncoder;

    // Coordenadas base de la sede (Madrid - Puerta del Sol)
    private static final double LAT_SEDE = 40.4168;
    private static final double LON_SEDE = -3.7038;

    // Offsets fijos por empleado para simular fichajes desde distintos puntos dentro del radio
    // Cada par representa (deltaLat, deltaLon) — máximo ~200m de la sede
    private static final double[][] OFFSETS_GPS = {
            {0.0002,  0.0001},  // [0] Carlos Jefe    — ~25m este
            {0.0000,  0.0000},  // [1] Juan Pérez     — fichajes desde la sede exacta
            {0.0005,  0.0003},  // [2] Ana García     — ~60m norte
            {-0.0008, 0.0006},  // [3] Roberto Díaz   — ~100m sur
            {0.0010, -0.0004},  // [4] Laura Sánchez  — ~110m norte
            {-0.0003, 0.0009},  // [5] Pedro Martínez — ~90m sur-este
            {0.0006,  0.0000},  // [6] Miguel Torres  — ~65m norte
            {-0.0004,-0.0005},  // [7] Elena Romero   — ~70m sur-oeste
            {0.0008,  0.0007},  // [8] Sofía Navarro  — ~120m norte-este
            {0.0001, -0.0008},  // [9] David Molina   — ~75m oeste
            {-0.0007, 0.0003},  // [10] Marcos Ruiz   — ~80m sur
            {0.0004, -0.0006},  // [11] Isabel Castro — ~75m norte-oeste
    };

    @Override
    public void run(String... args) throws Exception {
        if (empresaRepository.count() == 0) {
            log.info("[DataSeeder] Iniciando inyección de datos de prueba...");

            // ── Empresas ──────────────────────────────────────────────────────────
            List<Empresa> empresas = crearEmpresas();
            Empresa tech = empresas.get(0);

            // ── Turnos ────────────────────────────────────────────────────────────
            List<Turno> turnos = crearTurnos(tech);
            Turno tManana    = turnos.get(0); // 08:00-16:00
            Turno tTarde     = turnos.get(1); // 16:00-00:00
            Turno tMediodia  = turnos.get(2); // 10:00-18:00
            Turno tIntensiva = turnos.get(3); // 07:00-15:00
            Turno tMediaJorn = turnos.get(4); // 08:00-12:00

            // ── Empleados ─────────────────────────────────────────────────────────
            List<Empleado> empleados = crearEmpleados(tech);
            // Índice  0 → Carlos Jefe    (SUPERVISOR, IT)
            // Índice  1 → Juan Pérez     (EMPLEADO,   IT)        ← app Android
            // Índice  2 → Ana García     (EMPLEADO,   IT)
            // Índice  3 → Roberto Díaz   (EMPLEADO,   IT)        ← muchos retrasos
            // Índice  4 → Laura Sánchez  (SUPERVISOR, Marketing)
            // Índice  5 → Pedro Martínez (EMPLEADO,   Marketing)
            // Índice  6 → Miguel Torres  (EMPLEADO,   Marketing)
            // Índice  7 → Elena Romero   (SUPERVISOR, RRHH)
            // Índice  8 → Sofía Navarro  (EMPLEADO,   RRHH)      ← retrasos moderados
            // Índice  9 → David Molina   (EMPLEADO,   RRHH)      ← baja programada
            // Índice 10 → Marcos Ruiz    (SUPERVISOR, Contabilidad)
            // Índice 11 → Isabel Castro  (EMPLEADO,   Contabilidad) ← muchos retrasos

            // ── Rango de fechas ───────────────────────────────────────────────────
            LocalDate hoy         = LocalDate.now();
            LocalDate inicioMes   = hoy.minusDays(30); // último mes completo
            LocalDate finFuturo   = hoy.plusDays(35);  // próximo mes aproximado

            // ── Asignaciones + fichajes pasados ───────────────────────────────────
            crearAsignacionesYFichajesPasados(empleados, turnos, inicioMes, hoy);

            // ── Asignaciones futuras ──────────────────────────────────────────────
            crearAsignacionesFuturas(empleados, turnos, hoy, finFuturo);

            // ── Ausencias ─────────────────────────────────────────────────────────
            crearAusencias(empleados);

            log.info("[DataSeeder] ¡Ecosistema de pruebas inyectado con éxito!");
            log.info("Credenciales de prueba (Password para todos: 123456):");
            log.info("   - EMPRESA    (Login Empresa) : admin@tech.com");
            log.info("   - SUPERVISOR (Login Empleado): super@tech.com");
            log.info("   - EMPLEADO   (Login Empleado): empleado@tech.com");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMPRESAS
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Empresa> crearEmpresas() {
        Empresa tech = Empresa.builder()
                .email("admin@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Tech Solutions S.L.")
                .direccion("Calle Gran Vía 28, Madrid")
                .telefono("912345678")
                .latitudSede(LAT_SEDE)
                .longitudSede(LON_SEDE)
                .radioValidez(500)
                .build();

        Empresa fantasma = Empresa.builder()
                .email("admin@fantasma.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Empresa Fantasma S.L.")
                .direccion("Av. Olvido 404")
                .telefono("000000000")
                .build();

        return empresaRepository.saveAll(List.of(tech, fantasma));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TURNOS
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Turno> crearTurnos(Empresa empresa) {
        Turno manana = Turno.builder()
                .empresa(empresa).descripcion("Turno de Mañana")
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(16, 0))
                .build();

        Turno tarde = Turno.builder()
                .empresa(empresa).descripcion("Turno de Tarde")
                .horaInicio(LocalTime.of(16, 0)).horaFin(LocalTime.of(0, 0))
                .build();

        Turno mediodia = Turno.builder()
                .empresa(empresa).descripcion("Turno de Mediodía")
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(18, 0))
                .build();

        Turno intensiva = Turno.builder()
                .empresa(empresa).descripcion("Jornada Intensiva")
                .horaInicio(LocalTime.of(7, 0)).horaFin(LocalTime.of(15, 0))
                .build();

        Turno mediaJornada = Turno.builder()
                .empresa(empresa).descripcion("Media Jornada Mañana")
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(12, 0))
                .build();

        return turnoRepository.saveAll(List.of(manana, tarde, mediodia, intensiva, mediaJornada));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMPLEADOS
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Empleado> crearEmpleados(Empresa empresa) {
        List<Empleado> lista = new ArrayList<>();

        // ── IT ────────────────────────────────────────────────────────────────
        lista.add(Empleado.builder()
                .empresa(empresa).email("super@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Carlos").apellidos("Jefe")
                .puesto("Team Lead IT").departamento("IT")
                .telefono("600111001")
                .rol(RolEmpleado.SUPERVISOR).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("empleado@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Juan").apellidos("Pérez")
                .puesto("Desarrollador Junior").departamento("IT")
                .telefono("600111002")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("ana.garcia@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Ana").apellidos("García")
                .puesto("Desarrolladora Senior").departamento("IT")
                .telefono("600111003")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("roberto.diaz@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Roberto").apellidos("Díaz")
                .puesto("Técnico de Soporte").departamento("IT")
                .telefono("600111004")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        // ── Marketing ─────────────────────────────────────────────────────────
        lista.add(Empleado.builder()
                .empresa(empresa).email("laura.sanchez@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Laura").apellidos("Sánchez")
                .puesto("Directora de Marketing").departamento("Marketing")
                .telefono("600222001")
                .rol(RolEmpleado.SUPERVISOR).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("pedro.martinez@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Pedro").apellidos("Martínez")
                .puesto("Especialista en SEO").departamento("Marketing")
                .telefono("600222002")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("miguel.torres@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Miguel").apellidos("Torres")
                .puesto("Diseñador Gráfico").departamento("Marketing")
                .telefono("600222003")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        // ── RRHH ──────────────────────────────────────────────────────────────
        lista.add(Empleado.builder()
                .empresa(empresa).email("elena.romero@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Elena").apellidos("Romero")
                .puesto("Directora de RRHH").departamento("RRHH")
                .telefono("600333001")
                .rol(RolEmpleado.SUPERVISOR).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("sofia.navarro@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Sofía").apellidos("Navarro")
                .puesto("Técnica de Selección").departamento("RRHH")
                .telefono("600333002")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        // David Molina — baja programada en 60 días (badge "Baja programada" en escritorio)
        lista.add(Empleado.builder()
                .empresa(empresa).email("david.molina@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("David").apellidos("Molina")
                .puesto("Administrativo de RRHH").departamento("RRHH")
                .telefono("600333003")
                .rol(RolEmpleado.EMPLEADO).activo(true)
                .fechaBajaContrato(LocalDate.now().plusDays(60))
                .build());

        // ── Contabilidad ──────────────────────────────────────────────────────
        lista.add(Empleado.builder()
                .empresa(empresa).email("marcos.ruiz@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Marcos").apellidos("Ruiz")
                .puesto("Jefe de Contabilidad").departamento("Contabilidad")
                .telefono("600444001")
                .rol(RolEmpleado.SUPERVISOR).activo(true).build());

        lista.add(Empleado.builder()
                .empresa(empresa).email("isabel.castro@tech.com")
                .password(passwordEncoder.encode("123456"))
                .nombre("Isabel").apellidos("Castro")
                .puesto("Contable Senior").departamento("Contabilidad")
                .telefono("600444002")
                .rol(RolEmpleado.EMPLEADO).activo(true).build());

        return empleadoRepository.saveAll(lista);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASIGNACIONES + FICHAJES PASADOS (desde inicioMes hasta ayer inclusive)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Perfil de retrasos por empleado (índice = posición en la lista de empleados).
     * Valor: número de días con retraso que se distribuyen a lo largo del mes.
     * 0=Carlos, 1=Juan, 2=Ana, 3=Roberto, 4=Laura, 5=Pedro, 6=Miguel,
     * 7=Elena, 8=Sofía, 9=David, 10=Marcos, 11=Isabel
     */
    private static final int[] RETRASOS_POR_EMPLEADO = {1, 3, 0, 7, 1, 4, 0, 1, 5, 0, 3, 6};

    private void crearAsignacionesYFichajesPasados(
            List<Empleado> empleados, List<Turno> turnos,
            LocalDate desde, LocalDate hoy) {

        Turno tManana    = turnos.get(0);
        Turno tTarde     = turnos.get(1);
        Turno tMediodia  = turnos.get(2);
        Turno tIntensiva = turnos.get(3);
        Turno tMediaJorn = turnos.get(4);

        // Contador de retrasos acumulados por empleado para distribuirlos uniformemente
        int[] retrasosRestantes = RETRASOS_POR_EMPLEADO.clone();
        // Días laborables pasados totales (para calcular frecuencia de retraso)
        long diasLaborablesPasados = desde.datesUntil(hoy)
                .filter(d -> !esFinde(d)).count();

        for (LocalDate fecha = desde; fecha.isBefore(hoy); fecha = fecha.plusDays(1)) {

            // Solo días laborables (lunes a viernes)
            if (esFinde(fecha)) continue;

            for (int i = 0; i < empleados.size(); i++) {
                Empleado emp = empleados.get(i);

                // Determinar turno según departamento y día de semana
                Turno turnoDelDia = elegirTurno(emp, fecha, tManana, tTarde, tMediodia, tIntensiva, tMediaJorn);

                // Modalidad: viernes → 80% teletrabajo, resto → 30% teletrabajo (determinista por índice+día)
                ModalidadTurno modalidad = elegirModalidad(i, fecha);

                // Comprobar si este día cae dentro de una ausencia aprobada del empleado
                // (las ausencias se crean después, pero las fechas son conocidas — las excluimos manualmente)
                if (estaDeAusenciaAprobada(i, fecha)) continue;

                // Crear asignación
                AsignacionTurno asig = AsignacionTurno.builder()
                        .empleado(emp).turno(turnoDelDia).fecha(fecha).modalidad(modalidad)
                        .build();
                asig = asignacionRepository.save(asig);

                // Crear fichaje (excepto para Roberto el día que "olvidó fichar" — índice 3, día 15 del rango)
                // y excepto el día de hoy (hoy solo tiene entrada abierta, se gestiona por separado)
                boolean olvidoFichar = (i == 3 && esElDiaOlvidado(fecha, desde))
                        || (i == 2 && esElDiaOlvidadoAna(fecha, desde));
                if (olvidoFichar) continue;

                crearFichaje(emp, asig, fecha, turnoDelDia, modalidad, i, retrasosRestantes, diasLaborablesPasados);
            }
        }

        // ── Fichaje de HOY: solo entrada abierta para Juan Pérez (índice 1) ──
        // Juan tiene asignación hoy (Turno Mañana, Presencial)
        Empleado juan = empleados.get(1);
        AsignacionTurno asigHoyJuan = asignacionRepository.save(
                AsignacionTurno.builder()
                        .empleado(juan).turno(tManana).fecha(hoy).modalidad(ModalidadTurno.PRESENCIAL)
                        .build());
        // Entrada puntual hoy
        fichajeRepository.save(Fichaje.builder()
                .empleado(juan).asignacion(asigHoyJuan).fecha(hoy)
                .horaEntrada(hoy.atTime(8, 3)
                        .atZone(ZoneId.of("Europe/Madrid"))
                        .toOffsetDateTime())
                .latitudEntrada(LAT_SEDE + OFFSETS_GPS[1][0])
                .longitudEntrada(LON_SEDE + OFFSETS_GPS[1][1])
                .build());

        // Roberto Díaz (índice 3) tiene asignación hoy pero NO fichaje (olvidó fichar)
        Empleado roberto = empleados.get(3);
        asignacionRepository.save(
                AsignacionTurno.builder()
                        .empleado(roberto).turno(tManana).fecha(hoy).modalidad(ModalidadTurno.PRESENCIAL)
                        .build());

        // El resto de empleados también tienen asignación hoy
        for (int i = 0; i < empleados.size(); i++) {
            if (i == 1 || i == 3) continue; // Juan y Roberto ya tratados
            Empleado emp = empleados.get(i);
            if (estaDeAusenciaAprobada(i, hoy)) continue;
            Turno turnoHoy = elegirTurno(emp, hoy, tManana, tTarde, tMediodia, tIntensiva, tMediaJorn);
            ModalidadTurno modalidadHoy = elegirModalidad(i, hoy);
            AsignacionTurno asigHoy = asignacionRepository.save(
                    AsignacionTurno.builder()
                            .empleado(emp).turno(turnoHoy).fecha(hoy).modalidad(modalidadHoy)
                            .build());
            // Fichaje de entrada abierto para todos los demás
            fichajeRepository.save(Fichaje.builder()
                    .empleado(emp).asignacion(asigHoy).fecha(hoy)
                    .horaEntrada(hoy.atTime(turnoHoy.getHoraInicio().getHour(),
                                    turnoHoy.getHoraInicio().getMinute() + (i % 3))
                            .atZone(ZoneId.of("Europe/Madrid"))
                            .toOffsetDateTime())
                    .latitudEntrada(LAT_SEDE + OFFSETS_GPS[Math.min(i, OFFSETS_GPS.length - 1)][0])
                    .longitudEntrada(LON_SEDE + OFFSETS_GPS[Math.min(i, OFFSETS_GPS.length - 1)][1])
                    .build());
        }
    }

    /**
     * Crea un fichaje completo (entrada + salida) para un día pasado.
     * Distribuye retrasos de forma determinista según el perfil de cada empleado.
     */
    private void crearFichaje(Empleado emp, AsignacionTurno asig, LocalDate fecha,
                              Turno turno, ModalidadTurno modalidad,
                              int idxEmpleado, int[] retrasosRestantes, long diasLaborablesPasados) {

        double latOffset = OFFSETS_GPS[Math.min(idxEmpleado, OFFSETS_GPS.length - 1)][0];
        double lonOffset = OFFSETS_GPS[Math.min(idxEmpleado, OFFSETS_GPS.length - 1)][1];

        // ── Calcular hora de entrada ───────────────────────────────────────────
        LocalTime inicioTurno = turno.getHoraInicio();
        OffsetDateTime horaEntrada;
        String incidencias = null;

        // ¿Toca un retraso hoy? Distribución determinista: cada N días laborables
        boolean esRetraso = false;
        if (retrasosRestantes[idxEmpleado] > 0) {
            // Usar hash determinista: día del año + índice para decidir si este día tiene retraso
            int hashDia = (fecha.getDayOfYear() * 7 + idxEmpleado * 13) % 100;
            int umbralRetraso = (int) ((retrasosRestantes[idxEmpleado] * 100) / Math.max(diasLaborablesPasados - 5, 1));
            if (hashDia < Math.min(umbralRetraso, 60)) {
                esRetraso = true;
                retrasosRestantes[idxEmpleado]--;
            }
        }

        if (esRetraso) {
            // Retraso entre 20 y 45 minutos (supera los 15 de cortesía)
            int minutosRetraso = 20 + ((idxEmpleado * fecha.getDayOfMonth() * 3) % 26);
            horaEntrada = fecha.atTime(inicioTurno.plusMinutes(minutosRetraso))
                    .atZone(ZoneId.of("Europe/Madrid"))
                    .toOffsetDateTime();
            incidencias = "Retraso en la entrada. Fichó a las " + horaEntrada.toLocalTime() + ". ";
        } else {
            // Entrada puntual: entre -3 y +5 minutos del inicio del turno (determinista)
            int desfase = ((idxEmpleado + fecha.getDayOfMonth()) % 9) - 3;
            horaEntrada = fecha.atTime(inicioTurno.plusMinutes(desfase))
                    .atZone(ZoneId.of("Europe/Madrid"))
                    .toOffsetDateTime();
        }

        // ── Calcular hora de salida ────────────────────────────────────────────
        LocalTime finTurno = turno.getHoraFin();
        OffsetDateTime horaSalida;

        // El turno de tarde tiene horaFin = 00:00, que en LocalTime es medianoche del mismo día.
        // Para calcular la salida lo tratamos como 23:59 del mismo día más 1 min.
        boolean esTurnoNocturno = finTurno.equals(LocalTime.of(0, 0));

        if (esTurnoNocturno) {
            // Salida entre 23:55 y 00:05 del día siguiente — simplificamos a 23:58
            horaSalida = fecha.atTime(23, 58)
                    .atZone(ZoneId.of("Europe/Madrid"))
                    .toOffsetDateTime();
        } else {
            // Salida puntual: entre -2 y +8 minutos del fin del turno
            int desfaseSalida = ((idxEmpleado * 3 + fecha.getDayOfMonth()) % 11) - 2;
            // Comprobar salida anticipada (5% de los casos — determinista)
            boolean salidaAnticipada = ((idxEmpleado + fecha.getDayOfYear()) % 20 == 0);
            if (salidaAnticipada) {
                int minutosAntes = 20 + (idxEmpleado % 11);
                horaSalida = fecha.atTime(finTurno.minusMinutes(minutosAntes))
                        .atZone(ZoneId.of("Europe/Madrid"))
                        .toOffsetDateTime();
                String incSalida = "Salida anticipada a las " + horaSalida.toLocalTime() + ". ";
                incidencias = (incidencias == null) ? incSalida : incidencias + " | " + incSalida;
            } else {
                horaSalida = fecha.atTime(finTurno.plusMinutes(desfaseSalida))
                        .atZone(ZoneId.of("Europe/Madrid"))
                        .toOffsetDateTime();
            }
        }

        // ── Coordenadas GPS (solo para turnos presenciales) ───────────────────
        Double latE = null, lonE = null, latS = null, lonS = null;
        if (modalidad == ModalidadTurno.PRESENCIAL) {
            latE = LAT_SEDE + latOffset;
            lonE = LON_SEDE + lonOffset;
            latS = LAT_SEDE + latOffset;
            lonS = LON_SEDE + lonOffset;
        }

        fichajeRepository.save(Fichaje.builder()
                .empleado(emp).asignacion(asig).fecha(fecha)
                .horaEntrada(horaEntrada)
                .latitudEntrada(latE).longitudEntrada(lonE)
                .horaSalida(horaSalida)
                .latitudSalida(latS).longitudSalida(lonS)
                .incidencias(incidencias)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASIGNACIONES FUTURAS (desde mañana hasta finFuturo)
    // ═══════════════════════════════════════════════════════════════════════════

    private void crearAsignacionesFuturas(List<Empleado> empleados, List<Turno> turnos,
                                          LocalDate hoy, LocalDate hasta) {
        Turno tManana    = turnos.get(0);
        Turno tTarde     = turnos.get(1);
        Turno tMediodia  = turnos.get(2);
        Turno tIntensiva = turnos.get(3);
        Turno tMediaJorn = turnos.get(4);

        LocalDate manana = hoy.plusDays(1);

        for (LocalDate fecha = manana; fecha.isBefore(hasta); fecha = fecha.plusDays(1)) {
            if (esFinde(fecha)) continue;

            for (int i = 0; i < empleados.size(); i++) {
                Empleado emp = empleados.get(i);

                // No asignar turnos en fechas de ausencias aprobadas futuras
                if (estaDeAusenciaAprobada(i, fecha)) continue;

                Turno turnoDelDia = elegirTurno(emp, fecha, tManana, tTarde, tMediodia, tIntensiva, tMediaJorn);
                ModalidadTurno modalidad = elegirModalidad(i, fecha);

                asignacionRepository.save(AsignacionTurno.builder()
                        .empleado(emp).turno(turnoDelDia).fecha(fecha).modalidad(modalidad)
                        .build());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUSENCIAS
    // ═══════════════════════════════════════════════════════════════════════════

    private void crearAusencias(List<Empleado> empleados) {
        LocalDate hoy = LocalDate.now();

        Empleado carlos   = empleados.get(0);  // SUPERVISOR IT
        Empleado juan     = empleados.get(1);  // EMPLEADO IT        (app Android)
        Empleado ana      = empleados.get(2);  // EMPLEADO IT
        Empleado roberto  = empleados.get(3);  // EMPLEADO IT
        Empleado laura    = empleados.get(4);  // SUPERVISOR Marketing
        Empleado pedro    = empleados.get(5);  // EMPLEADO Marketing
        Empleado miguel   = empleados.get(6);  // EMPLEADO Marketing
        Empleado elena    = empleados.get(7);  // SUPERVISOR RRHH
        Empleado sofia    = empleados.get(8);  // EMPLEADO RRHH
        Empleado david    = empleados.get(9);  // EMPLEADO RRHH
        Empleado marcos   = empleados.get(10); // SUPERVISOR Contabilidad
        Empleado isabel   = empleados.get(11); // EMPLEADO Contabilidad

        List<Ausencia> ausencias = new ArrayList<>();

        // ── PASADAS APROBADAS ─────────────────────────────────────────────────

        // Juan Pérez — vacaciones de verano (ya aprobadas)
        ausencias.add(Ausencia.builder()
                .empleado(juan).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones de verano")
                .fechaInicio(hoy.minusDays(20)).fechaFin(hoy.minusDays(16))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(carlos.getNombre() + " " + carlos.getApellidos())
                .observacionesRevision("Aprobadas correctamente")
                .build());

        // Ana García — motivo personal (aprobada con observaciones)
        ausencias.add(Ausencia.builder()
                .empleado(ana).tipo(TipoAusencia.MOTIVO_PERSONAL)
                .descripcion("Trámite en el registro civil")
                .fechaInicio(hoy.minusDays(18)).fechaFin(hoy.minusDays(17))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(carlos.getNombre() + " " + carlos.getApellidos())
                .observacionesRevision("Trámite personal justificado")
                .build());

        // Pedro Martínez — médica RECHAZADA (no aportó justificante)
        ausencias.add(Ausencia.builder()
                .empleado(pedro).tipo(TipoAusencia.MEDICA)
                .descripcion("Visita al especialista")
                .fechaInicio(hoy.minusDays(14)).fechaFin(hoy.minusDays(12))
                .estado(EstadoAusencia.RECHAZADA)
                .responsableRevision(laura.getNombre() + " " + laura.getApellidos())
                .observacionesRevision("Solicitud rechazada: no se aportó justificante médico en el plazo establecido")
                .build());

        // Roberto Díaz — médica aprobada
        ausencias.add(Ausencia.builder()
                .empleado(roberto).tipo(TipoAusencia.MEDICA)
                .descripcion("Baja por gripe")
                .fechaInicio(hoy.minusDays(10)).fechaFin(hoy.minusDays(8))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(carlos.getNombre() + " " + carlos.getApellidos())
                .observacionesRevision("Justificante médico aportado correctamente")
                .build());

        // Sofía Navarro — motivo personal aprobada
        ausencias.add(Ausencia.builder()
                .empleado(sofia).tipo(TipoAusencia.MOTIVO_PERSONAL)
                .descripcion("Asuntos familiares")
                .fechaInicio(hoy.minusDays(22)).fechaFin(hoy.minusDays(21))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(elena.getNombre() + " " + elena.getApellidos())
                .observacionesRevision("Aprobado")
                .build());

        // Isabel Castro — vacaciones aprobadas
        ausencias.add(Ausencia.builder()
                .empleado(isabel).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones de verano")
                .fechaInicio(hoy.minusDays(25)).fechaFin(hoy.minusDays(20))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(marcos.getNombre() + " " + marcos.getApellidos())
                .observacionesRevision("Disfrutadas correctamente")
                .build());

        // Marcos Ruiz — médica aprobada
        ausencias.add(Ausencia.builder()
                .empleado(marcos).tipo(TipoAusencia.MEDICA)
                .descripcion("Intervención programada")
                .fechaInicio(hoy.minusDays(7)).fechaFin(hoy.minusDays(6))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision("admin@tech.com")
                .observacionesRevision("Baja médica justificada")
                .build());

        // Laura Sánchez — médica pasada aprobada
        ausencias.add(Ausencia.builder()
                .empleado(laura).tipo(TipoAusencia.MEDICA)
                .descripcion("Revisión médica anual")
                .fechaInicio(hoy.minusDays(3)).fechaFin(hoy.minusDays(3))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision("admin@tech.com")
                .observacionesRevision("Aprobada")
                .build());

        // ── PENDIENTES (bandeja llena para el dashboard de escritorio) ─────────

        // Juan Pérez — baja médica futura (SOLICITADA)
        ausencias.add(Ausencia.builder()
                .empleado(juan).tipo(TipoAusencia.MEDICA)
                .descripcion("Operación médica programada")
                .fechaInicio(hoy.plusDays(5)).fechaFin(hoy.plusDays(10))
                .estado(EstadoAusencia.SOLICITADA)
                .build());

        // Miguel Torres — vacaciones futuras (SOLICITADA)
        ausencias.add(Ausencia.builder()
                .empleado(miguel).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones de verano pendientes")
                .fechaInicio(hoy.plusDays(3)).fechaFin(hoy.plusDays(8))
                .estado(EstadoAusencia.SOLICITADA)
                .build());

        // David Molina — motivo personal próximo (SOLICITADA)
        ausencias.add(Ausencia.builder()
                .empleado(david).tipo(TipoAusencia.MOTIVO_PERSONAL)
                .descripcion("Gestión bancaria urgente")
                .fechaInicio(hoy.plusDays(2)).fechaFin(hoy.plusDays(3))
                .estado(EstadoAusencia.SOLICITADA)
                .build());

        // Pedro Martínez — nueva solicitud médica (SOLICITADA) tras la rechazada
        ausencias.add(Ausencia.builder()
                .empleado(pedro).tipo(TipoAusencia.MEDICA)
                .descripcion("Seguimiento médico con justificante")
                .fechaInicio(hoy.plusDays(1)).fechaFin(hoy.plusDays(2))
                .estado(EstadoAusencia.SOLICITADA)
                .build());

        // ── FUTURAS APROBADAS (planificadas — sin asignación de turno esas fechas) ──

        // Ana García — vacaciones futuras aprobadas
        ausencias.add(Ausencia.builder()
                .empleado(ana).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones de agosto")
                .fechaInicio(hoy.plusDays(15)).fechaFin(hoy.plusDays(22))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(carlos.getNombre() + " " + carlos.getApellidos())
                .observacionesRevision("Planificadas con antelación suficiente")
                .build());

        // Elena Romero — vacaciones futuras aprobadas
        ausencias.add(Ausencia.builder()
                .empleado(elena).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones programadas")
                .fechaInicio(hoy.plusDays(20)).fechaFin(hoy.plusDays(27))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision("admin@tech.com")
                .observacionesRevision("Aprobadas")
                .build());

        // Roberto Díaz — motivo personal futuro aprobado
        ausencias.add(Ausencia.builder()
                .empleado(roberto).tipo(TipoAusencia.MOTIVO_PERSONAL)
                .descripcion("Mudanza")
                .fechaInicio(hoy.plusDays(10)).fechaFin(hoy.plusDays(11))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(carlos.getNombre() + " " + carlos.getApellidos())
                .observacionesRevision("Aprobado")
                .build());

        // Isabel Castro — vacaciones futuras aprobadas
        ausencias.add(Ausencia.builder()
                .empleado(isabel).tipo(TipoAusencia.VACACIONES)
                .descripcion("Vacaciones de verano segunda parte")
                .fechaInicio(hoy.plusDays(30)).fechaFin(hoy.plusDays(37))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision(marcos.getNombre() + " " + marcos.getApellidos())
                .observacionesRevision("Aprobadas correctamente")
                .build());

        // Marcos Ruiz — otros motivos futuros aprobados
        ausencias.add(Ausencia.builder()
                .empleado(marcos).tipo(TipoAusencia.OTROS)
                .descripcion("Formación externa obligatoria")
                .fechaInicio(hoy.plusDays(12)).fechaFin(hoy.plusDays(13))
                .estado(EstadoAusencia.APROBADA)
                .responsableRevision("admin@tech.com")
                .observacionesRevision("Formación bonificada aprobada")
                .build());

        ausenciaRepository.saveAll(ausencias);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Devuelve true si la fecha es sábado o domingo. */
    private boolean esFinde(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SATURDAY
                || fecha.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Selecciona el turno del día según el departamento del empleado y el día de la semana.
     *
     * IT:            rotan Mañana / Mediodía; viernes siempre Mediodía
     * Marketing:     Mediodía principalmente; lunes y miércoles Mañana
     * RRHH:          Mañana fija; viernes Jornada Intensiva
     * Contabilidad:  Mañana e Intensiva alternas; viernes Media Jornada
     * Supervisores:  Mediodía (perfil directivo)
     */
    private Turno elegirTurno(Empleado emp, LocalDate fecha,
                              Turno manana, Turno tarde, Turno mediodia,
                              Turno intensiva, Turno mediaJornada) {
        DayOfWeek dow = fecha.getDayOfWeek();
        boolean esViernes = (dow == DayOfWeek.FRIDAY);

        // Supervisores siempre en Mediodía
        if (emp.getRol() == RolEmpleado.SUPERVISOR) {
            return mediodia;
        }

        return switch (emp.getDepartamento()) {
            case "IT" -> esViernes ? mediodia
                    : (fecha.getDayOfMonth() % 2 == 0 ? manana : mediodia);

            case "Marketing" -> (dow == DayOfWeek.MONDAY || dow == DayOfWeek.WEDNESDAY)
                    ? manana
                    : (dow == DayOfWeek.TUESDAY || dow == DayOfWeek.THURSDAY)
                    ? tarde : mediodia;

            case "RRHH" -> esViernes ? intensiva : manana;

            case "Contabilidad" -> esViernes ? mediaJornada
                    : (fecha.getDayOfMonth() % 3 == 0 ? intensiva : manana);

            default -> manana;
        };
    }

    /**
     * Determina la modalidad de forma determinista.
     * Viernes: 80% teletrabajo (índice par → teletrabajo).
     * Resto:   30% teletrabajo (índice del empleado + día divisible por 3).
     */
    private ModalidadTurno elegirModalidad(int idxEmpleado, LocalDate fecha) {
        if (fecha.getDayOfWeek() == DayOfWeek.FRIDAY) {
            return (idxEmpleado % 5 != 0) ? ModalidadTurno.TELETRABAJO : ModalidadTurno.PRESENCIAL;
        }
        return ((idxEmpleado + fecha.getDayOfMonth()) % 3 == 0)
                ? ModalidadTurno.TELETRABAJO : ModalidadTurno.PRESENCIAL;
    }

    /**
     * Devuelve true si el empleado (por índice) tiene una ausencia APROBADA
     * que cubre la fecha indicada. Se usa para no crear asignaciones ni fichajes
     * en esas fechas, manteniendo la coherencia con las ausencias del seeder.
     *
     * Mapa de ausencias aprobadas por índice de empleado:
     *  1 (Juan)    : hoy-20 → hoy-16
     *  2 (Ana)     : hoy-18 → hoy-17 | hoy+15 → hoy+22
     *  3 (Roberto) : hoy-10 → hoy-8  | hoy+10 → hoy+11
     *  5 (Pedro)   : (rechazada — no excluir)
     *  8 (Sofía)   : hoy-22 → hoy-21
     * 10 (Marcos)  : hoy-7  → hoy-6  | hoy+12 → hoy+13
     * 11 (Isabel)  : hoy-25 → hoy-20 | hoy+30 → hoy+37
     *  4 (Laura)   : hoy-3  → hoy-3
     *  7 (Elena)   : hoy+20 → hoy+27
     */
    private boolean estaDeAusenciaAprobada(int idxEmpleado, LocalDate fecha) {
        LocalDate hoy = LocalDate.now();

        return switch (idxEmpleado) {
            case 1 -> !fecha.isBefore(hoy.minusDays(20)) && !fecha.isAfter(hoy.minusDays(16));
            case 2 -> (!fecha.isBefore(hoy.minusDays(18)) && !fecha.isAfter(hoy.minusDays(17)))
                    || (!fecha.isBefore(hoy.plusDays(15)) && !fecha.isAfter(hoy.plusDays(22)));
            case 3 -> (!fecha.isBefore(hoy.minusDays(10)) && !fecha.isAfter(hoy.minusDays(8)))
                    || (!fecha.isBefore(hoy.plusDays(10)) && !fecha.isAfter(hoy.plusDays(11)));
            case 4 -> fecha.equals(hoy.minusDays(3));
            case 7 -> !fecha.isBefore(hoy.plusDays(20)) && !fecha.isAfter(hoy.plusDays(27));
            case 8 -> !fecha.isBefore(hoy.minusDays(22)) && !fecha.isAfter(hoy.minusDays(21));
            case 10 -> (!fecha.isBefore(hoy.minusDays(7)) && !fecha.isAfter(hoy.minusDays(6)))
                    || (!fecha.isBefore(hoy.plusDays(12)) && !fecha.isAfter(hoy.plusDays(13)));
            case 11 -> (!fecha.isBefore(hoy.minusDays(25)) && !fecha.isAfter(hoy.minusDays(20)))
                    || (!fecha.isBefore(hoy.plusDays(30)) && !fecha.isAfter(hoy.plusDays(37)));
            default -> false;
        };
    }

    /**
     * Día del mes en que Roberto Díaz "olvidó fichar".
     * Usamos el día 12 del rango (desde + 12 días laborables aprox.).
     */
    private boolean esElDiaOlvidado(LocalDate fecha, LocalDate desde) {
        // Contamos el 9º día laborable desde el inicio del rango
        int contador = 0;
        LocalDate cursor = desde;
        while (!cursor.isAfter(fecha)) {
            if (!esFinde(cursor)) contador++;
            if (contador == 9 && cursor.equals(fecha)) return true;
            cursor = cursor.plusDays(1);
        }
        return false;
    }

    /**
     * Día en que Ana García olvidó fichar (el 14º día laborable del rango).
     */
    private boolean esElDiaOlvidadoAna(LocalDate fecha, LocalDate desde) {
        int contador = 0;
        LocalDate cursor = desde;
        while (!cursor.isAfter(fecha)) {
            if (!esFinde(cursor)) contador++;
            if (contador == 14 && cursor.equals(fecha)) return true;
            cursor = cursor.plusDays(1);
        }
        return false;
    }
}
