package com.gestorrh.api.service;

import com.gestorrh.api.dto.PeticionTurnoDTO;
import com.gestorrh.api.dto.RespuestaTurnoDTO;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Turno;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.TurnoRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @InjectMocks
    private TurnoService turnoService;

    private Empresa empresaPrueba;
    private final String EMAIL_EMPRESA_AUTH = "admin@miempresa.com";

    @BeforeEach
    void setUp() {
        empresaPrueba = new Empresa();
        empresaPrueba.setIdEmpresa(1L);
        empresaPrueba.setEmail(EMAIL_EMPRESA_AUTH);

        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(EMAIL_EMPRESA_AUTH);

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Debe crear un turno con éxito si las horas son válidas")
    void crearTurno_Exito() {
        PeticionTurnoDTO peticion = PeticionTurnoDTO.builder()
                .descripcion("Mañana")
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(15, 0))
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));

        when(turnoRepository.save(any(Turno.class))).thenAnswer(invocation -> {
            Turno turnoGuardado = invocation.getArgument(0);
            turnoGuardado.setIdTurno(10L);
            return turnoGuardado;
        });

        RespuestaTurnoDTO respuesta = turnoService.crearTurno(peticion);

        assertNotNull(respuesta);
        assertEquals(10L, respuesta.getIdTurno());
        assertEquals("Mañana", respuesta.getDescripcion());
        verify(turnoRepository, times(1)).save(any(Turno.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si la hora de inicio es posterior o igual a la de fin")
    void crearTurno_HorasInvalidas_LanzaExcepcion() {
        PeticionTurnoDTO peticion = PeticionTurnoDTO.builder()
                .descripcion("Turno Imposible")
                .horaInicio(LocalTime.of(15, 0))
                .horaFin(LocalTime.of(8, 0))
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnoService.crearTurno(peticion);
        });

        assertTrue(exception.getMessage().contains("La hora de inicio debe ser estrictamente anterior"));
        verify(turnoRepository, never()).save(any(Turno.class));
    }

    @Test
    @DisplayName("Debe devolver la lista de turnos exclusiva de la empresa logueada")
    void obtenerTurnosDeEmpresa_Exito() {
        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));

        Turno turno1 = Turno.builder().idTurno(1L).descripcion("T1").empresa(empresaPrueba).build();
        Turno turno2 = Turno.builder().idTurno(2L).descripcion("T2").empresa(empresaPrueba).build();

        when(turnoRepository.findByEmpresaIdEmpresa(empresaPrueba.getIdEmpresa())).thenReturn(List.of(turno1, turno2));

        List<RespuestaTurnoDTO> resultado = turnoService.obtenerTurnosDeEmpresa();

        assertEquals(2, resultado.size());
        assertEquals("T1", resultado.get(0).getDescripcion());
        assertEquals("T2", resultado.get(1).getDescripcion());
    }

    @Test
    @DisplayName("Debe denegar la actualización si el turno pertenece a OTRA empresa (Multi-Tenant)")
    void actualizarTurno_OtraEmpresa_LanzaExcepcion() {
        Long idTurnoTarget = 99L;

        Empresa otraEmpresa = new Empresa();
        otraEmpresa.setIdEmpresa(2L);

        Turno turnoOtraEmpresa = Turno.builder()
                .idTurno(idTurnoTarget)
                .empresa(otraEmpresa)
                .horaInicio(LocalTime.of(8, 0))
                .horaFin(LocalTime.of(15, 0))
                .build();

        PeticionTurnoDTO peticion = PeticionTurnoDTO.builder()
                .descripcion("Intento Hackeo")
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(14, 0))
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(turnoRepository.findById(idTurnoTarget)).thenReturn(Optional.of(turnoOtraEmpresa));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            turnoService.actualizarTurno(idTurnoTarget, peticion);
        });

        assertTrue(exception.getMessage().contains("Acceso denegado: Este turno no pertenece a tu empresa"));
        verify(turnoRepository, never()).save(any(Turno.class));
    }

    @Test
    @DisplayName("Debe eliminar físicamente un turno de la base de datos si pertenece a la empresa")
    void eliminarTurno_Exito() {
        Long idTurnoTarget = 1L;
        Turno turnoPropio = Turno.builder()
                .idTurno(idTurnoTarget)
                .empresa(empresaPrueba)
                .build();
        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(turnoRepository.findById(idTurnoTarget)).thenReturn(Optional.of(turnoPropio));

        turnoService.eliminarTurno(idTurnoTarget);

        verify(turnoRepository, times(1)).delete(turnoPropio);
    }
}
