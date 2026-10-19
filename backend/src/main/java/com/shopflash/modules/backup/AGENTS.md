# Module backup

Chủ đề: Data backup and recovery.

## Chức năng

- F11 Sao lưu và phục hồi dữ liệu.
- App tạo backup thật cho toàn schema `SHOP` và toàn Redis keyspace; recovery vẫn chưa chạy từ UI.

## Quyết định đã chốt

- **Oracle là dữ liệu nghiệp vụ gốc.** Backup dự kiến dùng Data Pump cho toàn bộ schema `SHOP` theo tác vụ cố định (`expdp schemas=SHOP`); recovery dùng `impdp` theo script điều phối đã kiểm soát. Không giới hạn ở catalog hay bất kỳ bảng nào.
- **Redis là dữ liệu bổ trợ.** Backup dự kiến dùng RDB snapshot của toàn bộ keyspace trong Redis instance dành cho ShopFlash. Product projection `product:*` có thể rebuild từ Oracle; các Redis state không thể dựng lại mới cần RDB restore.
- **Không chạy shell từ request.** Backend không nhận command, đường dẫn file hay tham số Data Pump/RDB tùy ý từ client. Khi có backup runner, backend chỉ gọi tác vụ được định nghĩa trước.
- **Không recovery ghi đè từ UI.** Giai đoạn đầu chỉ cung cấp backup/status. Recovery demo chạy thủ công sau khi dừng app, vào môi trường hoặc quy trình đã kiểm soát.
- **Artifact backup nằm ngoài Docker data volume.** Mỗi backup về sau phải có manifest gồm backup id, thời điểm, loại artifact, kích thước, SHA-256, số liệu baseline và Product mẫu để verify.
- **Truy cập Oracle:** JDBC với bind variable; không dùng JPA/Hibernate. Flyway là nơi duy nhất sở hữu DDL.

## Dữ liệu theo dõi

| Nguồn | Giai đoạn app đầu tiên | Backup về sau |
|---|---|---|
| Oracle | Đếm toàn bộ bảng nghiệp vụ trong `SHOP`; Category/Product/Inventory chỉ là mốc kiểm tra nhanh. | Data Pump artifact cho toàn bộ schema `SHOP` và manifest. |
| Redis | Đếm toàn keyspace; `product:*` chỉ là mốc kiểm tra nhanh. | RDB artifact cho toàn bộ keyspace và manifest. |

## API hiện có

Base: `/api/admin/backups`.

### `GET /api/admin/backups/status`

Trả trạng thái đọc được của hai nguồn và trạng thái backup runner. Response `200` ngay cả khi một nguồn đang down để giao diện nói rõ nguồn nào không sẵn sàng:

```json
{
  "oracle": { "status": "UP", "applicationTables": 13, "categories": 100, "products": 1000, "inventoryRows": 1000 },
  "redis": { "status": "UP", "productDocuments": 1000, "totalKeys": 1008 },
  "runner": { "configured": false, "state": "NOT_CONFIGURED", "message": "Backup runner chưa được cấu hình." },
  "generatedAt": "2026-10-19T10:00:00+07:00"
}
```

`applicationTables` không tính bảng lịch sử Flyway. Khi `status = "DOWN"`, các count của nguồn đó là `null`; không trả nguyên văn lỗi kết nối cho client.

### `POST /api/admin/backups`

Tạo một backup qua runner cố định. Không nhận body, command, tên file hoặc đường dẫn từ client. Runner chạy Data Pump schema export của `SHOP`, `BGSAVE` Redis, rồi copy artifact ra `backups/<backup-id>/` trên host. Response là metadata và SHA-256 của dump/RDB; không lộ đường dẫn tuyệt đối.

### `GET /api/admin/backups?page=&size=`

Đọc các `manifest.json` hợp lệ trong thư mục `backups/`, trả lịch sử backup phân trang.

Không có `POST /{id}/restore` cho đến khi có cơ chế maintenance/restore test an toàn.

## Giao diện

- `/admin/backups`: trang quản trị Sao lưu và phục hồi.
- Hiển thị số liệu Oracle/Redis thật, trạng thái runner, lịch sử artifact và các bước recovery demo.
- Không hiển thị nút recovery ghi đè. Nút tạo backup chỉ khả dụng khi Docker Compose runner sẵn sàng và hai nguồn dữ liệu đang UP.
