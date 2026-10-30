-- Dung boi e3_filter.sql: do thoi gian mot cau loc F4.
-- &1 = nhan cau hinh index (in ra), &2 = cau hoi: A | B | C
--   A: danh muc 5, gia 1-5 trieu, dang ban     (321 ket qua)
--   B: gia 1-5 trieu, dang ban, moi danh muc   (15.218)
--   C: danh muc 5, dang ban, moi muc gia       (4.000)
-- Moi lan tim = lay trang dau 24 san pham xep gia tang + dem tong so. 1 lan khoi dong + 5 lan do, moi lan 20 lan tim.
SET SERVEROUTPUT ON VERIFY OFF
DECLARE
  TYPE t_ms  IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  TYPE t_ids IS TABLE OF NUMBER;
  c_lookups CONSTANT PLS_INTEGER := 20;
  v_q  VARCHAR2(1) := '&2';
  page_sql VARCHAR2(500);
  cnt_sql  VARCHAR2(500);
  ids t_ids;
  cnt NUMBER;
  ms  t_ms;
  t0  TIMESTAMP;
  d   INTERVAL DAY TO SECOND;
  s   NUMBER := 0;
  ss  NUMBER := 0;
  avg_ms NUMBER;
  sd  NUMBER;

  PROCEDURE search_once IS
  BEGIN
    IF v_q = 'A' THEN
      EXECUTE IMMEDIATE page_sql BULK COLLECT INTO ids USING 5, 1000000, 5000000;
      EXECUTE IMMEDIATE cnt_sql INTO cnt USING 5, 1000000, 5000000;
    ELSIF v_q = 'B' THEN
      EXECUTE IMMEDIATE page_sql BULK COLLECT INTO ids USING 1000000, 5000000;
      EXECUTE IMMEDIATE cnt_sql INTO cnt USING 1000000, 5000000;
    ELSE
      EXECUTE IMMEDIATE page_sql BULK COLLECT INTO ids USING 5;
      EXECUTE IMMEDIATE cnt_sql INTO cnt USING 5;
    END IF;
  END;
BEGIN
  IF v_q = 'A' THEN
    page_sql := 'SELECT product_id FROM idxlab_products WHERE category_id = :c AND price BETWEEN :lo AND :hi '
             || 'AND status = ''ACTIVE'' ORDER BY price FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE category_id = :c AND price BETWEEN :lo AND :hi '
             || 'AND status = ''ACTIVE''';
  ELSIF v_q = 'B' THEN
    page_sql := 'SELECT product_id FROM idxlab_products WHERE price BETWEEN :lo AND :hi '
             || 'AND status = ''ACTIVE'' ORDER BY price FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE price BETWEEN :lo AND :hi AND status = ''ACTIVE''';
  ELSIF v_q = 'C' THEN
    page_sql := 'SELECT product_id FROM idxlab_products WHERE category_id = :c '
             || 'AND status = ''ACTIVE'' ORDER BY price FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE category_id = :c AND status = ''ACTIVE''';
  ELSE
    RAISE_APPLICATION_ERROR(-20001, 'Cau hoi khong hop le: ' || v_q);
  END IF;

  FOR r IN 0 .. 5 LOOP
    t0 := SYSTIMESTAMP;
    FOR i IN 1 .. c_lookups LOOP
      search_once;
    END LOOP;
    d := SYSTIMESTAMP - t0;
    IF r > 0 THEN
      ms(r) := (EXTRACT(MINUTE FROM d) * 60 + EXTRACT(SECOND FROM d)) * 1000;
    END IF;
  END LOOP;
  FOR r IN 1 .. 5 LOOP s := s + ms(r); END LOOP;
  avg_ms := s / 5;
  FOR r IN 1 .. 5 LOOP ss := ss + POWER(ms(r) - avg_ms, 2); END LOOP;
  sd := SQRT(ss / 4);
  DBMS_OUTPUT.PUT_LINE(RPAD('&1' || ' / ' || v_q, 22) || ' khop ' || LPAD(cnt, 6) || ' | ' || c_lookups
    || ' lan tim = ' || TO_CHAR(avg_ms, 'FM999990.00') || ' +/- ' || TO_CHAR(sd, 'FM999990.00')
    || ' ms  =>  ' || TO_CHAR(avg_ms / c_lookups, 'FM99990.000') || ' ms/lan');
END;
/
SET SERVEROUTPUT OFF
