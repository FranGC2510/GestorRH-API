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
 * Servicio encargado de la gestión integral de JSON Web Tokens (JWT).
 * <p>
 * Proporciona funcionalidades para la generación de tokens tras una autenticación exitosa,
 * así como la validación y extracción de información (claims) de tokens recibidos en peticiones posteriores.
 * </p>
 */
@Service
public class ServicioJwt {

    /**
     * Clave secreta utilizada para la firma de los tokens JWT.
     * Se inyecta desde la configuración de la aplicación (Base64).
     */
    @Value("${jwt.secret}")
    private String claveSecreta;

    /**
     * Tiempo de expiración del token JWT en milisegundos.
     * Se inyecta desde la configuración de la aplicación.
     */
    @Value("${jwt.expiration}")
    private long expiracionJwt;

    /**
     * Genera un nuevo Token JWT para un usuario específico incluyendo información personalizada.
     * <p>
     * El token incluirá el correo del usuario como {@code subject}, la fecha de emisión,
     * la fecha de expiración y cualquier otro dato adicional proporcionado en el mapa {@code datosExtra}.
     * </p>
     *
     * @param email El correo electrónico del usuario, que servirá como identificador principal en el token.
     * @param datosExtra Mapa con información adicional que se desea incluir en el payload del token (ej: id, rol).
     * @return Una cadena de texto que representa el token JWT compactado y firmado.
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
     * Transforma la clave secreta codificada en Base64 en una clave criptográfica adecuada para algoritmos HMAC.
     *
     * @return Una {@link SecretKey} para realizar la firma o verificación de los tokens.
     */
    private SecretKey obtenerClaveFirma() {
        byte[] keyBytes = Decoders.BASE64.decode(claveSecreta);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extrae el identificador del usuario (subject) de un token JWT.
     *
     * @param token El token JWT del que se desea extraer la información.
     * @return El correo electrónico del usuario contenido en el token.
     */
    public String extraerEmail(String token) {
        return extraerTodosLosClaims(token).getSubject();
    }

    /**
     * Recupera el rol asignado al usuario desde el payload del token JWT.
     *
     * @param token El token JWT del que se desea extraer el rol.
     * @return El nombre del rol del usuario.
     */
    public String extraerRol(String token) {
        return extraerTodosLosClaims(token).get("rol", String.class);
    }

    /**
     * Recupera el identificador numérico único (ID) del usuario desde el token JWT.
     *
     * @param token El token JWT del que se desea extraer el ID.
     * @return El ID del usuario como un {@link Long}, o {@code null} si no está presente o no es válido.
     */
    public Long extraerId(String token) {
        Number id = extraerTodosLosClaims(token).get("id", Number.class);
        return id != null ? id.longValue() : null;
    }

    /**
     * Recupera el nombre de la empresa asociada al usuario desde el token JWT.
     *
     * @param token El token JWT del que se desea extraer el nombre de empresa.
     * @return El nombre de la empresa, o {@code null} si no está presente.
     */
    public String extraerNombreEmpresa(String token) {
        return extraerTodosLosClaims(token).get("nombreEmpresa", String.class);
    }

    /**
     * Valida la integridad y vigencia de un token JWT.
     * <p>
     * Comprueba que la firma del token sea correcta utilizando la clave secreta y que
     * la fecha de expiración no se haya superado.
     * </p>
     *
     * @param token El token JWT que se desea validar.
     * @return {@code true} si el token es válido y está vigente, {@code false} en caso contrario.
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
     * Método interno para parsear el token JWT y recuperar todos sus claims (información).
     * <p>
     * Utiliza la clave de firma para verificar la autenticidad del token antes de extraer su payload.
     * </p>
     *
     * @param token El token JWT a procesar.
     * @return Un objeto {@link Claims} que contiene toda la información del token.
     */
    private Claims extraerTodosLosClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerClaveFirma())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
