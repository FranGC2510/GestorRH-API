package com.gestorrh.api.service;

import com.gestorrh.api.dto.fichaje.PeticionFichajeEntradaDTO;
import com.gestorrh.api.dto.fichaje.PeticionFichajeSalidaDTO;
import com.gestorrh.api.dto.fichaje.PeticionModificacionFichajeDTO;
import com.gestorrh.api.dto.fichaje.RespuestaFichajeDTO;
import com.gestorrh.api.entity.AsignacionTurno;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.Fichaje;
import com.gestorrh.api.entity.Turno;
import com.gestorrh.api.entity.enums.ModalidadTurno;
import com.gestorrh.api.repository.AsignacionTurnoRepository;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.repository.FichajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FichajeServiceTest {

    @Mock private FichajeRepository fichajeRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private AsignacionTurnoRepository asignacionRepository;
    @Mock private GeofencingService geofencingService;

    @InjectMocks
    private FichajeService fichajeService;

    private Empleado empleadoPrueba;
    private Empresa empresaPrueba;
    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        empresaPrueba = Empresa.builder()
                .idEmpresa(1L)
                .email("empresa@test.com")
                .latitudSede(40.4168)
                .longitudSede(-3.7038)
                .radioValidez(100)
                .build();

        empleadoPrueba = Empleado.builder()
                .idEmpleado(1L)
                .nombre("Juan")
                .apellidos("Perez")
                .email("empleado@test.com")
                .empresa(empresaPrueba)
                .departamento("IT")
                .build();

        authentication = mock(Authentication.class);
        securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockAuth(String email, String role) {
        lenient().when(authentication.getName()).thenReturn(email);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        lenient().doReturn(authorities).when(authentication).getAuthorities();
    }

    @Nested
    @DisplayName("Tests para ficharEntrada")
    class FicharEntradaTests {

        @Test
        @DisplayName("Éxito: Fichaje entrada presencial correcto")
        void ficharEntrada_Presencial_Exito() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.4168, -3.7038);
            
            Turno turno = Turno.builder().horaInicio(LocalTime.now().plusHours(1)).descripcion("Turno Mañana").build();
            AsignacionTurno asignacion = AsignacionTurno.builder().idAsignacion(1L).turno(turno).modalidad(ModalidadTurno.PRESENCIAL).build();

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(Collections.emptyList());
            when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(anyLong(), any())).thenReturn(List.of(asignacion));
            when(geofencingService.esFichajeValido(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(true);
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> {
                Fichaje f = i.getArgument(0);
                f.setIdFichaje(100L);
                return f;
            });

            RespuestaFichajeDTO respuesta = fichajeService.ficharEntrada(peticion);

            assertNotNull(respuesta);
            assertEquals(100L, respuesta.getIdFichaje());
            assertNull(respuesta.getIncidencias());
            verify(geofencingService).esFichajeValido(eq(40.4168), eq(-3.7038), eq(40.4168), eq(-3.7038), eq(100));
        }

        @Test
        @DisplayName("Éxito: Fichaje entrada con retraso")
        void ficharEntrada_ConRetraso() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.4168, -3.7038);

            Turno turno = Turno.builder().horaInicio(LocalTime.now().minusMinutes(30)).descripcion("Turno Mañana").build();
            AsignacionTurno asignacion = AsignacionTurno.builder().turno(turno).modalidad(ModalidadTurno.TELETRABAJO).build();

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(Collections.emptyList());
            when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(anyLong(), any())).thenReturn(List.of(asignacion));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            RespuestaFichajeDTO respuesta = fichajeService.ficharEntrada(peticion);

            assertNotNull(respuesta.getIncidencias());
            assertTrue(respuesta.getIncidencias().contains("Retraso"));
        }

        @Test
        @DisplayName("Éxito: Fichaje sin turno asignado")
        void ficharEntrada_SinTurno() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.4168, -3.7038);

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(Collections.emptyList());
            when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(anyLong(), any())).thenReturn(Collections.emptyList());
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            RespuestaFichajeDTO respuesta = fichajeService.ficharEntrada(peticion);

            assertTrue(respuesta.getIncidencias().contains("sin turno asignado"));
            assertEquals("Sin turno asignado", respuesta.getDescripcionTurno());
        }

        @Test
        @DisplayName("Falla: Sede no configurada")
        void ficharEntrada_SedeNoConfigurada() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            empleadoPrueba.getEmpresa().setLatitudSede(null);
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.4168, -3.7038);
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> fichajeService.ficharEntrada(peticion));
            assertTrue(ex.getMessage().contains("debe configurar la ubicación de su sede"));
        }

        @Test
        @DisplayName("Falla: Ya hay un turno abierto")
        void ficharEntrada_FallaDobleEntrada() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.4168, -3.7038);
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any(LocalDate.class)))
                    .thenReturn(List.of(new Fichaje()));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> fichajeService.ficharEntrada(peticion));
            assertTrue(ex.getMessage().contains("ya tienes un turno abierto"));
        }

        @Test
        @DisplayName("Falla: Fuera del radio (Geofencing)")
        void ficharEntrada_FallaGeofencing() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeEntradaDTO peticion = new PeticionFichajeEntradaDTO(40.8000, -3.9000);

            Turno turno = Turno.builder().horaInicio(LocalTime.of(8, 0)).build();
            AsignacionTurno asignacion = AsignacionTurno.builder().turno(turno).modalidad(ModalidadTurno.PRESENCIAL).build();

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(Collections.emptyList());
            when(asignacionRepository.findByEmpleadoIdEmpleadoAndFecha(anyLong(), any())).thenReturn(List.of(asignacion));
            when(geofencingService.esFichajeValido(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(false);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> fichajeService.ficharEntrada(peticion));
            assertTrue(ex.getMessage().contains("fuera del radio permitido"));
        }
    }

    @Nested
    @DisplayName("Tests para ficharSalida")
    class FicharSalidaTests {

        @Test
        @DisplayName("Éxito: Fichaje salida correcto")
        void ficharSalida_Exito() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeSalidaDTO peticion = new PeticionFichajeSalidaDTO(40.4168, -3.7038);
            
            Fichaje fichajeAbierto = Fichaje.builder()
                    .idFichaje(1L)
                    .empleado(empleadoPrueba)
                    .horaEntrada(LocalDateTime.now().minusHours(8))
                    .build();

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(List.of(fichajeAbierto));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            RespuestaFichajeDTO respuesta = fichajeService.ficharSalida(peticion);

            assertNotNull(respuesta.getHoraSalida());
            verify(fichajeRepository).save(any(Fichaje.class));
        }

        @Test
        @DisplayName("Éxito: Salida anticipada")
        void ficharSalida_Anticipada() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeSalidaDTO peticion = new PeticionFichajeSalidaDTO(40.4168, -3.7038);
            
            // Turno que termina en 2 horas
            Turno turno = Turno.builder().horaFin(LocalTime.now().plusHours(2)).build();
            AsignacionTurno asignacion = AsignacionTurno.builder().turno(turno).build();
            Fichaje fichajeAbierto = Fichaje.builder()
                    .empleado(empleadoPrueba)
                    .asignacion(asignacion)
                    .build();

            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(List.of(fichajeAbierto));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            RespuestaFichajeDTO respuesta = fichajeService.ficharSalida(peticion);

            assertTrue(respuesta.getIncidencias().contains("Salida anticipada"));
        }

        @Test
        @DisplayName("Falla: No hay entrada abierta")
        void ficharSalida_FallaNoAbierto() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            PeticionFichajeSalidaDTO peticion = new PeticionFichajeSalidaDTO(40.4168, -3.7038);
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaAndHoraSalidaIsNull(anyLong(), any())).thenReturn(Collections.emptyList());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> fichajeService.ficharSalida(peticion));
            assertTrue(ex.getMessage().contains("no tienes ninguna entrada abierta"));
        }
    }

    @Nested
    @DisplayName("Tests para consultarFichajes")
    class ConsultarFichajesTests {

        @Test
        @DisplayName("Empresa: Consulta todos los fichajes de su empresa")
        void consultarFichajes_Empresa_Exito() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            LocalDate hoy = LocalDate.now();
            
            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).fecha(hoy).build();
            
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.of(empresaPrueba));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(anyLong(), any(), any())).thenReturn(List.of(f1));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(hoy, hoy, null);

            assertFalse(resultado.isEmpty());
            assertEquals(1, resultado.size());
        }

        @Test
        @DisplayName("Empresa: Filtra por empleado concreto")
        void consultarFichajes_Empresa_FiltroEmpleado() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            LocalDate hoy = LocalDate.now();
            
            Empleado e2 = Empleado.builder().idEmpleado(2L).nombre("Ana").apellidos("Gomez").build();
            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).fecha(hoy).build();
            Fichaje f2 = Fichaje.builder().idFichaje(2L).empleado(e2).fecha(hoy).build();
            
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.of(empresaPrueba));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(1L, hoy, hoy)).thenReturn(List.of(f1, f2));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(hoy, hoy, 1L);

            assertEquals(1, resultado.size());
            assertEquals(1L, resultado.get(0).getIdEmpleado());
        }

        @Test
        @DisplayName("Supervisor: Consulta fichajes de su departamento")
        void consultarFichajes_Supervisor_Exito() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            LocalDate hoy = LocalDate.now();
            
            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            Empleado empIT = Empleado.builder().idEmpleado(1L).departamento("IT").empresa(empresaPrueba).nombre("I").apellidos("T").build();
            Empleado empRRHH = Empleado.builder().idEmpleado(2L).departamento("RRHH").empresa(empresaPrueba).nombre("R").apellidos("H").build();
            
            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empIT).fecha(hoy).build();
            Fichaje f2 = Fichaje.builder().idFichaje(2L).empleado(empRRHH).fecha(hoy).build();
            
            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(1L, hoy, hoy)).thenReturn(List.of(f1, f2));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(hoy, hoy, null);

            assertEquals(1, resultado.size());
            assertEquals("IT", empIT.getDepartamento());
        }

        @Test
        @DisplayName("Supervisor: Filtra por empleado de su departamento (Permitido)")
        void consultarFichajes_Supervisor_FiltroPermitido() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            LocalDate hoy = LocalDate.now();
            
            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            Empleado empIT = Empleado.builder().idEmpleado(1L).departamento("IT").empresa(empresaPrueba).nombre("I").apellidos("T").build();
            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empIT).fecha(hoy).build();
            
            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(anyLong(), any(), any())).thenReturn(List.of(f1));
            when(empleadoRepository.findById(1L)).thenReturn(Optional.of(empIT));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(hoy, hoy, 1L);

            assertEquals(1, resultado.size());
            assertEquals(1L, resultado.get(0).getIdEmpleado());
        }

        @Test
        @DisplayName("Supervisor: Intenta filtrar por empleado de OTRO departamento (Denegado)")
        void consultarFichajes_Supervisor_FiltroDenegado() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            LocalDate hoy = LocalDate.now();

            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            Empleado empRRHH = Empleado.builder().idEmpleado(2L).departamento("RRHH").empresa(empresaPrueba).nombre("R").apellidos("H").build();

            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(anyLong(), any(), any())).thenReturn(Collections.emptyList());
            when(empleadoRepository.findById(2L)).thenReturn(Optional.of(empRRHH));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    fichajeService.consultarFichajes(hoy, hoy, 2L)
            );
            assertTrue(ex.getMessage().contains("Acceso denegado"));
        }

        @Test
        @DisplayName("Supervisor: Filtra por empleado de su departamento sin fichajes en el rango — debe devolver lista vacía, nunca 403")
        void consultarFichajes_Supervisor_FiltroPermitido_SinFichajesEnRango_DevuelveListaVacia() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            LocalDate hoy = LocalDate.now();

            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            Empleado empIT = Empleado.builder().idEmpleado(1L).departamento("IT").empresa(empresaPrueba).nombre("I").apellidos("T").build();

            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));
            when(fichajeRepository.findByEmpleadoEmpresaIdEmpresaAndFechaBetween(anyLong(), any(), any())).thenReturn(Collections.emptyList());
            when(empleadoRepository.findById(1L)).thenReturn(Optional.of(empIT));

            List<RespuestaFichajeDTO> resultado = assertDoesNotThrow(() ->
                    fichajeService.consultarFichajes(hoy, hoy, 1L)
            );

            assertTrue(resultado.isEmpty());
        }

        @Test
        @DisplayName("Falla: Empresa no encontrada")
        void consultarFichajes_EmpresaNoEncontrada() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> 
                fichajeService.consultarFichajes(LocalDate.now(), LocalDate.now(), null)
            );
        }

        @Test
        @DisplayName("Falla: Empleado no encontrado")
        void consultarFichajes_EmpleadoNoEncontrado() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () ->
                fichajeService.consultarFichajes(LocalDate.now(), LocalDate.now(), null)
            );
        }

        @Test
        @DisplayName("Params opcionales: sin fechas devuelve fichajes del mes actual")
        void consultarFichajes_SinFechas_UsaMesActual() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            LocalDate primerDiaMes = LocalDate.now().withDayOfMonth(1);
            LocalDate hoy = LocalDate.now();

            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).fecha(hoy).build();
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(
                    eq(empleadoPrueba.getIdEmpleado()), eq(primerDiaMes), eq(hoy)))
                .thenReturn(List.of(f1));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(null, null, null);

            assertEquals(1, resultado.size());
            verify(fichajeRepository).findByEmpleadoIdEmpleadoAndFechaBetween(
                    empleadoPrueba.getIdEmpleado(), primerDiaMes, hoy);
        }

        @Test
        @DisplayName("Params opcionales: solo fechaInicio, fechaFin defaults a hoy")
        void consultarFichajes_SoloFechaInicio_FechaFinDefaultsAHoy() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            LocalDate fechaInicio = LocalDate.of(2026, 1, 1);
            LocalDate hoy = LocalDate.now();

            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).fecha(fechaInicio).build();
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(
                    eq(empleadoPrueba.getIdEmpleado()), eq(fechaInicio), eq(hoy)))
                .thenReturn(List.of(f1));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(fechaInicio, null, null);

            assertEquals(1, resultado.size());
            verify(fichajeRepository).findByEmpleadoIdEmpleadoAndFechaBetween(
                    empleadoPrueba.getIdEmpleado(), fechaInicio, hoy);
        }

        @Test
        @DisplayName("Params opcionales: ambas fechas explícitas, sin cambio de comportamiento")
        void consultarFichajes_AmbasFechas_ComportamientoSinCambios() {
            mockAuth("empleado@test.com", "ROLE_EMPLEADO");
            LocalDate inicio = LocalDate.of(2026, 1, 1);
            LocalDate fin = LocalDate.of(2026, 1, 31);

            Fichaje f1 = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).fecha(inicio).build();
            when(empleadoRepository.findByEmail("empleado@test.com")).thenReturn(Optional.of(empleadoPrueba));
            when(fichajeRepository.findByEmpleadoIdEmpleadoAndFechaBetween(
                    eq(empleadoPrueba.getIdEmpleado()), eq(inicio), eq(fin)))
                .thenReturn(List.of(f1));

            List<RespuestaFichajeDTO> resultado = fichajeService.consultarFichajes(inicio, fin, null);

            assertEquals(1, resultado.size());
            verify(fichajeRepository).findByEmpleadoIdEmpleadoAndFechaBetween(
                    empleadoPrueba.getIdEmpleado(), inicio, fin);
        }
    }

    @Nested
    @DisplayName("Tests para modificarFichajeManual")
    class ModificarFichajeManualTests {

        @Test
        @DisplayName("Éxito: Empresa modifica fichaje")
        void modificarFichaje_Empresa_Exito() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            PeticionModificacionFichajeDTO peticion = new PeticionModificacionFichajeDTO(
                    LocalDateTime.now(), null, "Error del empleado"
            );
            
            Fichaje fichaje = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).build();
            
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.of(empresaPrueba));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            RespuestaFichajeDTO resultado = fichajeService.modificarFichajeManual(1L, peticion);

            assertNotNull(resultado);
            assertTrue(fichaje.getIncidencias().contains("MODIFICADO MANUALMENTE"));
            assertTrue(fichaje.getIncidencias().contains("Error del empleado"));
        }

        @Test
        @DisplayName("Falla: Supervisor intenta modificar su propio fichaje")
        void modificarFichaje_SupervisorPropio_Falla() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").build();
            Fichaje fichajePropio = Fichaje.builder().idFichaje(1L).empleado(supervisor).build();
            
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichajePropio));
            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> 
                fichajeService.modificarFichajeManual(1L, new PeticionModificacionFichajeDTO())
            );
            assertTrue(ex.getMessage().contains("Como supervisor no puedes modificar tus propios fichajes"));
        }

        @Test
        @DisplayName("Falla: Sin horas nuevas")
        void modificarFichaje_SinHoras_Falla() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            Fichaje fichaje = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).build();
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.of(empresaPrueba));

            PeticionModificacionFichajeDTO peticion = new PeticionModificacionFichajeDTO(null, null, "Sin horas");
            
            RuntimeException ex = assertThrows(RuntimeException.class, () -> 
                fichajeService.modificarFichajeManual(1L, peticion)
            );
            assertTrue(ex.getMessage().contains("Debes proporcionar al menos una nueva hora"));
        }
        @Test
        @DisplayName("Supervisor: Modifica fichaje de empleado de su departamento")
        void modificarFichaje_Supervisor_Exito() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            Fichaje fichaje = Fichaje.builder().idFichaje(1L).empleado(empleadoPrueba).build(); // empleadoPrueba es de IT
            
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichaje));
            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));
            when(fichajeRepository.save(any(Fichaje.class))).thenAnswer(i -> i.getArgument(0));

            PeticionModificacionFichajeDTO peticion = new PeticionModificacionFichajeDTO(
                    null, LocalDateTime.now(), "Ajuste"
            );

            RespuestaFichajeDTO resultado = fichajeService.modificarFichajeManual(1L, peticion);
            assertNotNull(resultado);
        }

        @Test
        @DisplayName("Falla: Empresa intenta modificar fichaje de OTRA empresa")
        void modificarFichaje_EmpresaAjena_Falla() {
            mockAuth("empresa@test.com", "ROLE_EMPRESA");
            Empresa otraEmpresa = Empresa.builder().idEmpresa(2L).build();
            Empleado empleadoAjeno = Empleado.builder().empresa(otraEmpresa).build();
            Fichaje fichajeAjeno = Fichaje.builder().idFichaje(1L).empleado(empleadoAjeno).build();
            
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichajeAjeno));
            when(empresaRepository.findByEmail("empresa@test.com")).thenReturn(Optional.of(empresaPrueba));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> 
                fichajeService.modificarFichajeManual(1L, new PeticionModificacionFichajeDTO())
            );
            assertTrue(ex.getMessage().contains("pertenece a otra empresa"));
        }

        @Test
        @DisplayName("Falla: Supervisor intenta modificar fichaje de otro departamento")
        void modificarFichaje_SupervisorDepartamentoAjeno_Falla() {
            mockAuth("supervisor@test.com", "ROLE_SUPERVISOR");
            Empleado supervisor = Empleado.builder().idEmpleado(10L).email("supervisor@test.com").departamento("IT").empresa(empresaPrueba).build();
            // empleadoRRHH debe ser de la misma empresa para que pase el primer chequeo de supervisor (que suele ser implícito en la lógica de negocio pero aquí el mock lo necesita)
            Empleado empleadoRRHH = Empleado.builder()
                    .idEmpleado(20L)
                    .departamento("RRHH")
                    .empresa(empresaPrueba)
                    .build();
            Fichaje fichajeRRHH = Fichaje.builder().idFichaje(1L).empleado(empleadoRRHH).build();
            
            when(fichajeRepository.findById(1L)).thenReturn(Optional.of(fichajeRRHH));
            when(empleadoRepository.findByEmail("supervisor@test.com")).thenReturn(Optional.of(supervisor));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> 
                fichajeService.modificarFichajeManual(1L, new PeticionModificacionFichajeDTO())
            );
            assertTrue(ex.getMessage().contains("Solo puedes modificar fichajes de los empleados de tu departamento"));
        }

        @Test
        @DisplayName("Falla: Fichaje no encontrado")
        void modificarFichaje_NoExiste_Falla() {
            when(fichajeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> 
                fichajeService.modificarFichajeManual(999L, new PeticionModificacionFichajeDTO())
            );
        }
    }
}
