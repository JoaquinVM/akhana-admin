# Akhana Admin

Sistema de punto de venta, caja, compras y control de dinero para la tienda Akhana (Bolivia, moneda Bs).
Objetivo actual: la V1, que reemplaza las planillas de Google Sheets (sin inventario; el stock llega en la V2).

## Documentacion valida (solo esta)

- `specs/requirements.md`: fuente de verdad del **que**. Estado, alcance, roles, requerimientos con ID (una o dos
  lineas cada uno), preguntas y hoja de ruta. Cuando se decida algo nuevo (respuesta a una pregunta, requerimiento
  nuevo o cambiado), actualizarlo en el mismo commit. Sin fechas, bitacora, endpoints, migraciones ni clases.
- `specs/<modulo>.md`: el **como** por modulo (escenarios cuando/entonces, notas de implementacion, validaciones con
  la planilla y tareas), con los mismos IDs. Ahi va el detalle de lo construido y probado.
- No existe otra documentacion ademas de `specs/requirements.md` y `specs/<modulo>.md`.
- No crear otros documentos (planes, informes, logs, user stories).

## Stack y comandos

- Backend: Spring Boot 4.1, Java 21, Gradle. Paquete `com.akhana.akhana_admin`. Puerto 8080, API en `/api`.
- Frontend: Angular 21 (standalone components + signals), pruebas con Vitest. Puerto 4200.
- Base: PostgreSQL 17 en Docker (`docker compose up -d postgres`).
- Secretos solo por entorno (`DB_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`/`ADMIN_PASSWORD` para el primer admin).
  En desarrollo: `cp .env.example .env` en la raíz y completar; lo leen docker compose, `bootRun` y las pruebas.
  Sin esas variables el backend no arranca. El primer admin debe cambiar su contraseña al ingresar.
- Node >= 20.19 o 22.12 (Angular CLI 21); con nvm: `nvm use 22`.

| Tarea | Comando |
| --- | --- |
| Preparar entorno (una vez) | `cp .env.example .env` y completar `JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD` |
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
  `shared/components/` (modal, confirm-modal, audit-modal, color-picker, sale-detail-modal, presentation-picker).
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
- POS (`pages/pos/`) y la venta (`pos/register-sale/`) ya estan divididos en `pos/components/`: el estado vive en el
  padre y los hijos usan `input()`/`output()`. Lo nuevo va en un componente hijo, sin volver a agrandar los HTML padre.
- Git: una rama por modulo (`feat/<modulo>`, `fix/<tema>`) creada desde `main`, commits convencionales
  (`feat(purchases): ...`). Nunca push directo a `main`. Al cerrar la sesion, con las pruebas locales pasando: push
  de la rama y PR a `main`; esperar CI en verde y preguntar al usuario si mergear ("Rebase and merge", historia
  lineal); no hacerlo sin su si. Si CI o las pruebas fallan, no se mergea y se reporta.
- Despliegue: un tag `vX.Y.Z` sobre `main` con CI en verde despliega en el VPS (NF-06, `deploy.md`); crearlo solo
  con el si del usuario.
- Avance: la tabla de la seccion 1 de `specs/requirements.md` es el tablero del proyecto. Al cerrar cada sesion,
  en el mismo commit: actualizar la fila del modulo (una linea: estado y que falta) y borrar de «Proximos pasos» lo
  terminado (queda en git). No agregar historia ni fechas al tablero.
- Cierre de sesion: ademas del resumen, dar el prompt listo para copiar de la siguiente sesion (proxima tarea
  pendiente segun la hoja de ruta de `specs/requirements.md`).
