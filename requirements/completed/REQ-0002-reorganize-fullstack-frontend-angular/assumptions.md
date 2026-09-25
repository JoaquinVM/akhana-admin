# Supuestos Operativos (Assumptions) - REQ-0002

### ASSUMPTION-001: Ubicación de la Raíz del Repositorio
- **Supuesto:** La raíz del repositorio Git será el workspace `/Users/joaquin/Documents/Akhana Admin`, conteniendo directamente `backend/`, `frontend/`, `database/`, `.gitignore` y `README.md`.
- **Justificación:** Mantiene unificada la estructura del proyecto y preserva los scripts de base de datos y la memoria viva del sistema.

### ASSUMPTION-002: Versión de Angular
- **Decisión del Usuario:** Se utiliza **Angular 21** (`@angular/cli@21`) con standalone components, enrutamiento habilitado (`--routing`), estilos CSS y sin SSR por defecto.
- **Justificación:** Elección explícita del desarrollador.

### ASSUMPTION-003: Vinculación con GitHub
- **Decisión del Usuario:** El repositorio remoto oficial es `https://github.com/JoaquinVM/akhana-admin.git`.
- **Justificación:** Repositorio provisto por el usuario para vinculación y push inicial.
