# Proposal: Gestión de cuentas y transacciones financieras

## Why

El sistema POS de Akhana administra ventas y sesiones de caja, pero carece de un módulo financiero integral para controlar el flujo de dinero consolidado de la empresa (efectivo en caja, cuentas bancarias, billeteras digitales y gastos operativos). Se requiere centralizar la gestión de cuentas financieras, saldos en tiempo real, ingresos manuales, gastos, transferencias entre cuentas y la imputación automática de las ventas del POS, garantizando trazabilidad y auditoría completa.

## What Changes

- **Nuevo menú principal de navegación**: Menú «Finanzas» con subsecciones: «Resumen», «Transacciones», «Cuentas» y «Categorías».
- **Gestión de Cuentas Financieras**:
  - Cuentas iniciales del sistema: «Caja» (cuenta fija protegida), «Cuenta bancaria BISA» y «Cuenta Joaco».
  - Creación, edición de nombre y selector de color orgánico discreto (usado visualmente en todo el módulo).
  - Configuración única obligatoria de **Cuenta de pagos QR**.
  - Restricción de eliminación: solo cuentas con saldo actual exactamente 0 y que no sean la cuenta QR ni la cuenta Caja.
  - Trazabilidad y auditoría completa (creación, modificación, eliminación con soft-delete).
- **Gestión de Categorías Financieras**:
  - Clasificación obligatoria en `INGRESO` o `GASTO`.
  - Configuración única obligatoria de **Categoría de ventas** (inicialmente «Ventas» de tipo `INGRESO`), protegida contra eliminación.
  - CRUD con auditoría completa.
- **Gestión de Transacciones Financieras**:
  - Registro de movimientos: `INGRESO_MANUAL`, `GASTO_MANUAL`, `TRANSFERENCIA` y `SALDO_INICIAL`.
  - Imputación automática de ventas: cada venta completada genera ingresos categorizados en «Ventas» (efectivo a «Caja», QR a la cuenta configurada como QR, o mixto distribuido).
  - Manejo independiente de **Fecha de transacción** (ocurrencia económica) vs. **Fecha/hora de registro** (auditoría del sistema).
  - Control de saldos: `Saldo = saldo inicial + ingresos - gastos + transferencias recibidas - transferencias enviadas`. Saldo persistido `current_balance` actualizado transaccionalmente.
  - Validación de saldo suficiente en transferencias entre cuentas diferentes.
  - Registro de gastos de Caja disponible directamente desde la pantalla de Caja con cuenta bloqueada.
  - Anulación auditada de transacciones (no eliminación física): revierte saldos, conserva fecha original y registra usuario, fecha/hora y comentario.
  - Transacciones de ventas protegidas: no editables ni anulables desde Finanzas; apertura directa del modal de detalle de venta (`SaleDetailModalComponent`).
- **Historial y Resumen Financiero**:
  - Dashboard de resumen financiero: saldo de Caja, BISA, Joaco, saldo total, cuenta QR activa y últimos movimientos.
  - Historial de transacciones con filtros por rango de fechas (por defecto mes en curso: 1º del mes 00:00:00 al día actual 23:59:59), tipo, cuenta, categoría, estado (`ACTIVO`/`ANULADO`) y buscador por descripción.
  - Modal de detalle de transacción con auditoría completa (registro, modificación, anulación).

## Capabilities

### New Capabilities
- `financial-management`: Gestión integral de cuentas financieras, categorías de ingresos/gastos, transferencias, movimientos manuales, auditoría, saldos y dashboard de resumen.

### Modified Capabilities
- `sales-registration`: Integración automática en el ciclo de vida de la venta para generar transacciones de ingreso financiero al registrar venta y anularlas si la venta es anulada.

## Impact

- **Database**: Nuevas tablas `financial_accounts`, `financial_categories` y `financial_transactions` con índices y migración incremental (`V8__create_financial_management_tables.sql`).
- **Backend**:
  - Nuevos modelos, repositorios, servicios (`AccountService`, `CategoryService`, `TransactionService`, `FinanceSummaryService`) y controladores REST bajo `/api/finances/**`.
  - Modificación en `SaleService`: inyección o llamado desacoplado al servicio financiero para registrar ingresos por venta y anularlos en caso de anulación de la venta.
- **Frontend**:
  - Actualización de `navigation.config.ts` y enrutamiento con grupo «Finanzas» (`/finances/summary`, `/finances/transactions`, `/finances/accounts`, `/finances/categories`).
  - Nuevos servicios HTTP en `core/finance/`.
  - Componentes de pantalla para Resumen, Transacciones, Cuentas y Categorías con diseño Corporate Organic Glassmorphism.
  - Reutilización de `ColorPickerComponent` y `SaleDetailModalComponent`.
