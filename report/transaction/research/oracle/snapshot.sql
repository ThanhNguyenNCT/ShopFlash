-- Chỉ đọc fixture theo bind :run_id; chạy trong kết nối độc lập khi kiểm chứng COMMIT/ROLLBACK.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_run_id  VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_user_id NUMBER;
  l_cart_id NUMBER;
  l_count   NUMBER;
BEGIN
  IF l_run_id IS NULL OR NOT REGEXP_LIKE(l_run_id, '^[a-z0-9]{1,12}$') THEN
    RAISE_APPLICATION_ERROR(-20001, 'run_id khong hop le');
  END IF;

  SELECT user_id INTO l_user_id FROM users
  WHERE email = 'txresearch_' || l_run_id || '@shopflash.local';
  SELECT cart_id INTO l_cart_id FROM carts WHERE user_id = l_user_id;
  DBMS_OUTPUT.PUT_LINE('RUN_ID=' || l_run_id || ' USER_ID=' || l_user_id || ' CART_ID=' || l_cart_id);

  SELECT COUNT(*) INTO l_count FROM cart_items WHERE cart_id = l_cart_id;
  DBMS_OUTPUT.PUT_LINE('CART_ITEMS=' || l_count);
  FOR r IN (
    SELECT p.product_id, p.sku, i.on_hand_quantity, i.reserved_flash_quantity,
           i.version, ci.quantity AS cart_quantity
    FROM products p
    JOIN product_inventory i ON i.product_id = p.product_id
    LEFT JOIN cart_items ci ON ci.cart_id = l_cart_id AND ci.product_id = p.product_id
    WHERE p.category_id = (SELECT category_id FROM categories
                           WHERE slug = 'txresearch-' || l_run_id)
    ORDER BY p.product_id
  ) LOOP
    DBMS_OUTPUT.PUT_LINE('PRODUCT=' || r.product_id || ' SKU=' || r.sku
      || ' ON_HAND=' || r.on_hand_quantity || ' RESERVED=' || r.reserved_flash_quantity
      || ' VERSION=' || r.version || ' CART_QTY=' || NVL(TO_CHAR(r.cart_quantity), 'NULL'));
  END LOOP;

  SELECT COUNT(*) INTO l_count FROM orders WHERE user_id = l_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDERS=' || l_count);
  SELECT COUNT(*) INTO l_count FROM order_items oi
  JOIN orders o ON o.order_id = oi.order_id WHERE o.user_id = l_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDER_ITEMS=' || l_count);
END;
/
