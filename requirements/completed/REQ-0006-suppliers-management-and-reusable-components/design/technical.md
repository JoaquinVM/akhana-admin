# Diseño Técnico de Arquitectura - REQ-0006: Proveedores y Componentes Reutilizables

## 🏛️ 1. Arquitectura Full Stack

```text
backend/
├── model/
│   ├── Supplier.java                  (Entidad JPA con campos y auditoría)
│   └── SupplierStatus.java            (Enum: ACTIVO, INACTIVO, ELIMINADO)
├── repository/
│   └── SupplierRepository.java        (Spring Data JPA con consultas personalizadas)
├── dto/
│   ├── SupplierRequest.java           (Payload de creación y edición)
│   └── SupplierResponse.java          (Payload de salida con datos y auditoría)
├── service/
│   ├── SupplierService.java           (Interfaz de negocio)
│   └── impl/SupplierServiceImpl.java  (Validación de unicidad en backend, soft-delete, auditoría)
├── controller/
│   └── SupplierController.java        (Endpoints REST en /api/suppliers)
└── exception/
    ├── DuplicateResourceException.java(HTTP 409 Conflict)
    └── GlobalExceptionHandler.java    (Mapeo consistente de errores de negocio)

frontend/
├── core/
│   ├── navigation/navigation.config.ts(Grupo Compras -> Proveedores /suppliers)
│   └── supplier/
│       ├── models/supplier.models.ts  (Interfaces TypeScript y estados)
│       └── supplier.service.ts        (Cliente HTTP para /api/suppliers)
├── shared/
│   └── components/
│       ├── confirm-modal/             (Modal de confirmación reutilizable)
│       └── audit-modal/               (Modal de auditoría reutilizable)
├── pages/
│   └── suppliers/                     (Vista principal de Proveedores: tabla, búsqueda, modal form)
└── styles.css                         (Tokens y utilidades compartidas para tablas y formularios)
```

---

## 💾 2. Especificación de Base de Datos y Entidad

### Entidad `Supplier`:
```java
@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVO;

    // Auditoría
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

---

## 🔒 3. Validación de Unicidad en Backend

En `SupplierServiceImpl`:
- **Creación:**
  1. Verificar si existe otro proveedor con `status != ELIMINADO` y `LOWER(name) == LOWER(request.name)`. Si existe ➔ lanzar `DuplicateResourceException("Ya existe un proveedor con el nombre especificado.")`.
  2. Verificar si existe otro proveedor con `status != ELIMINADO` y `LOWER(code) == LOWER(request.code)`. Si existe ➔ lanzar `DuplicateResourceException("Ya existe un proveedor con el código especificado.")`.
- **Edición:**
  1. Las mismas verificaciones excluyendo el `id` del proveedor en edición (`id != supplierId`).
- **Eliminación Lógica:**
  1. Verificar que el proveedor exista y su estado no sea ya `ELIMINADO`.
  2. Modificar `status = SupplierStatus.ELIMINADO`.
  3. Establecer `deletedBy = currentUsername` y `deletedAt = Instant.now()`.

---

## 🌐 4. Contratos de API REST

| Método | Endpoint | Descripción | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/suppliers` | Listar proveedores no eliminados (búsqueda opcional con `?search=...`) | `200 OK` |
| `GET` | `/api/suppliers/{id}` | Obtener detalle y auditoría de un proveedor | `200 OK`, `404 Not Found` |
| `POST` | `/api/suppliers` | Crear un nuevo proveedor | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `PUT` | `/api/suppliers/{id}` | Actualizar datos de un proveedor | `200 OK`, `400 Bad Request`, `404 Not Found`, `409 Conflict` |
| `DELETE` | `/api/suppliers/{id}` | Eliminación lógica de un proveedor | `204 No Content`, `400 Bad Request`, `404 Not Found` |

---

## 🧩 5. Componentes Reutilizables en Frontend

### `ConfirmModalComponent` (`shared/components/confirm-modal/`):
- **Inputs:** `title: string`, `message: string`, `confirmText?: string`, `cancelText?: string`, `isDanger?: boolean`.
- **Outputs:** `confirmed = new EventEmitter<void>()`, `cancelled = new EventEmitter<void>()`.

### `AuditModalComponent` (`shared/components/audit-modal/`):
- **Inputs:** `title: string`, `auditData: AuditData | null`.
- **Outputs:** `closed = new EventEmitter<void>()`.
- **Lógica:** Evalúa `Object.entries(auditData)` filtrando todo valor nulo, vacío o indefinido.
