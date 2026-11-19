# Lần đo lặp lại ngày 19/11/2026 (cùng dữ liệu, cùng khoảng ngày, chạy ngay trước bộ chính)

Dùng để kiểm độ ổn định của `benchmark-summary.md` và `cache-summary.md`. Plan và CSV thô chỉ giữ của bộ chính.

| Báo cáo | Nguồn | Biến thể | Trung bình ± độ lệch (ms) | p50 (ms) | p95 (ms) | Round-trip |
|---|---|---|---|---|---|---|
| revenue-by-month | oracle | AUTO | 51.4 ± 8.2 | 48.9 | 64.4 | 1 |
| revenue-by-month | redis | AGGREGATE | 56.9 ± 1.8 | 57.5 | 58.7 | 1 |
| revenue-by-month | redis | CLIENT | 650.5 ± 319.9 | 522.7 | 1,217.3 | 9 |
| revenue-by-category | oracle | AUTO | 88.9 ± 3.7 | 86.9 | 94.8 | 1 |
| revenue-by-category | oracle | NL | 468.1 ± 58.1 | 429.5 | 543.1 | 1 |
| revenue-by-category | oracle | HASH | 114.7 ± 11.3 | 112.0 | 131.8 | 1 |
| revenue-by-category | oracle | MERGE | 277.1 ± 9.4 | 275.9 | 291.0 | 1 |
| revenue-by-category | redis | AGGREGATE | 55.6 ± 2.2 | 55.2 | 58.5 | 1 |
| revenue-by-category | redis | CLIENT | 1,663.5 ± 95.5 | 1,650.9 | 1,761.2 | 22 |
| top-products | oracle | AUTO | 114.2 ± 15.4 | 108.1 | 133.1 | 1 |
| top-products | oracle | NL | 528.3 ± 26.5 | 530.1 | 566.8 | 1 |
| top-products | oracle | HASH | 171.1 ± 29.3 | 170.9 | 201.1 | 1 |
| top-products | oracle | MERGE | 309.6 ± 12.9 | 311.6 | 326.7 | 1 |
| top-products | redis | AGGREGATE | 92.4 ± 6.2 | 91.2 | 101.5 | 1 |
| top-products | redis | CLIENT | 2,207.9 ± 162.3 | 2,121.8 | 2,410.0 | 22 |
| vip-customers | oracle | AUTO | 151.9 ± 26.9 | 160.6 | 183.5 | 2 |
| vip-customers | oracle | NL | 270.8 ± 52.1 | 275.9 | 313.7 | 2 |
| vip-customers | oracle | HASH | 184.6 ± 31.9 | 192.3 | 219.9 | 2 |
| vip-customers | oracle | MERGE | 155.4 ± 22.7 | 147.2 | 194.2 | 2 |
| vip-customers | redis | AGGREGATE | 211.6 ± 14.9 | 204.4 | 236.3 | 2 |
| vip-customers | redis | CLIENT | 995.3 ± 93.4 | 981.0 | 1,097.6 | 9 |
| unsold-products | oracle | AUTO | 37.2 ± 3.5 | 37.2 | 41.9 | 2 |


| Báo cáo | Oracle AUTO (ms) | Cache MISS (ms) | Cache HIT (ms) | Round-trip MISS / HIT | Oracle / HIT |
|---|---|---|---|---|---|
| revenue-by-month | 44.8 ± 1.3 | 52.5 ± 7.3 | 1.29 ± 0.35 | 3 / 1 | 35× |
| revenue-by-category | 101.7 ± 9.8 | 110.7 ± 9.9 | 1.62 ± 0.09 | 3 / 1 | 63× |
| top-products | 121.7 ± 13.6 | 116.2 ± 13.5 | 1.25 ± 0.10 | 3 / 1 | 97× |
| vip-customers | 113.3 ± 6.3 | 138.9 ± 25.1 | 1.27 ± 0.38 | 4 / 1 | 89× |
| unsold-products | 31.2 ± 2.1 | 37.2 ± 8.6 | 1.37 ± 0.14 | 4 / 1 | 23× |
