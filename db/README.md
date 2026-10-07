# Local Database

Database runtime assets are grouped under `backend/src/main/resources/db/`: Flyway migrations in `migration/` and the Redis initializer in `redis/`. This `db/` directory contains the database design documentation.

ShopFlash uses exactly these local images:

```text
container-registry.oracle.com/database/free:latest-lite
redis/redis-stack-server:latest
```

They are fixed in `compose.yaml` with `pull_policy: never`. Image names are not configured through `.env`.

## Setup

From the repository root:

```bash
cp .env.example .env
```

Replace the placeholder passwords in `.env`, then start the databases:

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
```

Start the backend to let Flyway create the `SHOP` schema and apply migrations and seed data:

```bash
cd backend
./mvnw spring-boot:run
```

Flyway records applied versions and checksums in its schema history table. Later starts only apply new migrations. Redis initialization is idempotent and creates the approved indexes without creating business keys.

## Normal operation

```bash
docker compose ps
docker compose logs
docker compose stop
docker compose up -d --wait
```

Stopping or recreating containers preserves the named Oracle and Redis volumes.

## Reset

This deletes only the local volumes of the ShopFlash Compose project:

```bash
docker compose down --volumes
```

Afterward, repeat the commands under **Setup**.

## Seed data

The Oracle-only seed uses stable IDs below `10000` and covers all eleven V1 tables with a minimal valid relationship set. It does not add fields or datasets for individual course topics.

Local mock users:

```text
admin@shopflash.local / ShopFlash123!
customer@shopflash.local / ShopFlash123!
```

Only bcrypt hashes are stored in the V1 initialization migration; these credentials are for local development only.

## Migration convention

- Put Oracle migrations in `backend/src/main/resources/db/migration/` as `V<number>__description.sql`.
- Never edit an applied version; add the next version for corrections.
- IDs below `10000` are reserved for core fixtures; generated application IDs start at `10000`.
- Oracle is the durable source of truth. Redis product and analytics JSON documents are rebuildable projections, while Flash Sale keys are runtime state owned by their modules.
