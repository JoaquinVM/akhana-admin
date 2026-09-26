# Development Plan - REQ-0006: Proveedores y Componentes Reutilizables

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad técnica para adaptar o evolucionar la solución.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Backend - Entidad, Repositorio y Excepciones
- **`TASK-01`**: Crear `SupplierStatus`, entidad `Supplier`, DTOs (`SupplierRequest`, `SupplierResponse`) y excepción `DuplicateResourceException`.
- **`TASK-02`**: Crear `SupplierRepository` con métodos para validación de unicidad condicionada y listado ordenado filtrando eliminados.

### Fase 2: Backend - Servicio, API REST y Pruebas
- **`TASK-03`**: Implementar `SupplierService` con validación de unicidad en backend, auditoría automática desde `SecurityContext` y borrado lógico.
- **`TASK-04`**: Implementar `SupplierController` en `/api/suppliers` y mapeo en `GlobalExceptionHandler`.
- **`TASK-05`**: Crear pruebas unitarias y de integración (`SupplierServiceTest`, `SupplierControllerTest`).

### Fase 3: Frontend - Navegación y Componentes Reutilizables
- **`TASK-06`**: Actualizar `navigation.config.ts` incorporando el grupo `Compras` y la opción `Proveedores` (`/suppliers`).
- **`TASK-07`**: Implementar `ConfirmModalComponent` reutilizable en `src/app/shared/components/confirm-modal/` con tests.
- **`TASK-08`**: Implementar `AuditModalComponent` reutilizable en `src/app/shared/components/audit-modal/` con tests (omisión estricta de valores vacíos o nulos).
- **`TASK-09`**: Centralizar estilos reutilizables de tablas y formularios en `src/styles.css`.

### Fase 4: Frontend - Módulo de Proveedores y Rutas
- **`TASK-10`**: Crear `supplier.models.ts` y servicio Angular `SupplierService`.
- **`TASK-11`**: Implementar `SuppliersComponent` en `src/app/pages/suppliers/` (tabla A-Z, buscador en tiempo real, modal de creación/edición, manejo de error de unicidad, modal de confirmación para borrado y modal de auditoría).
- **`TASK-12`**: Configurar ruta `/suppliers` en `src/app/app.routes.ts` bajo `MainLayoutComponent` y `authGuard`.
- **`TASK-13`**: Crear pruebas unitarias para `suppliers.component.spec.ts` y `supplier.service.spec.ts`.

### Fase 5: Validación Full Stack
- **`TASK-14`**: Ejecutar pruebas en backend (`./gradlew test`) con 100% de éxito.
- **`TASK-15`**: Ejecutar pruebas en frontend (`npx ng test --watch=false`) con 100% de éxito.
- **`TASK-16`**: Ejecutar compilación de producción en frontend (`npm run build`).
