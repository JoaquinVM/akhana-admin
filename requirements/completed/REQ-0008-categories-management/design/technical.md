# Diseño Técnico de Arquitectura y Contratos de API - REQ-0008

## 1. Arquitectura de Software

Siguiendo la arquitectura limpia en capas del proyecto:

```
[ Frontend: Angular 21 + Signals + Standalone Components ]
  │
  ├── CategoriesComponent (/categories)
  │    ├── ModalComponent (app-modal)
  │    ├── ConfirmModalComponent (app-confirm-modal)
  │    ├── AuditModalComponent (app-audit-modal)
  │    └── ColorPickerComponent (app-color-picker)
  └── CategoryService (HTTP Client)
       │
       ▼ REST API (/api/categories)
[ Backend: Spring Boot 3.4.3 + Java 21 + Spring Security ]
  │
  ├── CategoryController (REST Endpoints)
  ├── CategoryService & CategoryServiceImpl (Business Rules & Uniqueness)
  ├── CategoryRepository (Spring Data JPA)
  └── PostgreSQL Database (Table: categories, Partial Unique Index)
```

---

## 2. Esquema de Base de Datos (`database/migrations/V3__create_categories_table.sql`)

```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Regla Mandatoria: Unicidad de nombre exclusiva entre registros no eliminados
CREATE UNIQUE INDEX uq_categories_name_active ON categories (LOWER(name)) WHERE status != 'ELIMINADO';

CREATE INDEX idx_categories_status ON categories(status);
CREATE INDEX idx_categories_name ON categories(name);
```

---

## 3. Contratos de API REST

### `GET /api/categories?search={search}&status={status}`
- **Parámetros query opcionales:**
  - `search`: coincidencia parcial en `name` (case-insensitive).
  - `status`: `ACTIVO` o `ELIMINADO`. Si no se envía o es vacío, por defecto retorna `ACTIVO` (no eliminados).
- **Respuesta 200 OK:** `List<CategoryResponse>` ordenada por `name ASC`.

### `GET /api/categories/{id}`
- **Respuesta 200 OK:** `CategoryResponse`.
- **Respuesta 404 Not Found:** `ErrorResponse` si el ID no existe.

### `POST /api/categories`
- **Body:** `CategoryRequest` (`name`, `description`, `color`).
- **Respuesta 201 Created:** `CategoryResponse`.
- **Respuesta 409 Conflict:** Si ya existe una categoría activa con el mismo nombre.
- **Respuesta 400 Bad Request:** Errores de validación de formato/obligatoriedad.

### `PUT /api/categories/{id}`
- **Body:** `CategoryRequest` (`name`, `description`, `color`).
- **Respuesta 200 OK:** `CategoryResponse`.
- **Respuesta 409 Conflict:** Si existe otra categoría activa con el mismo nombre y distinto ID.
- **Respuesta 404 Not Found:** Si no existe la categoría.
- **Respuesta 400 Bad Request:** Si la categoría tiene estado `ELIMINADO` (no editable).

### `DELETE /api/categories/{id}`
- **Respuesta 204 No Content:** Eliminación lógica (`status = ELIMINADO`, `deletedBy`, `deletedAt`).
- **Respuesta 400 Bad Request:** Si ya está eliminada.
- **Respuesta 404 Not Found:** Si no existe.

---

## 4. Estructuras de Datos (DTOs)

### `CategoryRequest`
```java
public record CategoryRequest(
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String name,

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    String description,

    @NotBlank(message = "El color de la categoría es obligatorio")
    @Size(max = 50, message = "El código de color no puede superar los 50 caracteres")
    String color
) {}
```

### `CategoryResponse`
```java
public record CategoryResponse(
    UUID id,
    String name,
    String description,
    String color,
    CategoryStatus status,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {}
```

---

## 5. Diseño del Componente Reutilizable `ColorPickerComponent`

- **Ubicación:** `frontend/src/app/shared/components/color-picker/`
- **Inputs:**
  - `selectedColor = input<string>('')` (código hexadecimal o seleccionado).
  - `label = input<string>('Color Identificador')`
  - `required = input<boolean>(true)`
  - `disabled = input<boolean>(false)`
- **Outputs:**
  - `colorChange = output<string>()`
- **Integración con Reactive Forms:** Soporta `ControlValueAccessor` (o sincronización directa por signal/output), permitiendo su uso tanto como form control nativo como componente desacoplado.
- **Paleta Curada (18 tonos botánicos/zen):**
  - Verdes orgánicos: Bosque `#164312`, Olivo `#3c6a00`, Salvia `#5b7f52`, Menta `#2e5b27`.
  - Tonos cálidos/dorados: Oro Zen `#d97706`, Mostaza `#fabd0d`, Terracota `#b45309`, Canela `#9a3412`.
  - Tonos tierra/piedra: Arcilla `#854d0e`, Pizarra `#475569`, Grafito `#334155`.
  - Tonos florales/frutales: Amatista `#7c3aed`, Ciruela `#86198f`, Rubí `#b91c1c`, Ámbar `#ea580c`.
  - Tonos agua: Índigo `#3b82f6`, Océano `#0284c7`, Teal `#0f766e`.
- **Accesibilidad y feedback:** Cada color se presenta como un botón accesible con `role="radio"`, muestra visual de 28x28px con borde suave, radio checkmark o anillo blanco de foco al estar seleccionado.
