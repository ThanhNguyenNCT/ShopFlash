# ShopFlash

Bài tập lớn môn Hệ quản trị Cơ sở dữ liệu (HK261, L01). Nhóm so sánh Oracle và Redis qua 6 chủ đề của môn học, minh họa bằng một ứng dụng bán hàng có flash sale, rồi chọn DBMS phù hợp cho từng loại dữ liệu.

Kế hoạch đầy đủ nằm ngoài repo: `../assignment/KE_HOACH_BTL.md`.

## Stack

- Oracle Database Free bản đầy đủ (image ghim theo digest trong `compose.yaml`), schema `SHOP`: dữ liệu nghiệp vụ, là nguồn dữ liệu chính, kể cả giỏ hàng.
- Redis Stack (ghim theo digest, có RediSearch, RedisJSON): projection tìm kiếm sản phẩm, trạng thái runtime và giữ suất flash sale, read model analytics. Dữ liệu Redis dựng lại được từ Oracle, trừ trạng thái runtime flash sale.
- Thiết kế dữ liệu chung: `db/DATABASE_DESIGN.md`. Phiên bản đang dùng: `db/README.md`.
- Backend: Java Spring Boot, trong `backend/`.
- Frontend: React + Vite + Ant Design, trong `frontend/`.

## Cấu trúc

- `backend/` và `frontend/` chia theo 6 module, tên module giống nhau ở hai bên.
- `backend/src/main/resources/db/migration/`: migration Oracle do Flyway chạy khi Spring Boot khởi động.
- `backend/src/main/resources/db/redis/`: script khởi tạo Redis.
- `db/`: tài liệu thiết kế dữ liệu.
- `docs/`: tài liệu dự án.
- Design system (giao diện): code ở `frontend/src/shared/design-system/`, bản xem nhanh `frontend/design-system.html`.
- `report/`: báo cáo môn học.

## Module

Mỗi module ứng với một chủ đề của môn học. Một người làm trọn module của mình, từ DB, backend, frontend đến tài liệu, và tự chốt thiết kế của module (bảng, key, API, cách cài đặt). Thiết kế đã chốt được ghi trong `AGENTS.md` của module đó.

Kế hoạch BTL chỉ để tham khảo. Đừng coi nội dung kế hoạch là quyết định của nhóm nếu `AGENTS.md` không ghi.

| Module | Chủ đề | Chức năng |
|---|---|---|
| `storage` | Data storage & management | F1 Quản lý danh mục, F2 Quản lý sản phẩm |
| `indexing` | Indexing | F3 Tìm kiếm sản phẩm, F4 Lọc nâng cao |
| `query` | Query processing | F5 Báo cáo doanh thu, F6 Top bán chạy và khách VIP |
| `transaction` | Transaction | F10 Giỏ hàng, F7 Đặt hàng, F8 Lịch sử và hủy đơn |
| `concurrency` | Concurrency control | F9 Flash sale |
| `backup` | Data backup and recovery | F11 Sao lưu |

Đăng nhập / đăng ký (F0) không tính vào 10 chức năng, nằm ở `common/security` (backend) và `shared/auth` (frontend). TV5 đã đồng ý bàn giao F0 cho TV4 để triển khai JWT phục vụ F10/F7/F8; TV5 tiếp tục phụ trách F9.

## Quy tắc

- Chỉ sửa module đang làm. Muốn sửa `common/`, `shared/` hoặc module khác thì báo người phụ trách trước.
- IMPORTANT: Câu SQL luôn dùng bind variable, không bao giờ nối chuỗi.
- Mật khẩu băm bằng bcrypt.

## Lệnh database

Hai image database được ghim theo digest trong `compose.yaml`, phải `docker pull` trước (xem `db/README.md`). User `SHOP` do `db/oracle-init` tạo khi container khởi động; migration do Flyway chạy bằng user `SHOP`; Redis index khởi tạo bằng `redis-cli`.

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # bỏ profile dev nếu không cần dữ liệu mẫu
```
