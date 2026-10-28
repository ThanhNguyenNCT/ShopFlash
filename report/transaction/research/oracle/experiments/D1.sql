-- D1: Checkout NORMAL hai mặt hàng; lỗi CHECK sau ghi một phần; full ROLLBACK.
-- Chạy nguyên file trong một SQL*Plus session với autocommit OFF.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_order_id NUMBER;
  l_tag VARCHAR2(30) := 'TXRESEARCH_' || UPPER(:run_id);
  l_name1 products.name%TYPE;
  l_sku1 products.sku%TYPE;
  l_name2 products.name%TYPE;
  l_sku2 products.sku%TYPE;
  l_order_count NUMBER;
  l_item_count NUMBER;
  l_cart_count NUMBER;
  l_p1_stock NUMBER;
  l_p2_stock NUMBER;
  l_p3_cart NUMBER;

  PROCEDURE snapshot(p_stage VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_order_count FROM orders
    WHERE user_id = :fixture_user_id AND order_code = l_tag || '_D1';
    SELECT COUNT(*) INTO l_item_count FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
    WHERE o.user_id = :fixture_user_id AND o.order_code = l_tag || '_D1';
    SELECT COUNT(*) INTO l_cart_count FROM cart_items WHERE cart_id = :fixture_cart_id;
    SELECT on_hand_quantity INTO l_p1_stock FROM product_inventory WHERE product_id = :p1_id;
    SELECT on_hand_quantity INTO l_p2_stock FROM product_inventory WHERE product_id = :p2_id;
    SELECT quantity INTO l_p3_cart FROM cart_items
    WHERE cart_id = :fixture_cart_id AND product_id = :p3_id;
    DBMS_OUTPUT.PUT_LINE(p_stage || ' ORDERS=' || l_order_count || ' ITEMS=' || l_item_count
      || ' CART_LINES=' || l_cart_count || ' P1_STOCK=' || l_p1_stock
      || ' P2_STOCK=' || l_p2_stock || ' P3_CART=' || l_p3_cart);
  END;
BEGIN
  snapshot('BEFORE');
  IF l_order_count <> 0 OR l_item_count <> 0 OR l_cart_count <> 3
     OR l_p1_stock <> 100 OR l_p2_stock <> 100 OR l_p3_cart <> 1 THEN
    RAISE_APPLICATION_ERROR(-20071, 'Fixture D1 khong o baseline');
  END IF;
  SELECT name, sku INTO l_name1, l_sku1 FROM products
  WHERE product_id = :p1_id AND status = 'ACTIVE' AND base_price = 100;
  SELECT name, sku INTO l_name2, l_sku2 FROM products
  WHERE product_id = :p2_id AND status = 'ACTIVE' AND base_price = 100;
  INSERT INTO orders (order_code, user_id, order_type, status, subtotal,
                      discount_amount, total_amount, recipient_name,
                      recipient_phone, shipping_address, idempotency_key,
                      created_at, updated_at)
  VALUES (l_tag || '_D1', :fixture_user_id, 'NORMAL', 'PENDING', 200,
          0, 200, 'TX Research', '0900000000', 'Local fixture',
          l_tag || ':D1', SYSTIMESTAMP, SYSTIMESTAMP)
  RETURNING order_id INTO l_order_id;
  DBMS_OUTPUT.PUT_LINE('TRANSIENT_ORDER_ID=' || l_order_id);
  INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                           unit_price, quantity, line_total)
  VALUES (l_order_id, :p1_id, l_name1, l_sku1, 100, 1, 100);
  INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                           unit_price, quantity, line_total)
  VALUES (l_order_id, :p2_id, l_name2, l_sku2, 100, 1, 100);
  UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1,
    version = version + 1, updated_at = SYSTIMESTAMP
  WHERE product_id = :p1_id AND on_hand_quantity - reserved_flash_quantity >= 1;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20072, 'P1 khong du ton'); END IF;
  UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1,
    version = version + 1, updated_at = SYSTIMESTAMP
  WHERE product_id = :p2_id AND on_hand_quantity - reserved_flash_quantity >= 1;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20073, 'P2 khong du ton'); END IF;
  DELETE FROM cart_items WHERE cart_id = :fixture_cart_id AND product_id IN (:p1_id, :p2_id);
  IF SQL%ROWCOUNT <> 2 THEN RAISE_APPLICATION_ERROR(-20074, 'Hai Cart item khong khop'); END IF;

  BEGIN
    UPDATE cart_items SET quantity = 0
    WHERE cart_id = :fixture_cart_id AND product_id = :p3_id;
    RAISE_APPLICATION_ERROR(-20075, 'CHECK khong chan quantity=0');
  EXCEPTION WHEN OTHERS THEN
    IF SQLCODE <> -2290 THEN RAISE; END IF;
    DBMS_OUTPUT.PUT_LINE('FAILED_STATEMENT=' || SQLCODE || ' ' || SQLERRM);
  END;
  snapshot('AFTER_FAILED_STATEMENT');
  IF l_order_count <> 1 OR l_item_count <> 2 OR l_cart_count <> 1
     OR l_p1_stock <> 99 OR l_p2_stock <> 99 OR l_p3_cart <> 1 THEN
    RAISE_APPLICATION_ERROR(-20076, 'Partial state D1 khong khop');
  END IF;
  ROLLBACK;
  snapshot('AFTER_FULL_ROLLBACK');
  IF l_order_count <> 0 OR l_item_count <> 0 OR l_cart_count <> 3
     OR l_p1_stock <> 100 OR l_p2_stock <> 100 OR l_p3_cart <> 1 THEN
    RAISE_APPLICATION_ERROR(-20077, 'Rollback D1 khong ve baseline');
  END IF;
END;
/
