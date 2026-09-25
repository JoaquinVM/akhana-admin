# Contexto de Directorio: database/

## Propósito y Responsabilidad
Gestión centralizada del modelo relacional, scripts SQL de creación, migraciones incrementales numeradas y definiciones de esquemas para la base de datos PostgreSQL (`akhana`).

## Estructura Interna
- `migrations/`: Scripts SQL versionados (`V1__...sql`, `V2__...sql`) para evolución incremental del esquema.
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
