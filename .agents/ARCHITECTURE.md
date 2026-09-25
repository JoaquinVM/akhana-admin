# Arquitectura del Developer Assistant Agéntico - Antigravity Core 2.0

> **Framework de Desarrollo Autónomo y Supervisado Basado en Requerimientos y Memoria Viva**

---

## 📋 1. Visión y Filosofía

Antigravity Core 2.0 evoluciona desde un orquestador rígido de tareas hacia un **Developer Assistant Agéntico Autónomo y Supervisado**. Acompaña el desarrollo integral de requerimientos expresados en lenguaje natural, manteniendo memoria persistente del proceso y del proyecto, investigando y diseñando cuando es estrictamente necesario, planificando, desarrollando bajo TDD, auditando y documentando en tiempo real.

### Principio de Convivencia con el Desarrollador Humano:
- El desarrollador humano mantiene la autoridad suprema del proyecto y puede modificar el código fuente libremente en cualquier momento.
- El agente **nunca lucha contra los cambios humanos ni intenta revertirlos**. Si el desarrollador decide extender una clase existente en lugar de crear la propuesta en el plan, el agente asimila la diferencia, adapta su contexto y registra la decisión en `decisions.md`.

---

## 🏛️ 2. Jerarquía de Verdad y Principio de Autoridad

```text
1. Decisión humana explícita
2. Código fuente real (Ground Truth)
3. Requerimiento aprobado (Scope oficial)
4. Decisiones técnicas registradas (decisions.md / ADRs)
5. Implementación documentada (implementation.md)
6. Plan de desarrollo propuesto (development-plan.md)
7. Recomendaciones del agente (Asesoría)
```

---

## 📦 3. El Requerimiento como Unidad Central de Trabajo

Todo trabajo de ingeniería se estructura en torno a una entidad `REQ-XXXX`:

```plaintext
requirements/
├── README.md                   # Catálogo general de requerimientos
├── active/
│   └── REQ-0042-volume-discounts/
│       ├── requirement.md      # Metadatos, objetivos y alcance
│       ├── scope.yaml          # Matriz estricta de alcance (approved vs excluded)
│       ├── conversation.md     # Bitácora cronológica de interacción
│       ├── research.md         # Hallazgos de NotebookLM/Web (condicional)
│       ├── user-stories.md     # Historias atómicas con criterios Gherkin
│       ├── design/
│       │   ├── ux-ui.md        # Especificaciones Stitch/UI (condicional)
│       │   └── technical.md    # Arquitectura, APIs, SQL y modelo de datos
│       ├── development-plan.md # Hoja de ruta planificada originalmente
│       ├── tests.md            # Casos de prueba y suite TDD
│       ├── implementation.md   # Registro de lo efectivamente implementado
│       ├── decisions.md        # Registro de decisiones y desviaciones (ADRs)
│       ├── assumptions.md      # Supuestos operativos
│       ├── autonomy-log.md     # Bitácora de acciones del agente
│       └── final-report.md     # Informe ejecutivo de entrega
└── completed/                  # Histórico inmutable de requerimientos cerrados
```

---

## ⚡ 4. Workflow Adaptativo por Complejidad

El orquestador evalúa factores como número de archivos afectados, impacto en base de datos, modificaciones de UI e incertidumbre externa para categorizar la complejidad en `complexity_assessment.yaml`:

| Nivel | Tipología de Requerimiento | Flujo Ejecutado | Checkpoints HITL |
| :--- | :--- | :--- | :--- |
| **TINY** | Ajuste CSS, corrección de texto, typo, 1 archivo. | `REQ` ➔ `Dev` ➔ `Validation` ➔ `Cierre` | HITL 3 (Directo) |
| **SMALL** | Bugfix o ajuste funcional menor. | `REQ` ➔ `US` ➔ `Plan` ➔ `Tests` ➔ `Dev` ➔ `Validation` ➔ `Cierre` | HITL 2/3 |
| **MEDIUM** | Nueva funcionalidad en múltiples componentes. | `REQ` ➔ `Refinement` ➔ `US` ➔ `Design (UI*/Tech)` ➔ `Plan` ➔ `Tests` ➔ `Dev` ➔ `Review` ➔ `Cierre` | HITL 1, 2, 3 |
| **LARGE** | Cambio arquitectónico, integración externa compleja. | Flujo exhaustivo con `Research (NotebookLM)*`, `Design (Stitch* / Tech)`, `TDD` y `Review` multidimensional. | HITL 1, 2, 3 |

`*` Condicional: solo se invoca si la evaluación determina que aporta valor real.

---

## 🤖 5. Red de Agentes Especialistas Desacoplados

```
                             ┌────────────────────────┐
                             │   ORCHESTRATOR AGENT   │
                             └───────────┬────────────┘
                                         │
     ┌───────────────────────┬───────────┴───────────┬───────────────────────┐
     ▼                       ▼                       ▼                       ▼
┌──────────────┐     ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│ Requirement  │     │   Research   │        │  User Story  │        │   UI / UX    │
│    Agent     │     │    Agent     │        │    Agent     │        │    Agent     │
└──────────────┘     └──────────────┘        └──────────────┘        └──────────────┘
     ▼                       ▼                       ▼                       ▼
┌──────────────┐     ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│  Technical   │     │   Planning   │        │     Test     │        │  Developer   │
│ Design Agent │     │    Agent     │        │    Agent     │        │    Agent     │
└──────────────┘     └──────────────┘        └──────────────┘        └──────────────┘
                             │                       │
                             ▼                       ▼
                     ┌──────────────┐        ┌──────────────┐
                     │    Review    │        │Documentation │
                     │    Agent     │        │    Agent     │
                     └──────────────┘        └──────────────┘
```

1. **`orchestrator`:** Coordinador general, evaluador de complejidad y supervisor Git.
2. **`requirement-agent`:** Refinamiento socrático, delimitación de alcance (`scope.yaml`) y supuestos.
3. **`research-agent`:** Investigación técnica externa vía NotebookLM MCP y búsqueda web con fallback resiliente.
4. **`user-story-agent`:** Historias atómicas con criterios de aceptación Gherkin y trazabilidad.
5. **`ui-ux-agent`:** Diseño de interfaces en Google Stitch cuando existe impacto visual (`ux_design: true`).
6. **`technical-design-agent`:** Arquitectura de software, contratos de API REST, base de datos y scripts SQL.
7. **`planning-agent`:** Desglose de tareas con dependencias. Recuerda: el plan es una propuesta, no una obligación.
8. **`test-agent`:** Estrategia TDD (RED ➔ GREEN ➔ REFACTOR) adaptada al runner del proyecto (`./gradlew test`, `npm test`, etc.).
9. **`developer-agent`:** Codificación autónoma y supervisada, cero resistencia a cambios humanos, registro en `autonomy-log.md`.
10. **`review-agent`:** Auditoría cruzada multidimensional (Req ↔ US ↔ Design ↔ Plan ↔ Code ↔ Tests), detección de desviaciones y redacción de ADRs en `decisions.md`.
11. **`documentation-agent`:** Context Engineering con `_context.md` y actualización de la memoria del proyecto (`project/PROJECT.md`).

---

## 🧭 6. Context Engineering: "Context First, Code Second"

Para optimizar el uso de tokens y mantener un entendimiento arquitectónico certero, cada carpeta clave posee un archivo `_context.md`:
- `frontend/_context.md`: Visión y convenciones de UI.
- `backend/_context.md`: Arquitectura Spring Boot, capas y servicios.
- `database/_context.md`: Motor PostgreSQL, migraciones numeradas y esquemas SQL canónicos.

---

## 🔄 7. Persistencia y Resiliencia ante Reinicios

El sistema desacopla su memoria del contexto temporal del chat:
1. En cada reinicio, el agente lee `.antigravity/state.json` y `project/PROJECT.md`.
2. Identifica el requerimiento activo en `requirements/active/`.
3. Reconstruye el estado exacto leyendo los artefactos estructurados (`requirement.md`, `development-plan.md`, `decisions.md`).
4. Reanuda la conversación en el punto preciso sin repetir preguntas.
