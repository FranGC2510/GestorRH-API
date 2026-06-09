package com.gestorrh.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de variables de entorno obligatorias para el perfil de producción.
 *
 * <p>
 * Se ejecuta durante el arranque de la aplicación, antes de que esta esté lista
 * para recibir tráfico. Si alguna de las variables obligatorias está ausente o vacía,
 * la aplicación falla de forma inmediata con un mensaje de error explícito y legible,
 * evitando errores crípticos en tiempo de ejecución.
 * </p>
 *
 * <p>
 * Aplica exclusivamente al perfil {@code prod}. En {@code dev} y {@code test}
 * las variables tienen valores por defecto y este bean no se carga.
 * </p>
 *
 * <p>
 * Para añadir nuevas variables obligatorias en el futuro (credenciales de Actuator,
 * Spring Boot Admin, etc.), basta con añadir el nombre exacto de la variable de
 * entorno a la lista {@code VARIABLES_OBLIGATORIAS}.
 * </p>
 *
 * @author Fco Javier García Cañero
 * @version 1.0
 */
@Slf4j
@Configuration
@Profile("prod")
public class ValidadorEntornoProduccion implements InitializingBean {

    /**
     * Lista de variables de entorno que deben estar presentes y no vacías
     * para que la aplicación arranque en producción.
     *
     * <p>
     * Para incorporar nuevas variables obligatorias en issues futuras,
     * añadir el nombre exacto de la variable de entorno a esta lista.
     * </p>
     */
    private static final List<String> VARIABLES_OBLIGATORIAS = List.of(
            "JWT_SECRET",
            "DB_USERNAME",
            "DB_PASSWORD"
    );

    private final Environment entorno;

    /**
     * Construye el validador inyectando el entorno de Spring para poder
     * consultar el valor de cada variable en tiempo de arranque.
     *
     * @param entorno el entorno de Spring que expone las propiedades y variables del sistema
     */
    public ValidadorEntornoProduccion(Environment entorno) {
        this.entorno = entorno;
    }

    /**
     * Valida que todas las variables de entorno obligatorias estén presentes y no vacías.
     *
     * <p>
     * Se ejecuta automáticamente por Spring tras la inicialización de las propiedades
     * del bean. Si falta alguna variable, registra un error por cada una ausente
     * y lanza una {@link IllegalStateException} que detiene el arranque de forma limpia.
     * </p>
     *
     * @throws IllegalStateException si una o más variables obligatorias están ausentes o vacías
     */
    @Override
    public void afterPropertiesSet() {
        List<String> ausentes = new ArrayList<>();

        for (String variable : VARIABLES_OBLIGATORIAS) {
            String valor = entorno.getProperty(variable);
            if (valor == null || valor.isBlank()) {
                ausentes.add(variable);
                log.error("[GESTORRH] Variable de entorno obligatoria ausente: {}. "
                        + "La aplicación no puede arrancar en perfil 'prod' sin esta variable.", variable);
            }
        }

        if (!ausentes.isEmpty()) {
            String resumen = String.join(", ", ausentes);
            throw new IllegalStateException(
                    "[GESTORRH] Arranque abortado. Faltan " + ausentes.size()
                            + " variable(s) de entorno obligatoria(s): " + resumen
                            + ". Revisa el archivo .env o las variables del sistema.");
        }

        log.info("[GESTORRH] Validación de entorno superada. "
                + "Todas las variables obligatorias están presentes.");
    }
}
