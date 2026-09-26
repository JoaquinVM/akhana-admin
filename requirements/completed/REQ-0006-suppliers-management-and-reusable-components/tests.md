# Estrategia de Pruebas Automatizadas - REQ-0006: Proveedores y Componentes Reutilizables

## 🧪 Enfoque de Pruebas
La estrategia cubre pruebas unitarias y de integración tanto en Backend (Spring Boot + JUnit 5 / Mockito / MockMvc) como en Frontend (Angular 21 + Vitest).

---

## 📋 Matriz de Pruebas Backend

### 1. `SupplierServiceTest` (`com.akhana.akhana_admin.service.SupplierServiceTest`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-SRV-01` | Creación exitosa de proveedor | Proveedor guardado con estado `ACTIVO`, `createdBy` y `createdAt` del usuario autenticado. |
| `TEST-SRV-02` | Rechazo por nombre duplicado | Lanza `DuplicateResourceException` si ya existe proveedor `ACTIVO` o `INACTIVO` con el mismo nombre (ignora mayúsculas). |
| `TEST-SRV-03` | Rechazo por código duplicado | Lanza `DuplicateResourceException` si ya existe proveedor `ACTIVO` o `INACTIVO` con el mismo código. |
| `TEST-SRV-04` | Reutilización de nombre/código de eliminado | Permite crear proveedor con nombre o código si los registros existentes están en estado `ELIMINADO`. |
| `TEST-SRV-05` | Listado ordenado por nombre | Retorna únicamente proveedores `ACTIVO` e `INACTIVO` ordenados alfabéticamente por nombre (A-Z). |
| `TEST-SRV-06` | Búsqueda por término | Filtra por coincidencia parcial en nombre o código ignorando mayúsculas y minúsculas. |
| `TEST-SRV-07` | Edición exitosa | Actualiza datos y auditoría (`updatedBy`, `updatedAt`), manteniendo intactos `createdBy` y `createdAt`. |
| `TEST-SRV-08` | Edición con validación de unicidad | Permite mantener el propio nombre/código del proveedor en edición, pero rechaza si colisiona con otro proveedor no eliminado. |
| `TEST-SRV-09` | Eliminación lógica | Cambia estado a `ELIMINADO`, registra `deletedBy` y `deletedAt`, no borra físicamente. |
| `TEST-SRV-10` | Eliminación no permitida en ya eliminado | Lanza excepción si se intenta eliminar un proveedor que ya está en estado `ELIMINADO`. |

### 2. `SupplierControllerTest` (`com.akhana.akhana_admin.controller.SupplierControllerTest`)
| ID | Endpoint y Método | Resultado Esperado |
|:---|:---|:---|
| `TEST-CTRL-01` | `GET /api/suppliers` | Retorna `200 OK` con listado de proveedores. |
| `TEST-CTRL-02` | `POST /api/suppliers` con datos válidos | Retorna `201 Created` con payload de respuesta. |
| `TEST-CTRL-03` | `POST /api/suppliers` con duplicado | Retorna `409 Conflict` y cuerpo con mensaje claro de negocio. |
| `TEST-CTRL-04` | `PUT /api/suppliers/{id}` con datos válidos | Retorna `200 OK` con proveedor actualizado. |
| `TEST-CTRL-05` | `DELETE /api/suppliers/{id}` | Retorna `204 No Content`. |

---

## 📋 Matriz de Pruebas Frontend

### 3. `ConfirmModalComponent` (`shared/components/confirm-modal/confirm-modal.component.spec.ts`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-MOD-01` | Renderizado de textos parametrizados | Muestra correctamente título, mensaje, confirmText y cancelText provistos vía inputs. |
| `TEST-MOD-02` | Emisión de evento confirmar | Emite `confirmed` al hacer clic en el botón de confirmación. |
| `TEST-MOD-03` | Emisión de evento cancelar | Emite `cancelled` al pulsar cancelar o hacer clic en backdrop. |

### 4. `AuditModalComponent` (`shared/components/audit-modal/audit-modal.component.spec.ts`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-AUD-01` | Renderizado selectivo | Muestra únicamente los campos de auditoría con valor no nulo y no vacío. |
| `TEST-AUD-02` | Omisión de campos nulos | No muestra filas para `deletedBy` o `deletedAt` si son nulos. |
| `TEST-AUD-03` | Cierre del modal | Emite evento `closed` al pulsar el botón de cerrar. |

### 5. `SupplierService` (`core/supplier/supplier.service.spec.ts`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-SUP-SVC-01` | Consulta de proveedores | Ejecuta `GET /api/suppliers` y propaga los resultados. |
| `TEST-SUP-SVC-02` | Creación de proveedor | Envía `POST /api/suppliers` con el payload de proveedor. |
| `TEST-SUP-SVC-03` | Actualización de proveedor | Envía `PUT /api/suppliers/{id}` con datos modificados. |
| `TEST-SUP-SVC-04` | Eliminación lógica | Envía `DELETE /api/suppliers/{id}`. |

### 6. `SuppliersComponent` (`pages/suppliers/suppliers.component.spec.ts`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-COMP-01` | Carga inicial de datos | Carga lista de proveedores ordenada A-Z y omite eliminados. |
| `TEST-COMP-02` | Búsqueda y filtrado | Filtra filas por coincidencia parcial en nombre o código case-insensitive. |
| `TEST-COMP-03` | Flujo de creación y error 409 | Envía formulario al servicio y muestra error de backend cuando recibe respuesta 409. |
| `TEST-COMP-04` | Restricción de acciones en ELIMINADO | Si existiese registro eliminado en vista administrativa o auditoría, no muestra botones de Editar o Eliminar. |
| `TEST-COMP-05` | Apertura de modal de confirmación | Al hacer clic en Eliminar, abre `ConfirmModalComponent` antes de llamar al endpoint. |
| `TEST-COMP-06` | Apertura de modal de auditoría | Al hacer clic en Auditoría, abre `AuditModalComponent` con los datos de trazabilidad. |

### 7. `NavbarComponent` (`layout/navbar/navbar.component.spec.ts`)
| ID | Descripción | Resultado Esperado |
|:---|:---|:---|
| `TEST-NAV-06` | Grupo Compras y opción Proveedores | Renderiza el grupo "Compras" y la subopción "Proveedores" con ruta `/suppliers`. |

---

## 🎯 Criterio de Éxito
- 100% de tests pasando en backend (`./gradlew test`).
- 100% de tests pasando en frontend (`npx ng test --watch=false`).
- Build de producción limpio (`npm run build`).
