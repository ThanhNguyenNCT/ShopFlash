# Môi trường đo (E1, E2)

Đo ngày 2026-11-16, một máy, backend chạy trực tiếp trên Windows, Oracle và Redis chạy trong Docker trên cùng máy.

| Thành phần | Giá trị |
|---|---|
| CPU | Intel Core i7-12700H (14 nhân, 20 luồng) |
| RAM | 31,6 GB; Docker Desktop (WSL2) được cấp 20 CPU, 15,4 GB |
| Hệ điều hành | Windows 11 Pro 10.0.26200 |
| Docker | Engine 28.5.2, kernel 6.18.58.1-microsoft-standard-WSL2 |
| Oracle | Oracle AI Database 26ai Free 23.26.3.0.0 (image ghim theo digest trong `compose.yaml`) |
| Redis | Redis Stack, Redis 7.4.7; AOF bật (`appendonly yes`, `appendfsync everysec`) |
| JDK | Oracle JDK 23.0.2 (dự án đặt `java.version` 21) |
| Backend | Spring Boot 4.1.1, profile `dev`, một instance, nhánh `dev-concurrency` (f638af0 và các sửa sau review ngày 2026-11-16, không đụng code Lab) |
| Pool Oracle | HikariCP, `maximumPoolSize = 16` (đặt bằng `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=16`; mặc định là 10) |
| Pool Redis | Jedis, `maxTotal = 16` (`common/redis/RedisConfig.java`) |
| Số lần thử lại | Tối đa 5 (`ORACLE_OPTIMISTIC`, `ORACLE_SERIALIZABLE`, `REDIS_WATCH`), không chờ giữa các lần |

## Cách chạy

```bash
docker compose up -d --wait
cd backend
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=16 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# cửa sổ khác, từ thư mục gốc repo
python report/concurrency/bench/run_lab.py e1    # 10 chiến lược, N = 1000, M = 100, C = 64
python report/concurrency/bench/run_lab.py e2    # 5 chiến lược, N = M = 2000, C = 1, 8, 32, 64, 128
```

Mỗi cấu hình: 1 lần khởi động không tính, 5 lần đo. Bộ đếm được đặt lại trước mỗi lần chạy.

## File kết quả

| File | Nội dung |
|---|---|
| `e1-runs.csv`, `e2-runs.csv` | Mỗi lần đo một dòng (số thô) |
| `e1-summary.csv`, `e2-summary.csv` | Trung bình và độ lệch chuẩn mẫu của 5 lần đo; `errorCodes` là tổng số lỗi theo mã của cả 5 lần |
| `../images/3.5-01-e1-ban-vuot.png` | E1: số lượt được báo thành công so với bộ đếm cuối cùng |
| `../images/3.5-02-e2-throughput.png`, `3.5-03-e2-p95.png` | E2: throughput và p95 theo C |

## Lưu ý khi đọc số

- Độ trễ một lượt gồm cả thời gian chờ kết nối từ pool (C = 32, 64, 128 lớn hơn pool 16), không gồm thời gian xếp hàng chờ thread.
- Throughput tính trên mọi lượt, kể cả lượt hết suất và lượt lỗi. Với chiến lược có lượt lỗi (`ORACLE_NOWAIT`, `ORACLE_OPTIMISTIC`, `REDIS_WATCH`), số suất bán được mỗi giây thấp hơn throughput.
- Trong lúc đo, máy vẫn mở IDE và trình duyệt; không chạy tải nào khác. Số liệu trên laptop qua Docker dao động giữa các lần, xem cột độ lệch chuẩn.
