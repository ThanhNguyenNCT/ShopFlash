-- A1 nhánh ROLLBACK. Để nguyên Order đã commit ở nhánh trước, chỉ thử Product 2.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_order_id NUMBER;
  l_name     products.name%TYPE;
  l_sku      products.sku%TYPE;
  l_tag      VARCHAR2(30) := 'TXRESEARCH_' || UPPER(:run_id);
  l_count    NUMBER;
BEGIN
  SELECT name, sku INTO l_name, l_sku FROM products
  WHERE product_id = :p2_id AND status = 'ACTIVE' AND base_price = 100;
  INSERT INTO orders (order_code, user_id, order_type, status, subtotal,
                      discount_amount, total_amount, recipient_name,
                      recipient_phone, shipping_address, idempotency_key,
                      created_at, updated_at)
  VALUES (l_tag || '_A1R', :fixture_user_id, 'NORMAL', 'PENDING', 100,
          0, 100, 'TX Research', '0900000000', 'Local fixture',
          l_tag || ':A1R', SYSTIMESTAMP, SYSTIMESTAMP)
  RETURNING order_id INTO l_order_id;
  INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                           unit_price, quantity, line_total)
  VALUES (l_order_id, :p2_id, l_name, l_sku, 100, 1, 100);
  UPDATE product_inventory
  SET on_hand_quantity = on_hand_quantity - 1, version = version + 1,
      updated_at = SYSTIMESTAMP
  WHERE product_id = :p2_id AND on_hand_quantity - reserved_flash_quantity >= 1;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20033, 'Khong du ton'); END IF;
  DELETE FROM cart_items WHERE cart_id = :fixture_cart_id AND product_id = :p2_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20034, 'Cart item khong khop'); END IF;
  SELECT COUNT(*) INTO l_count FROM orders WHERE order_id = l_order_id;
  DBMS_OUTPUT.PUT_LINE('A1_UNCOMMITTED_ORDER_ID=' || l_order_id || ' VISIBLE_IN_OWN_SESSION=' || l_count);
  ROLLBACK;
  DBMS_OUTPUT.PUT_LINE('A1_ROLLBACK=OK');
END;
/
