# Module backup (frontend)

Nghiệp vụ và API: xem `backend/src/main/java/com/shopflash/modules/backup/AGENTS.md`.

## Màn hình

- `/admin/backups`: phạm vi backup toàn schema `SHOP`/Redis keyspace, trạng thái Oracle/Redis, trạng thái backup runner và luồng recovery demo.
- Giai đoạn hiện tại tạo backup thật từ giao diện, nhưng không có recovery ghi đè từ UI. Không dựng dữ liệu mock để giả vờ đã tạo artifact.

## API đang dùng

- `GET /api/admin/backups/status` qua `api.js`.
- `GET /api/admin/backups?page=&size=` xem manifest backup cục bộ.
- `POST /api/admin/backups` tạo backup Oracle Data Pump + Redis RDB qua runner cố định.

## Giao diện

- Dùng Ant Design và token từ `src/shared/design-system`; không ghi mã màu/khoảng cách cố định.
- Giao diện tiếng Việt, có loading, thông báo lỗi và nút tải lại trạng thái.
- Hiển thị rõ backup hướng tới toàn schema `SHOP` và toàn Redis keyspace; số catalog chỉ là mốc kiểm tra, không phải giới hạn backup.
- Chỉ bật nút tạo backup khi backend báo runner sẵn sàng và hai nguồn dữ liệu đang UP.
- Lịch sử backup hiển thị backup id, thời điểm, trạng thái, kích thước và SHA-256 rút gọn; có phân trang.
- Recovery ghi đè không thuộc UI thông thường.
