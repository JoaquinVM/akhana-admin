# Contexto de Directorio: frontend/

## Propósito y Responsabilidad
Capa de interfaz de usuario para el panel de administración de Akhana. Proyecto SPA desarrollado en **Angular 21** con arquitectura standalone components, reactividad basada en Angular Signals, diseño *Corporate Organic Glassmorphism* (Stitch) alineado a la identidad de marca Akhana, navegación horizontal superior data-driven sin sidebar lateral, y enrutamiento modular protegido por JWT.

## Estructura del Proyecto Angular
- `src/app/`:
  - `app.ts`: Componente raíz de la aplicación.
  - `app.html`: Layout raíz (`<router-outlet />`).
  - `app.config.ts`: Configuración global de providers (`provideRouter`, `provideHttpClient(withInterceptors([authInterceptor]))`).
  - `app.routes.ts`: Enrutamiento principal. Configura layout protegido `MainLayoutComponent` para rutas internas (`/pos`, `/sales`, `/products`, `/categories`, `/tags`, `/users`), redirección por defecto de `/` a `/pos`, y `/login` para invitados.
  - `core/`:
    - `auth/`: Modelos (`auth.models.ts`), servicio centralizado con Signals (`auth.service.ts`), interceptor JWT (`auth.interceptor.ts`) y guards de navegación (`auth.guard.ts`).
    - `supplier/`: Modelos (`supplier.models.ts`) y servicio HTTP (`supplier.service.ts`).
    - `category/`: Modelos (`category.models.ts`) y servicio HTTP (`category.service.ts`).
    - `tag/`: Modelos (`tag.models.ts`) y servicio HTTP (`tag.service.ts`).
    - `product/`: Modelos (`product.models.ts`) y servicio HTTP (`product.service.ts`).
    - `cash/`: Modelos de sesión, cortes de denominaciones y ventas (`cash.models.ts`) y servicio reactivo con Signals (`cash.service.ts`).
    - `sale/`: Modelos detallados de ventas, líneas de venta y anulación (`sale.models.ts`) y servicio reactivo con Signals (`sale.service.ts`).
    - `quick-product/`: Modelos de grupos y productos rápidos (`quick-product.models.ts`) y servicio reactivo con Signals (`quick-product.service.ts`).
    - `finance/`: Modelos del módulo financiero (`models/finance.models.ts`) y servicios HTTP reactivos con Signals (`financial-account.service.ts`, `financial-category.service.ts`, `financial-transaction.service.ts`, `finance-summary.service.ts`).
    - `navigation/`:
      - `models/navigation.models.ts`: Interfaces `NavItem`, `NavGroup`.
      - `navigation.config.ts`: Estructura centralizada y extensible del menú principal (5 grupos corporativos: Ventas, Finanzas, Catálogo, Compras, Seguridad; grupo Finanzas con Resumen, Transacciones, Cuentas, Categorías).
  - `shared/components/`:
    - `modal/`: Componente modal accesible con control de backdrop (`ModalComponent`).
    - `confirm-modal/`: Diálogo de confirmación para descarte o eliminación (`ConfirmModalComponent`).
    - `audit-modal/`: Consulta estética de trazabilidad (`AuditModalComponent`).
    - `color-picker/`: Selector de color con paleta calibrada de 18 tonos orgánicos y `ControlValueAccessor` (`ColorPickerComponent`).
    - `sale-detail-modal/`: Componente desacoplado y reutilizable de visualización completa de ventas (`SaleDetailModalComponent`), con carga bajo demanda desde backend, desglose de ítems, descuentos acumulados, métodos de pago y auditoría de anulación (reutilizable en POS e Historial de ventas).
  - `layout/`:
    - `navbar/`: Barra de navegación horizontal superior compacta (logo corporativo, menús desplegables en hover, badge dinámico reactivo Abierta/Cerrada para POS, detección reactiva de grupo/opción activa, chip de usuario autenticado y botón de logout).
    - `main-layout/`: Contenedor maestro autenticado que aloja el `NavbarComponent` y el `<router-outlet />` de vistas.
  - `pages/`:
    - `login/`: Pantalla de inicio de sesión con Organic Glassmorphism y validación reactiva.
    - `pos/`:
      - `PosComponent` (`/pos`): Terminal principal de supervisión y control de caja compartida. Integra:
        - Estado cerrado con formulario de apertura (monto inicial en efectivo).
        - Estado abierto con KPIs financieros en tiempo real (monto inicial, ventas efectivo, ventas QR, gastos de caja y efectivo esperado en gaveta: apertura + ventas efectivo − gastos activos).
        - Selector visual de contenido (`.view-pill-group`) para alternar fluidamente entre **Ventas** (por defecto) y **Compras** (gastos de la sesión de caja).
        - Vista de **Ventas**: listado de ventas de la sesión con botón «Registrar venta» para navegar a `/pos/sale`, acciones estandarizadas (`.table-actions`): 1. «Ver detalle» (abre `SaleDetailModalComponent`) y 2. «Anular» (`VoidSaleModalComponent`).
        - Vista de **Compras**: listado de compras y gastos de la sesión de caja activa con categorías con indicador cromático, contador que excluye gastos anulados, botón «Registrar Gasto» y modal de anulación auditada de gasto (sin columna redundante de Cuenta ni botón Ver Detalle).
        - Modal de información de la sesión activa (`CashSessionInfoModalComponent`) y cierre de caja con arqueo ergonómico (`CloseCashModalComponent`).
        - Restricción estricta de navegación: sin botones ni enlaces hacia historial de cajas.
      - `register-sale/` (`RegisterSaleComponent` en `/pos/sale`): Pantalla dedicada de registro y cobro de ventas. Integra:
        - Catálogo y buscador predictivo de productos con adición en un clic e incremento automático de cantidades.
        - Accesos directos a "Productos Rápidos" organizados por grupos configurables con Drag & Drop (`QuickProductsConfigModalComponent` vía `@angular/cdk/drag-drop`).
        - Carrito interactivo optimizado verticalmente en una sola fila con alineación en columnas: edición de cantidad (con spinners nativos ocultos), descuentos unitarios alineados a la derecha, precios y subtotal, y descuento general de la venta.
        - Pasarela de cobro en modal ergonómico (`openCheckoutModal()`): Efectivo con cálculo reactivo de cambio y validación de monto suficiente, QR directo, y Mixto (porción en efectivo con remanente QR calculado y cambio).
        - Botón para volver al POS o cancelar la venta.
      - `components/`: Componentes modales auxiliares: `QuickProductsConfigModalComponent` y `VoidSaleModalComponent`.
    - `suppliers/`: Gestión completa de Proveedores (`SuppliersComponent`).
    - `categories/`: Gestión completa de Categorías y familias cromáticas (`CategoriesComponent`).
    - `tags/`: Gestión completa de Etiquetas y familias cromáticas (`TagsComponent`).
    - `products/`: Gestión completa de Productos (`ProductsComponent`).
    - `finances/`: Módulo de Gestión Financiera y Contable:
      - `summary/` (`FinanceSummaryComponent` en `/finances/summary`): KPIs consolidados (Saldo total, Caja, BISA, Joaco, cuenta QR activa), desglose de cuentas y tabla de últimos movimientos.
      - `transactions/` (`TransactionsComponent` en `/finances/transactions`): Tabla transaccional con indicador cromático de categorías (píldora con `color-dot` similar a productos), filtros avanzados (rango fechas, tipo, cuenta, categoría, estado), modales para ingreso manual (con exclusión de Caja), gasto manual, transferencias entre cuentas distintas, anulación auditada y consulta directa de venta (`SaleDetailModalComponent`).
      - `accounts/` (`AccountsComponent` en `/finances/accounts`): Administración de cuentas con selector de color (`ColorPickerComponent`), toggle de cuenta QR única, saldo consolidado, modal de movimientos por cuenta y eliminación condicionada (saldo 0, no Caja, no QR).
      - `categories/` (`FinancialCategoriesComponent` en `/finances/categories`): Clasificación por tipo Ingreso/Gasto, selector y muestra visual de color identificador (`ColorPickerComponent`), toggle de categoría de ventas única para ingresos, auditoría y protección de eliminación para categoría de ventas.
    - `cash/`:
      - `cash-history/`: Consulta histórica y auditoría de sesiones (`CashHistoryComponent`), accesible exclusivamente desde el menú Ventas.
      - `components/`: Componentes y modales reutilizables de caja: `OpenCashModalComponent`, `CloseCashModalComponent`, `CashDetailModalComponent` y `CashSessionInfoModalComponent`.
    - `section-page/`: Contenedor reactivo data-driven reutilizable que renderiza el encabezado y estado de la sección activa según `route.data`.
- `public/`:
  - `images/akhana-logo.png`: Logo oficial de Akhana (círculo zen dorado y follaje verde).
- `proxy.conf.json`: Proxy de desarrollo que reenvía peticiones `/api` a `http://localhost:8080`.
- `angular.json`: Configuración del workspace Angular con proxy y presupuestos de estilo ajustados.
- `package.json`: Dependencias (Angular 21.2+, TypeScript 5.9+, Vitest).

## Comandos Operativos
- Ejecución de desarrollo: `PATH="/opt/homebrew/bin:$PATH" npm start` (inicia en `http://localhost:4200` con proxy a `:8080`)
- Compilación de producción: `PATH="/opt/homebrew/bin:$PATH" npm run build`
- Pruebas unitarias: `PATH="/opt/homebrew/bin:$PATH" npx ng test --watch=false`

## Convenciones
1. Utilizar Standalone Components en Angular.
2. Centralizar la configuración de navegación en `navigation.config.ts` para posibilitar extensión modular sin tocar el HTML.
3. Usar Angular Signals (`signal`, `computed`) para la reactividad en componentes y servicios.
4. Mantener la paleta y estética Corporate Organic Glassmorphism de Akhana: Verde Bosque (`#1B3B18` / `#2E5B27`), Dorado Zen (`#F5B800`), Acento Hoja (`#7BB142`).
5. Todas las rutas administrativas deben residir dentro de `MainLayoutComponent` bajo `canActivate: [authGuard]`.
