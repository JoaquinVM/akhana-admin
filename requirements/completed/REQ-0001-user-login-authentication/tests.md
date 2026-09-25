# Estrategia de Pruebas (Tests) - REQ-0001: Login y Autenticación de Usuarios

---

## 🧪 Estrategia de Verificación y Casos de Prueba

La suite de pruebas validará el comportamiento unitario e integración del flujo de autenticación:

| Caso de Prueba | Tipo | Entrada | Resultado Esperado | US Asociada |
| :--- | :--- | :--- | :--- | :--- |
| `testLogin_Success` | Integración (MockMvc) | `admin` / `12345admin` | HTTP 200 OK + `LoginResponse` | `US-003` |
| `testLogin_InvalidPassword` | Integración (MockMvc) | `admin` / `wrongPass` | HTTP 401 Unauthorized genérico | `US-003` |
| `testLogin_UserNotFound` | Integración (MockMvc) | `unknown` / `12345` | HTTP 401 Unauthorized genérico | `US-003` |
| `testLogin_InactiveUser` | Integración (MockMvc) | `seller` (active=false) / `12345seller` | HTTP 401 Unauthorized genérico | `US-003` |
| `testDataInitializer_Idempotence` | Unitario | Base de datos con usuarios ya presentes | No genera duplicados ni altera hashes | `US-002` |
| `testPasswordHashing_BCrypt` | Unitario | Usuario nuevo | Hash empieza por `$2a$` o `$2b$`, `matches()` es true | `US-001` |

---

## 🛠️ Comandos de Ejecución
```bash
./gradlew test --tests com.akhana.akhana_admin.*
```
