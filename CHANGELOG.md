# Changelog

Todos los cambios relevantes de este proyecto se documentan en este fichero.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es/1.0.0/),
y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).
 
---

## [Unreleased]

### Security
- Implementado rate limiting en endpoints públicos de autenticación y registro
  mediante Bucket4j para prevenir ataques de fuerza bruta (#133)
- Los endpoints `/api/auth/login-empresa`, `/api/auth/login-empleado` y
  `/api/empresas/registro` quedan limitados por IP con contadores independientes
  por endpoint (#133)
- Al superar el límite se devuelve HTTP 429 con el formato estándar
  `RespuestaErrorDTO` y código `TOO_MANY_REQUESTS` (#133)
- Los límites son configurables mediante variables de entorno
  (`RATE_LIMIT_LOGIN_MAX_REQUESTS`, `RATE_LIMIT_LOGIN_WINDOW_MINUTES`,
  `RATE_LIMIT_REGISTRO_MAX_REQUESTS`, `RATE_LIMIT_REGISTRO_WINDOW_MINUTES`)
  con valores por defecto de 10 intentos cada 5 minutos para login y
  5 intentos cada 10 minutos para registro (#133)
- Añadidas las nuevas variables de configuración a `.env.example` (#133)

### Infraestructura
- Añadido appender de consola (texto plano) al perfil `prod` en `logback-spring.xml`, permitiendo visualizar los logs de aplicación mediante `docker logs` en producción (#159)
- Añadido volumen nombrado `api_logs` montado en `/app/logs` del servicio `api` en `docker-compose.prod.yml`, garantizando la persistencia de los logs entre recreaciones del contenedor (#159)
- Corregida inconsistencia en `application-prod.yml`: `logging.level.root` actualizado a `WARN` para reflejar el nivel efectivo que ya aplicaba `logback-spring.xml` (#159)
- Generación automática de datos de build (`build-info`) e información de Java y sistema operativo en el endpoint `/actuator/info`, visible en el panel de Spring Boot Admin
- Añadido contenedor `gestorrh-admin` en `docker-compose.prod.yml` con Spring Boot Admin conectado a los endpoints de Actuator de la API (#131)
- Añadidas variables `ACTUATOR_USER` y `ACTUATOR_PASSWORD` al servicio `api` en `docker-compose.prod.yml` (#131)
- Configurado bloque `location /admin` en Nginx para exponer el dashboard en `https://gestorrh.ddns.net/admin` (#131)
- Añadidas `ADMIN_USER` y `ADMIN_PASSWORD` como variables obligatorias en `ValidadorEntornoProduccion` y en `.env.example` (#131)
- Implementado logging estructurado en formato JSON con `logstash-logback-encoder` para ambos perfiles (`dev` y `prod`), compatible con Spring Boot Admin y herramientas externas de análisis de logs (#132)
- Configurado `logback-spring.xml` con appenders por perfil: JSON en fichero para `dev` y `prod`, texto plano en consola solo para `dev`, solo consola para `test` (#132)
- Perfil `test` aislado del fichero de log añadiendo `@ActiveProfiles("test")` en `GestorRhApiApplicationTests`, evitando mezcla de logs entre tests y desarrollo (#132)
- Añadidos logs de auditoría para respuestas 401 y 403 en `ConfigSeguridad` (#132)
- Añadidos códigos HTTP explícitos en todos los manejadores de `GestorExcepciones` para facilitar el filtrado en producción (#132)
- Eliminados logs previos en formato texto plano para garantizar ficheros con estructura JSON uniforme (#132)
- Integrado Spring Boot Actuator con endpoints de monitorización (`/actuator/health`, `info`, `metrics`, `env`, `loggers`) protegidos con HTTP Basic Auth mediante `AuthenticationManager` propio, completamente aislado del BCryptPasswordEncoder y del sistema JWT de la aplicación (#130)
- Credenciales de Actuator (`ACTUATOR_USER`, `ACTUATOR_PASSWORD`) leídas exclusivamente desde variables de entorno, añadidas como obligatorias en `ValidadorEntornoProduccion` y documentadas en `.env.example` (#130)
- Configurado `healthcheck` en `docker-compose.prod.yml` usando `/actuator/health` para que Docker refleje el estado real del contenedor (#130)
- Añadido `ValidadorEntornoProduccion` que falla explícitamente con mensajes legibles si alguna variable de entorno obligatoria (`JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`) está ausente al arrancar en perfil `prod` (#128)
- Integrado Flyway como gestor de migraciones de base de datos (#127)
- Creado `V1__esquema_inicial.sql` con el esquema completo generado desde la BD real en estado v1.4.1
- `ddl-auto: update` eliminado de todos los perfiles; Hibernate pasa a modo `validate`
- Tests migrados de H2 a Testcontainers con PostgreSQL real para garantizar paridad con producción
- Perfil `api-docs` configurado con `spring.flyway.enabled=false` para evitar conflictos en generación de Swagger
- Pipeline CD extraído a workflow independiente `cd.yml` con soporte para dispatch manual con tag de entrada (#148)
- El deploy ahora copia `docker-compose.prod.yml` al servidor antes de levantar los contenedores, garantizando que los cambios de configuración se aplican en cada despliegue (#148)

### Fixed
- Corregido el healthcheck del contenedor `admin` en `docker-compose.prod.yml`, que apuntaba a un endpoint de Actuator protegido y devolvía 401; ahora comprueba una ruta pública del panel, evitando el estado `unhealthy` (#131)
- Propagadas las variables `ADMIN_USER` y `ADMIN_PASSWORD` al servicio `api` en `docker-compose.prod.yml`, necesarias para el registro del cliente Spring Boot Admin y la validación de entorno en producción (#131)
- Permitida la sobrescritura del `docker-compose.prod.yml` en el servidor durante el despliegue, evitando el fallo cuando el fichero ya existe (#NNN)

---
## [1.4.1] - 2026-06-09

### Infraestructura
- Configurado HTTPS en el servidor mediante Nginx como reverse proxy y certificado Let's Encrypt con renovación automática vía Certbot (#129)
- El acceso por HTTP redirige automáticamente a HTTPS
- Puerto 8080 restringido a localhost; todo el tráfico exterior pasa por Nginx
- Añadida `forward-headers-strategy: framework` en `application.yml` para el correcto procesamiento de cabeceras `X-Forwarded-Proto`
### Documentación
- Creación de `CHANGELOG.md` siguiendo el estándar Keep a Changelog (#144)
---

## [1.4.0] - 2026-06-02

### Añadido
- Nombre de empresa incluido en el token JWT y en la respuesta de login del empleado (#121)
- Validación que impide solicitar una ausencia en fechas en las que el empleado ya tiene fichajes registrados (#120)
### Corregido
- Al aprobar una ausencia, ahora solo se eliminan las asignaciones de turno que no tienen fichajes asociados, evitando el error de clave foránea (#119)
- Normalización de la persistencia y serialización de fechas a UTC en toda la API (#110)
- Corrección de `@PreAuthorize` en endpoints `/me` y de gestión para que el rol `SUPERVISOR` funcione correctamente (#114)
- Corrección de `obtenerEmpresaAutenticada` en `TurnoService` para que soporte el rol `SUPERVISOR` (#117)
- Corrección de la validación de departamento en la consulta de fichajes filtrada por supervisor (#118)
- Añadido `@Transactional` en `loginEmpleado` para evitar `LazyInitializationException` al acceder a la empresa (#122)
---

## [1.3.0] - 2026-05-19

### Añadido
- Nuevo campo `eliminarJustificante` en `PeticionAusenciaDTO` que permite al empleado eliminar el justificante adjunto de una ausencia en estado `SOLICITADA` sin necesidad de adjuntar un archivo nuevo (#111)
---

## [1.2.0] - 2026-05-18

### Añadido
- El rol `SUPERVISOR` puede acceder a `GET /api/empleados` con filtrado automático y transparente por departamento, sin parámetros adicionales (#86)
### Corregido
- Soporte para turnos nocturnos: `TurnoService` acepta turnos con `horaInicio >= 16:00` y `horaFin <= 08:00` (#105)
- Corrección del `DataSeeder` y de la query `obtenerTopRetrasos` en `FichajeRepository`
- Pipeline de deploy en Oracle: añadido `script_stop` para detectar fallos reales en el paso de despliegue
---

## [1.1.3] - 2026-04-28

### Corregido
- Añadido prefijo `/api/auth` a las rutas públicas de autenticación en el filtro JWT y en la configuración de seguridad
### Infraestructura
- Añadido `--force-recreate` al paso de deploy en Oracle para garantizar que el contenedor se actualiza en cada despliegue
---

## [1.1.2] - 2026-04-28

### Corregido
- Las rutas públicas de autenticación y registro ahora se excluyen correctamente del filtro JWT, resolviendo el bloqueo en el login (#95)
---

## [1.1.1] - 2026-04-22

### Corregido
- Añadidas rutas `/api/swagger-ui/**` a los permisos públicos de Spring Security para que Swagger UI sea accesible sin token
---

## [1.1.0] - 2026-04-22

### Añadido
- Endpoint de reset de contraseña para RRHH: `PUT /api/empleados/{id}/reset-password`, accesible solo para el rol `EMPRESA` (#75)
- Endpoint BFF `GET /api/fichajes/estado-actual` para el dashboard móvil, que consolida en una sola llamada si el empleado tiene turno hoy y si ya ha fichado (#82)
- Parámetros `fechaInicio` y `fechaFin` ahora son opcionales en `GET /api/fichajes`; si se omiten se aplican valores por defecto (#87)
- Documentación del campo `justificante` en `RespuestaAusenciaDTO` con descripción de uso para descarga (#88)
- Campos `horaInicio` y `horaFin` añadidos a `RespuestaAsignacionTurnoDTO`
### Corregido
- El servidor devuelve `401` en lugar de `403` cuando el token JWT está caducado, corrigiendo el flujo de reautenticación del cliente Android (#91)
- Los DTOs de fichaje aceptan coordenadas nulas para teletrabajo; el fichaje de salida presencial obliga a estar dentro del radio de la sede
- Varios arreglos en el pipeline CI para resolver errores de conexión en la generación de Swagger y permisos en GitHub Pages
### Infraestructura
- Configuración del pipeline CD para despliegue automático en Oracle Cloud con imagen Docker ARM64
- Optimización del `Dockerfile` para construcción multi-stage
---

## [1.0.1] - 2026-04-13

### Corregido
- Clave JWT por defecto corregida a formato Base64 estricto en `application-dev.yml` y `application-test.yml` para evitar `DecodingException` en local (#76)
- Corrección de enlace en la documentación del `README.md`
---

## [1.0.0] - 2026-04-08

### Añadido
- Primera versión estable lista para integración con clientes y despliegue en producción
- Licencia Apache 2.0 aplicada al repositorio
- Refactorización final de `FichajeService` y `ReporteController` para cumplir con arquitectura N-Capas
- Preparación agnóstica para despliegue en producción: variables de entorno, perfiles `dev`/`test`/`prod`, fail-fast intencionado en producción
- Control de concurrencia optimista con `@Version` en entidades `AsignacionTurno` y `Ausencia`
- Cobertura de tests completada para `FichajeService`, `EmpresaService`, `FileStorageService`, `GeofencingService` y `ReportePdfService`
---

## [0.9.0] - sin fecha de release (tag de hito)

### Añadido
- Portal de documentación unificado (Swagger UI + Javadoc) desplegado automáticamente en GitHub Pages mediante CI/CD
- Sistema de logging y auditoría en todos los servicios core con `@Slf4j`
- Javadoc completo en controladores, servicios y DTOs con descripción de paquetes
- Refactorización global de respuestas de error en Swagger con anotaciones propias (`@ApiErroresLectura`, `@ApiErroresEscritura`, `@ApiErroresAccion`)
- Optimización de consultas N+1 con `JOIN FETCH` en repositorios JPA
### Corregido
- Códigos de respuesta HTTP corregidos en Swagger (200, 201, 204) para cada endpoint
---

## [0.8.0] - sin fecha de release (tag de hito)

### Añadido
- Módulo de estadísticas y KPIs para dashboards: empleados por departamento, ausencias por tipo/estado, ranking de retrasos (`GET /api/estadisticas/**`)
- Generación de reportes PDF (detalle y resumen) con OpenPDF (`GET /api/reportes/**/pdf`)
- Subida, validación y descarga de justificantes médicos en ausencias (multipart, extensiones permitidas: pdf, jpg, jpeg, png)
- Modificación manual auditada de fichajes con registro de incidencias (`PUT /api/fichajes/{id}/modificar`)
- Motor de cálculo de horas extras con margen de cortesía de 15 minutos
- Endpoints de reportes en formato JSON (detalle y resumen) con filtros por fecha y empleado
- Tests unitarios para `GeofencingService` y cálculo de horas extras en `ReporteService`
---

## [0.1.0] - sin fecha de release (tag de hito)

### Añadido
- Estructura base del proyecto Spring Boot con perfiles `dev`, `test` y configuración Docker Compose para PostgreSQL local
- Entidades JPA: `Empresa`, `Empleado`, `Turno`, `AsignacionTurno`, `Fichaje`, `Ausencia` con relaciones y enums de dominio
- Autenticación stateless con JWT: endpoints `POST /api/auth/login-empresa` y `POST /api/auth/login-empleado`
- Autorización RBAC con `@PreAuthorize` para los roles `EMPRESA`, `SUPERVISOR` y `EMPLEADO`
- CRUD completo de empleados con aislamiento multi-tenant, baja programada y readmisión (`/api/empleados`)
- Perfil propio del empleado y cambio de contraseña seguro (`GET /api/empleados/me`, `PUT /api/empleados/me/contrasena`)
- CRUD completo de turnos con validación de horas (`/api/turnos`)
- CRUD completo de asignaciones de turno con validación de jornada máxima (8h) y solapamiento con ausencias (`/api/asignaciones`)
- Gestión completa de ausencias: solicitud, edición, cancelación y flujo de aprobación/rechazo (`/api/ausencias`)
- Registro de fichajes de entrada y salida con validación de geovallado (fórmula Haversine), detección de retrasos y salidas anticipadas (`/api/fichajes`)
- CRUD de empresa con configuración de sede y radio de geovallado (`/api/empresas`)
- Gestor global de excepciones (`@RestControllerAdvice`) con respuesta estandarizada `RespuestaErrorDTO`
- Tarea programada (CRON diario) para desactivar empleados con contrato expirado
- Pipeline CI con GitHub Actions: tests automáticos en H2, generación de Swagger/Javadoc y despliegue en GitHub Pages
- Tests unitarios con Mockito para: `AutenticacionService`, `EmpleadoService`, `TurnoService`, `AsignacionTurnoService`, `AusenciaService`
- `DataSeeder` para inyección automática de datos de prueba en entorno `dev`
---

[Unreleased]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.4.1...HEAD
[1.4.1]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.1.3...v1.2.0
[1.1.3]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/GestorRH-Multiplataforma/GestorRH-API/compare/v0.1.0...v1.0.0