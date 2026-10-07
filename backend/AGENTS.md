# Backend

Java Spring Boot, REST API trả JSON cho frontend.

## Build và database

- Java 21, Maven Wrapper: `./mvnw`.
- Flyway tự chạy khi ứng dụng khởi động.
- Migration Oracle nằm trong `src/main/resources/db/migration/` theo tên `V<number>__description.sql`.
- Redis initializer nằm trong `src/main/resources/db/redis/`.
- Không sửa migration đã áp dụng; tạo version mới để thay đổi schema hoặc seed.

## Bố cục

- `src/main/java/com/shopflash/ShopFlashBackendApplication.java`: entrypoint Spring Boot.
- `src/main/java/com/shopflash/common/`: phần dùng chung; chỉ tạo package khi có code thực tế.
- `src/main/java/com/shopflash/modules/<module>/`: mỗi module tạo package cần dùng khi bắt đầu hiện thực, ví dụ:
  - `controller/`: nhận request, gọi service, trả response. Không chứa logic nghiệp vụ.
  - `dto/request/`: payload vào, kiểm tra bằng `@Valid`.
  - `dto/response/`: payload ra.
  - `service/`: logic nghiệp vụ.
  - `repository/`: truy cập dữ liệu Oracle và Redis.
  - `model/`: class ánh xạ dòng dữ liệu Oracle hoặc giá trị Redis.
  - `mapper/`: chuyển đổi model ↔ dto.
- Không giữ thư mục/package rỗng bằng `.gitkeep`.

## Quy ước

Cách cài đặt bên trong module (thư viện truy cập DB, định dạng response…) do người phụ trách module tự chọn. Quy ước chung nào nhóm đã chốt thì ghi vào đây.
