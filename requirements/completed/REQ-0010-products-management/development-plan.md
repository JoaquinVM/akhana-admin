# Plan de Desarrollo — REQ-0010: Gestión de Productos

## Fase 1: Base de Datos y Backend

- [x] **TASK-01: Migración V5 de Base de Datos**
  - Crear `database/migrations/V5__create_products_table.sql`.
  - Aplicar script en base de datos PostgreSQL (`akhana-postgres`).
  - Actualizar `database/schemas/schema.sql`.

- [x] **TASK-02: Modelo y Repositorio JPA**
  - Crear enum `ProductStatus` (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
  - Crear entidad `Product` con relaciones `@ManyToOne` (Category, Supplier), `@ManyToMany` (Tag) y auditoría inmutable.
  - Crear `ProductRepository` con consultas de unicidad (`existsByCode...`, `existsByName...`) y búsqueda combinada por código/nombre y estado.

- [x] **TASK-03: DTOs, Servicio y Controlador REST**
  - Crear `ProductRequest` y `ProductResponse`.
  - Crear `ProductService` e implementación `ProductServiceImpl` con validaciones de unicidad backend, cálculo automático de utilidades (fija y porcentual), conmutación de estado y soft-delete.
  - Crear `ProductController` (`/api/products`) con endpoints REST.

- [x] **TASK-04: Pruebas Backend**
  - Crear `ProductServiceTest` y `ProductControllerTest`.
  - Ejecutar `./gradlew test` (validar 100% de éxito).

---

## Fase 2: Frontend y Componentes Reutilizables

- [x] **TASK-05: Capa Core de Productos**
  - Crear `src/app/core/product/product.service.ts` y modelos `product.models.ts`.
  - Crear pruebas unitarias `product.service.spec.ts`.

- [x] **TASK-06: Vista `ProductsComponent`**
  - Crear `src/app/pages/products/products.component.ts`, `.html`, `.css`.
  - Implementar tabla con orden inicial A-Z, buscador en tiempo real por código/nombre y filtro segmentado (Activos/Inactivos, Activos, Inactivos, Eliminados).
  - Implementar selector cromático de Categoría y selector múltiple de Etiquetas con pastillas removibles.
  - Implementar cálculo reactivo en tiempo real de utilidades fija y porcentual (solo lectura).
  - Implementar dirty-checking, validación reactiva, indicadores `.field-modified-badge` y modales reutilizables de formulario, descarte, cambio de estado, eliminación y auditoría.
  - Conectar ruta `/products` en `app.routes.ts`.

- [x] **TASK-07: Pruebas Frontend y Verificación Final**
  - Crear `products.component.spec.ts` cubriendo criterios de aceptación.
  - Ejecutar `npm test -- --watch=false` (validar 100% de éxito) y `npm run build` (0 errores).

---

## Fase 3: Documentación y Entrega

- [x] **TASK-08: Registro y Entrega HITL 3**
  - Actualizar `_context.md` de backend y frontend.
  - Registrar implementación en `implementation.md` y `conversation.md`.
  - Presentar resultados ejecutivos al desarrollador.
