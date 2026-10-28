# Module Transaction: chạy và kiểm thử

[CONTRACT.md](CONTRACT.md) là hợp đồng API và quy tắc nghiệp vụ F10 Cart, F7 Checkout, F8 Order. Oracle là nguồn dữ liệu chính; giỏ hàng và đơn thường không nằm trong Redis. Hướng dẫn thí nghiệm DBMS-native và benchmark nằm tại [report/transaction/README.md](../../../report/transaction/README.md).

## Chạy local

Cần Java 21, Docker, Oracle và Redis theo [db/README.md](../../../db/README.md). Tại gốc repo, tạo `.env` từ `.env.example`, đặt mật khẩu và `JWT_SECRET_BASE64` (ít nhất 32 byte sau giải mã; có thể tạo bằng `openssl rand -base64 32`), rồi chạy:

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Profile `dev` nạp dữ liệu mẫu qua Flyway. Đăng nhập `customer@shopflash.local` với mật khẩu dev seed `ShopFlash123!` tại `http://127.0.0.1:5173/login` sau khi chạy `cd frontend && npm run dev`. Vite proxy `/api` về backend `127.0.0.1:8080`. Giữ JWT secret trong `.env`, không đưa vào `VITE_*` hoặc commit. Giỏ mẫu gồm `CORE-001`, `TX-101`–`TX-104`; `TX-105` hết tồn và `TX-106` ngừng bán phục vụ thử lỗi.

Nếu Flyway báo schema `SHOP` có bảng nhưng thiếu `flyway_schema_history`, dừng để xác định nguồn gốc dữ liệu. Không tự baseline, sửa migration cũ hoặc xóa schema. Test tích hợp cần schema đã được Flyway áp đúng; fixture dùng ID và dữ liệu riêng, không thay seed nền.

## Kiểm thử

Từ `backend/`:

```bash
./mvnw -q -DskipTests package
./mvnw test -Dtest='CartCoreTest,CheckoutCoreTest'
./mvnw test -Dtest='CartOracleIT,CheckoutOracleIT,OrderOracleIT'
./mvnw test -Dtest='TransactionFixtureIT,TransactionSemanticsIT,RedisTransactionIT'
```

`*CoreTest` không cần database. `*IT` dùng Oracle/Redis thật và phải được gọi đích danh; Surefire không tự chọn chúng khi chạy `test` thông thường. Các test nghiệp vụ dùng fixture riêng, kiểm tra kết quả commit/rollback bằng kết nối độc lập và tự dọn dữ liệu. `CheckoutProbe` và `OrderCancellationProbe` là điểm gây lỗi/đồng bộ của integration tests; không được đăng ký làm bean ở production. `TransactionDbBenchmarkIT` và các lệnh tạo fixture HTTP có workflow riêng trong tài liệu nghiên cứu; không đưa vào lệnh kiểm thử thường xuyên.

Không chạy `docker compose down -v` trên dữ liệu nhóm. Khi cần chạy lại seed `dev`, khởi động lại backend sau khi thay đổi file seed; seed không tự đặt lại giá, tồn kho hoặc giỏ đã được sửa.
