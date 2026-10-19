# Backup and Recovery

## 1. Objective

This section evaluates a complete local backup and recovery workflow for ShopFlash. The goal is to demonstrate that the application can preserve and restore both durable relational data in Oracle and the complete Redis keyspace used by ShopFlash.

The implementation does not treat the catalog as the entire system. Oracle backup covers the whole `SHOP` schema, while Redis backup covers every key in the configured ShopFlash Redis database. Category, Product, Inventory, and `product:*` documents are used as compact checkpoints because they are easy to count and verify.

## 2. Backup Design

| Source | Role in ShopFlash | Backup artifact | Recovery approach |
|---|---|---|---|
| Oracle | Authoritative business data, relationships, and constraints | Data Pump schema export (`SHOP`) | Controlled Data Pump import with existing schema retained |
| Redis | Product projections and other Redis runtime state | RDB snapshot of the complete keyspace | Restore the RDB; for the normal AOF-enabled configuration, rebuild AOF before starting the live Redis service |

Each backup is stored outside Docker data volumes in `backups/<backup-id>/` and contains:

```text
backups/<backup-id>/
├── oracle/
│   └── shop-<backup-id>.dmp
├── redis/
│   └── dump.rdb
└── manifest.json
```

The manifest records the backup ID, start and completion timestamps, artifact sizes, SHA-256 hashes, and a baseline checkpoint. The backend creates backups through a fixed runner; the browser cannot provide arbitrary shell commands or paths. The web UI creates and lists backups, but destructive recovery is intentionally performed only through the controlled scripts after the application has stopped.

## 3. Experimental Method

The dataset used in this experiment contains 100 Categories, 1,000 Products, 1,000 ProductInventory rows, and 1,000 Redis `product:*` documents. The five backup trials were created without changing this dataset between trials.

For each recovery trial, the following sequence was used with the same completed backup artifact:

1. Capture a baseline checkpoint.
2. Delete the Oracle business rows and all Redis keys through controlled scripts.
3. Restore Oracle from the Data Pump dump and compare it with the baseline.
4. Restore Redis from the RDB, rebuild AOF when required, and compare it with the baseline.
5. Write a compact per-run `state.json` and `summary.md`.

The backend and frontend remain stopped during simulated data loss and recovery. Reported recovery times are controlled technical durations measured by the scripts. They exclude the time spent reading instructions or choosing commands, so they are not an operational RTO.

## 4. Backup Creation Results

Backup duration is `completedAt - createdAt` from each manifest. It includes the fixed backend runner exporting Oracle, taking a Redis RDB snapshot, copying artifacts, and calculating SHA-256.

| Run | Oracle Data Pump | Redis RDB | Duration (s) |
|---:|---:|---:|---:|
| 1 | 1,355,776 B | 190,018 B | 63.026 |
| 2 | 1,355,776 B | 190,018 B | 67.478 |
| 3 | 1,355,776 B | 190,018 B | 60.923 |
| 4 | 1,355,776 B | 190,018 B | 60.679 |
| 5 | 1,355,776 B | 190,018 B | 62.402 |
| **Mean** | **1,355,776 B** | **190,018 B** | **62.902** |
| **Sample standard deviation** | **0 B** | **0 B** | **2.742** |

## 5. Recovery Results

The recovery experiment reused `BKP-20261019T102301-D44AE7FD` in all five trials. Both artifacts were hashed again before recovery and matched the SHA-256 values in its manifest.

| Run | Oracle restore (s) | Redis restore (s) | Combined technical duration (s) | Oracle verified | Redis verified |
|---:|---:|---:|---:|---|---|
| 1 | 25.901 | 8.946 | 34.847 | Yes | Yes |
| 2 | 24.676 | 8.848 | 33.524 | Yes | Yes |
| 3 | 23.539 | 9.246 | 32.785 | Yes | Yes |
| 4 | 22.678 | 9.642 | 32.320 | Yes | Yes |
| 5 | 21.755 | 9.246 | 31.001 | Yes | Yes |
| **Mean** | **23.710** | **9.186** | **32.895** | **Yes** | **Yes** |
| **Sample standard deviation** | **1.632** | **0.311** | **1.426** | — | — |

The representative first run demonstrated the complete data-loss and recovery path:

| Checkpoint | Categories | Products | Inventory rows | Redis keys | Redis `product:*` documents |
|---|---:|---:|---:|---:|---:|
| Before simulated loss | 100 | 1,000 | 1,000 | 1,000 | 1,000 |
| After simulated loss | 0 | 0 | 0 | 0 | 0 |
| After Oracle recovery | 100 | 1,000 | 1,000 | 0 | 0 |
| After Redis recovery | 100 | 1,000 | 1,000 | 1,000 | 1,000 |

## 6. Redis Persistence Benchmark

Redis write persistence was measured in a separate disposable container and Docker volume. It did not use the ShopFlash Redis service or its data volume. Each mode was repeated five times with 100,000 `SET` requests followed by 100,000 `GET` requests, 50 concurrent clients, pipeline depth 1, 256-byte values, and a random keyspace of 10,000 keys. Scheduled RDB snapshots were disabled during the benchmark to isolate the effect of AOF persistence.

| Mode | Mean SET throughput (ops/s) | Mean SET p95 (ms) | Mean GET throughput (ops/s) | Mean GET p95 (ms) |
|---|---:|---:|---:|---:|
| AOF disabled (`appendonly no`) | 205,127.55 | 0.180 | 206,017.96 | 0.172 |
| AOF `everysec` | 227,426.15 | 0.186 | 202,603.07 | 0.170 |
| AOF `always` | 8,493.02 | 7.764 | 202,353.34 | 0.183 |

The local measurements show that AOF `always` has a substantial cost on writes because every acknowledged write waits for disk synchronization. GET performance remains similar because it does not append to AOF. AOF `everysec` stayed in the same performance range as AOF disabled in this workload, but this does not mean it is universally faster; small differences can come from host and Docker variation.

## 7. Conclusion

ShopFlash can restore the tested Oracle and Redis dataset completely after a controlled loss event: all five trials returned the same checkpoint counts and matched both artifact hashes. Oracle remains the source of truth for business data, while Redis RDB provides recovery for the complete runtime keyspace; product projections can additionally be rebuilt from Oracle when appropriate.

For the normal ShopFlash Redis configuration, AOF `everysec` is the recommended balance. It limits the acknowledged-write loss window after a sudden failure to approximately one second while avoiding the severe SET-throughput and latency penalty observed with AOF `always`. AOF `always` is appropriate only when stronger write durability is more important than write latency and throughput.

The results apply to this local Docker environment, this dataset size, and the specified benchmark workload. They should not be generalized as universal Oracle, Redis, or RTO measurements.

## 8. Reproducing the Experiment

```text
backup-recovery/
├── README.md                         # This report overview
├── REPORT_DATA.md                    # Detailed raw values and report worksheet
├── oracle/                           # Checkpoint and controlled-loss SQL
├── redis/                            # Checkpoint and controlled-loss Lua
├── scripts/
│   ├── README.md                     # PowerShell and Bash recovery runbook
│   ├── Invoke-BackupRecoveryDemo.ps1 # Automated recovery workflow
│   └── measure_redis_persistence.py  # Isolated AOF benchmark
└── results/                          # Generated state, summary, CSV, and Markdown outputs
```

Create a backup from the F11 administration page, then follow the PowerShell or Bash recovery runbook in [`scripts/README.md`](scripts/README.md). To run the isolated Redis persistence benchmark from the repository root:

```text
python report/backup-recovery/scripts/measure_redis_persistence.py
```

The detailed per-run backup, recovery, and benchmark values are recorded in [`REPORT_DATA.md`](REPORT_DATA.md). Generated artifacts and result output are local evidence and should not be committed with secrets or binary backup files.
