package com.gestorrh.api.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Componente de filtrado de seguridad para la interceptación de peticiones HTTP.
 * <p>
 * Este filtro se ejecuta una vez por cada solicitud (extiende de {@link OncePerRequestFilter})
 * para validar la presencia y validez de un token JWT en el encabezado {@code Authorization}.
 * Si el token es válido, establece la autenticación del usuario en el {@link SecurityContextHolder}.
 * Si el token está caducado, malformado o es inválido, el contexto queda sin autenticación y
 * Spring Security delega la respuesta en el {@code AuthenticationEntryPoint} configurado,
 * devolviendo 401 en lugar de 403.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiltroJwt extends OncePerRequestFilter {

    /**
     * Servicio para la gestión de operaciones relacionadas con JWT.
     */
    private final ServicioJwt servicioJwt;

    /**
     * Realiza el filtrado interno de la petición para gestionar la seguridad basada en JWT.
     *
     * @param peticion La solicitud HTTP recibida.
     * @param respuesta La respuesta HTTP que se enviará.
     * @param cadenaFiltros La cadena de filtros de seguridad.
     * @throws ServletException Si ocurre un error en el procesamiento del servlet.
     * @throws IOException Si ocurre un error de entrada/salida.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest peticion,
            @NonNull HttpServletResponse respuesta,
            @NonNull FilterChain cadenaFiltros) throws ServletException, IOException {

        final String ruta = peticion.getRequestURI();
        if (esRutaPublica(ruta)) {
            cadenaFiltros.doFilter(peticion, respuesta);
            return;
        }

        final String encabezadoAutenticacion = peticion.getHeader("Authorization");

        if (encabezadoAutenticacion == null || !encabezadoAutenticacion.startsWith("Bearer ")) {
            cadenaFiltros.doFilter(peticion, respuesta);
            return;
        }

        final String jwt = encabezadoAutenticacion.substring(7);

        try {
            String correoUsuario = servicioJwt.extraerEmail(jwt);
            String rolToken = servicioJwt.extraerRol(jwt);

            if (correoUsuario != null && SecurityContextHolder.getContext().getAuthentication() == null) {
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
        } catch (ExpiredJwtException e) {
            log.debug("Token JWT caducado: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            peticion.setAttribute("jwt_expired", true);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Error al validar el token JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        cadenaFiltros.doFilter(peticion, respuesta);
    }

    /**
     * Determina si la ruta solicitada corresponde a un endpoint público
     * que no requiere validación de JWT (autenticación, registro, documentación).
     *
     * @param ruta la URI de la petición.
     * @return {@code true} si la ruta es pública y debe saltarse el filtro.
     */
    private boolean esRutaPublica(String ruta) {
        if (ruta == null) {
            return false;
        }
        return ruta.startsWith("/auth/")
                || ruta.startsWith("/api/auth/")
                || ruta.equals("/api/empresas/registro")
                || ruta.equals("/error")
                || ruta.startsWith("/v3/api-docs")
                || ruta.startsWith("/swagger-ui")
                || ruta.equals("/swagger-ui.html")
                || ruta.startsWith("/actuator");
    }
}
