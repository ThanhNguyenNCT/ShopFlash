# Cache-aside: Oracle tính, Redis giữ kết quả (khởi động 1 lần không tính, đo 5 lần)

MISS: cache trống, backend GET (không có) → chạy Oracle → SET kèm TTL. HIT: chỉ GET một key rồi đọc JSON.
Thời gian đo trong backend như các bảng khác. Số liệu thô: `benchmark-cache.csv`.
Cái giá: trong thời gian TTL, kết quả không thấy đơn mới (kiểm ở `tests/contract_test.py`, phần cache).

| Báo cáo | Oracle AUTO (ms) | Cache MISS (ms) | Cache HIT (ms) | Round-trip MISS / HIT | Oracle / HIT |
|---|---|---|---|---|---|
| revenue-by-month | 53.3 ± 8.6 | 77.8 ± 43.1 | 3.02 ± 0.59 | 3 / 1 | 18× |
| revenue-by-category | 137.9 ± 17.8 | 214.3 ± 53.5 | 4.24 ± 0.83 | 3 / 1 | 33× |
| top-products | 135.0 ± 47.5 | 144.1 ± 11.6 | 2.59 ± 0.64 | 3 / 1 | 52× |
| vip-customers | 137.9 ± 32.6 | 151.8 ± 30.8 | 2.11 ± 0.29 | 4 / 1 | 65× |
| unsold-products | 72.2 ± 15.4 | 176.6 ± 220.3 | 4.04 ± 0.20 | 4 / 1 | 18× |
