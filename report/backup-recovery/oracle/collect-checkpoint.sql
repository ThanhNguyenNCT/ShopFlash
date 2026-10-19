-- Emits a stable, machine-readable checkpoint for the current SHOP schema.
-- It contains every application table count and up to three catalog samples.

WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

SET ECHO OFF
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0
SET LINESIZE 32767
SET TRIMSPOOL ON
SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
  l_count NUMBER;
BEGIN
  FOR table_row IN (
    SELECT table_name
    FROM user_tables
    WHERE UPPER(table_name) <> 'FLYWAY_SCHEMA_HISTORY'
    ORDER BY table_name
  ) LOOP
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM "' || REPLACE(table_row.table_name, '"', '""') || '"'
      INTO l_count;
    DBMS_OUTPUT.PUT_LINE('TABLE_COUNT|' || table_row.table_name || '|' || TO_CHAR(l_count));
  END LOOP;
END;
/

SELECT 'PRODUCT_SAMPLE|' || p.product_id || '|' || p.sku || '|' ||
       TO_CHAR(p.base_price, 'FM999999999999990D00', 'NLS_NUMERIC_CHARACTERS=''.,''') || '|' ||
       NVL(TO_CHAR(i.on_hand_quantity), 'NULL') || '|' ||
       NVL(TO_CHAR(i.reserved_flash_quantity), 'NULL')
FROM products p
LEFT JOIN product_inventory i ON i.product_id = p.product_id
ORDER BY p.product_id
FETCH FIRST 3 ROWS ONLY;

EXIT SUCCESS
