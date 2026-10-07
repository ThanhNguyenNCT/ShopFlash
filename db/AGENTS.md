# Database

Thư mục `db/` chỉ chứa tài liệu thiết kế dữ liệu. Database runtime assets nằm cùng backend trong `backend/src/main/resources/db/`.

## Oracle

Flyway của Spring Boot quản lý Oracle. Migration nằm tại `backend/src/main/resources/db/migration/` với tên `V<number>__description.sql`. Mỗi module tự viết migration mới và không sửa version đã áp dụng.

## Redis

Mô tả key Redis nằm trong `DATABASE_DESIGN.md`. Foundation index được khởi tạo từ `backend/src/main/resources/db/redis/init.lua` qua `redis-cli --eval`.
