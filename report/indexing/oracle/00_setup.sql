-- Tao bang thi nghiem IDXLAB_PRODUCTS (200.000 san pham gia lap) cho chu de Indexing.
-- Bang rieng, KHONG dung bang PRODUCTS that, KHONG anh huong module khac hay so do cua TV1.
-- Du lieu tinh tu so thu tu dong (khong random) nen moi lan chay ra giong het nhau.
-- Ten san pham khong dau de tranh loi ma hoa khi chay qua sqlplus trong container.
SET ECHO OFF FEEDBACK ON TIMING ON LINESIZE 200 PAGESIZE 100
WHENEVER SQLERROR EXIT FAILURE

PROMPT == Xoa bang lab cu (neu co) ==
BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE idxlab_products PURGE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

PROMPT == Tao 200.000 san pham ==
CREATE TABLE idxlab_products AS
WITH kinds AS (
  SELECT column_value AS word, ROWNUM AS i
  FROM TABLE(sys.odcivarchar2list('Tai nghe', 'Dien thoai', 'Laptop', 'Chuot', 'Ban phim', 'Man hinh',
    'Loa', 'Sac du phong', 'Dong ho', 'May anh', 'Tivi', 'Noi chien', 'Quat', 'May loc khi', 'Balo'))
), brands AS (
  SELECT column_value AS word, ROWNUM AS i
  FROM TABLE(sys.odcivarchar2list('Sony', 'Samsung', 'Apple', 'Xiaomi', 'Asus', 'Dell',
    'Logitech', 'Anker', 'JBL', 'LG', 'Philips', 'Panasonic'))
), tags AS (
  SELECT column_value AS word, ROWNUM AS i
  FROM TABLE(sys.odcivarchar2list('Pro', 'Mini', 'Max', 'Lite', 'Plus', 'Ultra', 'Air', 'Gaming', 'Slim', 'Neo'))
)
SELECT g.n                                                        AS product_id,
       'LAB-' || LPAD(g.n, 7, '0')                                AS sku,
       k.word || ' ' || b.word || ' ' || t.word || ' ' || MOD(g.n * 37, 997) AS name,
       MOD(g.n * 7, 50) + 1                                       AS category_id,
       (MOD(g.n * 7919, 49951) + 50) * 1000                       AS price,
       CASE WHEN MOD(g.n, 20) = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END AS status,
       CASE WHEN MOD(g.n, 7) = 0 THEN 0 ELSE 1 END                AS in_stock
FROM (SELECT LEVEL AS n FROM dual CONNECT BY LEVEL <= 200000) g
JOIN kinds  k ON k.i = MOD(g.n, 15) + 1
JOIN brands b ON b.i = MOD(TRUNC(g.n / 15), 12) + 1
JOIN tags   t ON t.i = MOD(TRUNC(g.n / 180), 10) + 1;

PROMPT == Thu thap thong ke cho optimizer ==
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'IDXLAB_PRODUCTS')

SET TIMING OFF
PROMPT == Kiem tra ==
SELECT COUNT(*) AS so_dong FROM idxlab_products;

COLUMN name FORMAT A40
SELECT product_id, sku, name, category_id, price, status, in_stock
FROM idxlab_products WHERE product_id IN (1, 2, 123456, 199999);

PROMPT == Dung luong bang ==
COLUMN segment_name FORMAT A30
SELECT segment_name, segment_type, ROUND(bytes / 1024 / 1024, 1) AS mb, blocks
FROM user_segments WHERE segment_name = 'IDXLAB_PRODUCTS';
