-- E1 (Oracle): tim san pham theo SKU khi CHUA co index va khi CO index B-tree unique.
-- Can chay 00_setup.sql truoc. Chay lai bao nhieu lan cung duoc (tu xoa index cu).
SET ECHO OFF FEEDBACK OFF TIMING OFF LINESIZE 200 PAGESIZE 200 SERVEROUTPUT OFF VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE
COLUMN name FORMAT A40
COLUMN segment_name FORMAT A20
COLUMN index_name FORMAT A20
COLUMN plan_table_output FORMAT A150

PROMPT == Moi truong ==
SELECT banner_full FROM v$version;

BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX ix_idxlab_sku';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1418 THEN RAISE; END IF;
END;
/

VARIABLE sku VARCHAR2(20)
EXEC :sku := 'LAB-0123456'

PROMPT
PROMPT ================= BUOC 1: CHUA CO INDEX =================
PROMPT Truy van: tim san pham co sku = 'LAB-0123456'
SELECT /*+ gather_plan_statistics */ product_id, sku, name, price
FROM idxlab_products WHERE sku = :sku;

PROMPT Ke hoach thuc thi:
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));

PROMPT Do thoi gian (1 lan khoi dong + 5 lan do, moi lan tim 200 SKU):
@@lib/e1_measure.sql KHONG_INDEX

PROMPT
PROMPT ================= BUOC 2: TAO INDEX B-TREE UNIQUE =================
SET TIMING ON
CREATE UNIQUE INDEX ix_idxlab_sku ON idxlab_products (sku);
SET TIMING OFF

PROMPT Hinh dang cay index (BLEVEL = so tang phia tren la; LEAF_BLOCKS = so block la; CLUSTERING_FACTOR):
SELECT index_name, blevel, leaf_blocks, distinct_keys, clustering_factor
FROM user_indexes WHERE index_name = 'IX_IDXLAB_SKU';

PROMPT Dung luong bang so voi index:
SELECT segment_name, segment_type, ROUND(bytes / 1024 / 1024, 1) AS mb, blocks
FROM user_segments WHERE segment_name IN ('IDXLAB_PRODUCTS', 'IX_IDXLAB_SKU');

PROMPT
PROMPT ================= BUOC 3: DA CO INDEX =================
SELECT /*+ gather_plan_statistics */ product_id, sku, name, price
FROM idxlab_products WHERE sku = :sku;

PROMPT Ke hoach thuc thi:
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));

PROMPT Do thoi gian (1 lan khoi dong + 5 lan do, moi lan tim 200 SKU):
@@lib/e1_measure.sql CO_INDEX

PROMPT
PROMPT Xong E1 (Oracle). Index ix_idxlab_sku van con; chay lai file nay se xoa va lam lai tu dau.
