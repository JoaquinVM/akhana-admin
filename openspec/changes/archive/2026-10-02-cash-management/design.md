# Design: Gestión de Caja Compartida

## Context

El sistema Akhana Admin cuenta con autenticación JWT basada en roles y un catálogo consolidado (productos, categorías, etiquetas, proveedores). La operación diaria requiere gestionar el flujo financiero físico mediante una caja compartida entre los usuarios autenticados. Ver [proposal.md](file:///Users/joaquin/Documents/Akhana%20Admin/openspec/changes/cash-management/proposal.md) para la motivación.

## Goals / Non-Goals

**Goals:**
- Implementar un modelo de datos relacional robusto con restricción a nivel de base de datos para impedir más de una caja abierta a la vez.
- Garantizar que las ventas solo puedan emitirse si existe una caja abierta, registrando el usuario creador y el método de pago (`EFECTIVO` o `QR`).
- Proveer una pantalla "Caja Actual" con métricas en tiempo real del turno: monto inicial, efectivo acumulado, ventas por QR, total recaudado y efectivo esperado en gaveta.
- Facilitar el registro operativo directo de ventas rápidas desde "Caja Actual" para alimentar las métricas en tiempo real.
- Diseñar un flujo de arqueo con matriz interactiva de 11 denominaciones oficiales (Bs 200, 100, 50, 20, 10, 5, 2, 1, 0.50, 0.20, 0.10) distinguiendo cantidades en Caja y Reserva.
- Facilitar el cierre con cálculo automático de diferencia sin bloquear la finalización ante descuadres.
- Disponer de un historial de cajas ("Cajas") con modal de auditoría exhaustiva (aperturas, cierres, ventas vinculadas y detalle de cortes).

**Non-Goals:**
- Facturación electrónica tributaria (SIN) o impresión física de tickets térmicos en esta fase.
- Arqueo multimonedas (dólares u otras divisas); se opera exclusivamente en Bolivianos (Bs).
- Múltiples cajas físicas simultáneas (por ahora la caja es compartida a nivel de sucursal).

## Decisions

### 1. Modelo de Datos y Restricción de Concurrencia (PostgreSQL 17)
- **Decisión:** Crear las tablas `cash_sessions`, `cash_denomination_cuts` y `sales`.
- **Métodos de Pago:** Se define el enum `PaymentMethod` con dos valores específicos: `EFECTIVO` y `QR`.
- **Garantía de Unicidad de Caja Abierta:** Se implementa un índice único parcial en PostgreSQL:
  ```sql
  CREATE UNIQUE INDEX idx_cash_sessions_only_one_open 
  ON cash_sessions (status) 
  WHERE status = 'ABIERTA';
  ```
  Esto previene condiciones de carrera (*race conditions*) a nivel de motor de base de datos aun si dos usuarios intentan abrir caja exactamente en el mismo milisegundo.
- **Alternativa Descartada:** Validar únicamente en el backend con `SELECT COUNT(*) WHERE status = 'ABIERTA'`, lo cual es vulnerable a concurrencia sin bloqueo pesimista o índices exclusivos.

### 2. Contratos REST en Backend (`/api/cash-sessions`)
- `POST /api/cash-sessions/open`: Abre nueva caja (`openingAmount`, `openingComment`).
- `GET /api/cash-sessions/current`: Retorna la sesión activa con métricas calculadas en tiempo real.
- `POST /api/cash-sessions/current/sales`: Permite registrar ventas vinculadas a la caja activa (`totalAmount`, `paymentMethod: 'EFECTIVO' | 'QR'`, `description`).
- `POST /api/cash-sessions/current/close`: Cierra la caja (`closingAmount`, `closingComment`, `cuts[]`).
- `GET /api/cash-sessions`: Historial de cajas ordenadas de forma descendente por fecha de apertura.
- `GET /api/cash-sessions/{id}`: Detalle exhaustivo de una caja con sus ventas y matriz de cortes.

### 3. Matriz de Cortes de Denominaciones
- Las 11 denominaciones se representan de forma fija y ordenada:
  `[200.00, 100.00, 50.00, 20.00, 10.00, 5.00, 2.00, 1.00, 0.50, 0.20, 0.10]`.
- Cada fila calcula: `subtotal = (caja + reserva) * denominación`.
- Si el usuario opta por usar cortes, el total resultante se traslada automáticamente a `closingAmount` y la lista de cantidades se persiste en `cash_denomination_cuts`. Si ingresa el monto manualmente, los cortes se consideran omitidos.

### 4. Unificación de POS y Caja Actual (ADR: Consolidación en PosComponent)
- **Decisión de Arquitectura:** En lugar de mantener pantallas independientes para el POS y la Caja Actual, se unifican en un único componente de primer nivel: `PosComponent` (`frontend/src/app/pages/pos/pos.component.ts`), montado en la ruta canónica `/pos`.
- **Comportamiento Reactivo Adaptativo:**
  - **Caja Cerrada:** La pantalla muestra el estado de caja cerrada, inhabilita el registro de ventas y ofrece el botón destacado para abrir una nueva sesión compartida mediante `OpenCashModalComponent`.
  - **Caja Abierta:** Presenta de manera conjunta la barra de estado del turno (usuario, fecha de apertura, comentario opcional), los KPIs financieros en tiempo real (monto inicial, ventas en efectivo, ventas QR, efectivo esperado en gaveta), la tabla de transacciones de la sesión activa, la acción de registro de venta rápida (`RegisterSaleModalComponent`) y el cierre de caja (`CloseCashModalComponent`).
  - **Restricción de Navegación:** Queda expresamente prohibido incluir botones o enlaces hacia el historial de cajas desde la pantalla POS.
- **Navegación en Navbar:**
  - El grupo `Ventas` contiene:
    - `POS` (`/pos`): con etiqueta de estado dinámica (`Abierta` o `Cerrada`) calculada reactivamente desde `CashService.currentSession()`.
    - `Ventas` (`/sales`): historial general de ventas.
    - `Historial de cajas` (`/cash/history`): acceso exclusivo para consultar y auditar sesiones de caja.
  - Se elimina el menú independiente "Caja".
- **Eliminación de Código Obsoleto:**
  - Se eliminan por completo los archivos de `CurrentCashComponent` (`pages/cash/current-cash/`) y la ruta `/cash/current`.
- **Diseño Visual:** Alineado al sistema Corporate Organic Glassmorphism (paleta verde bosque `#1B3B18`, dorado `#F5B800` y acento `#7BB142`).

### 5. Modal de Información de Sesión de Caja (Simplificación de POS)
- **Decisión de UX:** Trasladar la información detallada de la sesión de caja de la vista principal del POS a un modal dedicado: `CashSessionInfoModalComponent`.
- **Eliminación de Redundancia:**
  - Se elimina el banner independiente de turno en la vista principal del POS, el cual contenía un botón "Cerrar caja" duplicado.
  - Se conserva únicamente el botón oficial "Cerrar caja" en las acciones principales de la cabecera.
  - Se añade un botón "Información" en la cabecera del POS que despliega el modal.
  - El modal de información exhibe estrictamente: número de sesión, usuario responsable de apertura, fecha/hora de apertura y comentario de apertura (omitiendo la sección si no existe). No muestra estado de la caja ni botones para cerrar caja.

### 6. Ajuste de Acciones Principales e Iconografía Monocromática SVG
- **Depuración de Acciones en Cabecera:** Se elimina el botón "Registrar venta" ubicado en la cabecera junto a "Cerrar caja". La acción de registro de ventas se centraliza en su contexto natural: la sección de transacciones de la sesión activa (`transactions-header`), evitando botones redundantes.
- **Unificación de Iconografía:** Se erradica por completo el uso de emojis como iconos en la pantalla POS y modales asociados. Se sustituyen por iconos SVG monocromáticos vectoriales (`stroke="currentColor"`, `stroke-width="2"`), alineados al sistema de diseño sobrio y profesional de Akhana.

### 7. Ajustes en Historial de Cajas
- **Eliminación de Acceso Inverso:** Se suprime el botón "Ir a caja actual" en `CashHistoryComponent`, consolidando la regla de que el acceso a POS se efectúa únicamente desde el menú superior del navbar.
- **Indicador Visual Homogéneo para Sesiones Cerradas:** Se añade la regla CSS para `.badge-closed::before` con fondo gris (`#94a3b8`), asegurando que tanto las sesiones abiertas (punto verde) como las cerradas (punto gris) posean el mismo dot indicador de 6px y posición, erradicando el espacio en blanco.

## Risks / Trade-offs

- **[Riesgo: Registro concurrente de venta mientras se ejecuta el cierre]**  
  → *Mitigación:* La transacción de registro de venta valida atómicamente que `session.status == 'ABIERTA'`. Si la sesión acaba de ser cerrada por otro usuario, se retorna un error HTTP 409 Conflict.
- **[Riesgo: Inconsistencias por redondeo en centavos (0.50, 0.20, 0.10)]**  
  → *Mitigación:* Se utiliza `BigDecimal` con 2 decimales y `RoundingMode.HALF_UP` en Java, y precisión de 2 decimales en el cálculo de TypeScript.
- **[Riesgo: Diferencia no nula en cierre bloqueando la operación]**  
  → *Mitigación:* Siguiendo la regla de negocio explícita, la diferencia se muestra en un banner informativo (rojo si hay faltante, verde si hay sobrante/cuadre exacto), pero nunca bloquea el botón de confirmación de cierre.

## Migration Plan

1. Crear script Flyway `database/migrations/V6__create_cash_management_tables.sql`.
2. Actualizar el esquema canónico [database/schemas/schema.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql).
3. Desplegar los componentes de backend y frontend correspondientes.
