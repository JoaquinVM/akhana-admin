# Akhana Admin

Punto de venta, caja, compras y control de dinero de la tienda Akhana (Spring Boot + Angular + PostgreSQL).

## Requisitos

- JDK 21
- Node.js 22 y npm 10+
- Docker

## Puesta en marcha

Base de datos (PostgreSQL 17 en `localhost:5432`, base `akhana`, usuario `akhana`, clave `akhana_dev`):

```bash
docker compose up -d postgres
```

Backend (API en `http://localhost:8080/api`):

```bash
cd backend && ./gradlew bootRun
```

Frontend (`http://localhost:4200`):

```bash
cd frontend && npm install && npm start
```

## Pruebas

```bash
cd backend && ./gradlew test
cd frontend && npm test -- --watch=false
```

## Documentacion

- `CLAUDE.md`: stack, comandos, estructura y reglas de trabajo.
- `specs/requirements.md`: requerimientos (fuente de verdad) y `specs/<modulo>.md` por modulo.
