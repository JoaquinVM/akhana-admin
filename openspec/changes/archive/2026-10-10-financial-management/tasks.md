# Tasks: Gestión de cuentas y transacciones financieras

## 1. Base de Datos y Migraciones

- [x] 1.1 Crear migración `database/migrations/V8__create_financial_management_tables.sql` con tablas `financial_accounts`, `financial_categories` y `financial_transactions`, restricciones únicas parciales y datos iniciales («Caja», «Cuenta bancaria BISA» como QR, «Cuenta Joaco» y categoría «Ventas»), verificando sintaxis SQL.

## 2. Entidades y Repositorios Backend

- [x] 2.1 Implementar entidades JPA (`FinancialAccount`, `FinancialCategory`, `FinancialTransaction`) y enums en `com.akhana.akhana_admin.model` con auditoría completa y relaciones.
- [x] 2.2 Crear repositorios Spring Data JPA (`FinancialAccountRepository`, `FinancialCategoryRepository`, `FinancialTransactionRepository`) con consultas para cuenta QR, categoría ventas y filtrado dinámico de transacciones.

## 3. Servicios y Controladores Backend

- [x] 3.1 Implementar `FinancialAccountService` y `FinancialCategoryService` con lógica de negocio (unicidad de cuenta QR, unicidad de categoría de ventas, reglas de eliminación con saldo cero, protección de Caja y categoría de ventas) con tests unitarios.
- [x] 3.2 Implementar `FinancialTransactionService` con registro de ingresos/gastos manuales, transferencias entre cuentas distintas con saldo suficiente, anulación auditada con reversión de saldos, control de fecha de transacción vs fecha de registro y tests unitarios.
- [x] 3.3 Implementar `FinancialSummaryService` para cálculo de saldos consolidados, cuentas activas y últimos movimientos con tests unitarios.
- [x] 3.4 Implementar controladores REST bajo `/api/finances/**` (`FinancialAccountController`, `FinancialCategoryController`, `FinancialTransactionController`, `FinancialSummaryController`) con validación de DTOs y pruebas de integración.

## 4. Integración con Módulo de Ventas y Caja

- [x] 4.1 Modificar `SaleService` para generar transacciones automáticas de ingreso al completar ventas (Efectivo en Caja, QR en cuenta QR, Mixto distribuido) bajo la categoría de ventas activa, y anularlas automáticamente al anular la venta, verificando con pruebas de integración.
- [x] 4.2 Habilitar endpoint o servicio para registro de gastos de Caja con cuenta Caja forzada y validada.
- [x] 4.3 Asociar los gastos de Caja con `cash_session_id` y exponer endpoint `GET /api/finances/transactions/session/{cashSessionId}` para consultar compras y gastos de la sesión.

## 5. Frontend: Modelos, Servicios y Enrutamiento

- [x] 5.1 Crear modelos TypeScript y servicios HTTP con Angular Signals en `frontend/src/app/core/finance/` (`financial-account.service.ts`, `financial-category.service.ts`, `financial-transaction.service.ts`, `finance-summary.service.ts`) con pruebas unitarias.
- [x] 5.2 Actualizar `navigation.config.ts` para incorporar el menú «Finanzas» (Resumen, Transacciones, Cuentas, Categorías) y configurar las rutas en `app.routes.ts`.

## 6. Frontend: Vistas y Componentes de Finanzas

- [x] 6.1 Implementar pantalla de Cuentas (`AccountsComponent` en `/finances/accounts`) con listado, selector de color, toggle de cuenta QR, saldos, modal de movimientos y pruebas unitarias.
- [x] 6.2 Implementar pantalla de Categorías (`CategoriesComponent` en `/finances/categories`) con clasificación Ingreso/Gasto, toggle de categoría de ventas, auditoría y pruebas unitarias.
- [x] 6.3 Implementar pantalla de Transacciones (`TransactionsComponent` en `/finances/transactions`) con filtros por rango de fechas (mes actual por defecto), tipo, cuenta, categoría, estado, modales de ingreso/gasto/transferencia, anulación auditada, apertura de `SaleDetailModalComponent` para ventas y pruebas unitarias.
- [x] 6.4 Implementar pantalla de Resumen (`FinanceSummaryComponent` en `/finances/summary`) con KPIs de cuentas (Caja, BISA, Joaco, Total), cuenta QR activa, últimos movimientos y pruebas unitarias.
- [x] 6.5 Incorporar acción de registro de gasto en la interfaz de Caja/POS con la cuenta Caja preseleccionada y bloqueada.
- [x] 6.6 Incorporar selector visual en la pantalla de Caja (`Ventas` | `Compras`) para alternar fluidamente entre el listado de ventas y el listado de compras y gastos asociados a la sesión activa.

## 7. Verificación Integral y Documentación

- [x] 7.1 Ejecutar suite completa de tests de backend con `./gradlew test` y verificar compilación exitosa.
- [x] 7.2 Ejecutar suite completa de tests de frontend con `npx ng test --no-watch` y compilación de producción con `npm run build`.
- [x] 7.3 Actualizar archivos `_context.md` en `frontend/`, `backend/` y `database/` con las nuevas capacidades y componentes.
