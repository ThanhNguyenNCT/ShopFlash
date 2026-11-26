-- Chỉ đọc: lịch sử Flyway của schema SHOP trên DB demo (lỗi nếu chưa migrate).
-- Flyway tạo bảng tên chữ thường có dấu nháy kép.
SET PAGESIZE 100 LINESIZE 120 FEEDBACK OFF
COLUMN version FORMAT A8
COLUMN description FORMAT A45
SELECT "version" AS version, "description" AS description, "success" AS success
FROM "flyway_schema_history" ORDER BY "installed_rank";
