-- P0: chỉ đọc metadata của schema SHOP. Chạy trong SQLcl/SQL*Plus sau khi đã CONNECT.
SET DEFINE OFF
SET PAGESIZE 500
SET LINESIZE 220
SET TRIMSPOOL ON
COLUMN table_name FORMAT A24
COLUMN constraint_name FORMAT A35
COLUMN columns FORMAT A58

PROMPT === Kết nối và phiên bản ===
SELECT USER AS schema_user,
       SYS_CONTEXT('USERENV', 'CON_NAME') AS pdb,
       SYS_CONTEXT('USERENV', 'SERVICE_NAME') AS service_name,
       SYS_CONTEXT('USERENV', 'SID') AS session_id
FROM dual;
SELECT banner_full FROM v$version;

PROMPT === Flyway ===
SELECT "installed_rank", "version", "description", "type", "success"
FROM "flyway_schema_history"
ORDER BY "installed_rank";

PROMPT === Cột nghiệp vụ V1/V2 ===
SELECT table_name, column_id, column_name, data_type, nullable
FROM user_tab_columns
WHERE table_name IN ('USERS', 'CATEGORIES', 'PRODUCTS', 'PRODUCT_INVENTORY',
                     'CARTS', 'CART_ITEMS', 'ORDERS', 'ORDER_ITEMS')
ORDER BY table_name, column_id;

PROMPT === Constraint và thời điểm kiểm tra ban đầu ===
SELECT c.table_name, c.constraint_name, c.constraint_type,
       c.status, c.validated, c.deferrable, c.deferred,
       LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS columns
FROM user_constraints c
LEFT JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name
WHERE c.table_name IN ('USERS', 'CATEGORIES', 'PRODUCTS', 'PRODUCT_INVENTORY',
                       'CARTS', 'CART_ITEMS', 'ORDERS', 'ORDER_ITEMS')
GROUP BY c.table_name, c.constraint_name, c.constraint_type,
         c.status, c.validated, c.deferrable, c.deferred
ORDER BY c.table_name, c.constraint_type, c.constraint_name;

PROMPT === Index nghiệp vụ ===
SELECT i.table_name, i.index_name, i.uniqueness,
       LISTAGG(ic.column_name, ', ') WITHIN GROUP (ORDER BY ic.column_position) AS columns
FROM user_indexes i
JOIN user_ind_columns ic ON ic.index_name = i.index_name
WHERE i.table_name IN ('USERS', 'CATEGORIES', 'PRODUCTS', 'PRODUCT_INVENTORY',
                       'CARTS', 'CART_ITEMS', 'ORDERS', 'ORDER_ITEMS')
GROUP BY i.table_name, i.index_name, i.uniqueness
ORDER BY i.table_name, i.index_name;

PROMPT === Số session SHOP theo trạng thái (không chứa thông tin đăng nhập) ===
SELECT status, COUNT(*) AS session_count
FROM v$session
WHERE username = 'SHOP'
GROUP BY status
ORDER BY status;

PROMPT === Ghi thủ công cấu hình Auto-commit và mọi SET CONSTRAINTS của client/session ===
PROMPT SQLPlus/SQLcl: SHOW AUTOCOMMIT; DBeaver: kiểm tra nút Auto-commit và transaction mode.
PROMPT Nếu client/script đã gọi SET CONSTRAINTS, ghi lệnh và thời điểm trước khi thử A2/D1.
