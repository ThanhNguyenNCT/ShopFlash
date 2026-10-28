# P0 — Kiểm tra môi trường trước khi ghi

P0 chỉ đọc, thực hiện lúc 2026-10-27T16:00:30Z trên Docker Compose local của ShopFlash. Mục đích là xác nhận ta đang kết nối đúng schema, migration và Redis instance trước khi tạo fixture. Lệnh đã dùng: `docker compose ps --format json`, `oracle/audit.sql` qua SQL*Plus, `SHOW AUTOCOMMIT` và `redis/audit.sh`. Output gốc: [Oracle](p0-oracle-audit.txt), [Redis](p0-redis-audit.txt). Không lưu mật khẩu.

## Oracle đã được xác minh

Kết nối tới user `SHOP`, PDB `FREEPDB1`, service `freepdb1` trên Oracle AI Database 26ai Free 23.26.3.0.0. Flyway V1, V2 và hai repeatable seed đều thành công; các cột `CARTS.VERSION` và `ORDERS.CHECKOUT_REQUEST_HASH` có thật. SQL*Plus đang tắt autocommit. Audit chỉ thấy một session `SHOP` active là chính kết nối audit; trong migration và Java Transaction không tìm thấy lệnh `SET CONSTRAINTS` làm đổi thời điểm kiểm tra.

Các constraint trên tám bảng liên quan đều `ENABLED` (được áp dụng), `VALIDATED` (dữ liệu đã kiểm tra), `NOT DEFERRABLE` (không thể dời việc kiểm tra đến commit) và `IMMEDIATE` (lỗi được báo ở câu SQL vi phạm). Vì vậy A2 và D1 có thể đối chiếu mã lỗi ngay tại statement. Tên PK/FK/UNIQUE/CHECK khớp migration V1; nghiên cứu không tạo hoặc sửa constraint.

## Redis đã được xác minh

Redis server 7.4.7 chạy standalone; AOF bật với `appendfsync everysec`, RediSearch và RedisJSON có mặt. Lúc audit, các prefix `product:*`, `flash:*`, `order:analytics:*`, `order:line:*`, `txlab:*`, `txresearch:*` đều có 0 key; chỉ một client audit đang kết nối. `FT._LIST` rỗng: index nền chưa khởi tạo trên instance này. Bộ 14 case chỉ dùng key `txresearch:*` và không cần index, nên không chạy `init.lua` hay thay cấu hình Redis.

## Quyết định trước P1

Hai service healthy, port chỉ bind `127.0.0.1`, không thấy client/session khác dùng dữ liệu liên quan lúc audit. Điều kiện này đủ để tạo fixture riêng trong môi trường local đã xác minh. Nếu chạy lại trên môi trường khác hoặc phát hiện người khác đang dùng DB, cần audit lại và dừng trước lệnh ghi. Khác biệt duy nhất ghi nhận là Redis index nền chưa có; nó không ảnh hưởng các case nghiên cứu. Không có blocker P0.
