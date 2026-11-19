# Cache-aside: Oracle tính, Redis giữ kết quả (khởi động 1 lần không tính, đo 5 lần)

MISS: cache trống, backend GET (không có) → chạy Oracle → SET kèm TTL. HIT: chỉ GET một key rồi đọc JSON.
Thời gian đo trong backend như các bảng khác. Số liệu thô: `benchmark-cache.csv`.
Cái giá: trong thời gian TTL, kết quả không thấy đơn mới (kiểm ở `tests/contract_test.py`, phần cache).

| Báo cáo | Oracle AUTO (ms) | Cache MISS (ms) | Cache HIT (ms) | Round-trip MISS / HIT | Oracle / HIT |
|---|---|---|---|---|---|
| revenue-by-month | 44.8 ± 1.4 | 48.5 ± 4.2 | 1.05 ± 0.09 | 3 / 1 | 43× |
| revenue-by-category | 118.0 ± 18.7 | 124.3 ± 48.8 | 1.49 ± 0.18 | 3 / 1 | 79× |
| top-products | 141.6 ± 39.7 | 161.1 ± 61.8 | 1.90 ± 0.10 | 3 / 1 | 75× |
| vip-customers | 134.2 ± 10.2 | 154.3 ± 13.0 | 1.40 ± 0.11 | 4 / 1 | 96× |
| unsold-products | 32.8 ± 2.2 | 36.5 ± 5.4 | 1.34 ± 0.10 | 4 / 1 | 24× |
