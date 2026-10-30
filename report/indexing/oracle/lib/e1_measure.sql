-- Dung boi e1_sku.sql: do thoi gian tim 200 SKU khac nhau.
-- Chay 1 lan khoi dong (bo qua) + 5 lan do, in tung lan, trung binh va do lech chuan.
-- Tham so &1: nhan in ra (vd. KHONG_INDEX, CO_INDEX).
SET SERVEROUTPUT ON VERIFY OFF
DECLARE
  TYPE t_ms IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  ms     t_ms;
  v_name VARCHAR2(200);
  t0     TIMESTAMP;
  d      INTERVAL DAY TO SECOND;
  s      NUMBER := 0;
  ss     NUMBER := 0;
  avg_ms NUMBER;
  sd     NUMBER;
BEGIN
  FOR r IN 0 .. 5 LOOP
    t0 := SYSTIMESTAMP;
    FOR i IN 1 .. 200 LOOP
      SELECT name INTO v_name FROM idxlab_products
      WHERE sku = 'LAB-' || LPAD(MOD(i * 997, 200000) + 1, 7, '0');
    END LOOP;
    d := SYSTIMESTAMP - t0;
    IF r = 0 THEN
      DBMS_OUTPUT.PUT_LINE('  Lan khoi dong: bo qua');
    ELSE
      ms(r) := (EXTRACT(MINUTE FROM d) * 60 + EXTRACT(SECOND FROM d)) * 1000;
      DBMS_OUTPUT.PUT_LINE('  Lan ' || r || ': ' || TO_CHAR(ms(r), 'FM999990.00') || ' ms');
    END IF;
  END LOOP;
  FOR r IN 1 .. 5 LOOP s := s + ms(r); END LOOP;
  avg_ms := s / 5;
  FOR r IN 1 .. 5 LOOP ss := ss + POWER(ms(r) - avg_ms, 2); END LOOP;
  sd := SQRT(ss / 4);
  DBMS_OUTPUT.PUT_LINE('&1: 200 lan tim = ' || TO_CHAR(avg_ms, 'FM999990.00') || ' +/- '
    || TO_CHAR(sd, 'FM999990.00') || ' ms  =>  ' || TO_CHAR(avg_ms / 200, 'FM9990.0000') || ' ms/lan');
END;
/
SET SERVEROUTPUT OFF
