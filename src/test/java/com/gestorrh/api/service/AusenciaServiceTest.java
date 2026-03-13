package com.gestorrh.api.service;

import com.gestorrh.api.dto.ausenciaDTO.PeticionAusenciaDTO;
import com.gestorrh.api.dto.ausenciaDTO.PeticionRevisionAusenciaDTO;
import com.gestorrh.api.dto.ausenciaDTO.RespuestaAusenciaDTO;
import com.gestorrh.api.entity.*;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.TipoAusencia;
import com.gestorrh.api.repository.AsignacionTurnoRepository;
import com.gestorrh.api.repository.AusenciaRepository;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AusenciaServiceTest {

    @Mock
    private AusenciaRepository ausenciaRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private AsignacionTurnoRepository asignacionRepository;

    @InjectMocks
    private AusenciaService ausenciaService;

    private Empresa empresaPrueba;
    private Empleado empleadoLogueado;
    private final String EMAIL_EMPLEADO = "juan@empresa.com";
    private final String EMAIL_EMPRESA = "admin@empresa.com";

    @BeforeEach
    void setUp() {
        empresaPrueba = new Empresa();
        empresaPrueba.setIdEmpresa(1L);

        empleadoLogueado = Empleado.builder()
                .idEmpleado(10L)
                .empresa(empresaPrueba)
                .departamento("IT")
                .nombre("Juan")
                .email(EMAIL_EMPLEADO)
                .build();
    }

    private void simularAutenticacion(String email, String rol) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(email);
        GrantedAuthority authority = mock(GrantedAuthority.class);
        lenient().when(authority.getAuthority()).thenReturn(rol);
        lenient().doReturn(Collections.singletonList(authority)).when(auth).getAuthorities();
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    @DisplayName("Crear ausencia falla si las fechas se solapan con otra existente")
    void crearAusencia_FallaPorSolapamiento() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.VACACIONES)
                .fechaInicio(LocalDate.of(2026, 10, 20))
                .fechaFin(LocalDate.of(2026, 10, 25))
                .build();

        Ausencia ausenciaSolapada = Ausencia.builder().idAusencia(99L).build();
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any()))
                .thenReturn(List.of(ausenciaSolapada));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ausenciaService.crearAusencia(peticion));
        assertTrue(ex.getMessage().contains("ya tienes otra solicitud"));
    }

    @Test
    @DisplayName("Editar ausencia falla si ya no está en estado SOLICITADA")
    void actualizarMiAusencia_FallaSiNoEstaSolicitada() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));

        Ausencia ausenciaAprobada = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.APROBADA).build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaAprobada));

        PeticionAusenciaDTO peticion = new PeticionAusenciaDTO();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ausenciaService.actualizarMiAusencia(1L, peticion));
        assertTrue(ex.getMessage().contains("Solo puedes modificar una ausencia que esté en estado SOLICITADA"));
    }

    @Test
    @DisplayName("Aprobar una ausencia borra los turnos solapados del empleado")
    void revisarAusencia_AprobadaBorraTurnos() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");
        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));

        Ausencia ausenciaPendiente = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.SOLICITADA)
                .fechaInicio(LocalDate.of(2026, 10, 20)).fechaFin(LocalDate.of(2026, 10, 25)).build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaPendiente));
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        AsignacionTurno turno1 = new AsignacionTurno();
        AsignacionTurno turno2 = new AsignacionTurno();
        when(asignacionRepository.findByEmpleadoIdEmpleadoAndFechaBetween(10L, ausenciaPendiente.getFechaInicio(), ausenciaPendiente.getFechaFin()))
                .thenReturn(List.of(turno1, turno2));

        PeticionRevisionAusenciaDTO peticion = PeticionRevisionAusenciaDTO.builder()
                .estado(EstadoAusencia.APROBADA).build();

        RespuestaAusenciaDTO respuesta = ausenciaService.revisarAusencia(1L, peticion);

        assertEquals(EstadoAusencia.APROBADA, respuesta.getEstado());
        verify(asignacionRepository, times(1)).deleteAll(anyList());
    }

    @Test
    @DisplayName("Rechazar una ausencia exige motivo de revisión")
    void revisarAusencia_RechazadaExigeMotivo() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");
        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));

        Ausencia ausenciaPendiente = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.SOLICITADA).build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaPendiente));

        PeticionRevisionAusenciaDTO peticion = PeticionRevisionAusenciaDTO.builder()
                .estado(EstadoAusencia.RECHAZADA).observacionesRevision("").build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ausenciaService.revisarAusencia(1L, peticion));
        assertTrue(ex.getMessage().contains("Debes proporcionar observaciones"));
        verify(ausenciaRepository, never()).save(any());
    }
}
