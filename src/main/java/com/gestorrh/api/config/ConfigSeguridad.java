package com.gestorrh.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Clase central de configuración de seguridad de la aplicación.
 * Aquí dictamos las reglas de quién puede entrar y cómo encriptamos las contraseñas.
 */
@Configuration
@EnableWebSecurity
public class ConfigSeguridad {

    /**
     * Herramienta para encriptar contraseñas.
     */
    @Bean
    public PasswordEncoder passwordCodificador() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cadena de filtros de seguridad.
     */
    @Bean
    public SecurityFilterChain cadenaFiltrosSeguridad(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/error").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
