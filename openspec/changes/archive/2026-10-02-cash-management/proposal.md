# Proposal: Gestión de Caja Compartida

## Why

El negocio requiere un control financiero estricto y transparente sobre el flujo de efectivo y pagos en el punto de atención. Actualmente no existe un mecanismo para abrir, operar y liquidar turnos o sesiones de caja ni para auditar el dinero en efectivo frente a las ventas concretadas. 

Implementar una gestión de **caja compartida** permite que múltiples usuarios autorizados operen de manera concurrente vinculando sus ventas a una sesión activa única, habilitando cortes de billetes/monedas (caja y reserva), cálculo automático de diferencias y trazabilidad histórica completa para prevenir pérdidas o descuadres.

## What Changes

- **Apertura de Caja Compartida:**
  - Registro de nueva sesión con monto inicial obligatorio y comentario opcional.
  - Restricción estricta de concurrencia: solo puede existir una caja con estado `ABIERTA` simultáneamente.
  - Registro de usuario de apertura, fecha/hora y estado `ABIERTA`.
- **Unificación de Pantallas (POS y Caja Actual):**
  - Consolidación de **POS** y **Caja Actual** en una única pantalla unificada denominada **POS** (`/pos`).
  - Eliminación de pantallas y rutas independientes para la caja actual.
  - Cuando la caja está **cerrada**: muestra estado de caja cerrada, botón para abrir caja con monto inicial obligatorio y comentario opcional, e impide registrar ventas.
  - Cuando la caja está **abierta**: muestra conjuntamente el registro de ventas, resumen financiero de la caja actual (monto inicial, ventas efectivo, ventas QR, efectivo esperado), transacciones del turno, datos de apertura y opción de cerrar caja.
  - Restricción estricta de navegación: desde POS no existe ningún botón o enlace hacia el historial de cajas.
- **Cierre de Caja y Arqueo por Cortes:**
  - Cierre con monto final obligatorio y comentario opcional.
  - Herramienta de conteo por denominaciones en 2 columnas sin scroll (11 denominaciones oficiales) con desglose en "Caja" y "Reserva".
  - Sincronización automática de cortes con el monto de cierre y cálculo en tiempo real de diferencia vs efectivo esperado.
  - Persistencia de los cortes para posterior auditoría.
- **Historial y Detalle ("Historial de cajas"):**
  - Consulta del historial cronológico de sesiones y auditoría exhaustiva.
- **Navegación en Navbar:**
  - Opción principal **POS** con etiqueta reactiva de estado (`Abierta` o `Cerrada` según la sesión compartida real).
  - Opción **Historial de cajas** trasladada dentro del menú desplegable **Ventas**.
  - Eliminación del menú independiente "Caja" del navbar.

## Capabilities

### New Capabilities
- `cash-management`: Ciclo de vida completo de sesiones de caja compartida (apertura, registro de ventas, cálculo de efectivo esperado, arqueo de cortes por denominación, cierre y consulta histórica detallada).

### Modified Capabilities
*(Ninguna)*

## Impact

- **Base de Datos:**
  - Nueva migración SQL `V6__create_cash_management_tables.sql` con tablas `cash_sessions`, `cash_denomination_cuts` y tabla auxiliar de `sales` asociada a la sesión de caja.
- **Backend (Spring Boot):**
  - Entidades `CashSession`, `CashDenominationCut`, `Sale` (o vinculación de ventas con usuario y método de pago).
  - Repositorios JPA y servicios de negocio con control de concurrencia y validaciones de estado.
  - Controlador REST `/api/cash-sessions` con endpoints para apertura, sesión actual, registro de ventas, cierre, historial y detalle.
  - Pruebas unitarias y de integración para reglas de negocio y cálculos de balance.
- **Frontend (Angular 21):**
  - Actualización de `navigation.config.ts` y `app.routes.ts`.
  - Servicio `CashService` con Angular Signals.
  - Componentes `CurrentCashComponent` (Caja Actual) y `CashHistoryComponent` (Cajas).
  - Modales: `OpenCashModalComponent`, `CloseCashModalComponent` (con tabla interactiva de cortes) y `CashDetailModalComponent`.
  - Pruebas unitarias de componentes y servicios en Vitest.
