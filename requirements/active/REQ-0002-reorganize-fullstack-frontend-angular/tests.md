# Estrategia de Pruebas y Verificación - REQ-0002

---

## 🧪 Verificaciones del Requerimiento

| Componente | Verificación | Comando / Prueba | Resultado Esperado |
| :--- | :--- | :--- | :--- |
| **Backend** | Tests unitarios y de integración de Spring Boot | `cd backend && ./gradlew test` | 10 tests pasados, BUILD SUCCESSFUL |
| **Backend** | Compilación y empaquetado JAR | `cd backend && ./gradlew build -x test` | BUILD SUCCESSFUL |
| **Frontend** | Compilación del proyecto Angular 19 | `cd frontend && npm run build` | Application bundle generated without errors |
| **Git** | Exclusión de node_modules, build y temporales | `git status` | Ningún archivo de build o node_modules staged |
| **Git** | Repositorio y commits | `git log -n 1` | Commit inicial limpio presente |
