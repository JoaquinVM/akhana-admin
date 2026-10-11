# Contexto de Directorio: database/

## Propósito y Responsabilidad
Gestión centralizada del modelo relacional, scripts SQL de creación, migraciones incrementales numeradas y definiciones de esquemas para la base de datos PostgreSQL (`akhana`).

## Estructura Interna
- `migrations/`: Scripts SQL versionados (`V1__...sql` a `V8__create_financial_management_tables.sql`) para evolución incremental del esquema.
  - `V8`: Tablas `financial_accounts`, `financial_categories` y `financial_transactions`, restricciones únicas condicionales (única cuenta QR activa, única categoría de ventas por tipo), semillas iniciales (Caja protegida, Cuenta bancaria BISA como QR inicial, Cuenta Joaco, Categoría Ventas).
- `schemas/`: Vistas consolidadas del modelo relacional actual (DDL canónico).
- `seeds/`: Datos iniciales de prueba y catálogos maestros.

## Conexión y Variables de Entorno (desde docker-compose.yml)
- **Motor:** PostgreSQL 17
- **Contenedor:** `akhana-postgres`
- **Host / Puerto:** `localhost:5432`
- **Base de Datos:** `akhana`
- **Usuario:** `akhana`
- **Contraseña:** `akhana_dev`

## Convenciones
1. Todo nombre de tabla, columna, restricción e índice se define en **minúsculas con snake_case**.
2. Las claves primarias estándar son de tipo `UUID` o `BIGINT GENERATED ALWAYS AS IDENTITY`.
3. Toda tabla debe incluir auditoría básica: `created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP` y `updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP`.
4. Los cambios destructivos (DROP COLUMN, RENAME) deben gestionarse en fases de migración seguras.
