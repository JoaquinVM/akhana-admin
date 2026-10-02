# Tasks: Implementación de Gestión de Caja Compartida

## 1. Base de Datos y Persistencia Relacional

- [x] 1.1 Crear migración Flyway `database/migrations/V6__create_cash_management_tables.sql` con las tablas `cash_sessions`, `cash_denomination_cuts` y `sales`, aplicando el índice único parcial `idx_cash_sessions_only_one_open` y verificar su sintaxis SQL.
- [x] 1.2 Actualizar el archivo de esquema canónico `database/schemas/schema.sql` y verificar su coherencia con las tablas existentes.

## 2. Backend — Modelos y Capa de Acceso a Datos (Spring Boot)

- [x] 2.1 Crear entidades JPA `CashSession`, `CashSessionStatus` (`ABIERTA`, `CERRADA`), `CashDenominationCut`, `Sale` y `PaymentMethod` (`EFECTIVO`, `QR`) en `model/` con sus relaciones y constraints.
- [x] 2.2 Crear `CashSessionRepository` (con consultas para sesión activa e historial) y `SaleRepository` (con consulta de ventas por sesión y sumatoria por método de pago) en `repository/`.

## 3. Backend — Servicios de Negocio, Endpoints REST y Pruebas

- [x] 3.1 Crear DTOs de transferencia (`OpenCashRequest`, `CloseCashRequest`, `CashCutDto`, `SaleRequest`, `CashSessionSummaryResponse`, `CashSessionDetailResponse`) en `dto/`.
- [x] 3.2 Implementar `CashSessionService` e `impl/CashSessionServiceImpl` con lógica de apertura única, validación de ventas con caja abierta, cálculo de efectivo esperado, arqueo de cortes, cierre y cálculo de diferencias.
- [x] 3.3 Implementar `CashSessionController` exponiendo los endpoints `/api/cash-sessions/open`, `/current`, `/current/sales`, `/current/close`, `/` (historial) y `/{id}` (detalle).
- [x] 3.4 Desarrollar pruebas unitarias y de integración en `CashSessionServiceTest` y `CashSessionControllerTest` verificando que `./gradlew test` apruebe al 100%.
- [x] 3.5 Actualizar `backend/src/main/java/com/akhana/akhana_admin/_context.md` documentando los nuevos modelos y endpoints.

## 4. Frontend — Modelos, Servicio Reactivo y Configuración de Navegación

- [x] 4.1 Definir interfaces y tipos TypeScript en `frontend/src/app/core/cash/models/cash.models.ts` y crear `CashService` en `core/cash/cash.service.ts` usando Angular Signals y HttpClient.
- [x] 4.2 Actualizar `navigation.config.ts` integrando las opciones **Caja Actual** (`/cash/current`) y **Cajas** (`/cash/history`) dentro del grupo `Ventas`, y configurar las rutas correspondientes en `app.routes.ts`.

## 5. Frontend — Pantalla "Caja Actual", Apertura y Registro de Ventas

- [x] 5.1 Crear componente `CurrentCashComponent` (`pages/cash/current-cash/`) con tarjetas de métricas en tiempo real (monto inicial, ventas en efectivo, ventas QR, efectivo esperado) y estados de caja abierta o sin caja activa.
- [x] 5.2 Implementar modal accesible de apertura de caja (`OpenCashModalComponent`) solicitando monto inicial obligatorio y comentario opcional.
- [x] 5.3 Implementar modal accesible de registro de venta rápida (`RegisterSaleModalComponent`) con monto y método de pago (`EFECTIVO`, `QR`) para asociar transacciones a la caja activa.

## 6. Frontend — Cierre con Cortes por Denominación, Historial y Detalle

- [x] 6.1 Crear modal `CloseCashModalComponent` que incluya la matriz reactiva de 11 denominaciones (Bs 200 a Bs 0.10) con columnas Caja y Reserva, cálculo en tiempo real del subtotal, traslado a monto de cierre, cálculo de diferencia vs esperado e ingreso manual opcional.
- [x] 6.2 Crear componente `CashHistoryComponent` (`pages/cash/cash-history/`) con tabla de historial de sesiones, estados, fechas, montos y diferencias calculadas.
- [x] 6.3 Implementar modal `CashDetailModalComponent` para auditar la información completa de una sesión: datos de apertura, ventas agrupadas por método de pago, datos de cierre y matriz de cortes si existió.
- [x] 6.4 Actualizar `frontend/_context.md` reflejando los nuevos componentes, servicios y rutas de caja.

## 7. Pruebas Unitarias Frontend y Verificación Final de Compilación

- [x] 7.1 Implementar pruebas unitarias en `cash.service.spec.ts` y `current-cash.component.spec.ts` asegurando cobertura de escenarios clave.
- [x] 7.2 Ejecutar `PATH="/opt/homebrew/bin:$PATH" npx ng test --watch=false` y verificar que la suite completa pase sin errores.
- [x] 7.3 Ejecutar `PATH="/opt/homebrew/bin:$PATH" npm run build` y certificar que la aplicación compile limpiamente.

## 8. Unificación de POS y Caja Actual en Pantalla Única

- [x] 8.1 Crear `PosComponent` (`frontend/src/app/pages/pos/pos.component.*`) unificando la operativa de POS con la gestión de caja: estado cerrado (abrir caja, ventas inhabilitadas), estado abierto (resumen financiero en vivo, transacciones de la sesión, registro de venta rápida, cierre de caja) y sin enlaces a historial de cajas.
- [x] 8.2 Actualizar `navigation.config.ts`, `navbar.component.ts` y `navbar.component.html`: opción POS con etiqueta reactiva ("Abierta"/"Cerrada"), opción "Historial de cajas" reubicada dentro del menú desplegable "Ventas" y eliminación del menú "Caja".
- [x] 8.3 Actualizar rutas en `app.routes.ts` (`/pos` mapeado a `PosComponent`, eliminación de `/cash/current`) y actualizar `frontend/_context.md`.
- [x] 8.4 Eliminar `CurrentCashComponent` (`frontend/src/app/pages/cash/current-cash/`) y código obsoleto.
- [x] 8.5 Crear pruebas unitarias para `PosComponent` (`pos.component.spec.ts`) y actualizar pruebas de `NavbarComponent` (`navbar.component.spec.ts`).
- [x] 8.6 Ejecutar `PATH="/opt/homebrew/bin:$PATH" npx ng test --watch=false` y `npm run build` certificando que todo pase al 100%.

## 9. Modal de Información de Sesión y Simplificación de POS

- [x] 9.1 Crear componente `CashSessionInfoModalComponent` (`frontend/src/app/pages/cash/components/cash-session-info-modal/`) para mostrar número de sesión, usuario de apertura, fecha/hora y comentario (solo si existe), sin estado ni opción de cierre.
- [x] 9.2 Modificar `PosComponent` para agregar el botón "Información" en la cabecera, gestionar la apertura del modal y remover la sección de información y el botón duplicado "Cerrar caja" de la vista principal.
- [x] 9.3 Limpiar estilos CSS huérfanos y variables asociadas a la sección eliminada en `pos.component.css`.
- [x] 9.4 Crear pruebas unitarias para `CashSessionInfoModalComponent` y actualizar pruebas de `PosComponent` para verificar el nuevo flujo, modal y criterios de aceptación.
- [x] 9.5 Ejecutar verificación completa con `npm test -- --watch=false` y `npm run build`.

## 10. Ajuste de Acciones Principales e Iconografía Monocromática en POS

- [x] 10.1 Eliminar el botón "Registrar venta" ubicado en la cabecera junto a "Cerrar caja", conservando la acción en la sección de transacciones de la sesión.
- [x] 10.2 Reemplazar emojis por iconos SVG monocromáticos en la pantalla POS (hero de caja cerrada, hint, KPIs financieros, estado vacío de transacciones, métodos de pago y banner de feedback).
- [x] 10.3 Reemplazar emojis por iconos SVG monocromáticos en los modales de operación de POS (`register-sale-modal`, `open-cash-modal`, `close-cash-modal`).
- [x] 10.4 Limpiar estilos, selectores y variables que hayan quedado sin uso tras remover el botón y los emojis.
- [x] 10.5 Actualizar y expandir pruebas unitarias verificando la ausencia del botón en cabecera y la erradicación total de emojis en la interfaz.
- [x] 10.6 Ejecutar verificación completa con `npm test -- --watch=false` y `npm run build`.

## 11. Ajustes en Historial de Cajas

- [x] 11.1 Eliminar el botón "Ir a caja actual" y la importación de `RouterLink` en `CashHistoryComponent`.
- [x] 11.2 Definir la regla CSS para `.badge-closed::before` con fondo gris (`#94a3b8`) en `styles.css` y `cash-history.component.css`, equiparando dimensiones y posición con el punto verde de `.badge-active`.
- [x] 11.3 Crear pruebas unitarias para `CashHistoryComponent` (`cash-history.component.spec.ts`) validando la ausencia del botón y la correcta asignación de clases e indicadores para sesiones abiertas y cerradas.
- [x] 11.4 Ejecutar suite de pruebas con `npm test -- --watch=false` y compilación con `npm run build`.
