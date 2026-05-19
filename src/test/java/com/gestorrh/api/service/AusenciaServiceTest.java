package com.gestorrh.api.service;

import com.gestorrh.api.dto.ausencia.PeticionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.PeticionRevisionAusenciaDTO;
import com.gestorrh.api.dto.ausencia.RespuestaAusenciaDTO;
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
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

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

    // NUEVO MOCK: Necesario porque AusenciaService ahora depende de él
    @Mock
    private FileStorageService fileStorageService;

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

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ausenciaService.crearAusencia(peticion, null));
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

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ausenciaService.actualizarMiAusencia(1L, peticion, null));
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

    @Test
    @DisplayName("Crear ausencia sin archivo deja el campo justificante a null en el DTO")
    void crearAusencia_SinArchivo_JustificanteNull() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any())).thenReturn(List.of());
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.VACACIONES)
                .fechaInicio(LocalDate.of(2026, 11, 1))
                .fechaFin(LocalDate.of(2026, 11, 5))
                .build();

        RespuestaAusenciaDTO respuesta = ausenciaService.crearAusencia(peticion, null);

        assertNull(respuesta.getJustificante());
        verify(fileStorageService, never()).guardarArchivo(any());
    }

    @Test
    @DisplayName("El campo justificante del DTO contiene el nombre simple devuelto por FileStorageService y es usable para descargar")
    void crearAusencia_ConArchivo_JustificanteEsNombreSimpleUsableParaDescarga() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any())).thenReturn(List.of());
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        String nombreSimpleGenerado = "a3f5c0e2-9b1d-4f7c-8e6a-2d1b0c4f5a6b.pdf";
        when(fileStorageService.guardarArchivo(any())).thenReturn(nombreSimpleGenerado);

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "justificante_original.pdf", "application/pdf", "contenido".getBytes()
        );

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.MEDICA)
                .fechaInicio(LocalDate.of(2026, 11, 1))
                .fechaFin(LocalDate.of(2026, 11, 1))
                .build();

        RespuestaAusenciaDTO respuesta = ausenciaService.crearAusencia(peticion, archivo);

        assertEquals(nombreSimpleGenerado, respuesta.getJustificante(),
                "El DTO debe exponer el nombre simple del archivo, sin rutas ni prefijos.");
        assertFalse(respuesta.getJustificante().contains("/"),
                "El justificante no debe contener separadores de ruta.");
        assertFalse(respuesta.getJustificante().contains("\\"),
                "El justificante no debe contener separadores de ruta.");

        Resource recursoSimulado = mock(Resource.class);
        when(fileStorageService.cargarArchivoComoRecurso(respuesta.getJustificante())).thenReturn(recursoSimulado);

        Resource descargado = fileStorageService.cargarArchivoComoRecurso(respuesta.getJustificante());
        assertNotNull(descargado, "El valor del campo justificante debe ser usable directamente para descargar el archivo.");
        verify(fileStorageService).cargarArchivoComoRecurso(nombreSimpleGenerado);
    }

    @Test
    @DisplayName("eliminarJustificante=true con justificante previo → campo null y archivo físico eliminado")
    void actualizarMiAusencia_EliminarJustificante_ConJustificantePrevio() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));

        Ausencia ausenciaConJustificante = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.SOLICITADA)
                .justificante("archivo-existente.pdf").build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaConJustificante));
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any())).thenReturn(List.of());
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.VACACIONES)
                .fechaInicio(LocalDate.of(2026, 11, 1))
                .fechaFin(LocalDate.of(2026, 11, 5))
                .eliminarJustificante(true)
                .build();

        RespuestaAusenciaDTO respuesta = ausenciaService.actualizarMiAusencia(1L, peticion, null);

        assertNull(respuesta.getJustificante());
        verify(fileStorageService, times(1)).eliminarArchivo("archivo-existente.pdf");
        verify(fileStorageService, never()).guardarArchivo(any());
    }

    @Test
    @DisplayName("eliminarJustificante=true sin justificante previo → no lanza excepción y campo queda null")
    void actualizarMiAusencia_EliminarJustificante_SinJustificantePrevio() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));

        Ausencia ausenciaSinJustificante = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.SOLICITADA)
                .justificante(null).build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaSinJustificante));
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any())).thenReturn(List.of());
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.VACACIONES)
                .fechaInicio(LocalDate.of(2026, 11, 1))
                .fechaFin(LocalDate.of(2026, 11, 5))
                .eliminarJustificante(true)
                .build();

        RespuestaAusenciaDTO respuesta = ausenciaService.actualizarMiAusencia(1L, peticion, null);

        assertNull(respuesta.getJustificante());
        verify(fileStorageService, never()).eliminarArchivo(any());
        verify(fileStorageService, never()).guardarArchivo(any());
    }

    @Test
    @DisplayName("eliminarJustificante=true con archivo simultáneo → eliminación tiene precedencia, archivo nuevo no se guarda")
    void actualizarMiAusencia_EliminarJustificante_PrecedenciaSobreArchivo() {
        simularAutenticacion(EMAIL_EMPLEADO, "ROLE_EMPLEADO");
        when(empleadoRepository.findByEmail(EMAIL_EMPLEADO)).thenReturn(Optional.of(empleadoLogueado));

        Ausencia ausenciaConJustificante = Ausencia.builder()
                .idAusencia(1L).empleado(empleadoLogueado).estado(EstadoAusencia.SOLICITADA)
                .justificante("archivo-existente.pdf").build();

        when(ausenciaRepository.findById(1L)).thenReturn(Optional.of(ausenciaConJustificante));
        when(ausenciaRepository.findAusenciasSolapadas(eq(10L), anyList(), any(), any())).thenReturn(List.of());
        when(ausenciaRepository.save(any(Ausencia.class))).thenAnswer(i -> i.getArgument(0));

        PeticionAusenciaDTO peticion = PeticionAusenciaDTO.builder()
                .tipo(TipoAusencia.VACACIONES)
                .fechaInicio(LocalDate.of(2026, 11, 1))
                .fechaFin(LocalDate.of(2026, 11, 5))
                .eliminarJustificante(true)
                .build();

        MockMultipartFile archivoNuevo = new MockMultipartFile(
                "archivo", "nuevo.pdf", "application/pdf", "contenido".getBytes()
        );

        RespuestaAusenciaDTO respuesta = ausenciaService.actualizarMiAusencia(1L, peticion, archivoNuevo);

        assertNull(respuesta.getJustificante());
        verify(fileStorageService, times(1)).eliminarArchivo("archivo-existente.pdf");
        verify(fileStorageService, never()).guardarArchivo(any());
    }
}
