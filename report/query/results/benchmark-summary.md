# Kết quả đo (khởi động 1 lần không tính, đo 5 lần)

Số liệu thô: `benchmark-<report>.csv`. Môi trường: `environment.md`.

| Báo cáo | Nguồn | Biến thể | Trung bình ± độ lệch (ms) | p50 (ms) | p95 (ms) | Round-trip |
|---|---|---|---|---|---|---|
| revenue-by-month | oracle | AUTO | 78.6 ± 17.4 | 70.5 | 107.7 | 1 |
| revenue-by-month | redis | AGGREGATE | 199.9 ± 22.1 | 200.6 | 227.3 | 1 |
| revenue-by-month | redis | CLIENT | 820.1 ± 55.2 | 842.2 | 862.4 | 9 |
| revenue-by-category | oracle | AUTO | 208.5 ± 40.7 | 193.3 | 255.8 | 1 |
| revenue-by-category | oracle | NL | 1,180.9 ± 227.9 | 1,254.3 | 1,373.0 | 1 |
| revenue-by-category | oracle | HASH | 233.2 ± 50.6 | 213.5 | 323.5 | 1 |
| revenue-by-category | oracle | MERGE | 411.9 ± 82.4 | 382.3 | 551.9 | 1 |
| revenue-by-category | redis | AGGREGATE | 373.4 ± 48.5 | 360.7 | 441.0 | 1 |
| revenue-by-category | redis | CLIENT | 2,597.1 ± 382.3 | 2,411.2 | 3,120.2 | 22 |
| top-products | oracle | AUTO | 155.2 ± 23.6 | 146.7 | 196.0 | 1 |
| top-products | oracle | NL | 852.9 ± 117.1 | 876.1 | 976.1 | 1 |
| top-products | oracle | HASH | 233.5 ± 28.6 | 221.7 | 279.5 | 1 |
| top-products | oracle | MERGE | 573.5 ± 87.7 | 546.6 | 670.0 | 1 |
| top-products | redis | AGGREGATE | 850.7 ± 32.4 | 835.3 | 903.2 | 1 |
| top-products | redis | CLIENT | 4,851.2 ± 1,463.0 | 4,254.0 | 7,396.8 | 22 |
| vip-customers | oracle | AUTO | 193.7 ± 24.4 | 193.6 | 223.2 | 2 |
| vip-customers | oracle | NL | 271.7 ± 16.9 | 268.0 | 294.4 | 2 |
| vip-customers | oracle | HASH | 187.2 ± 6.8 | 185.7 | 197.1 | 2 |
| vip-customers | oracle | MERGE | 250.6 ± 36.9 | 266.9 | 284.2 | 2 |
| vip-customers | redis | AGGREGATE | 875.4 ± 76.8 | 846.4 | 968.1 | 2 |
| vip-customers | redis | CLIENT | 2,362.9 ± 517.2 | 2,443.4 | 2,980.8 | 9 |
| unsold-products | oracle | AUTO | 139.3 ± 22.8 | 137.1 | 170.8 | 2 |
