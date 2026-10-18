-- Xóa toàn bộ dữ liệu benchmark do 01_generate.sql sinh ra rồi commit.
whenever sqlerror exit sql.sqlcode rollback
set feedback on timing on
@@98_delete_generated.sql
COMMIT;
