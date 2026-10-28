-- A2: ba lỗi constraint thật trên fixture native. Không dùng Java và không thêm constraint.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_ready NUMBER;
  l_missing_order_id NUMBER := -1;
  l_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO l_ready FROM user_constraints
  WHERE constraint_name IN ('UK_CART_ITEMS_CART_PRODUCT', 'FK_ORDER_ITEMS_ORDER',
                            'CK_CART_ITEMS_QUANTITY')
    AND status = 'ENABLED' AND validated = 'VALIDATED'
    AND deferrable = 'NOT DEFERRABLE' AND deferred = 'IMMEDIATE';
  IF l_ready <> 3 THEN RAISE_APPLICATION_ERROR(-20041, 'Constraint A2 khong o trang thai immediate da audit'); END IF;
  SELECT COUNT(*) INTO l_count FROM orders WHERE order_id = l_missing_order_id;
  IF l_count <> 0 THEN RAISE_APPLICATION_ERROR(-20042, 'Order ID doi chung da ton tai'); END IF;

  BEGIN
    INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
    VALUES (:fixture_cart_id, :p1_id, 1, SYSTIMESTAMP, SYSTIMESTAMP);
    RAISE_APPLICATION_ERROR(-20043, 'UNIQUE khong chan duplicate');
  EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN DBMS_OUTPUT.PUT_LINE('UNIQUE SQLCODE=' || SQLCODE || ' ' || SQLERRM);
  END;

  BEGIN
    INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                             unit_price, quantity, line_total)
    VALUES (l_missing_order_id, :p1_id, 'TX Research', 'TXRESEARCH_A2', 100, 1, 100);
    RAISE_APPLICATION_ERROR(-20044, 'FK khong chan orphan');
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE <> -2291 THEN RAISE; END IF;
      DBMS_OUTPUT.PUT_LINE('FK SQLCODE=' || SQLCODE || ' ' || SQLERRM);
  END;

  BEGIN
    UPDATE cart_items SET quantity = 0
    WHERE cart_id = :fixture_cart_id AND product_id = :p1_id;
    RAISE_APPLICATION_ERROR(-20045, 'CHECK khong chan so luong cart sai');
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE <> -2290 THEN RAISE; END IF;
      DBMS_OUTPUT.PUT_LINE('CHECK SQLCODE=' || SQLCODE || ' ' || SQLERRM);
  END;

  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('A2_ROLLBACK=OK');
END;
/
