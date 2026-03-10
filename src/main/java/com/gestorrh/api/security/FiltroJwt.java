package com.gestorrh.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Filtro que se ejecuta una vez por cada petición HTTP.
 * Intercepta el Token JWT, lo valida y establece la seguridad en el contexto de Spring.
 */
@Component
@RequiredArgsConstructor
public class FiltroJwt extends OncePerRequestFilter {

    private final ServicioJwt servicioJwt;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest peticion,
            @NonNull HttpServletResponse respuesta,
            @NonNull FilterChain cadenaFiltros) throws ServletException, IOException {

        final String encabezadoAutenticacion = peticion.getHeader("Authorization");
        final String jwt;
        final String correoUsuario;

        if (encabezadoAutenticacion == null || !encabezadoAutenticacion.startsWith("Bearer ")) {
            cadenaFiltros.doFilter(peticion, respuesta);
            return;
        }

        jwt = encabezadoAutenticacion.substring(7);

        try {
            correoUsuario = servicioJwt.extraerEmail(jwt);
            String rolToken = servicioJwt.extraerRol(jwt);

            if (correoUsuario != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (servicioJwt.esTokenValido(jwt)) {
                    List<GrantedAuthority> autoridades = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + rolToken)
                    );
                    UsernamePasswordAuthenticationToken tokenAutenticacion = new UsernamePasswordAuthenticationToken(
                            correoUsuario,
                            null,
                            autoridades
                    );
                    tokenAutenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(peticion));

                    SecurityContextHolder.getContext().setAuthentication(tokenAutenticacion);
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando el JWT: " + e.getMessage());
        }

        cadenaFiltros.doFilter(peticion, respuesta);
    }
}
