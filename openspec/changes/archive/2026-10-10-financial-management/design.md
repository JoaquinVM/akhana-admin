# Design: Gestión de cuentas y transacciones financieras

## Context
El sistema POS de Akhana administra ventas y sesiones de caja con persistencia en PostgreSQL (Spring Boot 4.x / Java 21) y Angular 21 Standalone con arquitectura reactiva de Signals y diseño Corporate Organic Glassmorphism. Este diseño técnico establece el modelo de datos, la capa de servicios transaccionales, las APIs REST y los componentes frontend para la administración financiera consolidada.

## Goals / Non-Goals

**Goals:**
- Proporcionar un modelo de datos robusto con trazabilidad total para cuentas financieras, categorías de ingresos/gastos y transacciones.
- Mantener el saldo persistido de cada cuenta actualizado automáticamente y con integridad transaccional ACID (`@Transactional` y bloqueos optimistas o pesimistas cuando aplique).
- Vincular el ciclo de vida de ventas de POS (`SaleService`) con transacciones financieras de ingreso de forma reactiva y consistente.
- Proveer interfaces de usuario fluidas, intuitivas y elegantes para Resumen, Transacciones, Cuentas y Categorías, reutilizando componentes existentes (`ColorPickerComponent`, `SaleDetailModalComponent`, `ModalComponent`).
- Preservar la distinción estricta entre **Fecha de transacción** (ocurrencia del movimiento) y **Fecha de registro** (auditoría del sistema).

**Non-Goals:**
- Conciliación bancaria automática vía APIs de entidades bancarias externas (movimientos bancarios se registran manual o automáticamente vía POS).
- Manejo de múltiples divisas (el sistema opera exclusivamente en Bolivianos `Bs`).
- Generación de estados contables formales o asientos de doble partida contable compleja (es un sistema de tesorería y flujo de caja para POS/gestión comercial).
- Generación retroactiva de transacciones para ventas previas a la migración (decisión explícita: saldos previos consolidados en saldos iniciales).

## Decisions

### 1. Modelo de Datos y Esquema Relacional (`database/migrations/V8__create_financial_management_tables.sql`)
- **`financial_accounts`**:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `name VARCHAR(100) NOT NULL UNIQUE`
  - `color VARCHAR(50) NOT NULL DEFAULT '#2E5B27'`
  - `initial_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00`
  - `current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0.00`
  - `is_cash_account BOOLEAN NOT NULL DEFAULT FALSE` (identifica la cuenta única Caja)
  - `is_qr_account BOOLEAN NOT NULL DEFAULT FALSE` (identifica la cuenta única receptora de QR)
  - Auditoría: `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by` (soft delete)
  - Restricción única parcial: `CREATE UNIQUE INDEX idx_financial_accounts_only_one_qr ON financial_accounts(is_qr_account) WHERE is_qr_account = TRUE AND deleted_at IS NULL;`
  - Restricción única parcial: `CREATE UNIQUE INDEX idx_financial_accounts_only_one_cash ON financial_accounts(is_cash_account) WHERE is_cash_account = TRUE;`

- **`financial_categories`**:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `name VARCHAR(100) NOT NULL`
  - `type VARCHAR(20) NOT NULL` (`INGRESO`, `GASTO`)
  - `is_sales_default BOOLEAN NOT NULL DEFAULT FALSE`
  - Auditoría: `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by`
  - Restricción única parcial: `CREATE UNIQUE INDEX idx_financial_categories_only_one_sales ON financial_categories(is_sales_default) WHERE is_sales_default = TRUE AND deleted_at IS NULL;`

- **`financial_transactions`**:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `type VARCHAR(30) NOT NULL` (`INGRESO_MANUAL`, `GASTO_MANUAL`, `TRANSFERENCIA`, `SALDO_INICIAL`, `VENTA_INGRESO`)
  - `source_account_id UUID NOT NULL REFERENCES financial_accounts(id)`
  - `destination_account_id UUID REFERENCES financial_accounts(id)` (solo para transferencias)
  - `category_id UUID REFERENCES financial_categories(id)`
  - `amount NUMERIC(14, 2) NOT NULL`
  - `transaction_date TIMESTAMP WITH TIME ZONE NOT NULL` (fecha económica editable en registro)
  - `description VARCHAR(255) NOT NULL`
  - `notes VARCHAR(500)`
  - `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'` (`ACTIVO`, `ANULADO`)
  - Vínculo opcional con venta: `sale_id UUID REFERENCES sales(id)`
  - Auditoría de registro: `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`, `created_by VARCHAR(100) NOT NULL`
  - Auditoría de modificación: `updated_at TIMESTAMP WITH TIME ZONE`, `updated_by VARCHAR(100)`
  - Auditoría de anulación: `voided_at TIMESTAMP WITH TIME ZONE`, `voided_by VARCHAR(100)`, `void_reason VARCHAR(500)`

### 2. Tratamiento de Saldos: Persistencia Transaccional vs Agregación (ADR)
- **Decisión humana / arquitectónica**: Saldo persistido en cuenta (`current_balance`) con actualización transaccional en cada evento y validación de integridad.
- **Mecanismo**: Cada inserción, modificación de monto o anulación de transacción ejecuta la actualización atómica del `current_balance` en `financial_accounts` dentro de `@Transactional`.
- **Fórmula de balance**:
  `current_balance = initial_balance + ∑(ingresos activos) - ∑(gastos activos) + ∑(transferencias recibidas activas) - ∑(transferencias enviadas activas)`.

### 3. Integración desacoplada con el módulo de Ventas (`SaleService`)
- Al registrar venta (`SaleService.createSale`):
  - Si pago `EFECTIVO`: genera transacción `VENTA_INGRESO` en cuenta Caja con categoría de ventas activa.
  - Si pago `QR`: genera transacción `VENTA_INGRESO` en la cuenta QR activa con categoría de ventas activa.
  - Si pago `MIXTO`: genera dos transacciones `VENTA_INGRESO` (una en Caja por `amountCash` y otra en cuenta QR por `amountQr`).
  - Cada transacción guarda `sale_id` y descripción `"Venta #" + saleNumber`.
- Al anular venta (`SaleService.voidSale`):
  - Busca las transacciones asociadas a la venta (`financialTransactionRepository.findBySaleId(sale.getId())`) y ejecuta su anulación con el mismo usuario, timestamp y motivo de anulación de la venta.

### 4. Estructura y Navegación Frontend (`Angular 21`)
- **Menú de navegación**: Se añade en `navigation.config.ts`:
  ```typescript
  {
    id: 'finances',
    label: 'Finanzas',
    children: [
      { label: 'Resumen', route: '/finances/summary' },
      { label: 'Transacciones', route: '/finances/transactions' },
      { label: 'Cuentas', route: '/finances/accounts' },
      { label: 'Categorías', route: '/finances/categories' }
    ]
  }
  ```
- **Rutas y vistas dedicadas**:
  - `/finances/summary`: `FinanceSummaryComponent` con KPIs de saldos, desglose de cuentas y últimos movimientos.
  - `/finances/transactions`: `TransactionsComponent` con filtros avanzados (fechas del mes por defecto), indicadores de color de cuenta, badges de tipo y estado, modales de nuevo ingreso/gasto/transferencia y detalle con auditoría o apertura de venta.
  - `/finances/accounts`: `AccountsComponent` con cards o tabla, selector de color, toggle de cuenta QR y modal de movimientos de cuenta.
  - `/finances/categories`: `CategoriesComponent` con clasificación `INGRESO`/`GASTO` y toggle de categoría de ventas.
- **Acceso a Gasto desde POS / Caja**:
  - En la vista de Caja se habilita botón «Registrar gasto de caja» que abre el modal de gasto con la cuenta Caja seleccionada y deshabilitada.

## Risks / Trade-offs

- **[Riesgo] Concurrencia en transferencias simultáneas sobre la misma cuenta**:
  - *Mitigación*: Uso de `@Transactional` en Spring Boot con nivel de aislamiento por defecto y actualización atómica en base de datos (`UPDATE financial_accounts SET current_balance = current_balance - :amount WHERE id = :id AND current_balance >= :amount`).
- **[Riesgo] Eliminación accidental de cuenta o categoría especial**:
  - *Mitigación*: Restricciones estrictas en backend y validaciones visuales en frontend (Caja y QR no eliminables; cuenta con saldo != 0 no eliminable; categoría de ventas no eliminable).
- **[Riesgo] Desalineación entre transacciones de venta y anulación**:
  - *Mitigación*: Operación ejecutada en la misma transacción `@Transactional` en `SaleService`. Si falla la anulación financiera, la anulación de la venta se revierte íntegramente.

## Migration Plan

1. **Migración Flyway/PostgreSQL (`V8__create_financial_management_tables.sql`)**:
   - Creación de tablas e índices únicos parciales.
   - Inserción de semillas maestras:
     - Cuentas: «Caja» (`is_cash_account = TRUE`), «Cuenta bancaria BISA» (`is_qr_account = TRUE`), «Cuenta Joaco» (`is_qr_account = FALSE`).
     - Categoría de ingreso: «Ventas» (`is_sales_default = TRUE`).
     - Movimientos iniciales de `SALDO_INICIAL` correspondientes a cada cuenta.
2. **Backend**:
   - Entidades JPA (`FinancialAccount`, `FinancialCategory`, `FinancialTransaction`).
   - Repositorios, DTOs, Servicios y Controladores REST.
   - Integración en `SaleService`.
3. **Frontend**:
   - Modelos y servicios en `core/finance/`.
   - Menú en `navigation.config.ts` y rutas en `app.routes.ts`.
   - Componentes de pantalla y modales auxiliares.
