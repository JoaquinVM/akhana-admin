# 📘 Memoria del Proyecto (Project Memory) - Akhana Admin

> **Documento Vivo de Arquitectura, Convenciones y Contexto Global del Sistema.**

---

## 🎯 1. Visión y Propósito del Proyecto
**Akhana Admin** es la plataforma administrativa y operativa para el ecosistema Akhana. Proporciona control centralizado de usuarios, servicios, métricas y gestión de datos con alta disponibilidad, seguridad y trazabilidad.

---

## 🏛️ 2. Arquitectura de Alto Nivel

El proyecto sigue una arquitectura desacoplada organizada en tres pilares fundamentales:

```
Akhana Admin/
├── frontend/             # Interfaz de usuario (Admin Dashboard / Web UI)
├── backend/              # Servicios de negocio, APIs REST, seguridad (Spring Boot)
│   └── akhana-admin/     # Núcleo de backend Java 17+ / Gradle
└── database/             # Scripts SQL, esquemas DDL, migraciones y seeds
    ├── migrations/       # Migraciones incrementales numeradas
    └── schemas/          # Definición canónica de entidades y relaciones
```

### Contenedores y Entorno (`docker-compose.yml`)
- **Base de Datos:** PostgreSQL 17 en puerto local `5432` (`akhana-postgres`).
- **Base de Datos por defecto:** `akhana` (usuario: `akhana`).

---

## 🛠️ 3. Stacks Tecnológicos y Comandos de Ejecución

| Componente | Stack / Tecnología | Comandos de Verificación | Directorio Principal |
| :--- | :--- | :--- | :--- |
| **Backend** | Java (Spring Boot) + Gradle | `./gradlew test`<br>`./gradlew bootRun`<br>`./gradlew build -x test` | `backend/akhana-admin/` |
| **Frontend** | React / TypeScript / Vite (o similar) | `npm test`<br>`npm run build`<br>`npm run dev` | `frontend/` |
| **Database** | PostgreSQL 17 / SQL ANSI | `docker compose up -d postgres` | `database/` |

---

## 📐 4. Convenciones de Ingeniería y Estándares

### Convenciones Generales
1. **Idioma del Código:** Todo el código fuente, comentarios técnicos, identificadores, commits y nombres de ramas se escriben en **inglés**.
2. **Comunicación con el Desarrollador:** En **español** fluido, conciso y profesional.
3. **Estrategia Context First:** Antes de inspeccionar o modificar código en cualquier directorio, consultar su archivo `_context.md`.
4. **Respeto a la Implementación Humana:** El código real es la fuente última de verdad. Si el desarrollador humano introduce una variación sobre el plan original, el agente la asimila y la documenta en `decisions.md` (ADR) sin disputar ni revertir.

### Convenciones Git
- **Ramas de Requerimiento:** `feature/REQ-XXXX-slug` o `bugfix/REQ-XXXX-slug` creadas desde `develop`.
- **Commits Convencionales:** `feat(REQ-XXXX): descripción`, `test(REQ-XXXX): descripción`, `fix(REQ-XXXX): descripción`.
- **Integración:** Merge a `develop` una vez validado y aprobado en la compuerta HITL 3.

---

## 📚 5. Registro de Decisiones de Arquitectura (ADRs)
Las decisiones estructurales de impacto global se registran en `project/decisions/`:
- `ADR-0001`: Adopción del Framework de Developer Assistant Autónomo y Supervisado basado en Requerimientos.
