# GestorRH 2.0 - API REST

API REST desarrollada en Spring Boot para la gestión centralizada de recursos humanos, control de turnos, ausencias y validación de fichaje móvil con geovallado.

## Requisitos Previos
- **Java 21**
- **Maven**
- **Docker** y **Docker Compose** (para la base de datos local)

## Entorno de Desarrollo (Local)

El proyecto utiliza perfiles de Spring (`dev`, `test`, `prod`) para separar la configuración. Por defecto, arranca en el perfil `dev`.

### 1. Levantar la Base de Datos
Para no instalar PostgreSQL en tu máquina, utilizamos un contenedor Docker. Abre una terminal en la raíz del proyecto y ejecuta:
```bash
docker compose up -d