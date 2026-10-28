-- C1: chỉ đọc metadata của quan hệ Order–Order Item–Product/Inventory.
SET PAGESIZE 200
SET LINESIZE 220
COLUMN table_name FORMAT A24
COLUMN constraint_name FORMAT A35
COLUMN columns FORMAT A55
COLUMN search_condition_vc FORMAT A80
SELECT c.table_name, c.constraint_name, c.constraint_type,
       c.status, c.validated, c.deferrable, c.deferred,
       LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS columns,
       c.search_condition_vc
FROM user_constraints c
LEFT JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name
WHERE c.table_name IN ('ORDERS', 'ORDER_ITEMS', 'PRODUCTS', 'PRODUCT_INVENTORY')
  AND c.constraint_name NOT LIKE 'SYS\_%' ESCAPE '\'
GROUP BY c.table_name, c.constraint_name, c.constraint_type,
         c.status, c.validated, c.deferrable, c.deferred, c.search_condition_vc
ORDER BY c.table_name, c.constraint_type, c.constraint_name;
