# Akhana Admin — Full Stack Platform

> Plataforma administrativa y operativa Full Stack desarrollada con **Spring Boot (Java 21)** y **Angular (v21)**.

---

## 🏛️ Estructura del Proyecto

El repositorio está organizado como una arquitectura Full Stack desacoplada:

```text
akhana-admin/
├── backend/                  # API REST y servicios de negocio (Spring Boot + JPA)
│   ├── src/                  # Código fuente Java y recursos
│   ├── build.gradle          # Configuración de dependencias y plugins
│   └── gradlew               # Wrapper de Gradle
│
├── frontend/                 # Aplicación de interfaz web (Angular 21)
│   ├── src/                  # Componentes, servicios y rutas TypeScript
│   ├── angular.json          # Configuración del workspace Angular
│   └── package.json          # Dependencias npm
│
├── database/                 # Persistencia relacional
│   ├── migrations/           # Scripts SQL versionados (PostgreSQL 17)
│   └── schemas/              # Esquema canónico actual DDL
│
├── docker-compose.yml        # Infraestructura de base de datos local (PostgreSQL 17)
├── .gitignore                # Reglas de exclusión para Git (Java + Node)
└── README.md                 # Guía general de uso y despliegue
```

---

## 📋 Requisitos del Sistema

- **Java:** JDK 21 o superior
- **Node.js:** Versión 20.19+ o 22+ (recomendado Node 22 o 23)
- **Gestor de Paquetes:** npm 10+
- **Docker:** Para ejecutar la base de datos PostgreSQL mediante Docker Compose

---

## 🚀 Guía de Puesta en Marcha

### 1. Iniciar la Base de Datos (PostgreSQL 17)

En la raíz del proyecto, levanta el contenedor de base de datos:

```bash
docker compose up -d postgres
```

> **Parámetros de conexión:**
> - Host: `localhost:5432`
> - Base de datos: `akhana`
> - Usuario: `akhana`
> - Contraseña: `akhana_dev`

---

### 2. Levantar el Backend (Spring Boot)

Navega a la carpeta `backend/` y ejecuta:

```bash
cd backend

# Ejecutar pruebas unitarias y de integración
./gradlew test

# Iniciar la aplicación en modo desarrollo (puerto 8080)
./gradlew bootRun
```

La API estará disponible en `http://localhost:8080/api`.

#### Usuarios Semilla Iniciales (Seed Data):
Al iniciar por primera vez, el backend crea de forma idempotente con contraseñas encriptadas con BCrypt:
- **Administrador:** `admin` / `12345admin` (Rol: `ADMIN`)
- **Vendedor:** `seller` / `12345seller` (Rol: `SELLER`)

---

### 3. Levantar el Frontend (Angular 21)

Navega a la carpeta `frontend/` y ejecuta:

```bash
cd frontend

# Instalar dependencias si es la primera vez
npm install

# Iniciar el servidor de desarrollo
npm start
```

La aplicación web estará disponible en `http://localhost:4200`.

---

## 🧪 Pruebas y Compilación

| Proyecto | Comando de Pruebas | Comando de Build |
| :--- | :--- | :--- |
| **Backend** | `cd backend && ./gradlew test` | `cd backend && ./gradlew build -x test` |
| **Frontend** | `cd frontend && npm test` | `cd frontend && npm run build` |
