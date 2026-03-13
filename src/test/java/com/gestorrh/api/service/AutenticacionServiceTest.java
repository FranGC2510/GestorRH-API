package com.gestorrh.api.service;

import com.gestorrh.api.dto.autenticacionDTO.PeticionLoginDTO;
import com.gestorrh.api.dto.autenticacionDTO.RespuestaLoginDTO;
import com.gestorrh.api.entity.Empresa;
import com.gestorrh.api.repository.EmpleadoRepository;
import com.gestorrh.api.repository.EmpresaRepository;
import com.gestorrh.api.security.ServicioJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests Unitarios para la lógica de Autenticación usando Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private PasswordEncoder codificadorPassword;

    @Mock
    private ServicioJwt servicioJwt;

    @InjectMocks
    private AutenticacionService autenticacionService;

    @Test
    void loginEmpresa_ConCredencialesCorrectas_DevuelveToken() {
        PeticionLoginDTO peticion = new PeticionLoginDTO("admin@test.com", "123456");

        Empresa empresaFalsa = Empresa.builder()
                .idEmpresa(1L)
                .email("admin@test.com")
                .password("passwordEncriptada")
                .nombre("Empresa Test")
                .build();

        when(empresaRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(empresaFalsa));
        when(codificadorPassword.matches("123456", "passwordEncriptada")).thenReturn(true);
        when(servicioJwt.generarToken(anyString(), any())).thenReturn("token-falso-123");

        RespuestaLoginDTO respuesta = autenticacionService.loginEmpresa(peticion);

        assertNotNull(respuesta);
        assertEquals("token-falso-123", respuesta.getToken());
        assertEquals("EMPRESA", respuesta.getRol());
        assertEquals("Empresa Test", respuesta.getNombre());
    }

    @Test
    void loginEmpresa_ConCorreoInexistente_LanzaExcepcion() {
        PeticionLoginDTO peticion = new PeticionLoginDTO("falso@test.com", "123456");

        when(empresaRepository.findByEmail("falso@test.com")).thenReturn(Optional.empty());

        RuntimeException excepcion = assertThrows(RuntimeException.class, () -> {
            autenticacionService.loginEmpresa(peticion);
        });

        assertEquals("Credenciales inválidas", excepcion.getMessage());
    }
}
