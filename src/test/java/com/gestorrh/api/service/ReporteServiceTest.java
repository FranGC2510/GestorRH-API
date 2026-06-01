package com.gestorrh.api.service;

import com.gestorrh.api.dto.reporte.ReporteDetalleDTO;
import com.gestorrh.api.entity.AsignacionTurno;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Fichaje;
import com.gestorrh.api.entity.Turno;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.FichajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock private FichajeRepository fichajeRepository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks
    private ReporteService reporteService;

    private Empleado empleadoPrueba;

    @BeforeEach
    void setUp() {
        empresaPrueba = Empresa.builder().idEmpresa(1L).build();
        empleadoPrueba = Empleado.builder()
                .idEmpleado(1L)
                .email("empleado@test.com")
                .empresa(empresaPrueba)
                .nombre("Test").apellidos("Empleado")
                .build();

        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn("empleado@test.com");

        lenient().when(auth.getAuthorities()).thenReturn(List.of());
        SecurityContextHolder.setContext(ctx);

        lenient().when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
    }

    private Empresa empresaPrueba;

    @Test
    @DisplayName("Calcula 0 horas extra si se respeta la cortesía (10 min de más)")
    void obtenerReporteDetallado_RespetaCortesia() {
        LocalDate hoy = LocalDate.now();
        Turno turno = Turno.builder().horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(15, 0)).build(); // 7 horas = 420 min
        AsignacionTurno asignacion = AsignacionTurno.builder().turno(turno).build();

        Fichaje fichaje = Fichaje.builder()
                .empleado(empleadoPrueba)
                .asignacion(asignacion)
                .fecha(hoy)
                .horaEntrada(hoy.atTime(8, 0).atOffset(ZoneOffset.UTC))
                .horaSalida(hoy.atTime(15, 10).atOffset(ZoneOffset.UTC))
                .build();

        lenient().when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(anyLong(), any(), any()))
                .thenReturn(List.of(fichaje));

        List<ReporteDetalleDTO> resultado = reporteService.obtenerReporteDetallado(hoy, hoy, null);

        assertEquals(1, resultado.size());
        assertEquals(420, resultado.get(0).getTiempoTeoricoMinutos());
        assertEquals(430, resultado.get(0).getTiempoTotalMinutos());
        assertEquals(0, resultado.get(0).getMinutosExtra(), "No debe haber extras por estar en cortesía");
    }

    @Test
    @DisplayName("Calcula horas extra reales si excede la cortesía (30 min de más)")
    void obtenerReporteDetallado_CalculaExtras() {
        LocalDate hoy = LocalDate.now();
        Turno turno = Turno.builder().horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(15, 0)).build();
        AsignacionTurno asignacion = AsignacionTurno.builder().turno(turno).build();

        Fichaje fichaje = Fichaje.builder()
                .empleado(empleadoPrueba)
                .asignacion(asignacion)
                .fecha(hoy)
                .horaEntrada(hoy.atTime(8, 0).atOffset(ZoneOffset.UTC))
                .horaSalida(hoy.atTime(15, 30).atOffset(ZoneOffset.UTC))
                .build();

        lenient().when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(anyLong(), any(), any()))
                .thenReturn(List.of(fichaje));

        List<ReporteDetalleDTO> resultado = reporteService.obtenerReporteDetallado(hoy, hoy, null);

        assertEquals(1, resultado.size());
        assertEquals(30, resultado.get(0).getMinutosExtra(), "Deben sumarse los 30 min extra");
    }
}
