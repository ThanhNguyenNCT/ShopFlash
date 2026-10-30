-- E2 (Oracle): tim san pham co TU KHOA trong ten (F3).
--   Cach 1: LIKE '%tu%'                  -> khong dung duoc B-tree, quet toan bang
--   Cach 2: Oracle Text CONTEXT, CONTAINS -> inverted index, co diem lien quan
--   Cach 3: Oracle Text CTXCAT, CATSEARCH -> inverted index cho danh muc, khong co diem
-- Tu khoa: "615" (hiem, 200 san pham), "sony pro" (2 tu, 1.679), "pro" (pho bien, 20.159).
-- Can chay 00_setup.sql truoc. Chay lai bao nhieu lan cung duoc.
SET ECHO OFF FEEDBACK OFF TIMING OFF LINESIZE 200 PAGESIZE 200 SERVEROUTPUT OFF VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE
COLUMN name FORMAT A40
COLUMN index_name FORMAT A22
COLUMN plan_table_output FORMAT A150

PROMPT == Moi truong ==
SELECT banner_full FROM v$version;
SELECT COUNT(*) AS so_dong_bang_lab FROM idxlab_products;

-- Bat dau tu trang thai chua co index tim kiem toan van
BEGIN
  FOR ix IN (SELECT index_name FROM user_indexes
             WHERE index_name IN ('IX_IDXLAB_NAME_TXT', 'IX_IDXLAB_NAME_CAT')) LOOP
    EXECUTE IMMEDIATE 'DROP INDEX ' || ix.index_name || ' FORCE';
  END LOOP;
END;
/

VARIABLE p1 VARCHAR2(30)
VARIABLE p2 VARCHAR2(30)
VARIABLE q  VARCHAR2(100)
EXEC :p1 := '%SONY%'; :p2 := '%PRO%'; :q := '{sony} AND {pro}'

PROMPT
PROMPT ================= CACH 1: LIKE '%tu%' (KHONG CO INDEX PHU HOP) =================
PROMPT Vi du "sony pro": 5 san pham dau tien
SELECT /*+ gather_plan_statistics */ product_id, name FROM idxlab_products
WHERE UPPER(name) LIKE :p1 AND UPPER(name) LIKE :p2 FETCH FIRST 5 ROWS ONLY;
PROMPT Ke hoach thuc thi cau dem (COUNT) - cau dem phai doc het bang:
SELECT /*+ gather_plan_statistics */ COUNT(*) AS so_ket_qua FROM idxlab_products
WHERE UPPER(name) LIKE :p1 AND UPPER(name) LIKE :p2;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));
PROMPT Do thoi gian (moi lan tim = lay 24 san pham dau + dem tong so):
@@lib/e2_measure.sql LIKE HIEM
@@lib/e2_measure.sql LIKE HAI_TU
@@lib/e2_measure.sql LIKE PHO_BIEN

PROMPT
PROMPT ================= CACH 2: ORACLE TEXT CONTEXT =================
SET TIMING ON
CREATE INDEX ix_idxlab_name_txt ON idxlab_products (name) INDEXTYPE IS CTXSYS.CONTEXT;
SET TIMING OFF
PROMPT Cac segment Oracle Text tao ra (bang phu DR$...$I la tu dien + posting list):
@@lib/text_index_size.sql IX_IDXLAB_NAME_TXT
PROMPT Mot vai tu trong tu dien ($I) va so san pham chua tu do:
SELECT token_text, SUM(token_count) AS so_san_pham
FROM dr$ix_idxlab_name_txt$i
WHERE token_type = 0 AND token_text IN ('SONY', 'PRO', '615', 'TAI', 'NGHE')
GROUP BY token_text ORDER BY so_san_pham DESC;

PROMPT Vi du "sony pro": 5 san pham lien quan nhat
SELECT /*+ gather_plan_statistics */ product_id, name, SCORE(1) AS diem FROM idxlab_products
WHERE CONTAINS(name, :q, 1) > 0 ORDER BY SCORE(1) DESC FETCH FIRST 5 ROWS ONLY;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));
PROMPT Do thoi gian:
@@lib/e2_measure.sql TEXT HIEM
@@lib/e2_measure.sql TEXT HAI_TU
@@lib/e2_measure.sql TEXT PHO_BIEN

PROMPT
PROMPT ================= CACH 3: ORACLE TEXT CTXCAT =================
SET TIMING ON
CREATE INDEX ix_idxlab_name_cat ON idxlab_products (name) INDEXTYPE IS CTXSYS.CTXCAT;
SET TIMING OFF
PROMPT Cac segment CTXCAT tao ra:
@@lib/text_index_size.sql IX_IDXLAB_NAME_CAT
EXEC :q := 'sony pro'
PROMPT Vi du "sony pro": 5 san pham (CATSEARCH khong co diem lien quan)
SELECT /*+ gather_plan_statistics */ product_id, name FROM idxlab_products
WHERE CATSEARCH(name, :q, NULL) > 0 FETCH FIRST 5 ROWS ONLY;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));
PROMPT Do thoi gian:
@@lib/e2_measure.sql CAT HIEM
@@lib/e2_measure.sql CAT HAI_TU
@@lib/e2_measure.sql CAT PHO_BIEN

PROMPT
PROMPT Kiem tra: so ket qua cua ca 3 cach phai giong nhau (615 = 200, sony pro = 1679, pro = 20159).
PROMPT Don dep: xoa 2 index Oracle Text (bang lab van giu).
BEGIN
  FOR ix IN (SELECT index_name FROM user_indexes
             WHERE index_name IN ('IX_IDXLAB_NAME_TXT', 'IX_IDXLAB_NAME_CAT')) LOOP
    EXECUTE IMMEDIATE 'DROP INDEX ' || ix.index_name || ' FORCE';
  END LOOP;
END;
/
PROMPT Xong E2 (Oracle).
