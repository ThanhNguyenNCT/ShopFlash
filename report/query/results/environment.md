# Môi trường đo (3.3 Query processing)

Đo lúc: 2026-10-18 13:03 (+07:00)

| Thành phần | Giá trị |
|---|---|
| Máy | AMD Ryzen 5 7535HS with Radeon Graphics, 15.3 GB RAM |
| Hệ điều hành, Docker | Windows-11-10.0.26300-SP0; Docker 29.5.2 (12 CPU, 8326844416 bytes) |
| Oracle | Version 23.26.3.0.0; image `container-registry.oracle.com/database/free@sha256:f988b0c04c4c386cd306a2a914c0d7a9702d83acc31b064a28ad8eb6278a8fba` |
| Redis | redis_version:7.4.7; image `redis/redis-stack-server@sha256:798ab84d9f266936b034ab11c4d04a2b8e4b441884c5aa7d17ac951eefdf742a` |
| Dữ liệu | Oracle: 87,141 đơn được tính / 215,807 dòng hàng (CONFIRMED + COMPLETED, trên tổng 100.000 đơn); Redis: 87,141 doc đơn, 215,807 doc dòng hàng |

Oracle Free bị giới hạn 2 CPU, 2 GB RAM, 12 GB dữ liệu. Thời gian `elapsedMs` đo trong backend quanh lời gọi DB (gồm mạng tới container và xử lý kết quả, không gồm HTTP tới trình duyệt).
