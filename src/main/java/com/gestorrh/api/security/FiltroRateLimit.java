package com.gestorrh.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestorrh.api.config.RateLimitConfig;
import com.gestorrh.api.dto.error.RespuestaErrorDTO;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de limitación de peticiones para los endpoints públicos de autenticación
 * y registro. Previene ataques de fuerza bruta limitando el número de intentos
 * por dirección IP y endpoint en una ventana de tiempo configurable.
 *
 * <p>El conteo es independiente por cada combinación IP + endpoint, de forma que
 * agotar el límite en un endpoint no afecta al contador de los demás.</p>
 *
 * <p>Cuando se supera el límite se devuelve HTTP 429 siguiendo el contrato
 * estándar de errores de la API mediante {@link RespuestaErrorDTO}.</p>
 *
 * <p>La implementación usa almacenamiento en memoria mediante {@link ConcurrentHashMap}.
 * Si en el futuro se requiere escala horizontal, el proveedor de buckets puede
 * sustituirse por una implementación Redis sin modificar este filtro.</p>
 *
 * @author Fco Javier García Cañero
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiltroRateLimit extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String RUTA_LOGIN_EMPRESA  = "/api/auth/login-empresa";
    private static final String RUTA_LOGIN_EMPLEADO = "/api/auth/login-empleado";
    private static final String RUTA_REGISTRO       = "/api/empresas/registro";

    /**
     * Ejecuta la lógica de rate limiting para cada petición entrante.
     * Si la ruta no está protegida, la petición pasa directamente a la
     * cadena de filtros sin ningún procesamiento adicional.
     *
     * @param peticion      la petición HTTP entrante
     * @param respuesta     la respuesta HTTP saliente
     * @param cadenaFiltros la cadena de filtros de Spring Security
     * @throws ServletException si ocurre un error en el procesamiento del servlet
     * @throws IOException      si ocurre un error de entrada/salida
     */
    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest peticion,
            @Nonnull HttpServletResponse respuesta,
            @Nonnull FilterChain cadenaFiltros) throws ServletException, IOException {

        String ruta = peticion.getRequestURI();

        if (!esRutaProtegida(ruta)) {
            cadenaFiltros.doFilter(peticion, respuesta);
            return;
        }

        String ip = extraerIpCliente(peticion);
        String claveUnica = ip + "::" + ruta;
        Bucket bucket = buckets.computeIfAbsent(claveUnica, k -> crearBucket(ruta));

        if (bucket.tryConsume(1)) {
            cadenaFiltros.doFilter(peticion, respuesta);
        } else {
            log.warn("RATE LIMIT superado [429]: IP='{}' bloqueada en '{}'.", ip, ruta);
            escribirRespuesta429(respuesta, peticion);
        }
    }

    /**
     * Determina si la ruta de la petición está sujeta a rate limiting.
     *
     * @param ruta la URI de la petición
     * @return true si la ruta debe ser limitada
     */
    private boolean esRutaProtegida(String ruta) {
        return RUTA_LOGIN_EMPRESA.equals(ruta)
                || RUTA_LOGIN_EMPLEADO.equals(ruta)
                || RUTA_REGISTRO.equals(ruta);
    }

    /**
     * Extrae la dirección IP real del cliente teniendo en cuenta que la
     * aplicación está desplegada detrás de un proxy Nginx que añade la
     * cabecera {@code X-Forwarded-For}.
     *
     * <p>Si la cabecera contiene múltiples IPs separadas por coma
     * (cadena de proxies), se toma la primera, que corresponde al
     * cliente original.</p>
     *
     * @param peticion la petición HTTP
     * @return la dirección IP del cliente original
     */
    private String extraerIpCliente(HttpServletRequest peticion) {
        String xForwardedFor = peticion.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    /**
     * Crea un nuevo {@link Bucket} con la configuración de límites correspondiente
     * a la ruta indicada. Los endpoints de login comparten configuración entre sí
     * pero mantienen contadores independientes por clave IP+ruta.
     *
     * @param ruta la URI del endpoint para el que se crea el bucket
     * @return un bucket configurado con los límites adecuados
     */
    private Bucket crearBucket(String ruta) {
        RateLimitConfig.LimiteEndpoint limites = RUTA_REGISTRO.equals(ruta)
                ? rateLimitConfig.getRegistro()
                : rateLimitConfig.getLogin();

        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limites.getMaxRequests())
                .refillIntervally(limites.getMaxRequests(),
                        Duration.ofMinutes(limites.getWindowMinutes()))
                .build();

        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Escribe la respuesta HTTP 429 en formato JSON siguiendo el contrato
     * estándar de errores de la API.
     *
     * @param respuesta la respuesta HTTP donde escribir el error
     * @param peticion  la petición original para extraer la URI
     * @throws IOException si ocurre un error al escribir en el stream de respuesta
     */
    private void escribirRespuesta429(HttpServletResponse respuesta,
                                      HttpServletRequest peticion) throws IOException {
        RespuestaErrorDTO error = RespuestaErrorDTO.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .errorCode("TOO_MANY_REQUESTS")
                .message("Has superado el límite de intentos permitidos. Por favor, espera unos minutos antes de volver a intentarlo.")
                .path(peticion.getRequestURI())
                .build();

        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
