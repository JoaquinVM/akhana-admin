# Historias de Usuario - REQ-0002: Reorganización Full Stack y Creación Frontend

---

### US-001: Traslado y Operación del Backend en `backend/`
- **ID:** `US-001` (Trazabilidad: `REQ-0002`)
- **Como:** Desarrollador backend
- **Quiero:** Que el proyecto Spring Boot resida completamente dentro de la carpeta `backend/`
- **Para:** Tener una separación limpia y desacoplada respecto a la interfaz de usuario.

#### Criterios de Aceptación:
```gherkin
Escenario: Compilación y ejecución de tests desde la nueva ubicación
  Dado que el código de Spring Boot se trasladó a "backend/"
  Cuando ejecuto "./gradlew test" en la carpeta "backend/"
  Entonces todos los tests (incluyendo autenticación JPA y seeds) se compilan y aprueban exitosamente
  Y la configuración de PostgreSQL se conserva intacta
```

---

### US-002: Proyecto Angular Base en `frontend/`
- **ID:** `US-002` (Trazabilidad: `REQ-0002`)
- **Como:** Desarrollador frontend
- **Quiero:** Disponer de una aplicación Angular estándar y funcional en `frontend/`
- **Para:** Contar con el andamiaje base preparado para implementar las pantallas y consumir la API REST.

#### Criterios de Aceptación:
```gherkin
Escenario: Inicialización de proyecto Angular funcional
  Dado que se ejecuta la creación con Angular CLI en "frontend/"
  Cuando inspecciono los archivos generados
  Entonces existe package.json, angular.json, src/ y configuración de build
  Y la aplicación cuenta con cliente HTTP preparado para llamadas REST
  Y se puede ejecutar la compilación con "npm run build" sin errores
```

---

### US-003: Versionado Git y Publicación en GitHub
- **ID:** `US-003` (Trazabilidad: `REQ-0002`)
- **Como:** Equipo de desarrollo
- **Quiero:** Un repositorio Git unificado en la raíz del proyecto con `.gitignore` adecuado y `README.md`
- **Para:** Mantener el control de versiones y publicar el proyecto Full Stack en GitHub sin subir artefactos temporales ni secretos.

#### Criterios de Aceptación:
```gherkin
Escenario: Exclusión de archivos generados y binarios
  Dado el archivo .gitignore en la raíz
  Cuando se ejecuta "git status"
  Entonces "node_modules/", "build/", ".gradle/", ".angular/" y ".idea/" no se incluyen en el staging
  Y se genera un commit inicial limpio con la estructura backend y frontend
  Y el repositorio queda vinculado a GitHub para realizar push
```
