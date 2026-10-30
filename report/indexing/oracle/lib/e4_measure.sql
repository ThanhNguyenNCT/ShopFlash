-- Dung boi e4_write.sql: do chi phi ghi tren IDXLAB_WRITE voi cau hinh index hien tai.
-- &1 = nhan cau hinh.
--   THEM: INSERT tung dong 10.000 san pham moi (id 200.001-210.000) roi COMMIT  (nhu admin them san pham)
--   SUA GIA: UPDATE tung dong gia cua 10.000 san pham (id 1-10.000) roi COMMIT
-- Sau moi lan do, tra du lieu ve nhu cu (khong tinh gio). 1 lan khoi dong + 5 lan do.
-- In them luong redo (nhat ky) sinh ra o lan do cuoi.
SET SERVEROUTPUT ON VERIFY OFF
DECLARE
  TYPE t_ms IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
  kinds  sys.odcivarchar2list := sys.odcivarchar2list('Tai nghe', 'Dien thoai', 'Laptop', 'Chuot', 'Ban phim',
           'Man hinh', 'Loa', 'Sac du phong', 'Dong ho', 'May anh', 'Tivi', 'Noi chien', 'Quat', 'May loc khi', 'Balo');
  brands sys.odcivarchar2list := sys.odcivarchar2list('Sony', 'Samsung', 'Apple', 'Xiaomi', 'Asus', 'Dell',
           'Logitech', 'Anker', 'JBL', 'LG', 'Philips', 'Panasonic');
  tags   sys.odcivarchar2list := sys.odcivarchar2list('Pro', 'Mini', 'Max', 'Lite', 'Plus', 'Ultra', 'Air',
           'Gaming', 'Slim', 'Neo');
  c_rows CONSTANT PLS_INTEGER := 10000;
  v_name VARCHAR2(200);
  ins t_ms;
  upd t_ms;
  t0  TIMESTAMP;
  d   INTERVAL DAY TO SECOND;
  redo0 NUMBER;
  redo_ins NUMBER;
  redo_upd NUMBER;

  FUNCTION redo_size RETURN NUMBER IS
    v NUMBER;
  BEGIN
    SELECT m.value INTO v FROM v$mystat m JOIN v$statname s ON s.statistic# = m.statistic#
    WHERE s.name = 'redo size';
    RETURN v;
  END;

  FUNCTION elapsed_ms RETURN NUMBER IS
  BEGIN
    d := SYSTIMESTAMP - t0;
    RETURN (EXTRACT(MINUTE FROM d) * 60 + EXTRACT(SECOND FROM d)) * 1000;
  END;

  PROCEDURE report(p_name VARCHAR2, p_ms t_ms, p_redo NUMBER) IS
    s NUMBER := 0; ss NUMBER := 0; m NUMBER;
  BEGIN
    FOR r IN 1 .. 5 LOOP s := s + p_ms(r); END LOOP;
    m := s / 5;
    FOR r IN 1 .. 5 LOOP ss := ss + POWER(p_ms(r) - m, 2); END LOOP;
    DBMS_OUTPUT.PUT_LINE('  ' || p_name || ' tung lan (ms): ' || TO_CHAR(p_ms(1), 'FM999990.0') || ', '
      || TO_CHAR(p_ms(2), 'FM999990.0') || ', ' || TO_CHAR(p_ms(3), 'FM999990.0') || ', '
      || TO_CHAR(p_ms(4), 'FM999990.0') || ', ' || TO_CHAR(p_ms(5), 'FM999990.0'));
    DBMS_OUTPUT.PUT_LINE(RPAD('&1' || ' / ' || p_name, 26) || c_rows || ' dong = ' || TO_CHAR(m, 'FM999990.00')
      || ' +/- ' || TO_CHAR(SQRT(ss / 4), 'FM999990.00') || ' ms  =>  ' || TO_CHAR(m * 1000 / c_rows, 'FM99990.0')
      || ' us/dong | redo ' || TO_CHAR(p_redo / 1048576, 'FM9990.0') || ' MB');
  END;
BEGIN
  FOR r IN 0 .. 5 LOOP
    -- THEM
    redo0 := redo_size;
    t0 := SYSTIMESTAMP;
    FOR n IN 200001 .. 200000 + c_rows LOOP
      v_name := kinds(MOD(n, 15) + 1) || ' ' || brands(MOD(TRUNC(n / 15), 12) + 1) || ' '
                || tags(MOD(TRUNC(n / 180), 10) + 1) || ' ' || MOD(n * 37, 997);
      INSERT INTO idxlab_write (product_id, sku, name, category_id, price, status, in_stock)
      VALUES (n, 'LAB-' || LPAD(n, 7, '0'), v_name,
              MOD(n * 7, 50) + 1, (MOD(n * 7919, 49951) + 50) * 1000,
              CASE WHEN MOD(n, 20) = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END,
              CASE WHEN MOD(n, 7) = 0 THEN 0 ELSE 1 END);
    END LOOP;
    COMMIT;
    IF r > 0 THEN ins(r) := elapsed_ms; END IF;
    redo_ins := redo_size - redo0;
    DELETE FROM idxlab_write WHERE product_id > 200000;
    COMMIT;

    -- SUA GIA
    redo0 := redo_size;
    t0 := SYSTIMESTAMP;
    FOR n IN 1 .. c_rows LOOP
      UPDATE idxlab_write SET price = price + 1000 WHERE product_id = n;
    END LOOP;
    COMMIT;
    IF r > 0 THEN upd(r) := elapsed_ms; END IF;
    redo_upd := redo_size - redo0;
    UPDATE idxlab_write SET price = price - 1000 WHERE product_id <= c_rows;
    COMMIT;
  END LOOP;
  report('THEM', ins, redo_ins);
  report('SUA GIA', upd, redo_upd);
END;
/
SET SERVEROUTPUT OFF
