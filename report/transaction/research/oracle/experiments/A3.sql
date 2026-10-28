-- A3: tất cả bước nằm trong cùng Oracle session/transaction, autocommit OFF.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_p1 NUMBER;
  l_p2 NUMBER;
  l_cart_qty NUMBER;
BEGIN
  UPDATE product_inventory SET on_hand_quantity = 99, updated_at = SYSTIMESTAMP
  WHERE product_id = :p1_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20051, 'Thieu Product 1'); END IF;
  SAVEPOINT before_p2;
  UPDATE product_inventory SET on_hand_quantity = 98, updated_at = SYSTIMESTAMP
  WHERE product_id = :p2_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20052, 'Thieu Product 2'); END IF;

  BEGIN
    UPDATE cart_items SET quantity = 0
    WHERE cart_id = :fixture_cart_id AND product_id = :p1_id;
    RAISE_APPLICATION_ERROR(-20053, 'CHECK khong chan quantity=0');
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE <> -2290 THEN RAISE; END IF;
      DBMS_OUTPUT.PUT_LINE('STATEMENT_ERROR=' || SQLCODE || ' ' || SQLERRM);
  END;

  SELECT on_hand_quantity INTO l_p1 FROM product_inventory WHERE product_id = :p1_id;
  SELECT on_hand_quantity INTO l_p2 FROM product_inventory WHERE product_id = :p2_id;
  SELECT quantity INTO l_cart_qty FROM cart_items
  WHERE cart_id = :fixture_cart_id AND product_id = :p1_id;
  DBMS_OUTPUT.PUT_LINE('AFTER_FAILED_STATEMENT P1=' || l_p1 || ' P2=' || l_p2
    || ' CART_QTY=' || l_cart_qty);
  IF l_p1 <> 99 OR l_p2 <> 98 OR l_cart_qty <> 1 THEN
    RAISE_APPLICATION_ERROR(-20054, 'Statement rollback khong khop');
  END IF;

  ROLLBACK TO before_p2;
  SELECT on_hand_quantity INTO l_p1 FROM product_inventory WHERE product_id = :p1_id;
  SELECT on_hand_quantity INTO l_p2 FROM product_inventory WHERE product_id = :p2_id;
  DBMS_OUTPUT.PUT_LINE('AFTER_ROLLBACK_TO P1=' || l_p1 || ' P2=' || l_p2);
  IF l_p1 <> 99 OR l_p2 <> 100 THEN
    RAISE_APPLICATION_ERROR(-20055, 'Savepoint rollback khong khop');
  END IF;

  ROLLBACK;
  SELECT on_hand_quantity INTO l_p1 FROM product_inventory WHERE product_id = :p1_id;
  SELECT on_hand_quantity INTO l_p2 FROM product_inventory WHERE product_id = :p2_id;
  DBMS_OUTPUT.PUT_LINE('AFTER_FULL_ROLLBACK P1=' || l_p1 || ' P2=' || l_p2);
  IF l_p1 <> 100 OR l_p2 <> 100 THEN
    RAISE_APPLICATION_ERROR(-20056, 'Full rollback khong khop');
  END IF;
END;
/
