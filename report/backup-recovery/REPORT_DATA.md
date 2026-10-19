# Backup and Recovery - Report Data Sheet

> Fill this file while running experiments. It is a working sheet for the report, not a generated result artifact.

## 1. Experiment environment

| Item | Value |
|---|---|
| Contributor | |
| Date | |
| Git commit / branch | |
| Operating system | |
| CPU | |
| RAM | |
| Disk type / free space | |
| Docker Desktop / Docker Engine version | |
| Oracle image | `container-registry.oracle.com/database/free` |
| Redis image | `redis/redis-stack-server` |
| Java / backend version | |
| Dataset | `100` categories, `1000` products, `1000` inventory rows, `1000` Redis product documents |

## 2. Backup creation time

Definition: `completedAt - createdAt` in the backup manifest. It includes the fixed backend runner creating the Oracle Data Pump dump, Redis RDB snapshot, copying artifacts, and calculating SHA-256.

| Run | Backup ID | Oracle dump size | Redis RDB size | Duration (s) | Notes |
|---:|---|---:|---:|---:|---|
| 1 | `BKP-20261019T102301-D44AE7FD` | 1,355,776 B | 190,018 B | 63.026 | 2026-10-19 17:23:01.915--17:24:04.940 (UTC+07:00); completed |
| 2 | `BKP-20261019T102438-1C56E51F` | 1,355,776 B | 190,018 B | 67.478 | 2026-10-19 17:24:38.297--17:25:45.776 (UTC+07:00); completed |
| 3 | `BKP-20261019T102706-3971A211` | 1,355,776 B | 190,018 B | 60.923 | 2026-10-19 17:27:06.364--17:28:07.287 (UTC+07:00); completed |
| 4 | `BKP-20261019T102850-F699AA7E` | 1,355,776 B | 190,018 B | 60.679 | 2026-10-19 17:28:50.436--17:29:51.115 (UTC+07:00); completed |
| 5 | `BKP-20261019T103005-776E7744` | 1,355,776 B | 190,018 B | 62.402 | 2026-10-19 17:30:05.533--17:31:07.935 (UTC+07:00); completed |
| **Mean** | — | 1,355,776 B | 190,018 B | 62.902 | Five completed runs |
| **Standard deviation** | — | 0 B | 0 B | 2.742 | Sample standard deviation |

Source: `results/run-<id>/state.json` → `backupTiming`, or the backup `manifest.json` fields `createdAt` and `completedAt`.

## 3. Recovery time

Definition: controlled technical restore time. It starts after preflight safety checks and ends after the relevant checkpoint matches the baseline. It excludes manual pauses between commands, so it is **not RTO**.

| Run | Backup ID | Oracle restore (s) | Redis restore (s) | Combined (s) | Oracle verified | Redis verified |
|---:|---|---:|---:|---:|---|---|
| 1 | `BKP-20261019T102301-D44AE7FD` | 25.901 | 8.946 | 34.847 | Yes | Yes |
| 2 | `BKP-20261019T102301-D44AE7FD` | 24.676 | 8.848 | 33.524 | Yes | Yes |
| 3 | `BKP-20261019T102301-D44AE7FD` | 23.539 | 9.246 | 32.785 | Yes | Yes |
| 4 | `BKP-20261019T102301-D44AE7FD` | 22.678 | 9.642 | 32.320 | Yes | Yes |
| 5 | `BKP-20261019T102301-D44AE7FD` | 21.755 | 9.246 | 31.001 | Yes | Yes |
| **Mean** | — | 23.710 | 9.186 | 32.895 | Yes | Yes |
| **Standard deviation** | — | 1.632 | 0.311 | 1.426 | — | — |

Source:

- PowerShell: `results/run-<id>/state.json` → `oracleRecovery.durationMilliseconds` and `redisRecovery.durationMilliseconds`.
- Bash: `results/bash-run-<id>/.work/oracle-restore-duration.txt` and `redis-restore-duration.txt`.

## 4. Correctness evidence for one representative run

| Checkpoint | Oracle categories | Oracle products | Inventory rows | Redis total keys | Redis `product:*` documents | Result file / screenshot |
|---|---:|---:|---:|---:|---:|---|
| Before simulated loss | 100 | 1000 | 1000 | 1000 | 1000 | `results/run-01/state.json` → `beforeLoss` |
| After simulated loss | 0 | 0 | 0 | 0 | 0 | `results/run-01/state.json` → `afterLoss` |
| After Oracle recovery | 100 | 1000 | 1000 | 0 | 0 | `results/run-01/state.json` → `oracleRecovery`; Redis remains unchanged after loss until its restore step |
| After Redis recovery | 100 | 1000 | 1000 | 1000 | 1000 | `results/run-01/state.json` → `redisRecovery` |

Artifact integrity:

| Artifact | SHA-256 matches manifest? | Notes |
|---|---|---|
| Oracle Data Pump `.dmp` | Yes | Hash recomputed from the artifact and matched `manifest.json`. |
| Redis `dump.rdb` | Yes | Hash recomputed from the artifact and matched `manifest.json`. |

## 5. Redis AOF persistence benchmark

Run each configuration in a separate benchmark volume or reset the Redis dataset before the next configuration. Do not benchmark against the Redis volume used by the app demo.

### Workload definition

| Parameter | Value |
|---|---|
| Tool / command | `redis-benchmark` in an isolated `redis/redis-stack-server` Docker container |
| Operations measured | `SET` followed by `GET` |
| Total requests per run | `100000` SET + `100000` GET |
| Concurrency | `50` clients |
| Pipeline depth | `1` |
| Value size (bytes) | `256` |
| Dataset reset method | Fresh temporary Redis container and Docker volume for every sample; `save ""` disables scheduled RDB snapshots during measurement |
| Number of repetitions | `5` |

### Results

| Mode | Run | SET throughput (ops/s) | SET p50 (ms) | SET p95 (ms) | GET throughput (ops/s) | GET p50 (ms) | GET p95 (ms) | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| AOF disabled (`appendonly no`) | 1 | 191204.59 | 0.135 | 0.231 | 204918.03 | 0.127 | 0.175 | Fresh temporary volume |
|  | 2 | 211416.50 | 0.127 | 0.167 | 206185.56 | 0.127 | 0.167 | Fresh temporary volume |
|  | 3 | 211864.41 | 0.127 | 0.159 | 205761.31 | 0.127 | 0.167 | Fresh temporary volume |
|  | 4 | 207900.20 | 0.127 | 0.175 | 206185.56 | 0.127 | 0.183 | Fresh temporary volume |
|  | 5 | 203252.03 | 0.127 | 0.167 | 207039.33 | 0.127 | 0.167 | Fresh temporary volume |
| AOF `everysec` | 1 | 226244.34 | 0.119 | 0.191 | 203252.03 | 0.127 | 0.175 | Fresh temporary volume |
|  | 2 | 227790.42 | 0.119 | 0.207 | 202429.16 | 0.127 | 0.167 | Fresh temporary volume |
|  | 3 | 231481.47 | 0.119 | 0.175 | 201612.91 | 0.127 | 0.175 | Fresh temporary volume |
|  | 4 | 229885.06 | 0.119 | 0.183 | 200803.22 | 0.127 | 0.175 | Fresh temporary volume |
|  | 5 | 221729.48 | 0.119 | 0.175 | 204918.03 | 0.127 | 0.159 | Fresh temporary volume |
| AOF `always` | 1 | 8992.81 | 5.551 | 7.087 | 204081.62 | 0.127 | 0.175 | Fresh temporary volume |
|  | 2 | 8673.78 | 5.639 | 7.751 | 201612.91 | 0.127 | 0.207 | Fresh temporary volume |
|  | 3 | 8265.83 | 5.895 | 8.271 | 201207.23 | 0.135 | 0.183 | Fresh temporary volume |
|  | 4 | 8840.95 | 5.647 | 7.407 | 203252.03 | 0.127 | 0.167 | Fresh temporary volume |
|  | 5 | 7691.72 | 6.623 | 8.303 | 201612.91 | 0.127 | 0.183 | Fresh temporary volume |

### Summary for comparison

| Mode | Mean SET throughput | Mean SET p95 | Mean GET throughput | Mean GET p95 | Durability trade-off |
|---|---:|---:|---:|---:|---|
| AOF disabled | 205127.55 | 0.180 | 206017.96 | 0.172 | AOF disabled; scheduled RDB snapshots were also disabled during this benchmark |
| AOF `everysec` | 227426.15 | 0.186 | 202603.07 | 0.170 | At most about one second of acknowledged writes may be lost after sudden failure |
| AOF `always` | 8493.02 | 7.764 | 202353.34 | 0.183 | Strongest durability; each write waits for disk synchronization |

## 6. Figures to capture

| Figure | File name to use | What it proves |
|---:|---|---|
| 1 | | Backup page after a successful backup, with artifact metadata |
| 2 | | `state.json` or terminal output showing the backup duration |
| 3 | | Checkpoint before loss |
| 4 | | Checkpoint after loss, showing empty Oracle and Redis data |
| 5 | | Oracle recovery success and duration |
| 6 | | Redis recovery success and duration |
| 7 | | Final `summary.md` or empty diff confirming recovery correctness |
| 8 | | Redis benchmark chart: throughput by persistence mode |
| 9 | | Redis benchmark chart: p95 latency by persistence mode |

## 7. Findings to write in the report

- Backup creation time: 62.902 s mean across 5 runs (sample standard deviation: 2.742 s).
- Oracle recovery time: 23.710 s mean across 5 runs (sample standard deviation: 1.632 s).
- Redis recovery time: 9.186 s mean across 5 runs (sample standard deviation: 0.311 s).
- Does recovered data match the baseline? Yes, for all 5 runs.
- Best Redis persistence mode for ShopFlash and why: AOF `everysec`. In this local workload, it kept SET and GET results in the same range as AOF disabled while limiting the acknowledged-write loss window to about one second. AOF `always` provided stronger durability but reduced mean SET throughput to 8,493.02 ops/s and raised SET p95 to 7.764 ms; the result is specific to this hardware and workload.
- Limits of this experiment: local Docker environment, sample dataset size, technical timing is not RTO, and benchmark results depend on hardware/disk.
