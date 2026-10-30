-- E4 (Oracle): index lam GHI cham di bao nhieu? Cung mot luong ghi tren 5 cau hinh index tang dan:
--   C0: chi khoa chinh (product_id)                                   -> moc so sanh
--   C1: C0 + B-tree unique (sku)                                      -> nhu E1
--   C2: C1 + (category_id, status, price) + (price)                   -> them 2 index co cot gia (E3)
--   C3: C2 + Oracle Text CTXCAT tren name (tu cap nhat trong transaction)
--   C4: C2 + Oracle Text CONTEXT tren name, SYNC (ON COMMIT) (dong bo khi commit)
-- Moi cau hinh: THEM 10.000 san pham (INSERT tung dong) va SUA GIA 10.000 san pham (UPDATE tung dong), moi viec 1 COMMIT.
-- Dung bang rieng IDXLAB_WRITE (tao lai cho moi cau hinh), khong dung IDXLAB_PRODUCTS.
-- Can chay 00_setup.sql truoc. Chay lai bao nhieu lan cung duoc.
SET ECHO OFF FEEDBACK OFF TIMING OFF LINESIZE 200 PAGESIZE 200 SERVEROUTPUT OFF VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE

PROMPT == Moi truong ==
SELECT banner_full FROM v$version;
SELECT COUNT(*) AS so_dong_bang_lab FROM idxlab_products;

PROMPT
PROMPT ================= C0: CHI KHOA CHINH =================
@@lib/e4_reset_table.sql
@@lib/e4_index_info.sql
@@lib/e4_measure.sql C0_KHOA_CHINH

PROMPT
PROMPT ================= C1: + SKU =================
@@lib/e4_reset_table.sql
CREATE UNIQUE INDEX ix_e4_sku ON idxlab_write (sku);
@@lib/e4_index_info.sql
@@lib/e4_measure.sql C1_SKU

PROMPT
PROMPT ================= C2: + (category_id, status, price) + (price) =================
@@lib/e4_reset_table.sql
CREATE UNIQUE INDEX ix_e4_sku ON idxlab_write (sku);
CREATE INDEX ix_e4_cat_status_price ON idxlab_write (category_id, status, price);
CREATE INDEX ix_e4_price ON idxlab_write (price);
@@lib/e4_index_info.sql
@@lib/e4_measure.sql C2_3_BTREE

PROMPT
PROMPT ================= C3: C2 + ORACLE TEXT CTXCAT =================
@@lib/e4_reset_table.sql
CREATE UNIQUE INDEX ix_e4_sku ON idxlab_write (sku);
CREATE INDEX ix_e4_cat_status_price ON idxlab_write (category_id, status, price);
CREATE INDEX ix_e4_price ON idxlab_write (price);
CREATE INDEX ix_e4_name_cat ON idxlab_write (name) INDEXTYPE IS CTXSYS.CTXCAT;
@@lib/e4_index_info.sql
@@lib/e4_measure.sql C3_CTXCAT

PROMPT
PROMPT ================= C4: C2 + ORACLE TEXT CONTEXT, SYNC (ON COMMIT) =================
@@lib/e4_reset_table.sql
CREATE UNIQUE INDEX ix_e4_sku ON idxlab_write (sku);
CREATE INDEX ix_e4_cat_status_price ON idxlab_write (category_id, status, price);
CREATE INDEX ix_e4_price ON idxlab_write (price);
CREATE INDEX ix_e4_name_txt ON idxlab_write (name) INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS ('SYNC (ON COMMIT)');
@@lib/e4_index_info.sql
@@lib/e4_measure.sql C4_CONTEXT

PROMPT
PROMPT Don dep: xoa bang IDXLAB_WRITE.
DROP TABLE idxlab_write PURGE;
PROMPT Xong E4 (Oracle).
