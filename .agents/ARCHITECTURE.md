# Arquitectura del Developer Assistant Agéntico - Antigravity Core + OpenSpec

> **Framework de Desarrollo Autónomo y Supervisado Guiado por Especificaciones (Spec-Driven Development)**

---

## 📋 1. Visión y Filosofía

Esta arquitectura integra **OpenSpec** como el motor canónico de especificación (Spec-Driven Development) y una red de **agentes especialistas desacoplados** que asisten al desarrollador humano a lo largo del ciclo de vida del software.

### Principio de Convivencia con el Desarrollador Humano:
- El desarrollador humano mantiene la autoridad suprema del proyecto y puede modificar el código fuente libremente en cualquier momento.
- El agente **nunca lucha contra los cambios humanos ni intenta revertirlos**. Si el desarrollador toma un enfoque técnico diferente al propuesto en el plan, el agente asimila la diferencia, adapta su contexto y registra la decisión en `design.md` (ADR).

---

## 🏛️ 2. Jerarquía de Verdad y Principio de Autoridad

```text
1. Decisión humana explícita (El desarrollador manda)
2. Código fuente real en el repositorio (Ground Truth)
3. Especificación aprobada en OpenSpec (Scope oficial)
4. Decisiones técnicas y ADRs registradas (design.md)
5. Recomendaciones y propuestas del agente (Asesoría)
```

---

## 📦 3. El Cambio de OpenSpec como Unidad Central de Trabajo

Todo trabajo estructurado se gestiona mediante los artefactos nativos de **OpenSpec**:

```plaintext
openspec/
├── config.yaml                   # Configuración del esquema (spec-driven)
├── changes/
│   ├── <nombre-del-cambio>/      # Cambio Activo
│   │   ├── proposal.md           # Contexto, justificación, alcance y no-objetivos
│   │   ├── specs/                # Requerimientos delta con escenarios BDD
│   │   │   └── <capability>/spec.md
│   │   ├── design.md             # Arquitectura técnica, APIs, BD, UI/Stitch y ADRs
│   │   └── tasks.md              # Lista ordenada de tareas ejecutables con checkboxes
│   └── archive/                  # Histórico inmutable de cambios completados
└── specs/                        # Especificación consolidada y viva del sistema
```

---

## ⚡ 4. Workflow Adaptativo por Complejidad

El orquestador evalúa factores como número de archivos afectados, impacto en base de datos, modificaciones de UI e incertidumbre externa:

| Nivel | Tipología de Requerimiento | Flujo Ejecutado | Checkpoints HITL |
| :--- | :--- | :--- | :--- |
| **TINY** | Ajuste CSS, corrección de texto, typo, 1 archivo. | `Orquestador` ➔ `developer-agent` ➔ Verificación (build/test) ➔ Retorno a `IDLE` | Validación directa |
| **SMALL** | Bugfix o ajuste funcional menor aislado. | OpenSpec `/opsx-propose` rápido ➔ `developer-agent` (`/opsx-apply`) ➔ Build & Test ➔ `/opsx-archive` ➔ `IDLE` | Aprobación rápida |
| **MEDIUM** | Nueva funcionalidad en múltiples componentes o servicios. | OpenSpec `/opsx-propose` (asistido por `architect` o `ui-ux`) ➔ Rama Git (si hay) ➔ `developer-agent` ➔ `review-agent` ➔ `/opsx-archive` | HITL de propuesta y entrega |
| **LARGE** | Cambio arquitectónico, integración externa o alta incertidumbre. | `research-agent` ➔ `architect` + `ui-ux` ➔ OpenSpec `/opsx-propose` ➔ Rama Git (si hay) ➔ `developer-agent` (fases TDD) ➔ `review-agent` ➔ `/opsx-archive` | HITL riguroso en propuesta y entrega |

---

## 🤖 5. Red de 6 Agentes Especialistas Desacoplados

```
                             ┌────────────────────────┐
                             │   ORCHESTRATOR AGENT   │
                             └───────────┬────────────┘
                                         │
      ┌───────────────────────┬───────────┴───────────┬───────────────────────┐
      ▼                       ▼                       ▼                       ▼
┌──────────────┐     ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│   Research   │     │  Architect   │        │   UI / UX    │        │  Developer   │
│    Agent     │     │    Agent     │        │    Agent     │        │    Agent     │
└──────────────┘     └──────────────┘        └──────────────┘        └──────────────┘
 (NotebookLM / Web)   (DB, APIs, Mod)         (Stitch / UI)           (Apply tasks,
                                                                      tests & code)
                                                                             │
                                                                             ▼
                                                                     ┌──────────────┐
                                                                     │    Review    │
                                                                     │    Agent     │
                                                                     └──────────────┘
                                                                      (Auditoría y ADR)
```

1. **`orchestrator`:** Coordinador maestro, clasificador de intenciones (`IDLE` vs `Cambio Activo`), evaluador de complejidad y supervisor condicional de Git.
2. **`research-agent`:** Consultor de investigación técnica profunda (NotebookLM MCP / WebSearch) para incertidumbres externas o tecnologías desconocidas.
3. **`architect-agent`:** Diseña persistencia, scripts SQL de migración y contratos de API REST para `design.md` de OpenSpec.
4. **`ui-ux-agent`:** Prototipado interactivo en Google Stitch MCP y especificaciones de componentes visuales para `design.md`.
5. **`developer-agent`:** Ejecutor de código. Aplica `tasks.md` de OpenSpec (`/opsx-apply`), programa tests unitarios/integración, compila y mantiene `_context.md`.
6. **`review-agent`:** Auditor independiente de coherencia (Spec vs Código vs Tests) y redactor de ADRs en `design.md`.

---

## 🐙 6. Integración Condicional con Git / GitFlow

- **Detección Automática:** Se ejecuta `git rev-parse --is-inside-work-tree 2>/dev/null`.
- **Sin Git:** Si el proyecto no es un repositorio Git, se omiten todas las operaciones de ramas, commits y pushes.
- **Con Git:**
  - Se detecta la rama base (`develop` o `main`). Nunca se trabaja directamente sobre ramas protegidas.
  - Para cambios MEDIUM y LARGE se crea una rama de trabajo: `feature/<nombre-cambio>` o `fix/<nombre-cambio>`.
  - Commits convencionales atómicos (`feat(...)`, `fix(...)`) solo tras verificar que los tests locales compilan y pasan.
  - Al completar el cambio y archivarlo en OpenSpec, se ofrece merge o preparación de Pull Request.

---

## 🧭 7. Context Engineering: "Context First, Code Second"

Para optimizar el uso de tokens y mantener un entendimiento certero de la arquitectura, cada directorio clave posee o mantiene un archivo `_context.md`:
- `frontend/_context.md`: Convenciones de UI y librerías visuales.
- `backend/_context.md`: Convenciones de arquitectura backend, capas y servicios.
- `database/_context.md`: Motor de datos, esquemas canónicos y migraciones.

---

## 🔄 8. Estados del Asistente: IDLE y Cambio Activo

- **`IDLE`:** El asistente está disponible para resolver dudas técnicas directas o para abrir un nuevo cambio cuando el desarrollador lo solicite.
- **`CAMBIO ACTIVO`:** Existe un cambio en curso en `openspec/changes/<nombre>/`. Toda charla, refinamiento o ajuste durante este periodo se asocia al cambio activo (usando `/opsx-update` o ajustando las tareas/especificaciones).
