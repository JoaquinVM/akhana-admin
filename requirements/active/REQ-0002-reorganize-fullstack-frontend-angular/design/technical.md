# Technical Design - REQ-0002: Reorganización Full Stack y Creación Frontend

---

## 🏛️ 1. Estructura de Directorios Resultante

```text
Akhana Admin/
├── backend/                  # Proyecto Spring Boot (trasladado desde akhana-admin/)
│   ├── src/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   └── gradle/
├── frontend/                 # Proyecto Angular 19 (generado con Angular CLI)
│   ├── src/
│   ├── angular.json
│   ├── package.json
│   └── tsconfig.json
├── database/                 # Migraciones y esquemas PostgreSQL
├── project/                  # Memoria viva del proyecto y ADRs
├── requirements/             # Catálogo y ciclo de vida de requerimientos
├── .agents/                  # Framework de agentes y reglas
├── .gitignore                # Archivo unificado de exclusión Git
├── docker-compose.yml        # Contenedores de soporte (PostgreSQL 17)
└── README.md                 # Documentación maestra del repositorio Full Stack
```

---

## ⚙️ 2. Procedimiento de Traslado del Backend

1. **Detener procesos en ejecución:** Asegurar que ningún proceso `bootRun` bloquee archivos en `akhana-admin/`.
2. **Mover archivos y directorios:**
   - Mover todo el contenido de `akhana-admin/` directamente a `backend/`:
     - `src/` ➔ `backend/src/`
     - `gradle/` ➔ `backend/gradle/`
     - `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat` ➔ `backend/`
   - Eliminar la carpeta vacía remanente `akhana-admin/`.
3. **Verificación:** Ejecutar `./gradlew test` dentro de `backend/` para corroborar que todas las rutas relativas y dependencias funcionen al 100%.

---

## 🅰️ 3. Creación y Configuración del Proyecto Angular (`frontend/`)

1. **Comando de Generación:**
   ```bash
   npx @angular/cli@19 new akhana-frontend --directory=frontend --routing=true --style=css --ssr=false --skip-git=true --defaults=true
   ```
2. **Configuración para Consumo REST:**
   - En `frontend/src/app/app.config.ts`, registrar `provideHttpClient()` dentro del array de providers para dejar el cliente HTTP listo para interactuar con los endpoints del backend en Spring Boot (`http://localhost:8080/api/...`).
3. **Verificación:** Ejecutar `npm run build` dentro de `frontend/` y asegurar que compile sin errores.

---

## 🐙 4. Configuración Git y Repositorio GitHub

1. **Archivo `.gitignore` Unificado:**
   Excluir artefactos de build y dependencias de ambos ecosistemas:
   - Backend: `backend/.gradle/`, `backend/build/`, `.gradle/`, `build/`.
   - Frontend: `frontend/node_modules/`, `frontend/.angular/`, `frontend/dist/`.
   - IDE y Sistema: `.idea/`, `*.iml`, `.DS_Store`, `*.log`.
2. **README.md en la Raíz:**
   - Descripción del proyecto Akhana Admin Full Stack.
   - Requisitos: Java 21, Node.js 20, Docker (PostgreSQL 17).
   - Comandos para levantar backend (`./gradlew bootRun` en `backend/`).
   - Comandos para levantar frontend (`npm start` o `ng serve` en `frontend/`).
3. **Control de Versiones y GitHub:**
   - Ejecutar `git init` en la raíz.
   - Configurar rama principal `main` o `develop`.
   - Realizar commit inicial: `feat: initial full stack repository setup (Spring Boot + Angular)`.
   - Solicitar al desarrollador la URL de GitHub (o vincular y empujar mediante `git remote add origin <URL>` y `git push -u origin <branch>`).
