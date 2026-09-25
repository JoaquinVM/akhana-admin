# REQ-0002: Reorganización del Proyecto y Creación del Frontend Angular

- **Identificador:** `REQ-0002`
- **Título:** Reorganización Full Stack y Creación del Frontend Angular
- **Complejidad:** `MEDIUM`
- **Estado:** `COMPLETED`
- **Fecha de Creación:** 2026-09-25
- **Fecha de Cierre:** 2026-09-25
- **Módulos Afectados:** `backend/`, `frontend/`, `.gitignore`, `README.md`, `git`
- **Rama Asociada:** `main` (remoto `origin/main`)

---

## 🎯 1. Objetivo
Reorganizar el proyecto **Akhana Admin** para establecer una arquitectura Full Stack clara que separe el backend del frontend en la raíz del repositorio, crear un proyecto base funcional de **Angular** en `frontend/`, configurar un `.gitignore` integral para Spring Boot + Angular, e inicializar y vincular el repositorio Git para publicación en GitHub.

---

## 🏛️ 2. Estructura Requerida

```text
akhana-admin/
├── backend/
│   └── [proyecto Spring Boot actual trasladado]
│
├── frontend/
│   └── [nuevo proyecto Angular generado con Angular CLI]
│
├── .gitignore
└── README.md
```

---

## 📋 3. Requisitos Funcionales y Técnicos

### Backend
1. Trasladar íntegramente los archivos del proyecto Spring Boot actual (código fuente `src/`, configuración Gradle, `build.gradle`, wrapper, propiedades, entidades, tests y migraciones) a la carpeta `backend/`.
2. Conservar el funcionamiento completo:
   - Configuración de PostgreSQL en `application.properties`.
   - Entidad `User`, repositorio, servicios de autenticación y endpoints REST.
   - Ejecución de `./gradlew test` y `./gradlew bootRun` desde la nueva ruta `backend/`.

### Frontend
1. Generar un nuevo proyecto estándar de **Angular** (versión 19 compatible) dentro de `frontend/` mediante `ng new` con enrutamiento y estilos estándar (CSS o SCSS).
2. Dejar el proyecto preparado para consumir posteriormente la API REST del backend (`HttpClientModule` / `provideHttpClient`).
3. No implementar vistas ni componentes adicionales en este requerimiento; únicamente el esqueleto base funcional.

### Repositorio Git y GitHub
1. Inicializar Git en la raíz del proyecto unificado.
2. Crear un `.gitignore` completo que excluya:
   - `node_modules/`, `.angular/`, dist compilada en frontend.
   - `build/`, `.gradle/`, `.idea/`, binarios compilados en backend.
   - Archivos locales temporales o secretos.
3. Crear un `README.md` en la raíz con descripción del proyecto, estructura, requisitos y comandos para levantar backend y frontend.
4. Crear commit inicial y vincular con el repositorio remoto de GitHub para realizar el push inicial.
