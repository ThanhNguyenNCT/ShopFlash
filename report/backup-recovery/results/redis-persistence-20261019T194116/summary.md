# Redis persistence benchmark

- Redis image: `redis/redis-stack-server@sha256:798ab84d9f266936b034ab11c4d04a2b8e4b441884c5aa7d17ac951eefdf742a`
- Repetitions per mode: 5
- SET and GET requests per repetition: 100,000
- Concurrent clients: 50
- Pipeline depth: 1
- Value size: 256 B
- Random keyspace: 10,000
- Each sample used a fresh disposable Docker volume and Redis container; the ShopFlash Redis service and its volume were not used.

| Mode | Mean SET throughput (ops/s) | Mean SET p50 (ms) | Mean SET p95 (ms) | Mean GET throughput (ops/s) | Mean GET p50 (ms) | Mean GET p95 (ms) |
|---|---:|---:|---:|---:|---:|---:|
| AOF disabled (`appendonly no`) | 205127.55 | 0.129 | 0.180 | 206017.96 | 0.127 | 0.172 |
| AOF `everysec` | 227426.15 | 0.119 | 0.186 | 202603.07 | 0.127 | 0.170 |
| AOF `always` | 8493.02 | 5.871 | 7.764 | 202353.34 | 0.129 | 0.183 |
