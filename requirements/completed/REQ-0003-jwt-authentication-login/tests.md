# Estrategia de Pruebas (Tests) - REQ-0003: Autenticación JWT

---

## 🧪 Matriz de Casos de Prueba

| Caso de Prueba | Tipo | Entrada | Resultado Esperado | US Asociada |
| :--- | :--- | :--- | :--- | :--- |
| `testGenerateToken_ContainsRequiredClaims` | Unitario (`JwtServiceTest`) | Objeto `User` (admin) | Token no nulo, claims `sub`, `username`, `role`, `iat`, `exp` presentes | `US-001` |
| `testValidateToken_ValidToken` | Unitario (`JwtServiceTest`) | Token generado para admin | `isTokenValid()` es true, `extractUsername()` retorna "admin" | `US-003` |
| `testValidateToken_ExpiredToken` | Unitario (`JwtServiceTest`) | Token generado con expiración en el pasado | Falla validación o lanza excepción de expiración | `US-003` |
| `testLogin_ReturnsJwtToken` | Integración (`AuthControllerTest`) | `POST /api/auth/login` con credenciales válidas | HTTP 200 OK con campo `token` no vacío | `US-001` |
| `testProtectedEndpoint_WithoutToken_Returns401` | Integración (`JwtAuthenticationIntegrationTest`) | `GET /api/auth/me` sin header `Authorization` | HTTP 401 Unauthorized | `US-003` |
| `testProtectedEndpoint_WithValidToken_Returns200` | Integración (`JwtAuthenticationIntegrationTest`) | `GET /api/auth/me` con `Authorization: Bearer <validToken>` | HTTP 200 OK con datos de perfil extraídos de `SecurityContext` | `US-003` |
| `testProtectedEndpoint_WithInvalidToken_Returns401` | Integración (`JwtAuthenticationIntegrationTest`) | `GET /api/auth/me` con `Authorization: Bearer token_falso` | HTTP 401 Unauthorized | `US-003` |
| `testLogin_InvalidCredentials_Unchanged` | Integración (`AuthControllerTest`) | `POST /api/auth/login` con clave incorrecta | HTTP 401 Unauthorized genérico (sin token) | `US-001` |

---

## 🛠️ Comandos de Ejecución
```bash
cd backend
./gradlew test
```
