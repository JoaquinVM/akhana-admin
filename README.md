# Akhana Admin

Punto de venta, caja, compras y control de dinero de la tienda Akhana (Spring Boot + Angular + PostgreSQL).

## Requisitos

- JDK 21
- Node.js 22 y npm 10+
- Docker

## Puesta en marcha

Variables de entorno: los secretos no tienen valor por defecto. Para desarrollo, copiar el ejemplo y completar
`DB_PASSWORD`, `JWT_SECRET` (`openssl rand -hex 32`) y `ADMIN_PASSWORD` (primer admin; debe cambiarla al ingresar):

```bash
cp .env.example .env
```

Base de datos (PostgreSQL 17 en `localhost:5432`, base y usuario `akhana`, clave `DB_PASSWORD` del `.env`):

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
