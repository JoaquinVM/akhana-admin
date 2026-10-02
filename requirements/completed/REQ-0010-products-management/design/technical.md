# Diseño Técnico de Arquitectura — REQ-0010: Gestión de Productos

## 1. Modelo de Datos y Migración SQL (V5)

### Migración: `database/migrations/V5__create_products_table.sql`
```sql
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    description VARCHAR(500),
    buy_price NUMERIC(12, 2) NOT NULL,
    sell_price NUMERIC(12, 2) NOT NULL,
    fixed_profit NUMERIC(12, 2) NOT NULL,
    percentage_profit NUMERIC(8, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Índices únicos parciales (permiten reusar código y nombre de productos ELIMINADO)
CREATE UNIQUE INDEX uq_products_code_active ON products (LOWER(code)) WHERE status != 'ELIMINADO';
CREATE UNIQUE INDEX uq_products_name_active ON products (LOWER(name)) WHERE status != 'ELIMINADO';

-- Índices de filtrado y relaciones
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_supplier ON products(supplier_id);
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_code ON products(code);

-- Tabla de unión Many-to-Many con Etiquetas
CREATE TABLE product_tags (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tag_id)
);

CREATE INDEX idx_product_tags_tag_id ON product_tags(tag_id);
```

---

## 2. Capa Backend (Spring Boot 3.5 & Java 21)

### Entidad `Product`
- Relación `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id") Category category`
- Relación `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id") Supplier supplier`
- Relación `@ManyToMany @JoinTable(name = "product_tags", ...)` con `Set<Tag> tags`
- Columnas numéricas `buyPrice`, `sellPrice`, `fixedProfit`, `percentageProfit`.
- Campos de auditoría estándar inmutables en creación.

### Reglas de Cálculo de Utilidad en Backend
```java
BigDecimal fixedProfit = sellPrice.subtract(buyPrice).setScale(2, RoundingMode.HALF_UP);
BigDecimal percentageProfit = (buyPrice.compareTo(BigDecimal.ZERO) > 0)
    ? fixedProfit.divide(buyPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
```

### Contrato de API REST (`/api/products`)

| Método | Endpoint | Descripción | Respuestas |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/products?search=&status=` | Lista filtrada de productos ordenada por `name ASC`. | 200 OK |
| `GET` | `/api/products/{id}` | Obtener producto por UUID con relaciones. | 200 OK, 404 Not Found |
| `POST` | `/api/products` | Crear nuevo producto (valida unicidad de código y nombre en backend). | 201 Created, 409 Conflict, 400 Bad Request |
| `PUT` | `/api/products/{id}` | Actualizar producto existente (auto-excluye registro actual en unicidad). | 200 OK, 409 Conflict, 404 Not Found |
| `PATCH`| `/api/products/{id}/status` | Conmutar estado entre `ACTIVO` e `INACTIVO`. | 200 OK, 400 Bad Request |
| `DELETE`| `/api/products/{id}` | Soft-delete lógico (`status = 'ELIMINADO'`). | 204 No Content, 404 Not Found |

---

## 3. Capa Frontend (Angular 21 Standalone & Signals)

### Modelos (`core/product/models/product.models.ts`)
```typescript
export type ProductStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

export interface ProductTagItem {
  id: string;
  name: string;
  color: string;
}

export interface Product {
  id: string;
  code: string;
  name: string;
  categoryId: string;
  categoryName: string;
  categoryColor: string;
  supplierId: string;
  supplierName: string;
  description?: string | null;
  tags: ProductTagItem[];
  buyPrice: number;
  sellPrice: number;
  fixedProfit: number;
  percentageProfit: number;
  status: ProductStatus;
  createdBy: string;
  createdAt: string;
  updatedBy?: string | null;
  updatedAt?: string | null;
  deletedBy?: string | null;
  deletedAt?: string | null;
}

export interface ProductRequest {
  code: string;
  name: string;
  categoryId: string;
  supplierId: string;
  description?: string | null;
  tagIds: string[];
  buyPrice: number;
  sellPrice: number;
  status?: ProductStatus;
}
```

### Componente `ProductsComponent` (`pages/products/`)
- Inyección de `ProductService`, `CategoryService`, `SupplierService`, `TagService`.
- Selectores personalizados para Categoría (muestra círculo cromático + nombre) y Etiquetas (selección múltiple interactiva con tags removibles cromáticos).
- Suscripción y cálculo reactivo en tiempo real de utilidades con Signal / Form Value Changes:
  - `fixedProfit = sellPrice - buyPrice`
  - `percentageProfit = buyPrice > 0 ? ((sellPrice - buyPrice) / buyPrice) * 100 : 0`
- Manejo de dirty-checking para edición e identificación visual de campos modificados (`isFieldModified`).
- Modales reutilizables: `ModalComponent`, `ConfirmModalComponent` (descarte, cambio de estado y eliminación), `AuditModalComponent`.
