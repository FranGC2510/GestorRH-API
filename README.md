# GestorRH - API REST
 
[![CI Pipeline](https://github.com/GestorRH-Multiplataforma/GestorRH-API/actions/workflows/ci.yml/badge.svg)](https://github.com/GestorRH-Multiplataforma/GestorRH-API/actions/workflows/ci.yml)
[![Version](https://img.shields.io/badge/version-v1.4.0--stable-brightgreen)](https://github.com/GestorRH-Multiplataforma/GestorRH-API/releases/tag/v1.4.0)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
 
API REST desarrollada en Spring Boot para la gestión centralizada de recursos humanos, control de turnos, ausencias y validación de fichaje móvil con geovallado.
 
> Este repositorio forma parte del ecosistema **GestorRH Multiplataforma**. Para entender el contexto general del proyecto (clientes móvil, web y arquitectura global), consulta el [README de la organización](https://github.com/GestorRH-Multiplataforma#gestorrh---ecosistema-multiplataforma).
 
**Portal de Documentación (API REST & Código):** [Acceder al portal interactivo](https://gestorrh-multiplataforma.github.io/GestorRH-API/)
*(Desplegado automáticamente mediante pipeline CI/CD en GitHub Pages. Incluye Swagger UI y Javadoc).*
 
---
 
## Tecnologías Utilizadas
 
- **Java 21** & **Spring Boot 3**
- **Spring Security & JWT** (Autenticación Stateless)
- **Spring Data JPA / Hibernate**
- **PostgreSQL** (Base de datos relacional)
- **Swagger / OpenAPI** (Documentación interactiva de endpoints)
- **Maven** & **GitHub Actions** (CI/CD)
- **Docker** & **Docker Compose**
- **OpenPDF** (Generación de reportes en PDF)
---
 
## Requisitos Previos
 
Dependiendo de si quieres desarrollar o simplemente desplegar la aplicación, los requisitos varían:
 
**Para Despliegue Completo (Recomendado):**
- **Docker** y **Docker Compose** instalados. (No necesitas instalar Java ni Maven, la imagen se construye y ejecuta de forma autónoma).
**Para Desarrollo Local:**
- **Java 21** instalado en el sistema.
- **Maven** para la gestión de dependencias y compilación.
- **Docker** (solo para levantar el contenedor local de PostgreSQL).
---
 
## Estructura del Proyecto
 
```
src/main/java/com/gestorrh/api/
├── annotation/        # Anotaciones personalizadas para Swagger (respuestas de error reutilizables)
├── config/            # Configuración de seguridad, Swagger/OpenAPI y DataSeeder (datos de prueba)
├── controller/        # Controladores REST: Autenticación, Empresa, Empleado, Turno,
│                      #   Asignación, Fichaje, Ausencia, Estadísticas y Reportes
├── dto/               # Objetos de transferencia de datos, organizados por módulo
│   ├── asignacion/
│   ├── ausencia/
│   ├── autenticacion/
│   ├── empleado/
│   ├── empresa/
│   ├── error/
│   ├── estadisticas/
│   ├── fichaje/
│   ├── reporte/
│   └── turno/
├── entity/            # Entidades JPA: Empresa, Empleado, Turno, AsignacionTurno, Fichaje, Ausencia
│   └── enums/         # Enumeraciones de dominio (RolEmpleado, EstadoAusencia, ModalidadTurno, etc.)
├── exception/         # Gestor global de excepciones (@RestControllerAdvice)
├── repository/        # Repositorios Spring Data JPA con consultas JPQL personalizadas
├── security/          # Filtro JWT (FiltroJwt) y servicio de generación/validación de tokens (ServicioJwt)
└── service/           # Lógica de negocio: servicios de dominio + infraestructura
                       #   (GeofencingService, FileStorageService, ReportePdfService, TareaProgramadaService)
```
 
---
 
## Variables de Entorno
 
El despliegue en producción requiere un archivo `.env` en la raíz del proyecto (basado en `.env.example`).
 
| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_USERNAME` | Usuario de la base de datos PostgreSQL | `gestor_user` |
| `DB_PASSWORD` | Contraseña de la base de datos PostgreSQL | `contraseña_segura` |
| `JWT_SECRET` | Clave secreta para firmar los tokens JWT (64 caracteres hexadecimales) | `a1b2c3d4...` |
 
> **Nota de seguridad:** El archivo `.env` nunca debe subirse al repositorio. Está incluido en `.gitignore`.
 
---
 
## Entorno de Desarrollo (Local)
 
El proyecto utiliza perfiles de Spring (`dev`, `test`, `prod`) para separar la configuración. Por defecto, arranca en el perfil `dev`.
 
### 1. Levantar la Base de Datos
Para no instalar PostgreSQL en tu máquina, utilizamos un contenedor Docker. Abre una terminal en la raíz del proyecto y ejecuta:
```bash
docker compose up -d
```
 
### 2. Arrancar la Aplicación
Una vez que el contenedor de la base de datos esté corriendo, puedes compilar y ejecutar la API con Maven:
```bash
mvn spring-boot:run
```
La API estará disponible en la ruta base: `http://localhost:8080/api`
 
### 3. Datos de Prueba (DataSeeder)
Al arrancar en perfil `dev` con la base de datos vacía, el sistema inyecta automáticamente un ecosistema de pruebas completo con empresa, empleados, turnos, fichajes y ausencias. Las credenciales son:
 
| Rol | Email | Contraseña | Endpoint de login |
|---|---|---|---|
| EMPRESA | `admin@tech.com` | `123456` | `/auth/login-empresa` |
| SUPERVISOR | `super@tech.com` | `123456` | `/auth/login-empleado` |
| EMPLEADO | `empleado@tech.com` | `123456` | `/auth/login-empleado` |
 
---
 
## Entorno de Producción (Despliegue con Docker)
 
El proyecto está preparado para desplegarse en cualquier servidor (agnóstico a la nube) utilizando un enfoque *Multi-stage* con Docker.
 
### 1. Configurar Variables de Entorno
Copia el archivo de plantilla `.env.example` en la raíz del proyecto y renómbralo a `.env`. Rellena las variables obligatorias con tus credenciales de producción (ver tabla de [Variables de Entorno](#variables-de-entorno)).
 
### 2. Levantar la Infraestructura Completa
La API y su base de datos aislada se construyen y orquestan automáticamente con Docker Compose. Ejecuta:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
 
---
 
## Tests
 
El proyecto cuenta con una suite de tests unitarios implementados con **JUnit 5** y **Mockito**, cubriendo la capa de servicios completa.
 
Los módulos con cobertura de tests son: `AutenticacionService`, `EmpresaService`, `EmpleadoService`, `TurnoService`, `AsignacionTurnoService`, `AusenciaService`, `FichajeService`, `EstadisticasService`, `ReporteService`, `ReportePdfService`, `FileStorageService` y `GeofencingService`.
 
Para ejecutar los tests:
```bash
mvn test
```
 
Los tests utilizan el perfil `test` de Spring, que arranca el contexto de la aplicación con una configuración aislada sin necesidad de base de datos real.
 
---
 
## CI/CD
 
El proyecto dispone de un pipeline de integración continua definido en `.github/workflows/ci.yml` que se ejecuta automáticamente en cada push a `main` o `feature/**` y en cada Pull Request a `main`.
 
El pipeline realiza las siguientes etapas en orden:
 
1. **Build & Test**: Compila el proyecto y ejecuta la suite de tests con `mvn clean test` bajo el perfil `test`.
2. **Generación de documentación** *(solo en `main`)*: Genera el Javadoc y el fichero `openapi.json` a partir del perfil Maven `api-docs`.
3. **Construcción del portal** *(solo en `main`)*: Ensambla Swagger UI y Javadoc en un sitio estático unificado.
4. **Despliegue en GitHub Pages** *(solo en `main`)*: Publica el portal en [gestorrh-multiplataforma.github.io/GestorRH-API/](https://gestorrh-multiplataforma.github.io/GestorRH-API/).
---
 
## Documentación y Pruebas (Swagger UI)
 
Una vez que la aplicación esté corriendo (ya sea en tu entorno local con Maven o contenedorizada con Docker), puedes probar todos los endpoints, autenticarte y ver los esquemas de datos interactivos accediendo a:
 
`http://localhost:8080/swagger-ui/index.html`
 
*(Nota: Para probar los endpoints protegidos, primero debes usar el endpoint de Login y pegar el token JWT resultante en el botón "Authorize").*

---

## Matriz de Acceso (Seguridad)

Todos los endpoints de la API están protegidos mediante `@PreAuthorize` con control de acceso basado en roles (RBAC). Los tres roles disponibles son `EMPRESA`, `SUPERVISOR` y `EMPLEADO`.

| Nivel de acceso | Roles permitidos | Endpoints |
|---|---|---|
| **Solo EMPRESA** | `EMPRESA` | `POST /api/empleados`, `PUT /api/empleados/{id}`, `POST /api/empleados/{id}/baja`, `POST /api/empleados/{id}/readmitir`, `PUT /api/empleados/{id}/reset-password`, `POST /api/turnos`, `PUT /api/turnos/{id}`, `DELETE /api/turnos/{id}`, `GET /api/empresas/me`, `PUT /api/empresas/me`, `PUT /api/empresas/me/contrasena`, `DELETE /api/empresas/me` |
| **Gestión de equipo** | `EMPRESA`, `SUPERVISOR` | `POST /api/asignaciones`, `GET /api/asignaciones`, `PUT /api/asignaciones/{id}`, `DELETE /api/asignaciones/{id}`, `GET /api/turnos`, `GET /api/estadisticas/**`, `PUT /api/ausencias/{id}/revision`, `GET /api/ausencias`, `PUT /api/fichajes/{id}/modificar` , `GET /api/empleados`|
| **Perfil propio** | `EMPLEADO`, `SUPERVISOR` | `GET /api/empleados/me`, `PUT /api/empleados/me/contrasena`, `GET /api/asignaciones/me`, `GET /api/ausencias/me`, `POST /api/ausencias`, `PUT /api/ausencias/{id}`, `DELETE /api/ausencias/{id}`, `POST /api/fichajes/entrada`, `PUT /api/fichajes/salida`, `GET /api/fichajes/estado-actual` |
| **Consulta general** | `EMPRESA`, `SUPERVISOR`, `EMPLEADO` | `GET /api/fichajes`, `GET /api/reportes/**`, `GET /api/ausencias/tipos`, `GET /api/ausencias/estados`, `GET /api/ausencias/justificantes/{nombreArchivo}`, `GET /api/asignaciones/modalidades`, `GET /api/empleados/roles` |
| **Público** | Sin autenticación | `POST /api/auth/login-empresa`, `POST /api/auth/login-empleado`, `POST /api/empresas/registro`, `/swagger-ui/**`, `/v3/api-docs/**` |

---
 
## Versionado
 
Este proyecto utiliza **Git tags anotados** para marcar hitos funcionales y versiones relevantes de la API REST.
 
La estrategia de versionado sigue **Semantic Versioning** (`MAJOR.MINOR.PATCH`):
 
- **MAJOR**: cambios incompatibles con clientes existentes.
- **MINOR**: nuevas funcionalidades compatibles.
- **PATCH**: correcciones compatibles sin ruptura del contrato de la API.
### Hitos publicados
 
- **`v0.1.0`** → primera versión funcional del backend.
  Incluye autenticación, autorización, gestión básica de empleados y una primera base de calidad.
- **`v0.8.0`** → backlog funcional principal completado.
  Se consideran implementadas las funcionalidades principales de negocio de la API: seguridad, empresa, empleados, turnos, asignaciones, ausencias y fichajes.
- **`v0.9.0`** → infraestructura, documentación y estabilización.
  Incluye la contenedorización agnóstica con Docker, variables de entorno, control de concurrencia optimista y datos de prueba automatizados (DataSeeder).
- **`v1.0.0`** → primera versión estable.
  Versión de referencia para integración con clientes y futuros despliegues, con la arquitectura base consolidada, preparación para producción y licencia aplicada.
- **`v1.0.1`** → versión estable actual. *(latest)*
  Parche sobre v1.0.0 con correcciones compatibles sin ruptura del contrato de la API.
- **`v1.1.0`** → nuevas funcionalidades y pipeline CD. 
    Reset de contraseña por RRHH, filtros en fichajes, BFF dashboard móvil y despliegue automático en Oracle Cloud.
- **`v1.1.2`** → parche de seguridad en el filtro JWT. 
    Corrección crítica que excluye correctamente las rutas públicas del filtro de autenticación JWT, resolviendo el bloqueo en el login.
- **`v1.1.3`** → parche de autenticación y pipeline.
  Añade prefijo `/api/auth` a las rutas públicas de autenticación y garantiza la actualización del contenedor en cada deploy con `--force-recreate`.
- **`v1.2.0`** → nueva funcionalidad y correcciones.
  Acceso del rol SUPERVISOR a `GET /api/empleados` filtrado por departamento, soporte de turnos nocturnos, corrección del DataSeeder y mejora del pipeline de deploy.
- **`v1.3.0`** → soporte para eliminar justificante en ausencias.
  Permite al empleado eliminar el justificante adjunto de una ausencia en estado SOLICITADA
  sin necesidad de adjuntar un archivo nuevo, resolviendo el bloqueo cuando se adjuntaba
  un archivo incorrecto.
- **`v1.4.0`** → mejoras en autenticación, validaciones de ausencias y correcciones de roles. *(latest)*
    Añade el nombre de empresa en el token JWT y respuesta de login, valida fichajes existentes
    al solicitar y aprobar ausencias, corrige el rol SUPERVISOR en TurnoService, fichajes y
    endpoints /me, y normaliza la persistencia de fechas a UTC.
### Criterio de uso
 
Para integración con clientes y despliegue, la referencia será siempre la **última versión estable aprobada**, no necesariamente el último commit de la rama `main`.
 
---
 
## Licencia
 
Este proyecto está bajo la Licencia Apache 2.0 - mira el archivo [LICENSE](LICENSE) para más detalles.
 
*Eres libre de utilizar, modificar y distribuir este software, siempre y cuando se incluya una copia de la licencia y se respeten los términos de la misma.*
