-- Đặt lại đúng fixture đã ghi trong manifest. Dùng cùng bind như cleanup.sql.
-- Mọi Order đã COMMIT phải có ID trong :order_ids; không tự dò/xóa Order lạ.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_run_id     VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_tag        VARCHAR2(30);
  l_count      NUMBER;
  l_orders     NUMBER;
  l_listed     NUMBER;
  l_valid      NUMBER;
  l_order_item NUMBER;
BEGIN
  IF l_run_id IS NULL OR NOT REGEXP_LIKE(l_run_id, '^[a-z0-9]{1,12}$') THEN
    RAISE_APPLICATION_ERROR(-20001, 'run_id khong hop le');
  END IF;
  IF :p1_id = :p2_id OR :p1_id = :p3_id OR :p2_id = :p3_id THEN
    RAISE_APPLICATION_ERROR(-20002, 'Product ID trung nhau');
  END IF;
  l_tag := 'TXRESEARCH_' || UPPER(l_run_id);
  SELECT COUNT(*) INTO l_count FROM users
  WHERE user_id = :fixture_user_id
    AND email = 'txresearch_' || l_run_id || '@shopflash.local';
  IF l_count <> 1 THEN RAISE_APPLICATION_ERROR(-20003, 'User ID/tag khong khop manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM categories
  WHERE category_id = :fixture_category_id AND slug = 'txresearch-' || l_run_id;
  IF l_count <> 1 THEN RAISE_APPLICATION_ERROR(-20004, 'Category ID/tag khong khop manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM carts
  WHERE cart_id = :fixture_cart_id AND user_id = :fixture_user_id;
  IF l_count <> 1 THEN RAISE_APPLICATION_ERROR(-20005, 'Cart ID/user khong khop manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM products
  WHERE category_id = :fixture_category_id
    AND ((product_id = :p1_id AND sku = l_tag || '_1')
      OR (product_id = :p2_id AND sku = l_tag || '_2')
      OR (product_id = :p3_id AND sku = l_tag || '_3'));
  IF l_count <> 3 THEN RAISE_APPLICATION_ERROR(-20006, 'Product ID/SKU khong khop manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM products WHERE category_id = :fixture_category_id;
  IF l_count <> 3 THEN RAISE_APPLICATION_ERROR(-20007, 'Category co Product ngoai manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM product_inventory
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  IF l_count <> 3 THEN RAISE_APPLICATION_ERROR(-20008, 'Inventory thieu hoac sai manifest'); END IF;
  SELECT COUNT(*) INTO l_count FROM cart_items
  WHERE cart_id = :fixture_cart_id AND product_id NOT IN (:p1_id, :p2_id, :p3_id);
  IF l_count <> 0 THEN RAISE_APPLICATION_ERROR(-20009, 'Cart co item ngoai manifest'); END IF;

  SELECT COUNT(*) INTO l_orders FROM orders WHERE user_id = :fixture_user_id;
  SELECT COUNT(*) INTO l_listed
  FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$'));
  SELECT COUNT(*) INTO l_valid FROM orders o
  WHERE o.user_id = :fixture_user_id
    AND SUBSTR(o.order_code, 1, LENGTH(l_tag)) = l_tag
    AND o.order_id IN
      (SELECT jt.order_id FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$')) jt);
  IF l_orders <> l_listed OR l_valid <> l_orders THEN
    RAISE_APPLICATION_ERROR(-20010, 'Order ID/tag khong khop manifest; khong reset');
  END IF;

  DELETE FROM order_items
  WHERE order_id IN
    (SELECT jt.order_id FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$')) jt);
  l_order_item := SQL%ROWCOUNT;
  DELETE FROM orders
  WHERE user_id = :fixture_user_id AND order_id IN
    (SELECT jt.order_id FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$')) jt);
  IF SQL%ROWCOUNT <> l_orders THEN RAISE_APPLICATION_ERROR(-20011, 'So Order reset khong khop'); END IF;

  DELETE FROM cart_items WHERE cart_id = :fixture_cart_id;
  UPDATE carts SET version = 0, updated_at = SYSTIMESTAMP
  WHERE cart_id = :fixture_cart_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20012, 'Cart reset khong khop'); END IF;

  UPDATE products SET status = 'ACTIVE', base_price = 100, updated_at = SYSTIMESTAMP
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  IF SQL%ROWCOUNT <> 3 THEN RAISE_APPLICATION_ERROR(-20013, 'Product reset khong khop'); END IF;
  UPDATE product_inventory
  SET on_hand_quantity = 100, reserved_flash_quantity = 0,
      version = 0, updated_at = SYSTIMESTAMP
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  IF SQL%ROWCOUNT <> 3 THEN RAISE_APPLICATION_ERROR(-20014, 'Inventory reset khong khop'); END IF;

  INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
  VALUES (:fixture_cart_id, :p1_id, 1, SYSTIMESTAMP, SYSTIMESTAMP);
  INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
  VALUES (:fixture_cart_id, :p2_id, 1, SYSTIMESTAMP, SYSTIMESTAMP);
  INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
  VALUES (:fixture_cart_id, :p3_id, 1, SYSTIMESTAMP, SYSTIMESTAMP);

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('RESET_COMMITTED RUN_ID=' || l_run_id
    || ' ORDERS=' || l_orders || ' ORDER_ITEMS=' || l_order_item
    || ' CART_ITEMS=3 INVENTORY_ON_HAND=100');
END;
/
