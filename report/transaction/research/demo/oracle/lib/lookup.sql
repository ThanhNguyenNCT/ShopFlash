-- Chỉ đọc. Tìm ID fixture của run theo đúng tag mà setup.sql tạo, Order đã COMMIT của user fixture,
-- và các session SHOP khác còn transaction mở (DBeaver chưa COMMIT/ROLLBACK) trước khi reset/cleanup.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_run  VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_tag  VARCHAR2(30) := 'TXRESEARCH_' || UPPER(LOWER(TRIM(:run_id)));
  l_user NUMBER;
  l_cat  NUMBER;
  l_cart NUMBER;
  l_id   NUMBER;
  l_ids  VARCHAR2(4000);
BEGIN
  IF l_run IS NULL OR NOT REGEXP_LIKE(l_run, '^[a-z0-9]{1,12}$') THEN
    RAISE_APPLICATION_ERROR(-20001, 'run_id khong hop le');
  END IF;
  FOR r IN (SELECT s.sid, s.serial#, s.program, s.machine
            FROM v$session s JOIN v$transaction t ON t.addr = s.taddr
            WHERE s.username = 'SHOP' AND s.sid <> SYS_CONTEXT('USERENV', 'SID')) LOOP
    DBMS_OUTPUT.PUT_LINE('OPEN_TX=sid ' || r.sid || ',' || r.serial# || ' program=' || r.program);
  END LOOP;
  BEGIN
    SELECT user_id INTO l_user FROM users WHERE email = 'txresearch_' || l_run || '@shopflash.local';
  EXCEPTION WHEN NO_DATA_FOUND THEN
    DBMS_OUTPUT.PUT_LINE('FIXTURE=ABSENT');
    RETURN;
  END;
  SELECT category_id INTO l_cat FROM categories WHERE slug = 'txresearch-' || l_run;
  SELECT cart_id INTO l_cart FROM carts WHERE user_id = l_user;
  DBMS_OUTPUT.PUT_LINE('FIXTURE=PRESENT');
  DBMS_OUTPUT.PUT_LINE('USER_ID=' || l_user);
  DBMS_OUTPUT.PUT_LINE('CATEGORY_ID=' || l_cat);
  DBMS_OUTPUT.PUT_LINE('CART_ID=' || l_cart);
  FOR n IN 1..3 LOOP
    SELECT product_id INTO l_id FROM products
    WHERE sku = l_tag || '_' || n AND category_id = l_cat;
    DBMS_OUTPUT.PUT_LINE('P' || n || '=' || l_id);
  END LOOP;
  SELECT '[' || LISTAGG(order_id, ',') WITHIN GROUP (ORDER BY order_id) || ']' INTO l_ids
  FROM orders WHERE user_id = l_user;
  DBMS_OUTPUT.PUT_LINE('ORDER_IDS=' || l_ids);
END;
/
