# Database

Thư mục `db/` chỉ chứa tài liệu thiết kế dữ liệu. Database runtime assets nằm cùng backend trong `backend/src/main/resources/db/` (xem bảng trong `README.md`).

## Oracle

- User `SHOP`, tablespace và quyền do `backend/src/main/resources/db/oracle-init/01_create_shop_schema.sh` tạo khi container khởi động, không thuộc Flyway.
- Flyway chạy bằng user `SHOP`. Migration nằm tại `backend/src/main/resources/db/migration/`: `V1__schema.sql` là nền chung, module thêm `V<number>__<module>_<mô tả>.sql`. Mỗi module tự viết migration mới và không sửa version đã có trên `main`.
- Dữ liệu mẫu nằm ở `backend/src/main/resources/db/dev-seed/`: `R__00_core_seed.sql` rồi `R__10_<module>_seed.sql`, chỉ chạy ở profile `dev`. Không đặt dữ liệu mẫu trong `migration/`.
- Ràng buộc trên một dòng thì đặt bằng CHECK. Khóa ngoại nào cũng phải có index.

## Redis

Mô tả key Redis nằm trong `DATABASE_DESIGN.md`. Foundation index được khởi tạo từ `backend/src/main/resources/db/redis/init.lua` qua `redis-cli --eval`. Không gom nhóm (`GROUPBY`) trên trường JSON nhiều giá trị (`$.items[*]`): RediSearch chỉ lấy giá trị đầu tiên.
