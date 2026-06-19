package com.gestorrh.api.security;

import com.gestorrh.api.config.RateLimitConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para el filtro de rate limiting.
 * Se usan valores de configuración reducidos (3 intentos en 1 minuto)
 * para no tener que realizar 10 llamadas en cada test.
 */
@ExtendWith(MockitoExtension.class)
class FiltroRateLimitTest {

    private FiltroRateLimit filtro;
    private FilterChain cadenaFiltros;

    private static final String RUTA_LOGIN_EMPRESA  = "/api/auth/login-empresa";
    private static final String RUTA_LOGIN_EMPLEADO = "/api/auth/login-empleado";
    private static final String RUTA_REGISTRO       = "/api/empresas/registro";
    private static final String IP_CLIENTE          = "192.168.1.1";
    private static final String IP_CLIENTE_2        = "192.168.1.2";

    @BeforeEach
    void setUp() {
        RateLimitConfig config = new RateLimitConfig();

        RateLimitConfig.LimiteEndpoint limiteLogin = new RateLimitConfig.LimiteEndpoint();
        limiteLogin.setMaxRequests(3);
        limiteLogin.setWindowMinutes(1);
        config.setLogin(limiteLogin);

        RateLimitConfig.LimiteEndpoint limiteRegistro = new RateLimitConfig.LimiteEndpoint();
        limiteRegistro.setMaxRequests(2);
        limiteRegistro.setWindowMinutes(1);
        config.setRegistro(limiteRegistro);

        filtro = new FiltroRateLimit(config);
        cadenaFiltros = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Petición a ruta no protegida pasa libremente sin consumir tokens")
    void rutaNoProtegida_PasaLibremente() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/empleados");
        peticion.setRemoteAddr(IP_CLIENTE);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);

        verify(cadenaFiltros, times(1)).doFilter(peticion, respuesta);
        assertEquals(200, respuesta.getStatus());
    }

    @Test
    @DisplayName("Peticiones dentro del límite de login-empresa pasan correctamente")
    void loginEmpresa_DentroDelLimite_PasaCorrectamente() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
            peticion.setRemoteAddr(IP_CLIENTE);
            MockHttpServletResponse respuesta = new MockHttpServletResponse();

            filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);

            assertEquals(200, respuesta.getStatus(),
                    "La petición " + (i + 1) + " debería pasar correctamente");
        }
        verify(cadenaFiltros, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Petición que supera el límite en login-empresa recibe 429")
    void loginEmpresa_SuperaLimite_Recibe429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
            peticion.setRemoteAddr(IP_CLIENTE);
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);
        }

        MockHttpServletRequest peticionExtra = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
        peticionExtra.setRemoteAddr(IP_CLIENTE);
        MockHttpServletResponse respuestaExtra = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionExtra, respuestaExtra, cadenaFiltros);

        assertEquals(429, respuestaExtra.getStatus());
        String cuerpo = respuestaExtra.getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cuerpo).contains("TOO_MANY_REQUESTS");
        verify(cadenaFiltros, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Contadores de login-empresa y login-empleado son independientes por endpoint")
    void contadoresDeEndpointsSonIndependientes() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
            peticion.setRemoteAddr(IP_CLIENTE);
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);
        }

        MockHttpServletRequest peticionEmpleado = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPLEADO);
        peticionEmpleado.setRemoteAddr(IP_CLIENTE);
        MockHttpServletResponse respuestaEmpleado = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionEmpleado, respuestaEmpleado, cadenaFiltros);

        assertEquals(200, respuestaEmpleado.getStatus(),
                "login-empleado debe tener su propio contador independiente de login-empresa");
    }

    @Test
    @DisplayName("Contadores de IPs distintas son independientes")
    void contadoresDeIPsSonIndependientes() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
            peticion.setRemoteAddr(IP_CLIENTE);
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);
        }

        MockHttpServletRequest peticionOtraIp = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
        peticionOtraIp.setRemoteAddr(IP_CLIENTE_2);
        MockHttpServletResponse respuestaOtraIp = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionOtraIp, respuestaOtraIp, cadenaFiltros);

        assertEquals(200, respuestaOtraIp.getStatus(),
                "Una IP distinta debe tener su propio contador independiente");
    }

    @Test
    @DisplayName("Extrae la IP original del cliente desde la cabecera X-Forwarded-For")
    void extraeIpDesdeXForwardedFor() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
        peticion.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");
        peticion.setRemoteAddr("172.16.0.1");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);

        assertEquals(200, respuesta.getStatus());

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest pet = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
            pet.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");
            pet.setRemoteAddr("172.16.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filtro.doFilterInternal(pet, resp, cadenaFiltros);
        }

        MockHttpServletRequest peticionExtra = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
        peticionExtra.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");
        peticionExtra.setRemoteAddr("172.16.0.1");
        MockHttpServletResponse respuestaExtra = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionExtra, respuestaExtra, cadenaFiltros);

        assertEquals(429, respuestaExtra.getStatus(),
                "El contador debe asignarse a la IP original 10.0.0.1, no al proxy 172.16.0.1");
    }

    @Test
    @DisplayName("El endpoint de registro usa su propio límite independiente del de login")
    void registroUsaSuPropioLimite() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("POST", RUTA_REGISTRO);
            peticion.setRemoteAddr(IP_CLIENTE);
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilterInternal(peticion, respuesta, cadenaFiltros);
            assertEquals(200, respuesta.getStatus());
        }

        MockHttpServletRequest peticionExtra = new MockHttpServletRequest("POST", RUTA_REGISTRO);
        peticionExtra.setRemoteAddr(IP_CLIENTE);
        MockHttpServletResponse respuestaExtra = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionExtra, respuestaExtra, cadenaFiltros);

        assertEquals(429, respuestaExtra.getStatus(),
                "El registro tiene límite de 2 intentos, el tercero debe recibir 429");

        MockHttpServletRequest peticionLogin = new MockHttpServletRequest("POST", RUTA_LOGIN_EMPRESA);
        peticionLogin.setRemoteAddr(IP_CLIENTE);
        MockHttpServletResponse respuestaLogin = new MockHttpServletResponse();

        filtro.doFilterInternal(peticionLogin, respuestaLogin, cadenaFiltros);

        assertEquals(200, respuestaLogin.getStatus(),
                "El login debe tener su propio contador, no verse afectado por el límite de registro");
    }
}
