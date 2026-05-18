package com.gestorrh.api.service;

import com.gestorrh.api.dto.empleado.PeticionActualizarEmpleadoDTO;
import com.gestorrh.api.dto.empleado.PeticionCrearEmpleadoDTO;
import com.gestorrh.api.dto.empleado.RespuestaCrearEmpleadoDTO;
import com.gestorrh.api.dto.empleado.RespuestaEmpleadoDTO;
import jakarta.persistence.EntityNotFoundException;
import com.gestorrh.api.entity.Empleado;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.entity.enums.RolEmpleado;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private PasswordEncoder codificadorPassword;

    @InjectMocks
    private EmpleadoService empleadoService;

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
    @DisplayName("Debe crear un empleado con éxito y devolver la contraseña autogenerada plana")
    void crearEmpleado_Exito() {
        PeticionCrearEmpleadoDTO peticion = PeticionCrearEmpleadoDTO.builder()
                .email("nuevo@empleado.com")
                .nombre("Juan")
                .apellidos("Pérez")
                .rol(RolEmpleado.EMPLEADO)
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findByEmail(peticion.getEmail())).thenReturn(Optional.empty());
        when(codificadorPassword.encode(anyString())).thenReturn("hashedPassword123");
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(invocation -> {
            Empleado empleadoGuardado = invocation.getArgument(0);
            empleadoGuardado.setIdEmpleado(100L);
            return empleadoGuardado;
        });

        RespuestaCrearEmpleadoDTO respuesta = empleadoService.crearEmpleado(peticion);

        assertNotNull(respuesta);
        assertEquals(100L, respuesta.getIdEmpleado());
        assertEquals("nuevo@empleado.com", respuesta.getEmail());
        assertNotNull(respuesta.getPasswordGenerada());
        assertEquals(8, respuesta.getPasswordGenerada().length());
        verify(empleadoRepository, times(1)).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Debe lanzar RuntimeException si se intenta crear un empleado con un email ya registrado")
    void crearEmpleado_EmailYaExiste_LanzaExcepcion() {

        PeticionCrearEmpleadoDTO peticion = PeticionCrearEmpleadoDTO.builder()
                .email("duplicado@empleado.com")
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findByEmail(peticion.getEmail())).thenReturn(Optional.of(new Empleado()));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            empleadoService.crearEmpleado(peticion);
        });

        assertTrue(exception.getMessage().contains("Ya existe un usuario con el correo"));

        verify(empleadoRepository, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Debe devolver la lista de empleados de la empresa autenticada")
    void obtenerEmpleadosDeEmpresa_Exito() {

        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(EMAIL_EMPRESA_AUTH);
        GrantedAuthority authority = mock(GrantedAuthority.class);
        lenient().when(authority.getAuthority()).thenReturn("ROLE_EMPRESA");
        lenient().doReturn(Collections.singletonList(authority)).when(auth).getAuthorities();
        SecurityContextHolder.setContext(ctx);

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));

        Empleado emp1 = Empleado.builder().idEmpleado(1L).email("emp1@test.com").nombre("Ana").activo(true).build();
        Empleado emp2 = Empleado.builder().idEmpleado(2L).email("emp2@test.com").nombre("Luis").activo(true).build();

        when(empleadoRepository.findByEmpresaIdEmpresa(empresaPrueba.getIdEmpresa())).thenReturn(List.of(emp1, emp2));

        List<RespuestaEmpleadoDTO> resultado = empleadoService.obtenerEmpleadosDeEmpresa();

        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        assertEquals("Ana", resultado.get(0).getNombre());
        assertEquals("Luis", resultado.get(1).getNombre());
    }

    @Test
    @DisplayName("Debe actualizar los datos de un empleado con éxito si pertenece a la empresa")
    void actualizarEmpleado_Exito() {

        Long idEmpleadoTarget = 10L;
        PeticionActualizarEmpleadoDTO peticion = PeticionActualizarEmpleadoDTO.builder()
                .nombre("Ana Modificada")
                .puesto("Manager")
                .build();

        Empleado empleadoEnBd = Empleado.builder()
                .idEmpleado(idEmpleadoTarget)
                .empresa(empresaPrueba)
                .nombre("Ana Antigua")
                .puesto("Junior")
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(idEmpleadoTarget)).thenReturn(Optional.of(empleadoEnBd));

        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(i -> i.getArgument(0));

        RespuestaEmpleadoDTO respuesta = empleadoService.actualizarEmpleado(idEmpleadoTarget, peticion);

        assertNotNull(respuesta);
        assertEquals("Ana Modificada", respuesta.getNombre());
        assertEquals("Manager", respuesta.getPuesto());
        verify(empleadoRepository, times(1)).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Debe denegar la actualización si el empleado pertenece a OTRA empresa (Aislamiento Multi-tenant)")
    void actualizarEmpleado_OtraEmpresa_LanzaExcepcion() {
        Long idEmpleadoTarget = 99L;

        Empresa otraEmpresa = new Empresa();
        otraEmpresa.setIdEmpresa(2L);

        Empleado empleadoDeOtraEmpresa = Empleado.builder()
                .idEmpleado(idEmpleadoTarget)
                .empresa(otraEmpresa)
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(idEmpleadoTarget)).thenReturn(Optional.of(empleadoDeOtraEmpresa));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            empleadoService.actualizarEmpleado(idEmpleadoTarget, new PeticionActualizarEmpleadoDTO());
        });

        assertTrue(exception.getMessage().contains("Acceso denegado: Este empleado no pertenece a tu empresa"));
        verify(empleadoRepository, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Debe restablecer la contraseña de un empleado cifrándola y sin exponerla en la respuesta")
    void resetPassword_Exito() {
        Long idEmpleadoTarget = 20L;
        String nuevaPassword = "NuevaPassword123";

        Empleado empleadoEnBd = Empleado.builder()
                .idEmpleado(idEmpleadoTarget)
                .empresa(empresaPrueba)
                .email("empleado@test.com")
                .nombre("Carlos")
                .password("hashAntiguo")
                .activo(true)
                .rol(RolEmpleado.EMPLEADO)
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(idEmpleadoTarget)).thenReturn(Optional.of(empleadoEnBd));
        when(codificadorPassword.encode(nuevaPassword)).thenReturn("hashNuevo");
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(i -> i.getArgument(0));

        RespuestaEmpleadoDTO respuesta = empleadoService.resetPassword(idEmpleadoTarget, nuevaPassword);

        assertNotNull(respuesta);
        assertEquals(idEmpleadoTarget, respuesta.getIdEmpleado());
        assertEquals("empleado@test.com", respuesta.getEmail());
        assertEquals("hashNuevo", empleadoEnBd.getPassword());
        verify(codificadorPassword, times(1)).encode(nuevaPassword);
        verify(empleadoRepository, times(1)).save(empleadoEnBd);
    }

    @Test
    @DisplayName("Debe lanzar EntityNotFoundException si el empleado a restablecer no existe")
    void resetPassword_EmpleadoNoExiste_LanzaExcepcion() {
        Long idInexistente = 999L;

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> empleadoService.resetPassword(idInexistente, "Password123"));

        verify(codificadorPassword, never()).encode(anyString());
        verify(empleadoRepository, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("Debe denegar el reseteo si el empleado pertenece a OTRA empresa (Aislamiento Multi-tenant)")
    void resetPassword_OtraEmpresa_LanzaExcepcion() {
        Long idEmpleadoTarget = 77L;

        Empresa otraEmpresa = new Empresa();
        otraEmpresa.setIdEmpresa(2L);

        Empleado empleadoDeOtraEmpresa = Empleado.builder()
                .idEmpleado(idEmpleadoTarget)
                .empresa(otraEmpresa)
                .build();

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));
        when(empleadoRepository.findById(idEmpleadoTarget)).thenReturn(Optional.of(empleadoDeOtraEmpresa));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> empleadoService.resetPassword(idEmpleadoTarget, "Password123"));

        assertTrue(exception.getMessage().contains("Acceso denegado"));
        verify(codificadorPassword, never()).encode(anyString());
        verify(empleadoRepository, never()).save(any(Empleado.class));
    }

    @Test
    @DisplayName("SUPERVISOR obtiene únicamente los empleados de su departamento")
    void obtenerEmpleadosDeEmpresa_ComoSupervisor_FiltraPorDepartamento() {
        String emailSupervisor = "super@miempresa.com";
        simularAutenticacionSupervisor(emailSupervisor);

        Empleado supervisor = Empleado.builder()
                .idEmpleado(99L)
                .email(emailSupervisor)
                .empresa(empresaPrueba)
                .departamento("IT")
                .build();

        Empleado emp1 = Empleado.builder().idEmpleado(1L).email("emp1@test.com").nombre("Ana").apellidos("García").departamento("IT").empresa(empresaPrueba).activo(true).build();
        Empleado emp2 = Empleado.builder().idEmpleado(2L).email("emp2@test.com").nombre("Luis").apellidos("Pérez").departamento("IT").empresa(empresaPrueba).activo(true).build();

        when(empleadoRepository.findByEmail(emailSupervisor)).thenReturn(Optional.of(supervisor));
        when(empleadoRepository.findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(empresaPrueba.getIdEmpresa(), "IT"))
                .thenReturn(List.of(emp1, emp2));

        List<RespuestaEmpleadoDTO> resultado = empleadoService.obtenerEmpleadosDeEmpresa();

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().allMatch(e -> "IT".equals(e.getDepartamento())));
        verify(empleadoRepository, times(1)).findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(empresaPrueba.getIdEmpresa(), "IT");
        verify(empleadoRepository, never()).findByEmpresaIdEmpresa(anyLong());
    }

    @Test
    @DisplayName("SUPERVISOR sin departamento asignado devuelve lista vacía")
    void obtenerEmpleadosDeEmpresa_SupervisorSinDepartamento_DevuelveListaVacia() {
        String emailSupervisor = "super@miempresa.com";
        simularAutenticacionSupervisor(emailSupervisor);

        Empleado supervisorSinDepto = Empleado.builder()
                .idEmpleado(99L)
                .email(emailSupervisor)
                .empresa(empresaPrueba)
                .departamento(null)
                .build();

        when(empleadoRepository.findByEmail(emailSupervisor)).thenReturn(Optional.of(supervisorSinDepto));

        List<RespuestaEmpleadoDTO> resultado = empleadoService.obtenerEmpleadosDeEmpresa();

        assertTrue(resultado.isEmpty());
        verify(empleadoRepository, never()).findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(anyLong(), anyString());
    }

    @Test
    @DisplayName("EMPRESA sigue obteniendo todos sus empleados sin regresión")
    void obtenerEmpleadosDeEmpresa_ComoEmpresa_DevuelveTodosSinRegresion() {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(EMAIL_EMPRESA_AUTH);
        GrantedAuthority authority = mock(GrantedAuthority.class);
        lenient().when(authority.getAuthority()).thenReturn("ROLE_EMPRESA");
        lenient().doReturn(Collections.singletonList(authority)).when(auth).getAuthorities();
        SecurityContextHolder.setContext(ctx);

        when(empresaRepository.findByEmail(EMAIL_EMPRESA_AUTH)).thenReturn(Optional.of(empresaPrueba));

        Empleado emp1 = Empleado.builder().idEmpleado(1L).email("emp1@test.com").nombre("Ana").activo(true).departamento("IT").build();
        Empleado emp2 = Empleado.builder().idEmpleado(2L).email("emp2@test.com").nombre("Luis").activo(true).departamento("RRHH").build();

        when(empleadoRepository.findByEmpresaIdEmpresa(empresaPrueba.getIdEmpresa())).thenReturn(List.of(emp1, emp2));

        List<RespuestaEmpleadoDTO> resultado = empleadoService.obtenerEmpleadosDeEmpresa();

        assertEquals(2, resultado.size());
        verify(empleadoRepository, times(1)).findByEmpresaIdEmpresa(empresaPrueba.getIdEmpresa());
        verify(empleadoRepository, never()).findByEmpresaIdEmpresaAndDepartamentoIgnoreCase(anyLong(), anyString());
    }

    private void simularAutenticacionSupervisor(String email) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        GrantedAuthority authority = mock(GrantedAuthority.class);
        when(authority.getAuthority()).thenReturn("ROLE_SUPERVISOR");
        doReturn(Collections.singletonList(authority)).when(authentication).getAuthorities();
        SecurityContextHolder.setContext(securityContext);
    }
}
