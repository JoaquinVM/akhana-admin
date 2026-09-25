# Registro de Decisiones y Desviaciones Técnicas - REQ-0002

---

### DEC-001: Raíz del Repositorio Unificado
- **Contexto:** El usuario solicita que la raíz del proyecto contenga `backend/`, `frontend/`, `.gitignore` y `README.md`.
- **Decisión:** El workspace raíz `/Users/joaquin/Documents/Akhana Admin` se convierte en la raíz del repositorio Git, alojando `backend/` y `frontend/` en un monorepo ligero.
- **Razón:** Facilita la orquestación, el versionado sincronizado y preserva los scripts de base de datos (`database/`) y el framework de agentes (`.agents/`).
- **Impacto:** Positivo para la mantenibilidad.
- **Autor:** Technical Design Agent.
- **Fecha:** 2026-09-25.
