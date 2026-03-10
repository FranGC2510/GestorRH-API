package com.gestorrh.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * Servicio encargado de la creación y validación de los Tokens JWT.
 */
@Service
public class ServicioJwt {

    @Value("${jwt.secret}")
    private String claveSecreta;

    @Value("${jwt.expiration}")
    private long expiracionJwt;

    /**
     * Genera un nuevo Token JWT con los datos del usuario.
     * @param email El email del usuario.
     * @param datosExtra Datos adicionales como el ID, Nombre o Rol.
     * @return El Token JWT (String).
     */
    public String generarToken(String email, Map<String, Object> datosExtra) {
        return Jwts.builder()
                .claims(datosExtra)
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiracionJwt))
                .signWith(obtenerClaveFirma())
                .compact();
    }

    /**
     * Traduce nuestra clave secreta de texto (Base64) a una clave criptográfica real.
     */
    private SecretKey obtenerClaveFirma() {
        byte[] keyBytes = Decoders.BASE64.decode(claveSecreta);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extrae el email del token.
     */
    public String extraerEmail(String token) {
        return extraerTodosLosClaims(token).getSubject();
    }

    /**
     * Extrae el rol del token.
     */
    public String extraerRol(String token) {
        return extraerTodosLosClaims(token).get("rol", String.class);
    }

    /**
     * Extrae el ID del usuario del token.
     */
    public Long extraerId(String token) {
        Number id = extraerTodosLosClaims(token).get("id", Number.class);
        return id != null ? id.longValue() : null;
    }

    /**
     * Verifica si un token es válido criptográficamente y no ha expirado.
     */
    public boolean esTokenValido(String token) {
        try {
            extraerTodosLosClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Método privado que hace el trabajo duro de desencriptar el token con nuestra clave secreta.
     */
    private Claims extraerTodosLosClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerClaveFirma())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
