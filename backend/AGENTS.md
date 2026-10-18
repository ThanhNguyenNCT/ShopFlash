# Backend

Java Spring Boot, REST API trả JSON cho frontend.

## Build và database

- Java 21, Maven Wrapper: `./mvnw`.
- Flyway tự chạy khi ứng dụng khởi động, bằng user `SHOP`. User này do `src/main/resources/db/oracle-init/` tạo sẵn khi container Oracle khởi động.
- Migration Oracle nằm trong `src/main/resources/db/migration/` theo tên `V<number>__<module>_<mô tả>.sql`.
- Dữ liệu mẫu nằm trong `src/main/resources/db/dev-seed/` dạng `R__10_<module>_seed.sql` (Flyway chạy repeatable theo thứ tự chữ cái, nên seed lõi `R__00_core_seed.sql` luôn chạy trước), chỉ chạy với profile `dev` (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`).
- Redis initializer nằm trong `src/main/resources/db/redis/`.
- Không sửa migration đã có trên `main`; tạo version mới để thay đổi schema.

## Bố cục

- `src/main/java/com/shopflash/ShopFlashBackendApplication.java`: entrypoint Spring Boot.
- `src/main/java/com/shopflash/common/`: phần dùng chung cho mọi module:
  - `redis/RedisConfig`: bean `RedisClient` (Jedis, RESP2). Inject theo kiểu `RedisClient redis`, không tự tạo kết nối Redis.
  - `web/ApiException`, `web/ErrorBody`, `web/GlobalExceptionHandler`: format lỗi chung.
  - `web/HealthController`: `GET /api/health`, kiểm tra Oracle và Redis.
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

Cách cài đặt bên trong module (thư viện truy cập DB, định dạng response…) do người phụ trách module tự chọn. Các quy ước chung đã chốt:

- **API:** đường dẫn `/api/...`; trang quản trị dùng `/api/admin/...`. Request/response là JSON. Chưa có đăng nhập (F0), nên backend chỉ nghe trên `127.0.0.1` (`server.address`).
- **Lỗi:** luôn trả `{ "error": { "code", "message", "details" } }`. Trong service chỉ cần `throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm")` hoặc `ApiException.badRequest("price", "...")`; `GlobalExceptionHandler` đổi sang JSON. Lỗi `@Valid`, body sai, `DataAccessException`, lỗi bất ngờ đã được xử lý sẵn. Mã lỗi viết HOA_GẠCH_DƯỚI, thông báo tiếng Việt, không đưa nguyên văn lỗi Oracle/Redis ra client.
- **Module tự có handler riêng** (như `query`) thì đặt `@RestControllerAdvice(basePackages = "...modules.<module>")` và `@Order(Ordered.HIGHEST_PRECEDENCE)` để chạy trước handler chung.
- **SQL:** luôn bind variable (`NamedParameterJdbcTemplate` hoặc `JdbcTemplate` với `?`), không nối chuỗi.
- **Redis:** lệnh `FT.SEARCH` / `FT.AGGREGATE` luôn kèm `TIMEOUT 0` (Redis Stack mặc định trả kết quả dở sau 500 ms). Trường dùng để `GROUPBY`/`SORTBY` phải `SORTABLE`.
- **Test:** unit test không cần DB đặt tên `*CoreTest` (`./mvnw test -Dtest='*CoreTest'`); test chạy với DB thật đặt tên khác (ví dụ `*IT`) để không chạy mặc định.
- Module mẫu đã làm xong: `modules/query` (contract ở `AGENTS.md` của module). Hướng dẫn từng bước: `docs/HUONG_DAN_DEV.md`.
