package com.gestorrh.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestorrh.api.dto.error.RespuestaErrorDTO;
import com.gestorrh.api.security.FiltroJwt;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Clase de configuración global de seguridad de Spring Security.
 * <p>
 * Define las políticas de acceso a los diferentes endpoints de la API, la gestión de sesiones (sin estado),
 * el codificador de contraseñas y la integración del filtro personalizado para tokens JWT.
 * </p>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class ConfigSeguridad {

    /**
     * Filtro personalizado para la validación de tokens JWT.
     */
    private final FiltroJwt filtroJwt;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Define el bean para el codificador de contraseñas de la aplicación.
     *
     * @return Una instancia de {@link PasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordCodificador() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Entry point que responde con 401 Unauthorized en formato JSON cuando la petición
     * llega sin autenticación válida (token ausente, caducado o malformado).
     * <p>
     * Esto resuelve la incoherencia por la que Spring Security devolvía 403 al no encontrar
     * usuario autenticado, rompiendo el flujo de reautenticación del cliente Android, que
     * espera 401 para detectar tokens caducados.
     * </p>
     */
    @Bean
    public AuthenticationEntryPoint puntoEntradaAutenticacion() {
        return (request, response, authException) -> {
            boolean tokenCaducado = Boolean.TRUE.equals(request.getAttribute("jwt_expired"));
            String mensaje = tokenCaducado
                    ? "Token JWT caducado"
                    : "No autenticado: token ausente o inválido";

            log.warn("ACCESO DENEGADO (401): {} {} - {}", request.getMethod(), request.getRequestURI(), mensaje);

            RespuestaErrorDTO error = RespuestaErrorDTO.builder()
                    .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .status(HttpServletResponse.SC_UNAUTHORIZED)
                    .errorCode(tokenCaducado ? "TOKEN_EXPIRADO" : "NO_AUTENTICADO")
                    .message(mensaje)
                    .path(request.getRequestURI())
                    .build();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(error));
        };
    }

    /**
     * Handler que responde con 403 Forbidden en formato JSON cuando el usuario está
     * autenticado pero carece de los permisos necesarios para acceder al recurso.
     */
    @Bean
    public AccessDeniedHandler manejadorAccesoDenegado() {
        return (request, response, accessDeniedException) -> {
            log.warn("ACCESO DENEGADO (403): {} {} - Sin permisos suficientes", request.getMethod(), request.getRequestURI());

            RespuestaErrorDTO error = RespuestaErrorDTO.builder()
                    .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .status(HttpServletResponse.SC_FORBIDDEN)
                    .errorCode("ACCESO_DENEGADO")
                    .message("No tiene permisos para acceder a este recurso")
                    .path(request.getRequestURI())
                    .build();

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(error));
        };
    }

    /**
     * Cadena de seguridad exclusiva para los endpoints de Actuator.
     * <p>
     * Tiene mayor precedencia que la cadena JWT (@Order(1) vs @Order(2)) e intercepta
     * únicamente las rutas bajo /actuator/**. La autenticación se realiza mediante
     * HTTP Basic con credenciales propias del administrador del sistema, completamente
     * independientes del sistema JWT y del BCryptPasswordEncoder de la aplicación.
     * </p>
     *
     * @param http     El objeto {@link HttpSecurity} para configurar la seguridad web.
     * @param username Nombre de usuario leído desde la variable de entorno ACTUATOR_USER.
     * @param password Contraseña leída desde la variable de entorno ACTUATOR_PASSWORD.
     * @return La cadena de filtros de Actuator configurada.
     * @throws Exception Si ocurre algún error durante la configuración.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain cadenaFiltrosActuator(
            HttpSecurity http,
            @Value("${spring.security.user.name:actuator}") String username,
            @Value("${spring.security.user.password:actuator}") String password) throws Exception {

        UserDetailsService uds = new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(password)
                        .roles("ACTUATOR")
                        .build()
        );

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());

        AuthenticationManager authManager = new ProviderManager(provider);

        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().hasRole("ACTUATOR")
                )
                .httpBasic(Customizer.withDefaults())
                .authenticationManager(authManager)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Configura la cadena de filtros de seguridad (Security Filter Chain).
     *
     * @param http El objeto {@link HttpSecurity} para configurar la seguridad web.
     * @return La cadena de filtros configurada.
     * @throws Exception Si ocurre algún error durante la configuración.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain cadenaFiltrosSeguridad(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/api/auth/**", "/error", "/api/empresas/registro").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v3/api-docs/**", "/api/swagger-ui/**", "/api/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(puntoEntradaAutenticacion())
                        .accessDeniedHandler(manejadorAccesoDenegado())
                )
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
