# Contexto de Directorio: backend/

## Propósito y Responsabilidad
Capa de lógica de negocio, procesamiento transaccional, APIs REST y seguridad del sistema Akhana Admin.

## Estructura y Servicios
- `akhana-admin/` (o módulo raíz Spring Boot): Servicio principal desarrollado en Java 17+ y Gradle.
  - Paquete base: `com.akhana.akhana_admin`
  - Puerto del servidor: `8080` (configurado en `application.properties`)
  - Conexión PostgreSQL: `jdbc:postgresql://localhost:5432/akhana`

## Comandos de Verificación
- Compilación y build: `./gradlew build -x test`
- Pruebas unitarias/integración: `./gradlew test`
- Ejecución local: `./gradlew bootRun`

## Convenciones de Arquitectura Backend
1. **Separación en Capas:**
   - `controller`: Exposición de endpoints REST, validación de payloads y códigos HTTP estándar (`UserController`, `SaleController`, `QuickProductController`, `CashSessionController`, `FinancialAccountController`, `FinancialCategoryController`, `FinancialTransactionController`, `FinancialSummaryController`).
   - `service`: Reglas de negocio puras, lógica de transacciones (`@Transactional`), cálculos de descuentos, pagos en efectivo/QR/mixto, anulación auditada, actualización atómica de saldos financieros, cálculo sincronizado de efectivo en gaveta y gestión de usuarios con inmutabilidad de username, BCrypt, baja lógica y protección contra auto-eliminación y eliminación del último administrador (`UserService`, `SaleService`, `QuickProductService`, `CashSessionService`, `FinancialAccountService`, `FinancialCategoryService`, `FinancialTransactionService`, `FinancialSummaryService`).
   - `repository`: Acceso a datos mediante Spring Data JPA / JDBC (`UserRepository`, `SaleRepository`, `SaleItemRepository`, `QuickProductGroupRepository`, `QuickProductGroupItemRepository`, `FinancialAccountRepository`, `FinancialCategoryRepository`, `FinancialTransactionRepository`).
   - `model` / `entity`: Entidades JPA mapeadas a las tablas en `database/` (`User`, `UserPermission`, `Sale`, `SaleItem`, `SaleStatus`, `PaymentMethod`, `QuickProductGroup`, `QuickProductGroupItem`, `FinancialAccount`, `FinancialCategory`, `FinancialTransaction`, `FinancialCategoryType`, `FinancialTransactionType`, `FinancialTransactionStatus`).
   - `dto`: Objetos de transferencia de datos de entrada/salida desacoplados de las entidades de BD (`CreateUserRequest`, `UpdateUserRequest` inmutable, `UserResponse`, `SaleDetailRequest`, `SaleItemRequest`, `QuickProductGroupRequest`, `ReorderGroupsRequest`, `FinancialAccountRequest`, `FinancialAccountResponse`, `FinancialCategoryRequest`, `FinancialCategoryResponse`, `FinancialTransactionRequest`, `FinancialTransactionResponse`, `CashExpenseRequest`, `VoidFinancialTransactionRequest`, `FinancialSummaryResponse`).
2. **Manejo de Errores:** Control global de excepciones con `@RestControllerAdvice` retornando respuestas estructuradas (`ProblemDetail` o DTO estándar de error).
3. **Seguridad y Autorización:** Autenticación stateless (JWT / Spring Security) con `@EnableMethodSecurity`. Carga de autoridades canónicas `PERMISSION_<KEY>` desde `user_permissions`, validación de usuario activo y no eliminado en `JwtAuthenticationFilter`, y control de acceso con `@PreAuthorize`.

