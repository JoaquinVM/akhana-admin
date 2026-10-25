# Akhana Admin

Sistema de punto de venta, caja, compras y control de dinero para la tienda Akhana (Bolivia, moneda Bs).
Objetivo actual: la V1, que reemplaza las planillas de Google Sheets (sin inventario; el stock llega en la V2).

## Documentacion valida (solo esta)

- `specs/requirements.md`: fuente de verdad. Alcance por version, roles, requerimientos con ID, preguntas y hoja de ruta.
  Cuando se decida o descubra algo nuevo (respuesta a una pregunta, hallazgo tecnico, requerimiento nuevo),
  actualizar este archivo en el mismo commit.
- `specs/<modulo>.md`: detalle por modulo (escenarios cuando/entonces + tareas), con los mismos IDs.
- No existe otra documentacion ademas de `specs/requirements.md` y `specs/<modulo>.md`.
- No crear otros documentos (planes, informes, logs, user stories).

## Stack y comandos

- Backend: Spring Boot 4.1, Java 21, Gradle. Paquete `com.akhana.akhana_admin`. Puerto 8080, API en `/api`.
- Frontend: Angular 21 (standalone components + signals), pruebas con Vitest. Puerto 4200.
- Base: PostgreSQL 17 en Docker (`docker compose up -d postgres`).

| Tarea | Comando |
| --- | --- |
| Levantar backend | `cd backend && ./gradlew bootRun` |
| Pruebas de lo que cambiaste | `cd backend && ./gradlew test --tests "*NombreClase*"` |
| Suite backend completa (solo al cerrar un modulo) | `cd backend && ./gradlew test` |
| Levantar frontend | `cd frontend && npm start` |
| Pruebas frontend | `cd frontend && npm test -- --watch=false` |
| Build frontend | `cd frontend && npm run build` |

Log de SQL apagado por defecto; para verlo: `SHOW_SQL=true ./gradlew bootRun`.

## Estructura

- Backend: `controller/`, `service/` + `service/impl/`, `repository/`, `model/`, `dto/`, `config/`, `exception/`.
- Frontend: `core/<dominio>/` (models + service), `pages/<pantalla>/`, `layout/` (navbar),
  `shared/components/` (modal, confirm-modal, audit-modal, color-picker, sale-detail-modal).
- Migraciones SQL (Flyway): `backend/src/main/resources/db/migration/V<n>__descripcion.sql`. Hibernate solo valida
  (`ddl-auto=validate`): todo cambio de esquema es una migración nueva, nunca se edita una ya aplicada.

## Patrones de referencia: copiar en vez de explorar

- CRUD backend: `SupplierController`, `SupplierServiceImpl`, `SupplierRepository`, `SupplierRequest/Response`.
- CRUD frontend: `pages/suppliers/` + `core/supplier/`.
- Logica de dinero: `CashSessionServiceImpl`, `SaleServiceImpl`, `FinancialTransactionServiceImpl`.
- Permisos: `navigation.config.ts` (claves) + `permission.guard.ts` + `@PreAuthorize` en controllers.

## Reglas de negocio que no se rompen

- Dinero con `BigDecimal`, escala 2, `RoundingMode.HALF_UP`; toda operacion de dinero en `@Transactional`.
- Nada se borra fisicamente: baja logica o anulacion con motivo. Auditoria (quien/cuando) la genera el servidor.
- Validaciones de negocio en el backend; el frontend solo muestra el error.
- Codigo, identificadores y commits en ingles; textos de la interfaz y conversacion en espanol.

## Como trabajar (ahorro de tokens)

- Leer solo los archivos que se indiquen o los del patron de referencia. No recorrer todo el repo.
- Una tarea o modulo por sesion. Al terminar: respuesta breve con que cambio, que falta y como probarlo.
- Correr solo las pruebas de lo que se toco; la suite completa al cerrar el modulo.
- `pos.component.html` y `transactions.component.html` son muy grandes: si hay que cambiarlos, proponer
  dividirlos en componentes antes.
- Git: una rama por modulo (`feat/<modulo>`), commits convencionales (`feat(purchases): ...`). No hacer push
  ni merge sin pedirlo.
