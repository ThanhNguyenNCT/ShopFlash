# Backend

Java Spring Boot, REST API trả JSON cho frontend.

## Bố cục

- `src/main/java/com/shopflash/common/`: phần dùng chung: `config/` (Oracle, Redis, CORS), `security/` (F0 đăng nhập, filter session, phân quyền ADMIN/CUSTOMER), `exception/`, `dto/` dùng chung.
- `src/main/java/com/shopflash/modules/<module>/`: mỗi module có cùng một khung:
  - `controller/`: nhận request, gọi service, trả response. Không chứa logic nghiệp vụ.
  - `dto/request/`: payload vào, kiểm tra bằng `@Valid`.
  - `dto/response/`: payload ra.
  - `service/`: logic nghiệp vụ.
  - `repository/`: truy cập dữ liệu Oracle và Redis.
  - `model/`: class ánh xạ dòng dữ liệu Oracle hoặc giá trị Redis.
  - `mapper/`: chuyển đổi model ↔ dto.
- `src/main/resources/redis/<module>/`: script Lua của Redis.

## Quy ước

Cách cài đặt bên trong module (thư viện truy cập DB, định dạng response…) do người phụ trách module tự chọn. Quy ước chung nào nhóm đã chốt thì ghi vào đây.
