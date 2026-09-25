# ADR-0001: Adopción del Framework Developer Assistant Agéntico Basado en Requerimientos

- **Estado:** Aceptado
- **Fecha:** 2026-09-24
- **Autor:** Orquestador de Antigravity / Aprobado por el Desarrollador

## Contexto
El flujo original de desarrollo agéntico operaba en dos fases monolíticas y rígidas (MVP y Continuo), con dependencias estrictas de herramientas MCP (NotebookLM y Stitch) incluso en cambios triviales, sin persistencia de la conversación, sin memoria estructurada de ingeniería y con riesgo de colisión frente a decisiones directas del desarrollador humano.

## Decisión
Se evoluciona Antigravity Core hacia un **Developer Assistant Autónomo y Supervisado**:
1. **Unidad Central:** Todo trabajo se estructura en torno a un Requerimiento (`REQ-XXXX`).
2. **Evaluación de Complejidad:** Flujo adaptativo en cuatro niveles (`TINY`, `SMALL`, `MEDIUM`, `LARGE`).
3. **Persistencia Estructurada:** Cada requerimiento contiene su propio ciclo de vida (`requirement.md`, `user-stories.md`, `design/`, `development-plan.md`, `tests.md`, `implementation.md`, `decisions.md`, `assumptions.md`, `autonomy-log.md`).
4. **Context Engineering:** Estructuración jerárquica con `_context.md` en `frontend/`, `backend/`, y `database/`.
5. **Principio de Autoridad:** La decisión y el código humano prevalecen sobre el plan del agente. El agente no revierte cambios humanos, los asimila y actualiza la documentación.
6. **HITL Conversacional:** Interacción directa y fluida en lenguaje natural a través de compuertas estratégicas (Definición, Solución, Entrega).

## Consecuencias
- **Positivas:** Trazabilidad histórica completa, cero burocracia en cambios pequeños, resiliencia ante reinicios de IDE, y colaboración simbiótica con el desarrollador humano.
- **Compromisos:** Requiere mantener actualizados los archivos `_context.md` al alterar módulos y registrar desviaciones técnicas en `decisions.md`.
