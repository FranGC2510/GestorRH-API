package com.gestorrh.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Swagger/OpenAPI para la documentación de la API.
 * 
 * <p>Esta clase define la información general de la API, el esquema de seguridad
 * basado en JWT y personalizaciones globales para las respuestas de error.</p>
 */
@Configuration
public class ConfigSwagger {

    /**
     * Define la configuración base de OpenAPI para el sistema GestorRH.
     * 
     * <p>Configura el título, versión, descripción detallada del flujo de uso
     * y los requisitos de seguridad globales (Bearer Token).</p>
     * 
     * @return instancia de OpenAPI con la configuración del proyecto
     */
    @Bean
    public OpenAPI gestorRhOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("GestorRH API")
                        .version("1.4.1")
                        .description("Documentación interactiva de la API REST para el sistema GestorRH.<br><br>" +

                                "<h3>Flujo de inicio rápido (Ciclo Natural)</h3>" +
                                "<ol>" +
                                "<li><b>Registro Empresa:</b> Registra una nueva empresa en el bloque 2 o usa las credenciales de prueba.</li>" +
                                "<li><b>Login:</b> Ve al bloque 1 (Autenticación), haz login con la empresa, copia el Token JWT y pégalo en el botón <b>Authorize</b> de arriba a la derecha.</li>" +
                                "<li><b>Configuración:</b> Ya como Empresa, crea Empleados (bloque 3) y Turnos (bloque 4).</li>" +
                                "<li><b>Operación:</b> Haz login como el Empleado recién creado para obtener su Token, ponlo en <b>Authorize</b> y ya podrás Fichar (bloque 6) o pedir Ausencias (bloque 7).</li>" +
                                "</ol>" +

                                "<h3>Credenciales de prueba (Entorno Local/Dev)</h3>" +
                                "El sistema inicializa automáticamente estos datos para facilitar las pruebas:" +
                                "<ul>" +
                                "<li><b>EMPRESA:</b> <code>admin@tech.com</code> <i>(Usar Login Empresa)</i> | Contraseña: <code>123456</code></li><br>" +
                                "<li><b>SUPERVISOR:</b> <code>super@tech.com</code> <i>(Usar Login Empleado)</i> | Contraseña: <code>123456</code></li><br>" +
                                "<li><b>EMPLEADO:</b> <code>empleado@tech.com</code> <i>(Usar Login Empleado)</i> | Contraseña: <code>123456</code></li>" +
                                "</ul><br>" +

                                "<b>Instrucciones de Seguridad:</b> Los endpoints con el candado requieren que introduzcas el Token correspondiente en el botón verde <b>Authorize</b>.")
                        .contact(new Contact().name("Equipo de Desarrollo GestorRH")))

                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))

                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Pega aquí directamente tu token JWT (sin la palabra 'Bearer ').")));
    }
}
