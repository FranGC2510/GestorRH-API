package com.gestorrh.api.service;

import com.gestorrh.api.dto.asignacionDTO.PeticionAsignacionTurnoDTO;
import com.gestorrh.api.dto.asignacionDTO.RespuestaAsignacionTurnoDTO;
import com.gestorrh.api.entity.*;
import com.gestorrh.api.entity.enums.EstadoAusencia;
import com.gestorrh.api.entity.enums.ModalidadTurno;
import com.gestorrh.api.repository.*;
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
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsignacionTurnoServiceTest {

    @Mock
    private AsignacionTurnoRepository asignacionRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private AusenciaRepository ausenciaRepository;

    @InjectMocks
    private AsignacionTurnoService asignacionService;

    private Empresa empresaPrueba;
    private Empleado empleadoDestino;
    private Turno turnoManana;
    private final String EMAIL_EMPRESA = "admin@empresa.com";

    @BeforeEach
    void setUp() {
        empresaPrueba = new Empresa();
        empresaPrueba.setIdEmpresa(1L);

        empleadoDestino = Empleado.builder()
                .idEmpleado(10L)
                .empresa(empresaPrueba)
                .departamento("IT")
                .nombre("Juan").apellidos("Pérez")
                .build();

        turnoManana = Turno.builder()
                .idTurno(100L)
                .empresa(empresaPrueba)
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(15, 0))
                .descripcion("Mañana")
                .build();
        lenient().when(ausenciaRepository.tieneAusenciaAprobadaEnFecha(anyLong(), eq(EstadoAusencia.APROBADA), any()))
                .thenReturn(false);
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
    @DisplayName("La Empresa puede crear una asignación con éxito sin exceder 8h")
    void crearAsignacion_Exito() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");

        PeticionAsignacionTurnoDTO peticion = PeticionAsignacionTurnoDTO.builder()
                .idEmpleado(10L).idTurno(100L).fecha(LocalDate.of(2026, 10, 15)).modalidad(ModalidadTurno.PRESENCIAL)
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleadoDestino));
        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turnoManana));
        when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(10L, peticion.getFecha())).thenReturn(Collections.emptyList());

        when(asignacionRepository.save(any(AsignacionTurno.class))).thenAnswer(i -> i.getArgument(0));

        RespuestaAsignacionTurnoDTO respuesta = asignacionService.crearAsignacion(peticion);

        assertNotNull(respuesta);
        assertEquals("Juan Pérez", respuesta.getNombreCompletoEmpleado());
        verify(asignacionRepository, times(1)).save(any(AsignacionTurno.class));
    }

    @Test
    @DisplayName("Debe fallar si el empleado ya supera las 8 horas en ese día")
    void crearAsignacion_FallaPorExcesoHoras() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");

        PeticionAsignacionTurnoDTO peticion = PeticionAsignacionTurnoDTO.builder()
                .idEmpleado(10L).idTurno(100L).fecha(LocalDate.of(2026, 10, 15)).build();

        Turno turnoExistente = Turno.builder().horaInicio(LocalTime.of(16,0)).horaFin(LocalTime.of(20,0)).build();
        AsignacionTurno asignacionPrevia = AsignacionTurno.builder().turno(turnoExistente).build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleadoDestino));
        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turnoManana)); // Este turno dura 7 horas

        when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(10L, peticion.getFecha())).thenReturn(List.of(asignacionPrevia));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> asignacionService.crearAsignacion(peticion));
        assertTrue(ex.getMessage().contains("El empleado no puede exceder las 8 horas"));
    }

    @Test
    @DisplayName("Un SUPERVISOR no puede asignar turnos a un empleado de OTRO departamento")
    void crearAsignacion_SupervisorFallaPorDepartamento() {
        String emailSupervisor = "super@empresa.com";
        simularAutenticacion(emailSupervisor, "ROLE_EMPLEADO");

        Empleado supervisor = Empleado.builder()
                .empresa(empresaPrueba).departamento("RRHH")
                .build();

        PeticionAsignacionTurnoDTO peticion = PeticionAsignacionTurnoDTO.builder()
                .idEmpleado(10L).idTurno(100L).fecha(LocalDate.now()).build();

        when(empleadoRepository.findById(10L)).thenReturn(Optional.of(empleadoDestino));
        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turnoManana));
        when(empleadoRepository.findByEmail(emailSupervisor)).thenReturn(Optional.of(supervisor));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> asignacionService.crearAsignacion(peticion));
        assertTrue(ex.getMessage().contains("Como supervisor, solo puedes gestionar turnos del departamento"));
    }

    @Test
    @DisplayName("La Empresa obtiene TODAS las asignaciones de su compañía")
    void obtenerAsignacionesPermitidas_ComoEmpresa() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");
        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));

        AsignacionTurno asignacion = AsignacionTurno.builder()
                .idAsignacion(1L).empleado(empleadoDestino).turno(turnoManana).build();

        when(asignacionRepository.findByEmpleadoEmpresaIdEmpresa(empresaPrueba.getIdEmpresa()))
                .thenReturn(List.of(asignacion));

        List<RespuestaAsignacionTurnoDTO> resultado = asignacionService.obtenerAsignacionesPermitidas();

        assertEquals(1, resultado.size());
        verify(asignacionRepository, times(1)).findByEmpleadoEmpresaIdEmpresa(anyLong());
    }

    @Test
    @DisplayName("Un SUPERVISOR obtiene SOLO las asignaciones de su departamento")
    void obtenerAsignacionesPermitidas_ComoSupervisor() {
        String emailSupervisor = "sup@empresa.com";
        simularAutenticacion(emailSupervisor, "ROLE_EMPLEADO");

        Empleado supervisor = Empleado.builder()
                .empresa(empresaPrueba).departamento("IT").build();

        when(empleadoRepository.findByEmail(emailSupervisor)).thenReturn(Optional.of(supervisor));

        AsignacionTurno asignacion = AsignacionTurno.builder()
                .idAsignacion(1L).empleado(empleadoDestino).turno(turnoManana).build();

        when(asignacionRepository.findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(
                empresaPrueba.getIdEmpresa(), "IT")).thenReturn(List.of(asignacion));

        List<RespuestaAsignacionTurnoDTO> resultado = asignacionService.obtenerAsignacionesPermitidas();

        assertEquals(1, resultado.size());
        verify(asignacionRepository, times(1)).findByEmpleadoEmpresaIdEmpresaAndEmpleadoDepartamentoIgnoreCase(anyLong(), anyString());
    }

    @Test
    @DisplayName("Un empleado obtiene exclusivamente sus propios turnos")
    void obtenerMisAsignaciones_Exito() {
        String emailEmpleado = "juan@empresa.com";
        simularAutenticacion(emailEmpleado, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(emailEmpleado)).thenReturn(Optional.of(empleadoDestino));

        AsignacionTurno asignacion = AsignacionTurno.builder()
                .idAsignacion(1L).empleado(empleadoDestino).turno(turnoManana).build();

        when(asignacionRepository.findByEmpleadoIdEmpleado(empleadoDestino.getIdEmpleado()))
                .thenReturn(List.of(asignacion));

        List<RespuestaAsignacionTurnoDTO> resultado = asignacionService.obtenerMisAsignaciones();

        assertEquals(1, resultado.size());
    }

    @Test
    @DisplayName("Actualizar asignación requiere motivoCambio y registra la auditoría correctamente")
    void actualizarAsignacion_Exito() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");

        AsignacionTurno asigExistente = AsignacionTurno.builder()
                .idAsignacion(1L)
                .empleado(empleadoDestino)
                .turno(turnoManana)
                .fecha(LocalDate.now())
                .build();

        PeticionAsignacionTurnoDTO peticion = PeticionAsignacionTurnoDTO.builder()
                .idTurno(100L)
                .fecha(LocalDate.now())
                .modalidad(ModalidadTurno.TELETRABAJO)
                .motivoCambio("Cambio por teletrabajo excepcional")
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));
        when(asignacionRepository.findById(1L)).thenReturn(Optional.of(asigExistente));
        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turnoManana));
        when(asignacionRepository.save(any(AsignacionTurno.class))).thenAnswer(i -> i.getArgument(0));

        RespuestaAsignacionTurnoDTO respuesta = asignacionService.actualizarAsignacion(1L, peticion);

        assertNotNull(respuesta);
        assertEquals("Cambio por teletrabajo excepcional", respuesta.getMotivoCambio());
        assertEquals(EMAIL_EMPRESA, respuesta.getResponsableCambio());
        assertNotNull(respuesta.getFechaCambio());
        assertEquals(ModalidadTurno.TELETRABAJO, respuesta.getModalidad());
    }

    @Test
    @DisplayName("Actualizar asignación SIN motivo lanza excepción para proteger la auditoría")
    void actualizarAsignacion_SinMotivoFalla() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");

        AsignacionTurno asigExistente = AsignacionTurno.builder()
                .idAsignacion(1L).empleado(empleadoDestino).turno(turnoManana).fecha(LocalDate.now()).build();

        PeticionAsignacionTurnoDTO peticion = PeticionAsignacionTurnoDTO.builder()
                .idTurno(100L).fecha(LocalDate.now()).modalidad(ModalidadTurno.PRESENCIAL)
                .motivoCambio("")
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));
        when(asignacionRepository.findById(1L)).thenReturn(Optional.of(asigExistente));
        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turnoManana));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> asignacionService.actualizarAsignacion(1L, peticion));
        assertTrue(ex.getMessage().contains("motivo del cambio es obligatorio"));
        verify(asignacionRepository, never()).save(any(AsignacionTurno.class));
    }

    @Test
    @DisplayName("Eliminar físicamente una asignación con éxito")
    void eliminarAsignacion_Exito() {
        simularAutenticacion(EMAIL_EMPRESA, "ROLE_EMPRESA");

        AsignacionTurno asigExistente = AsignacionTurno.builder()
                .idAsignacion(1L).empleado(empleadoDestino).turno(turnoManana).build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA)).thenReturn(Optional.of(empresaPrueba));
        when(asignacionRepository.findById(1L)).thenReturn(Optional.of(asigExistente));

        asignacionService.eliminarAsignacion(1L);

        verify(asignacionRepository, times(1)).delete(asigExistente);
    }
}
