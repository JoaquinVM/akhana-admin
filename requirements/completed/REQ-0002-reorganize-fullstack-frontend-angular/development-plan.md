# Development Plan - REQ-0002: Reorganización Full Stack y Creación Frontend

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad de implementar la solución según su criterio técnico.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Reorganización del Backend
- **`TASK-01`**: Detener cualquier proceso `bootRun` que esté ejecutándose en `akhana-admin/` para liberar los archivos.
- **`TASK-02`**: Mover todos los archivos y subcarpetas de `akhana-admin/` directamente a `backend/`.
- **`TASK-03`**: Eliminar la carpeta vacía `akhana-admin/`.
- **`TASK-04`**: Ejecutar `./gradlew test` en `backend/` para validar que el backend compile y apruebe todos los tests en su nueva ubicación.

### Fase 2: Creación del Proyecto Frontend Angular
- **`TASK-05`**: Respaldar temporalmente `frontend/_context.md` e inicializar el proyecto Angular 19 en `frontend/` mediante Angular CLI (`ng new`).
- **`TASK-06`**: Restaurar `frontend/_context.md` y configurar `provideHttpClient()` en `frontend/src/app/app.config.ts`.
- **`TASK-07`**: Ejecutar `npm run build` en `frontend/` para corroborar compilación exitosa.

### Fase 3: Archivo .gitignore y Documentación Raíz
- **`TASK-08`**: Crear el archivo `.gitignore` unificado en la raíz del repositorio para Spring Boot y Angular.
- **`TASK-09`**: Actualizar el archivo `README.md` principal en la raíz con la guía Full Stack (backend + frontend).

### Fase 4: Git y Publicación en GitHub
- **`TASK-10`**: Ejecutar `git init` en la raíz del proyecto y preparar el staging verificando con `git status` que no haya archivos excluidos.
- **`TASK-11`**: Crear el commit inicial: `feat: initial repository setup with Spring Boot backend and Angular frontend`.
- **`TASK-12`**: Configurar el origen remoto con la URL de GitHub provista por el usuario y realizar el `git push`.
