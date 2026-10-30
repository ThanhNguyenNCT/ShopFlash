-- Dung boi e3_filter.sql: in ke hoach thuc thi ma optimizer TU CHON cho 3 cau loc (trang dau) va cau dem cua A.
-- Can cac bien :c, :lo, :hi da gan san.
SET FEEDBACK OFF
PROMPT --- A (trang dau): danh muc 5, gia 1-5 trieu, dang ban, xep gia tang
SELECT /*+ gather_plan_statistics */ product_id FROM idxlab_products
WHERE category_id = :c AND price BETWEEN :lo AND :hi AND status = 'ACTIVE' ORDER BY price FETCH FIRST 24 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'IOSTATS LAST -NOTE'))
WHERE plan_table_output NOT LIKE 'SQL_ID%' AND plan_table_output NOT LIKE 'Plan hash%';
PROMPT --- A (dem tong so)
SELECT /*+ gather_plan_statistics */ COUNT(*) FROM idxlab_products
WHERE category_id = :c AND price BETWEEN :lo AND :hi AND status = 'ACTIVE';
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'IOSTATS LAST -NOTE'))
WHERE plan_table_output NOT LIKE 'SQL_ID%' AND plan_table_output NOT LIKE 'Plan hash%';
PROMPT --- B (trang dau): gia 1-5 trieu, dang ban, moi danh muc
SELECT /*+ gather_plan_statistics */ product_id FROM idxlab_products
WHERE price BETWEEN :lo AND :hi AND status = 'ACTIVE' ORDER BY price FETCH FIRST 24 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'IOSTATS LAST -NOTE'))
WHERE plan_table_output NOT LIKE 'SQL_ID%' AND plan_table_output NOT LIKE 'Plan hash%';
PROMPT --- C (trang dau): danh muc 5, dang ban, moi muc gia
SELECT /*+ gather_plan_statistics */ product_id FROM idxlab_products
WHERE category_id = :c AND status = 'ACTIVE' ORDER BY price FETCH FIRST 24 ROWS ONLY;
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'IOSTATS LAST -NOTE'))
WHERE plan_table_output NOT LIKE 'SQL_ID%' AND plan_table_output NOT LIKE 'Plan hash%';
