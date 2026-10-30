-- E3 (Oracle): loc nhieu dieu kien + xep theo gia + phan trang (F4), voi 4 cau hinh index:
--   1. KHONG_INDEX                                         -> quet toan bang
--   2. INDEX_DON:   (category_id) va (price) rieng re      -> nhu cach thuong thay
--   3. GHEP_DUNG:   (category_id, status, price)           -> dung quy tac bai 2: cot so sanh bang truoc, cot khoang/sap xep sau
--   4. GHEP_SAI:    (price, category_id, status)           -> cung cac cot nhung sai thu tu
-- Ba cau hoi: A (danh muc 5 + gia 1-5 trieu, 321 ket qua), B (chi gia 1-5 trieu, 15.218), C (chi danh muc 5, 4.000).
-- Optimizer tu chon cach chay (khong ep hint). Can chay 00_setup.sql truoc. Chay lai bao nhieu lan cung duoc.
SET ECHO OFF FEEDBACK OFF TIMING OFF LINESIZE 200 PAGESIZE 200 SERVEROUTPUT OFF VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE
COLUMN plan_table_output FORMAT A150

PROMPT == Moi truong ==
SELECT banner_full FROM v$version;
SELECT COUNT(*) AS so_dong_bang_lab FROM idxlab_products;
@@lib/e3_drop_indexes.sql

VARIABLE c  NUMBER
VARIABLE lo NUMBER
VARIABLE hi NUMBER
EXEC :c := 5; :lo := 1000000; :hi := 5000000

PROMPT
PROMPT ================= CAU HINH 1: KHONG INDEX =================
@@lib/e3_plans.sql
PROMPT Do thoi gian (moi lan tim = trang dau 24 san pham + dem tong):
@@lib/e3_measure.sql KHONG_INDEX A
@@lib/e3_measure.sql KHONG_INDEX B
@@lib/e3_measure.sql KHONG_INDEX C

PROMPT
PROMPT ================= CAU HINH 2: HAI INDEX DON (category_id) VA (price) =================
SET TIMING ON
CREATE INDEX ix_e3_cat ON idxlab_products (category_id);
CREATE INDEX ix_e3_price ON idxlab_products (price);
SET TIMING OFF
@@lib/e3_index_info.sql
@@lib/e3_plans.sql
PROMPT Do thoi gian:
@@lib/e3_measure.sql INDEX_DON A
@@lib/e3_measure.sql INDEX_DON B
@@lib/e3_measure.sql INDEX_DON C
@@lib/e3_drop_indexes.sql

PROMPT
PROMPT ================= CAU HINH 3: INDEX GHEP DUNG THU TU (category_id, status, price) =================
SET TIMING ON
CREATE INDEX ix_e3_cat_status_price ON idxlab_products (category_id, status, price);
SET TIMING OFF
@@lib/e3_index_info.sql
@@lib/e3_plans.sql
PROMPT Do thoi gian:
@@lib/e3_measure.sql GHEP_DUNG A
@@lib/e3_measure.sql GHEP_DUNG B
@@lib/e3_measure.sql GHEP_DUNG C
@@lib/e3_drop_indexes.sql

PROMPT
PROMPT ================= CAU HINH 4: INDEX GHEP SAI THU TU (price, category_id, status) =================
SET TIMING ON
CREATE INDEX ix_e3_price_cat_status ON idxlab_products (price, category_id, status);
SET TIMING OFF
@@lib/e3_index_info.sql
@@lib/e3_plans.sql
PROMPT Do thoi gian:
@@lib/e3_measure.sql GHEP_SAI A
@@lib/e3_measure.sql GHEP_SAI B
@@lib/e3_measure.sql GHEP_SAI C
@@lib/e3_drop_indexes.sql

PROMPT
PROMPT Kiem tra: so ket qua moi cau hinh phai giong nhau (A = 321, B = 15218, C = 4000).
PROMPT Xong E3 (Oracle). Da xoa cac index E3; bang lab van giu.
