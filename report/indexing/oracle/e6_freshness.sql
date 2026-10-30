-- E6 (Oracle): sua du lieu xong thi index (va tim kiem) thay luc nao? Demo hanh vi, khong do toc do.
--   Phan 1: B-tree - index thay doi cung transaction: phien minh thay ngay, phien khac chi thay sau COMMIT,
--           ROLLBACK thi bang va index cung quay ve.
--   Phan 2: Oracle Text CONTEXT mac dinh - da COMMIT nhung tim kiem chua thay ngay; do xem bao lau thi thay.
--   Phan 3: Oracle Text CONTEXT SYNC (ON COMMIT) - thay ngay sau COMMIT, nhung truoc COMMIT thi ca phien minh cung chua thay.
-- "Phien khac" duoc gia lap bang ham AUTONOMOUS_TRANSACTION (chay trong transaction rieng, chi thay du lieu da commit).
-- San pham thu nghiem id 900001-900003, xoa khi xong. Can chay 00_setup.sql truoc.
SET ECHO OFF FEEDBACK OFF TIMING OFF LINESIZE 200 PAGESIZE 200 SERVEROUTPUT ON VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE
COLUMN buoc FORMAT A62
COLUMN ket_qua FORMAT A40
COLUMN pnd_index_name FORMAT A16

PROMPT == Moi truong ==
SELECT banner_full FROM v$version;

-- Chuan bi: index SKU (nhu E1), xoa san pham thu nghiem va index text con sot
DELETE FROM idxlab_products WHERE product_id >= 900001;
COMMIT;
BEGIN
  FOR ix IN (SELECT index_name FROM user_indexes WHERE index_name = 'IX_E6_TXT') LOOP
    EXECUTE IMMEDIATE 'DROP INDEX ' || ix.index_name || ' FORCE';
  END LOOP;
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ix_idxlab_sku ON idxlab_products (sku)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;   -- index da ton tai
END;
/

-- "Phien khac": dem trong mot transaction rieng
CREATE OR REPLACE FUNCTION idxlab_e6_other_sku(p_sku VARCHAR2) RETURN NUMBER IS
  PRAGMA AUTONOMOUS_TRANSACTION;
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM idxlab_products WHERE sku = p_sku;
  COMMIT;
  RETURN n;
END;
/

PROMPT
PROMPT ================= PHAN 1: B-TREE (ix_idxlab_sku) =================
INSERT INTO idxlab_products (product_id, sku, name, category_id, price, status, in_stock)
VALUES (900001, 'LAB-0900001', 'Robot hut bui Ecovacs Zeta 1', 5, 7990000, 'ACTIVE', 1);
SELECT '1a. Vua INSERT, chua COMMIT - phien minh tim SKU' AS buoc,
       COUNT(*) || ' san pham' AS ket_qua FROM idxlab_products WHERE sku = 'LAB-0900001';
SELECT '1b. Vua INSERT, chua COMMIT - phien khac tim SKU' AS buoc,
       idxlab_e6_other_sku('LAB-0900001') || ' san pham' AS ket_qua FROM dual;
ROLLBACK;
SELECT '1c. Sau ROLLBACK - phien minh' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE sku = 'LAB-0900001';
INSERT INTO idxlab_products (product_id, sku, name, category_id, price, status, in_stock)
VALUES (900001, 'LAB-0900001', 'Robot hut bui Ecovacs Zeta 1', 5, 7990000, 'ACTIVE', 1);
COMMIT;
SELECT '1d. INSERT lai va COMMIT - phien khac' AS buoc,
       idxlab_e6_other_sku('LAB-0900001') || ' san pham' AS ket_qua FROM dual;
PROMPT Ke hoach cua cau tim SKU (de chac chan dang tim qua index):
EXPLAIN PLAN FOR SELECT COUNT(*) FROM idxlab_products WHERE sku = 'LAB-0900001';
SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, NULL, 'BASIC'));

PROMPT
PROMPT ================= PHAN 2: ORACLE TEXT CONTEXT, CAU HINH MAC DINH =================
SET TIMING ON
CREATE INDEX ix_e6_txt ON idxlab_products (name) INDEXTYPE IS CTXSYS.CONTEXT;
SET TIMING OFF
INSERT INTO idxlab_products (product_id, sku, name, category_id, price, status, in_stock)
VALUES (900002, 'LAB-0900002', 'Robot lau nha Ecovacs Omega 2', 5, 12990000, 'ACTIVE', 1);
COMMIT;
SELECT '2a. Da COMMIT - bang co san pham (LIKE)' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE UPPER(name) LIKE '%OMEGA%';
SELECT '2b. Da COMMIT - tim bang Oracle Text CONTAINS(omega)' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE CONTAINS(name, 'omega') > 0;
PROMPT 2c. Cau hinh dong bo cua index (CTX_USER_INDEXES):
DECLARE
  v_type VARCHAR2(100);
  v_int  VARCHAR2(400);
BEGIN
  EXECUTE IMMEDIATE 'SELECT idx_sync_type, idx_sync_interval FROM ctx_user_indexes WHERE idx_name = ''IX_E6_TXT'''
    INTO v_type, v_int;
  DBMS_OUTPUT.PUT_LINE('    idx_sync_type = ' || NVL(v_type, '(trong)') || ', idx_sync_interval = ' || NVL(v_int, '(trong)'));
EXCEPTION
  WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('    Khong doc duoc CTX_USER_INDEXES: ' || SQLERRM);
END;
/
PROMPT 2d. Hang doi cho dong bo (CTX_USER_PENDING):
SELECT pnd_index_name, pnd_rowid, pnd_timestamp FROM ctx_user_pending;
PROMPT 2e. Khong goi dong bo tay (user SHOP khong co quyen CTX_DDL). Tim lai moi giay, toi da 120 giay:
DECLARE
  t0 TIMESTAMP := SYSTIMESTAMP;
  d  INTERVAL DAY TO SECOND;
  n  NUMBER;
BEGIN
  FOR i IN 1 .. 120 LOOP
    SELECT COUNT(*) INTO n FROM idxlab_products WHERE CONTAINS(name, 'omega') > 0;
    EXIT WHEN n > 0;
    DBMS_SESSION.SLEEP(1);
  END LOOP;
  d := SYSTIMESTAMP - t0;
  IF n > 0 THEN
    DBMS_OUTPUT.PUT_LINE('    CONTAINS(omega) thay san pham sau khoang '
      || TO_CHAR(EXTRACT(MINUTE FROM d) * 60 + EXTRACT(SECOND FROM d), 'FM9990.0')
      || ' giay ke tu luc bat dau tim (Oracle tu dong bo o che do nen)');
  ELSE
    DBMS_OUTPUT.PUT_LINE('    Sau 120 giay CONTAINS(omega) van ra 0: index khong tu dong bo, can CTX_DDL.SYNC_INDEX (quyen CTXAPP)');
  END IF;
END;
/
SELECT '2f. Hang doi sau cung' AS buoc, COUNT(*) || ' dong' AS ket_qua FROM ctx_user_pending;

PROMPT
PROMPT ================= PHAN 3: ORACLE TEXT CONTEXT SYNC (ON COMMIT) =================
DROP INDEX ix_e6_txt FORCE;
SET TIMING ON
CREATE INDEX ix_e6_txt ON idxlab_products (name) INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS ('SYNC (ON COMMIT)');
SET TIMING OFF
INSERT INTO idxlab_products (product_id, sku, name, category_id, price, status, in_stock)
VALUES (900003, 'LAB-0900003', 'May hut am Ecovacs Sigma 3', 5, 3490000, 'ACTIVE', 1);
SELECT '3a. Chua COMMIT - phien minh tim SKU (B-tree)' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE sku = 'LAB-0900003';
SELECT '3b. Chua COMMIT - phien minh CONTAINS(sigma)' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE CONTAINS(name, 'sigma') > 0;
COMMIT;
SELECT '3c. Sau COMMIT - CONTAINS(sigma)' AS buoc, COUNT(*) || ' san pham' AS ket_qua
FROM idxlab_products WHERE CONTAINS(name, 'sigma') > 0;

PROMPT
PROMPT Don dep: xoa san pham thu nghiem, index ix_e6_txt va ham idxlab_e6_other_sku.
DELETE FROM idxlab_products WHERE product_id >= 900001;
COMMIT;
DROP INDEX ix_e6_txt FORCE;
DROP FUNCTION idxlab_e6_other_sku;
PROMPT Xong E6 (Oracle).
