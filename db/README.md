# Local Database

Database runtime assets are grouped under `backend/src/main/resources/db/`:

| Path | Runs | Purpose |
|---|---|---|
| `oracle-init/` | By the Oracle container on every start, as SYSDBA | Creates tablespace `SHOP_DATA`, user `SHOP` and grants if missing; keeps the `SHOP` password in sync with `.env` |
| `migration/` | By Flyway as user `SHOP` when the backend starts | Schema: tables, constraints, indexes, views (`V1__schema.sql` is the shared foundation; modules add `V<number>__<module>_<description>.sql`) |
| `dev-seed/` | By Flyway, only with Spring profile `dev` | Repeatable local fixtures: `R__00_catalog_seed.sql`, then `R__00_core_seed.sql` |
| `redis/init.lua` | Manually through `redis-cli --eval` | RediSearch indexes |

This `db/` directory contains the database design documentation (`DATABASE_DESIGN.md`).

## Versions

Images are pinned by digest in `compose.yaml` with `pull_policy: never`, so every member runs and benchmarks the same build. Use these values in the "Môi trường đo" table of the report.

| Component | Image | Version |
|---|---|---|
| Oracle | `container-registry.oracle.com/database/free@sha256:f988b0c04c4c386cd306a2a914c0d7a9702d83acc31b064a28ad8eb6278a8fba` | Oracle AI Database 26ai Free 23.26.3 (full image: includes `expdp`, `impdp`, `rman`, `sqlldr`, Oracle Text; 3.7 GB download, 10 GB on disk) |
| Redis | `redis/redis-stack-server@sha256:798ab84d9f266936b034ab11c4d04a2b8e4b441884c5aa7d17ac951eefdf742a` | Redis 7.4.7 with RediSearch and RedisJSON |

The Oracle *lite* image is not used: it ships without `expdp`, `impdp`, `rman` and `sqlldr`, which the backup topic needs.

## Setup

From the repository root:

```bash
docker pull container-registry.oracle.com/database/free@sha256:f988b0c04c4c386cd306a2a914c0d7a9702d83acc31b064a28ad8eb6278a8fba
docker pull redis/redis-stack-server@sha256:798ab84d9f266936b034ab11c4d04a2b8e4b441884c5aa7d17ac951eefdf742a
cp .env.example .env
```

Replace the placeholder passwords in `.env`. Passwords must start with a letter and contain only letters, digits and `_` (they are used unquoted in Oracle connect strings). Then start the databases:

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
```

`--wait` returns only when user `SHOP` can log in. The first start of a new Oracle volume takes about two minutes. If `--wait` keeps waiting much longer, check `docker compose logs oracle | grep shopflash`: an invalid `ORACLE_APP_PASSWORD` stops the script from creating `SHOP`, and the healthcheck only gives up after about 17 minutes.

Both ports are bound to `127.0.0.1`, so the databases are reachable only from your own machine.

To change the `SHOP` password, edit `ORACLE_APP_PASSWORD` in `.env` and run `docker compose up -d --wait`: the container is recreated and the startup script applies the new password. Variables exported in your shell override `.env` for both Compose and Spring, so do not export them.

Start the backend. Flyway applies the migrations as `SHOP`; profile `dev` also loads the fixtures:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway records applied versions and checksums in `SHOP.flyway_schema_history`. Later starts only apply new migrations. Redis initialization is idempotent and creates the approved indexes without creating business keys.

The backend no longer needs `ORACLE_ADMIN_PASSWORD`; only the Oracle container uses it.

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

If `init.lua` reports that `idx:order-analytics` has the old schema, drop only the index (documents are kept) and run it again:

```bash
docker compose exec -T redis redis-cli FT.DROPINDEX idx:order-analytics
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
```

## Seed data

`dev-seed/R__00_catalog_seed.sql` holds the shared catalog every module uses: categories and products with image URLs (direct links, no image files in the repo). It keeps the original fixture products (ID 1 and 101-106) and adds 22 categories (ID 201-222) and 184 products (ID 2001-2194) taken from DummyJSON. It runs before the core seed, which references catalog products 2101 and 2123 for the demo orders. All initial ProductInventory rows, including Product 1, are owned by the catalog seed and are inserted only when missing. After Flyway loads it, rebuild the Redis projection with `POST /api/admin/catalog/projection/rebuild` (admin).

`dev-seed/R__00_core_seed.sql` creates two local accounts and the customer cart only when missing. New carts are empty: add products from the storefront. It maintains exactly two demo orders: ID 1 uses AirPods Max (Product 2101, NORMAL/COMPLETED) at catalog price; ID 2 uses iPhone 13 Pro (2123, FLASH_SALE/CONFIRMED), with Flash Sale 1 ended and its sale price equal to 80% of catalog price. Order-item name/SKU/price are derived from the catalog and totals are checked against the lines. These two demo orders are refreshed when this repeatable changes; real customer order snapshots are not rewritten. Catalog stock represents current stock after the demo and is not decremented again on rerun.

`R__10_transaction_seed.sql` has been removed. It no longer fills the cart or increments its version at startup. Existing cart items are preserved; clear them through the application if desired. After pulling this change, run `cd backend && ./mvnw clean spring-boot:run -Dspring-boot.run.profiles=dev` once so Maven removes the old copied seed under `target/classes`. Keep Flyway history intact: `repeatable:missing` already permits retired repeatables. No schema migration or Flyway repair is needed.

Local mock users (local development only):

```text
admin@shopflash.local / ShopFlash123!
customer@shopflash.local / ShopFlash123!
```

## Migration convention

- Schema changes: `backend/src/main/resources/db/migration/V<number>__<module>_<description>.sql`. Take the next free number when you open your PR.
- Never edit a version that is on `main`; add the next version for corrections.
- App demo seeds run in order: `R__00_catalog_seed.sql`, then `R__00_core_seed.sql`. Put shared products/categories/inventory in catalog; accounts and demo orders in core. Do not auto-fill customer carts. Experiment fixtures remain isolated in their existing harnesses, not in application seed files.
- Large benchmark datasets are scripts under `report/<topic>/`, run by hand, never migrations.
- IDs below `10000` are reserved for fixtures; generated application IDs start at `10000`.
- Every foreign key column gets an index (check query in `DATABASE_DESIGN.md` §2.2).
- Oracle is the durable source of truth. Redis product and analytics JSON documents are rebuildable projections, while Flash Sale keys are runtime state owned by their modules.
