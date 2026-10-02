# Contexto de Paquete: com.akhana.akhana_admin

## Propósito y Responsabilidad
Paquete raíz de la aplicación Spring Boot. Contiene la clase principal de arranque `AkhanaAdminApplication.java` y los subpaquetes de autenticación, seguridad y servicios.

## Estructura de Subpaquetes Implementada
- `controller/`: 
  - `AuthController.java`: Endpoints `POST /api/auth/login` (público) y `GET /api/auth/me` (protegido).
  - `SupplierController.java`: Endpoints CRUD `/api/suppliers` (filtro por estado y búsqueda).
  - `CategoryController.java`: Endpoints CRUD `/api/categories` (filtro por estado y búsqueda).
  - `TagController.java`: Endpoints CRUD `/api/tags` (filtro por estado y búsqueda).
  - `ProductController.java`: Endpoints CRUD `/api/products` (filtro por estado, búsqueda por código/nombre y conmutación de estado).
  - `CashSessionController.java`: Endpoints `/api/cash-sessions` para apertura (`/open`), consulta activa (`/current`), venta rápida (`/current/sales`), cierre con cortes (`/current/close`), historial y detalle.
- `service/`: 
  - `AuthService.java` y `impl/AuthServiceImpl.java`: Autenticación con BCrypt, verificación de usuario activo y generación de JWT.
  - `JwtService.java` y `impl/JwtServiceImpl.java`: Generación y validación de tokens JWT mediante JJWT.
  - `SupplierService.java` y `impl/SupplierServiceImpl.java`: Gestión de proveedores con unicidad backend y soft-delete.
  - `CategoryService.java` y `impl/CategoryServiceImpl.java`: Gestión de categorías con unicidad case-insensitive backend excluyendo eliminadas y soft-delete.
  - `TagService.java` y `impl/TagServiceImpl.java`: Gestión de etiquetas con unicidad case-insensitive backend excluyendo eliminadas y soft-delete.
  - `ProductService.java` y `impl/ProductServiceImpl.java`: Gestión de productos con cálculo automático de utilidades, unicidad de código/nombre y conmutación de estado.
  - `CashSessionService.java` y `impl/CashSessionServiceImpl.java`: Ciclo de vida de caja compartida, validación de sesión única activa, registro de ventas vinculadas (Efectivo/QR), cálculo de esperado y balance de cierre con cortes.
- `repository/`: 
  - `UserRepository.java`: Repositorio Spring Data JPA para la entidad `User`.
  - `SupplierRepository.java`: Repositorio Spring Data JPA para proveedores.
  - `CategoryRepository.java`: Repositorio Spring Data JPA para categorías.
  - `TagRepository.java`: Repositorio Spring Data JPA para etiquetas.
  - `ProductRepository.java`: Repositorio Spring Data JPA para productos con JOIN FETCH de categoría, proveedor y etiquetas.
  - `CashSessionRepository.java`: Repositorio para sesiones de caja compartida.
  - `SaleRepository.java`: Repositorio para ventas asociadas a sesiones de caja.
- `model/`: 
  - `User.java`: Entidad JPA persistida en tabla `app_users`.
  - `Role.java`: Enum con roles `ADMIN` y `SELLER`.
  - `Supplier.java`: Entidad JPA en tabla `suppliers`.
  - `SupplierStatus.java`: Enum (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
  - `Category.java`: Entidad JPA en tabla `categories`.
  - `CategoryStatus.java`: Enum (`ACTIVO`, `ELIMINADO`).
  - `Tag.java`: Entidad JPA en tabla `tags`.
  - `TagStatus.java`: Enum (`ACTIVO`, `ELIMINADO`).
  - `Product.java`: Entidad JPA en tabla `products` con ManyToMany a `tags` y ManyToOne a `category` y `supplier`.
  - `ProductStatus.java`: Enum (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
  - `CashSession.java`: Entidad JPA en `cash_sessions` con índice único parcial para asegurar sesión única abierta.
  - `CashSessionStatus.java`: Enum (`ABIERTA`, `CERRADA`).
  - `CashDenominationCut.java`: Entidad JPA en `cash_denomination_cuts` para desglose de billetes/monedas (caja y reserva).
  - `Sale.java`: Entidad JPA en `sales` vinculada a la sesión de caja activa.
  - `PaymentMethod.java`: Enum (`EFECTIVO`, `QR`).
- `dto/`: 
  - `LoginRequest.java`, `LoginResponse.java`, `UserProfileResponse.java`.
  - `SupplierRequest.java`, `SupplierResponse.java`.
  - `CategoryRequest.java`, `CategoryResponse.java`.
  - `TagRequest.java`, `TagResponse.java`.
  - `ProductRequest.java`, `ProductResponse.java`.
  - `OpenCashRequest.java`, `CloseCashRequest.java`, `CashCutDto.java`.
  - `SaleRequest.java`, `SaleResponse.java`.
  - `CashSessionSummaryResponse.java`, `CashSessionDetailResponse.java`.
  - `ErrorResponse.java`: DTO de error estructurado.
- `config/`: 
  - `SecurityConfig.java`: Configuración de Spring Security stateless con `JwtAuthenticationFilter` antes de `UsernamePasswordAuthenticationFilter` y `JwtAuthenticationEntryPoint`.
  - `JwtAuthenticationFilter.java`: Filtro `OncePerRequestFilter` para validación de `Authorization: Bearer <token>` y persistencia en `RequestAttributeSecurityContextRepository`.
  - `JwtAuthenticationEntryPoint.java`: Manejador de respuestas 401 Unauthorized estructuradas.
  - `DataInitializer.java`: Inicialización idempotente de seeds `admin` y `seller`.
- `exception/`: 
  - `AuthenticationFailedException.java`: Excepción de autenticación.
  - `GlobalExceptionHandler.java`: Manejador `@RestControllerAdvice` retornando HTTP 401 y 400.

## Regla de Oro
Mantener el código limpio, desacoplado y con pruebas unitarias e integración en `src/test/java/com/akhana/akhana_admin/`.
