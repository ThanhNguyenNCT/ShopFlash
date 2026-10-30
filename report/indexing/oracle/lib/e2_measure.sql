-- Dung boi e2_name.sql: do thoi gian tim theo ten.
-- &1 = cach tim: LIKE | TEXT (Oracle Text CONTEXT) | CAT (Oracle Text CTXCAT)
-- &2 = tu khoa: HIEM ("615") | HAI_TU ("sony pro") | PHO_BIEN ("pro")
-- Moi lan tim = 1 cau lay trang dau (24 san pham) + 1 cau dem tong so ket qua, nhu mot trang tim kiem that.
-- 1 lan khoi dong (bo qua) + 5 lan do; moi lan do tim 20 lan.
SET SERVEROUTPUT ON VERIFY OFF
DECLARE
  TYPE t_ms  IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  TYPE t_ids IS TABLE OF NUMBER;
  c_lookups CONSTANT PLS_INTEGER := 20;
  v_variant VARCHAR2(10) := '&1';
  v_term    VARCHAR2(10) := '&2';
  w1 VARCHAR2(20);
  w2 VARCHAR2(20);
  q  VARCHAR2(100);
  p1 VARCHAR2(30);
  p2 VARCHAR2(30);
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
    IF v_variant = 'LIKE' THEN
      EXECUTE IMMEDIATE page_sql BULK COLLECT INTO ids USING p1, p2;
      EXECUTE IMMEDIATE cnt_sql INTO cnt USING p1, p2;
    ELSE
      EXECUTE IMMEDIATE page_sql BULK COLLECT INTO ids USING q;
      EXECUTE IMMEDIATE cnt_sql INTO cnt USING q;
    END IF;
  END;
BEGIN
  IF v_term = 'HIEM' THEN w1 := '615';
  ELSIF v_term = 'HAI_TU' THEN w1 := 'sony'; w2 := 'pro';
  ELSIF v_term = 'PHO_BIEN' THEN w1 := 'pro';
  ELSE RAISE_APPLICATION_ERROR(-20001, 'Tu khoa khong hop le: ' || v_term);
  END IF;

  IF v_variant = 'LIKE' THEN
    -- Khong dung duoc index: ky tu dai dien o dau. Mot tu thi lap lai cung mau cho tham so thu hai.
    p1 := '%' || UPPER(w1) || '%';
    p2 := '%' || UPPER(NVL(w2, w1)) || '%';
    page_sql := 'SELECT product_id FROM idxlab_products WHERE UPPER(name) LIKE :p1 AND UPPER(name) LIKE :p2 '
             || 'FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE UPPER(name) LIKE :p1 AND UPPER(name) LIKE :p2';
  ELSIF v_variant = 'TEXT' THEN
    -- Moi tu boc {} de Oracle Text khong hieu thanh toan tu; xep theo diem lien quan nhu RediSearch.
    q := '{' || w1 || '}' || CASE WHEN w2 IS NOT NULL THEN ' AND {' || w2 || '}' END;
    page_sql := 'SELECT product_id FROM idxlab_products WHERE CONTAINS(name, :q, 1) > 0 '
             || 'ORDER BY SCORE(1) DESC FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE CONTAINS(name, :q) > 0';
  ELSIF v_variant = 'CAT' THEN
    -- CATSEARCH: dau cach = AND; khong co diem lien quan.
    q := w1 || CASE WHEN w2 IS NOT NULL THEN ' ' || w2 END;
    page_sql := 'SELECT product_id FROM idxlab_products WHERE CATSEARCH(name, :q, NULL) > 0 '
             || 'FETCH FIRST 24 ROWS ONLY';
    cnt_sql  := 'SELECT COUNT(*) FROM idxlab_products WHERE CATSEARCH(name, :q, NULL) > 0';
  ELSE
    RAISE_APPLICATION_ERROR(-20002, 'Cach tim khong hop le: ' || v_variant);
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
  DBMS_OUTPUT.PUT_LINE(RPAD(v_variant || ' / ' || v_term, 20) || ' khop ' || LPAD(cnt, 6) || ' san pham, trang dau '
    || LPAD(ids.COUNT, 2) || ' | ' || c_lookups || ' lan tim = ' || TO_CHAR(avg_ms, 'FM999990.00') || ' +/- '
    || TO_CHAR(sd, 'FM999990.00') || ' ms  =>  ' || TO_CHAR(avg_ms / c_lookups, 'FM99990.000') || ' ms/lan');
END;
/
SET SERVEROUTPUT OFF
