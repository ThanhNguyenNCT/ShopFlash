# Kết quả đo (khởi động 1 lần không tính, đo 5 lần)

Số liệu thô: `benchmark-<report>.csv`. Môi trường: `environment.md`.

| Báo cáo | Nguồn | Biến thể | Trung bình ± độ lệch (ms) | p50 (ms) | p95 (ms) | Round-trip |
|---|---|---|---|---|---|---|
| revenue-by-month | oracle | AUTO | 49.7 ± 3.3 | 48.5 | 54.1 | 1 |
| revenue-by-month | redis | AGGREGATE | 62.7 ± 1.1 | 62.6 | 64.4 | 1 |
| revenue-by-month | redis | CLIENT | 625.0 ± 59.0 | 635.0 | 693.9 | 9 |
| revenue-by-category | oracle | AUTO | 94.5 ± 7.6 | 91.0 | 108.0 | 1 |
| revenue-by-category | oracle | NL | 459.4 ± 10.3 | 456.0 | 477.0 | 1 |
| revenue-by-category | oracle | HASH | 108.5 ± 8.8 | 105.7 | 118.6 | 1 |
| revenue-by-category | oracle | MERGE | 314.1 ± 16.9 | 314.6 | 335.4 | 1 |
| revenue-by-category | redis | AGGREGATE | 55.8 ± 2.3 | 56.1 | 58.2 | 1 |
| revenue-by-category | redis | CLIENT | 1,686.0 ± 121.9 | 1,663.3 | 1,890.0 | 22 |
| top-products | oracle | AUTO | 101.4 ± 4.5 | 100.2 | 106.9 | 1 |
| top-products | oracle | NL | 528.4 ± 27.9 | 541.6 | 551.6 | 1 |
| top-products | oracle | HASH | 136.1 ± 15.1 | 132.7 | 162.2 | 1 |
| top-products | oracle | MERGE | 306.4 ± 10.9 | 303.8 | 324.0 | 1 |
| top-products | redis | AGGREGATE | 92.2 ± 7.9 | 88.8 | 106.3 | 1 |
| top-products | redis | CLIENT | 1,961.9 ± 76.5 | 1,973.3 | 2,047.8 | 22 |
| vip-customers | oracle | AUTO | 126.5 ± 10.6 | 123.7 | 144.4 | 2 |
| vip-customers | oracle | NL | 160.3 ± 13.2 | 155.9 | 183.0 | 2 |
| vip-customers | oracle | HASH | 138.9 ± 10.0 | 139.0 | 151.3 | 2 |
| vip-customers | oracle | MERGE | 140.1 ± 10.3 | 135.4 | 155.6 | 2 |
| vip-customers | redis | AGGREGATE | 192.8 ± 9.1 | 193.9 | 202.8 | 2 |
| vip-customers | redis | CLIENT | 673.7 ± 44.3 | 663.2 | 736.9 | 9 |
| unsold-products | oracle | AUTO | 31.7 ± 3.1 | 30.2 | 36.9 | 2 |
