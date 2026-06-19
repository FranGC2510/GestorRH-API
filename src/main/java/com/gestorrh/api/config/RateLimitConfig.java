package com.gestorrh.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de los límites de peticiones para los endpoints públicos
 * de autenticación y registro. Los valores se leen desde variables de entorno
 * con fallback a los valores por defecto definidos en application.yml.
 *
 * <p>Para modificar los límites en producción sin redespliegue, ajusta las
 * variables de entorno correspondientes en el fichero .env del servidor.</p>
 *
 * @author Fco Javier García Cañero
 * @version 1.0
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    /**
     * Límites aplicables a los endpoints de login de empresa y empleado.
     */
    private LimiteEndpoint login = new LimiteEndpoint();

    /**
     * Límites aplicables al endpoint de registro de empresa.
     */
    private LimiteEndpoint registro = new LimiteEndpoint();

    /**
     * Parámetros de límite de peticiones para un endpoint concreto.
     */
    @Getter
    @Setter
    public static class LimiteEndpoint {

        /**
         * Número máximo de peticiones permitidas en la ventana de tiempo.
         */
        private int maxRequests;

        /**
         * Duración de la ventana de tiempo en minutos tras la cual
         * el contador se reinicia completamente.
         */
        private int windowMinutes;
    }
}
