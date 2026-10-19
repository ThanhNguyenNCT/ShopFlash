# Backup and recovery demo

This is the single runbook for the ShopFlash backup and recovery demo. It supports the automated PowerShell workflow and the manual Bash/Docker Compose workflow for Linux, macOS Bash, WSL, and Git Bash.

The application creates a whole-schema Oracle Data Pump export and an RDB snapshot of the complete ShopFlash Redis keyspace. Recovery is deliberately run only after the application is stopped; no HTTP endpoint overwrites live data.

## Safety and prerequisites

1. Create a completed backup in F11 and keep its `backupId`.
2. Start Oracle and Redis with `docker compose up -d --wait`.
3. Stop the ShopFlash backend and frontend before simulating loss or restoring data.
4. Run only against the local demo database. The loss step deletes all business rows in `SHOP` and every key in the ShopFlash Redis database.
5. Do not commit `.dmp`, `.rdb`, AOF files, passwords, or files generated under `results/`.

The binary artifacts are under `backups/<backup-id>/`; SHA-256 is verified before PowerShell recovery begins.

## Result layout

The PowerShell workflow keeps a run compact:

```text
results/run-<id>/
├── state.json   # Baseline, checkpoints, hashes and timing; overwritten as each stage completes.
└── summary.md   # Created only after every recovery stage succeeds.
```

The Bash workflow exposes only `results/bash-run-<id>/summary.md`. Its raw checkpoints and Data Pump output remain in `results/bash-run-<id>/.work/` so the commands can run `diff` without cluttering the visible result folder.

The technical restore durations exclude time spent reading instructions, choosing commands, and waiting between actions. They are not RTO.

## Option A: automated PowerShell workflow

Run from `report/backup-recovery/scripts`. Set the completed backup ID and a new run label once, then reuse them in every command.

```powershell
$id = 'BKP-20261018T175354-318009F8'
$runId = '03'

# 0. Record backup creation time from manifest.json.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action RecordBackupTiming -RunId $runId

# 1. Capture and validate the pre-loss baseline.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action CaptureBaseline -RunId $runId

# 2. Stop backend/frontend first, then simulate data loss.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action SimulateDataLoss -RunId $runId -ConfirmDataLoss

# 3. Restore the complete Oracle schema data and verify it.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action RestoreOracle -RunId $runId -ConfirmOracleRestore

# 4. Restore Redis live data and verify it.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action RestoreRedisLive -RunId $runId -ConfirmRedisLiveRestore

# 5. Write the concise report summary.
.\Invoke-BackupRecoveryDemo.ps1 -BackupId $id -Action Summarize -RunId $runId
```

`RecordBackupTiming` calculates backup duration from backend `createdAt` to `completedAt`. `RestoreOracle` and `RestoreRedisLive` use a stopwatch from the start of the controlled restore to a successful checkpoint comparison. `summary.md` includes the three measurements.

The PowerShell Redis-live action is designed for the current application configuration: AOF enabled. It restores the RDB in an isolated helper with AOF disabled, rebuilds a valid AOF from the loaded keys, and only then starts the Compose Redis service.

## Option B: manual Bash workflow

The commands below run from a host Bash shell, not from an Oracle/Redis container shell. No local Oracle installation is needed.

### Git Bash notes

Git Bash/MSYS converts a standalone container path such as `/tmp/script.lua` into a Windows path before calling `docker.exe`. Every container command below passes those paths within a quoted `sh -lc '...'` command. Do not place a container path directly after `redis-cli --eval` from Git Bash.

`REDIS_DATA_DIR=/data` is an environment variable consumed before the Redis entrypoint starts; quoting it alone does not stop MSYS conversion. The AOF helper therefore uses `MSYS_NO_PATHCONV=1` for that one `docker run` command.

### 1. Set up a run and verify artifacts

Run these commands from the repository root. Select a new `RUN_ID` for every complete measurement.

```bash
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

BACKUP_ID="BKP-20261018T175354-318009F8"
RUN_ID="02"
RESULTS="report/backup-recovery/results/bash-run-${RUN_ID}"
WORK="${RESULTS}/.work"
SUMMARY_SCRIPT="report/backup-recovery/scripts/Write-BashRecoverySummary.sh"
ORACLE_DIR="report/backup-recovery/oracle"
REDIS_DIR="report/backup-recovery/redis"
BACKUP_DIR="backups/${BACKUP_ID}"
DUMP_NAME="shop-${BACKUP_ID}.dmp"

case "$(uname -s)" in
  MINGW*|MSYS*) BACKUP_HOST_DIR="$(pwd -W)/${BACKUP_DIR}/redis" ;;
  *) BACKUP_HOST_DIR="$(pwd)/${BACKUP_DIR}/redis" ;;
esac

test -f "${BACKUP_DIR}/manifest.json"
test -f "${BACKUP_DIR}/oracle/${DUMP_NAME}"
test -s "${BACKUP_DIR}/redis/dump.rdb"
test -s "${BACKUP_HOST_DIR}/dump.rdb"
mkdir -p "$WORK"

docker compose up -d --wait
```

Optionally record hashes for the report:

```bash
sha256sum "${BACKUP_DIR}/oracle/${DUMP_NAME}" "${BACKUP_DIR}/redis/dump.rdb" \
  | tee "${WORK}/artifact-sha256.txt"
```

Copy the fixed checkpoint and loss scripts to their containers:

```bash
docker compose exec -T oracle sh -lc 'mkdir -p /tmp/shopflash-backup-recovery'
for file in collect-checkpoint.sql simulate-data-loss.sql disable-foreign-keys.sql enable-foreign-keys.sql; do
  docker compose cp "${ORACLE_DIR}/${file}" "oracle:/tmp/shopflash-backup-recovery/${file}"
done

docker compose exec -T redis sh -lc 'mkdir -p /tmp/shopflash-backup-recovery'
for file in collect-checkpoint.lua simulate-data-loss.lua; do
  docker compose cp "${REDIS_DIR}/${file}" "redis:/tmp/shopflash-backup-recovery/${file}"
done
```

### 2. Capture the baseline

This is read-only. Redis records only total keys and `product:*` document count; it does not write Redis key names or values.

```bash
docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/collect-checkpoint.sql' \
  | tee "${WORK}/before-loss.oracle.txt"

docker compose exec -T redis sh -lc \
  'redis-cli --raw --eval /tmp/shopflash-backup-recovery/collect-checkpoint.lua' \
  | tee "${WORK}/before-loss.redis.txt"
```

For the sample dataset, confirm `CATEGORIES = 100`, `PRODUCTS = 1000`, `PRODUCT_INVENTORY = 1000`, `DBSIZE = 1000`, and `PRODUCT_DOCUMENTS = 1000` before continuing.

### 3. Simulate loss

Stop the ShopFlash backend/frontend first. Keep Oracle and Redis running.

```bash
read -r -p 'Type DELETE_DEMO_DATA to continue: ' confirmation
test "$confirmation" = 'DELETE_DEMO_DATA'

docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/simulate-data-loss.sql'

docker compose exec -T redis sh -lc \
  'redis-cli --raw --eval /tmp/shopflash-backup-recovery/simulate-data-loss.lua'

for attempt in $(seq 1 30); do
  key_count="$(docker compose exec -T redis redis-cli --raw DBSIZE | tr -d '\r')"
  test "$key_count" = '0' && break
  sleep 1
done
test "$key_count" = '0'

docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/collect-checkpoint.sql' \
  | tee "${WORK}/after-loss.oracle.txt"

docker compose exec -T redis sh -lc \
  'redis-cli --raw --eval /tmp/shopflash-backup-recovery/collect-checkpoint.lua' \
  | tee "${WORK}/after-loss.redis.txt"
```

Both sources must now be empty.

### 4. Restore Oracle

This imports data only: existing DDL and Flyway history are retained. Foreign keys are disabled during import because Data Pump can load child rows before parent rows; the script re-enables them with `VALIDATE`.

```bash
ORACLE_RESTORE_STARTED_SECONDS=$SECONDS

DATA_PUMP_DIR="$(
  docker compose exec -T oracle bash -lc '
    sqlplus -s -L /nolog <<SQL
connect shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1
whenever sqlerror exit failure
set heading off feedback off pages 0 verify off
select directory_path from all_directories where directory_name = '\''DATA_PUMP_DIR'\'';
exit success
SQL
  ' | tr -d '\r' | awk '/^\// { print; exit }'
)"
test -n "$DATA_PUMP_DIR"

docker compose cp "${BACKUP_DIR}/oracle/${DUMP_NAME}" "oracle:${DATA_PUMP_DIR}/${DUMP_NAME}"
docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/disable-foreign-keys.sql'

set +e
docker compose exec -T oracle bash -lc \
  "impdp \"shop/\${SHOP_PASSWORD}@//localhost:1521/FREEPDB1\" DIRECTORY=DATA_PUMP_DIR DUMPFILE=${DUMP_NAME} LOGFILE=shop-${BACKUP_ID}-import.log CONTENT=DATA_ONLY TABLE_EXISTS_ACTION=TRUNCATE METRICS=YES" \
  | tee "${WORK}/oracle-import.txt"
import_status=${PIPESTATUS[0]}
set -e

docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/enable-foreign-keys.sql'
test "$import_status" -eq 0

docker compose exec -T oracle bash -lc \
  'sqlplus -s -L "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" @/tmp/shopflash-backup-recovery/collect-checkpoint.sql' \
  | tee "${WORK}/after-oracle-recovery.oracle.txt"

diff -u "${WORK}/before-loss.oracle.txt" "${WORK}/after-oracle-recovery.oracle.txt"

ORACLE_RESTORE_ELAPSED_SECONDS=$((SECONDS - ORACLE_RESTORE_STARTED_SECONDS))
printf 'ORACLE_TECHNICAL_RESTORE_SECONDS=%s\n' "$ORACLE_RESTORE_ELAPSED_SECONDS" \
  | tee "${WORK}/oracle-restore-duration.txt"
```

## 5. Restore Redis live

Choose **one** of the following variants. The normal application uses AOF enabled with Redis's default `appendfsync everysec`.

### A. AOF enabled: current application configuration

Redis prioritizes its AOF over a standalone `dump.rdb`. The helper below loads the backup RDB with AOF disabled, enables AOF to rebuild it from loaded keys, then the normal Compose service starts with its normal configuration.

```bash
test "$(docker compose exec -T redis redis-cli --raw DBSIZE | tr -d '\r')" = '0'

REDIS_CONTAINER="$(docker compose ps -aq redis | head -n 1)"
REDIS_VOLUME="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' "$REDIS_CONTAINER")"
REDIS_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$REDIS_CONTAINER")"
ARCHIVE_NAME="recovery-snapshots/bash-run-${RUN_ID}-${BACKUP_ID}-before-live-restore-$(date +%Y%m%dT%H%M%S)"
test -n "$REDIS_VOLUME"
test -n "$REDIS_IMAGE"

REDIS_RESTORE_STARTED_SECONDS=$SECONDS
docker compose stop redis

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint sh \
  -e ARCHIVE_NAME="$ARCHIVE_NAME" \
  -v "${REDIS_VOLUME}:/data" \
  -v "${BACKUP_HOST_DIR}:/backup:ro" \
  "$REDIS_IMAGE" -lc '
    set -eu
    archive="/data/${ARCHIVE_NAME}"
    mkdir -p "$archive"
    if [ -d /data/appendonlydir ]; then cp -a /data/appendonlydir "$archive/appendonlydir"; fi
    if [ -f /data/dump.rdb ]; then cp -a /data/dump.rdb "$archive/dump.rdb"; fi
    rm -rf /data/appendonlydir
    rm -f /data/dump.rdb
    cp /backup/dump.rdb /data/dump.rdb
    test -s /data/dump.rdb
  '

AOF_BUILDER="shopflash-aof-rebuild-${RUN_ID}-$$"
MSYS_NO_PATHCONV=1 docker run -d --rm --name "$AOF_BUILDER" --network none \
  -v "${REDIS_VOLUME}:/data" \
  -e REDIS_DATA_DIR=/data \
  -e REDIS_ARGS='--appendonly no' \
  -e REDISCLI_AUTH= \
  "$REDIS_IMAGE"

for attempt in $(seq 1 30); do
  if docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw PING' 2>/dev/null | grep -qx PONG; then break; fi
  sleep 1
done
docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw PING' | grep -qx PONG
test "$(docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw DBSIZE' | tr -d '\r')" -gt 0
docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw CONFIG SET appendonly yes' | grep -qx OK

for attempt in $(seq 1 60); do
  if docker exec "$AOF_BUILDER" sh -lc 'test -s /data/appendonlydir/appendonly.aof.manifest && find /data/appendonlydir -type f -name "appendonly.aof.*.base.rdb" -size +0c -print -quit | grep -q .' \
    && docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw INFO persistence' | grep -qx 'aof_rewrite_in_progress:0'; then break; fi
  sleep 1
done
docker exec "$AOF_BUILDER" sh -lc 'test -s /data/appendonlydir/appendonly.aof.manifest && find /data/appendonlydir -type f -name "appendonly.aof.*.base.rdb" -size +0c -print -quit | grep -q .'
docker exec "$AOF_BUILDER" sh -lc 'unset REDISCLI_AUTH; redis-cli --raw INFO persistence' | grep -qx 'aof_rewrite_in_progress:0'
docker stop "$AOF_BUILDER"

docker compose up -d --wait redis
```

### B. AOF disabled: benchmark alternative

Before beginning a no-AOF run, set the Redis service in `compose.yaml` to:

```yaml
REDIS_ARGS: "--requirepass ${REDIS_PASSWORD:?Set REDIS_PASSWORD in .env} --appendonly no"
```

Then recreate Redis before capturing the baseline:

```bash
docker compose up -d --force-recreate redis
```

Use a separate benchmark volume or a fresh local environment. A Redis instance changed from AOF to no-AOF ignores AOF data and relies on an RDB. After the loss step, use the following instead of variant A; it copies the RDB and starts Redis directly, with no AOF helper:

```bash
test "$(docker compose exec -T redis redis-cli --raw DBSIZE | tr -d '\r')" = '0'

REDIS_CONTAINER="$(docker compose ps -aq redis | head -n 1)"
REDIS_VOLUME="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' "$REDIS_CONTAINER")"
REDIS_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$REDIS_CONTAINER")"
ARCHIVE_NAME="recovery-snapshots/bash-run-${RUN_ID}-${BACKUP_ID}-no-aof-$(date +%Y%m%dT%H%M%S)"
test -n "$REDIS_VOLUME"
test -n "$REDIS_IMAGE"

REDIS_RESTORE_STARTED_SECONDS=$SECONDS
docker compose stop redis

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint sh \
  -e ARCHIVE_NAME="$ARCHIVE_NAME" \
  -v "${REDIS_VOLUME}:/data" \
  -v "${BACKUP_HOST_DIR}:/backup:ro" \
  "$REDIS_IMAGE" -lc '
    set -eu
    archive="/data/${ARCHIVE_NAME}"
    mkdir -p "$archive"
    if [ -d /data/appendonlydir ]; then cp -a /data/appendonlydir "$archive/appendonlydir"; fi
    if [ -f /data/dump.rdb ]; then cp -a /data/dump.rdb "$archive/dump.rdb"; fi
    rm -rf /data/appendonlydir
    rm -f /data/dump.rdb
    cp /backup/dump.rdb /data/dump.rdb
    test -s /data/dump.rdb
  '

docker compose up -d --wait redis
```

### 6. Verify Redis and write the Bash summary

Run this after either Redis variant above:

```bash
docker compose exec -T redis sh -lc 'mkdir -p /tmp/shopflash-backup-recovery'
docker compose cp "${REDIS_DIR}/collect-checkpoint.lua" "redis:/tmp/shopflash-backup-recovery/collect-checkpoint.lua"

docker compose exec -T redis sh -lc \
  'redis-cli --raw --eval /tmp/shopflash-backup-recovery/collect-checkpoint.lua' \
  | tee "${WORK}/after-redis-live-recovery.redis.txt"

diff -u "${WORK}/before-loss.redis.txt" "${WORK}/after-redis-live-recovery.redis.txt"

REDIS_RESTORE_ELAPSED_SECONDS=$((SECONDS - REDIS_RESTORE_STARTED_SECONDS))
printf 'REDIS_TECHNICAL_RESTORE_SECONDS=%s\n' "$REDIS_RESTORE_ELAPSED_SECONDS" \
  | tee "${WORK}/redis-restore-duration.txt"

bash "$SUMMARY_SCRIPT" "$RESULTS" "$BACKUP_ID" "$RUN_ID"
```

An empty `diff` and a generated `summary.md` confirm the live Redis checkpoint matches the baseline. Only then restart the backend and frontend.

## Redis AOF persistence benchmark

The recovery workflow above measures restore time. This separate runner measures Redis `SET` and `GET` performance with AOF disabled, AOF `everysec`, and AOF `always`.

It does **not** change `compose.yaml`, the ShopFlash Redis container, or the `redis_data` volume. Each sample creates a fresh temporary Redis container and Docker volume, then removes both when that sample finishes. Stop the frontend and backend and avoid other disk-heavy Docker work while measuring so the host has less background load.

The default workload is five repetitions per mode, with 100,000 `SET` requests then 100,000 `GET` requests, 50 concurrent clients, pipeline depth 1, 256-byte values, and a random keyspace of 10,000 keys. Pipeline depth 1 is intentional: it makes the cost of `appendfsync always` visible instead of hiding it in batches.

Run this from the repository root on PowerShell, Bash, Linux, macOS, WSL, or Git Bash:

```text
python report/backup-recovery/scripts/measure_redis_persistence.py
```

The runner prints each sample as it finishes and writes only two files to a new timestamped directory under `report/backup-recovery/results/`:

```text
redis-persistence-<timestamp>/
├── redis-persistence.csv  # One SET/GET row for each of the 15 samples
└── summary.md             # Workload definition and mean values for the report
```

Optional parameters let the workload be recorded explicitly or rerun partially:

```text
# One quick trial for each mode.
python report/backup-recovery/scripts/measure_redis_persistence.py --runs 1

# Only rerun AOF always with the default workload.
python report/backup-recovery/scripts/measure_redis_persistence.py --modes always

# Explicit alternative workload.
python report/backup-recovery/scripts/measure_redis_persistence.py --runs 5 --requests 100000 --clients 50 --pipeline 1 --data-size 256 --keyspace 10000
```

Copy the five rows per mode from `redis-persistence.csv` into section 5 of `REPORT_DATA.md`; copy the means from `summary.md` into the comparison table. These results are local performance measurements, not general Redis performance claims.
