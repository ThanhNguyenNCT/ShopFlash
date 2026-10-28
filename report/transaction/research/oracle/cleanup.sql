-- Xóa đúng 6 ID fixture + danh sách Order ID trong manifest. Chạy cleanup-preview.sql trước.
-- Nếu kiểm tra nào sai, PL/SQL báo lỗi và SQL*Plus wrapper ROLLBACK.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_run_id      VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_tag         VARCHAR2(30);
  l_count       NUMBER;
  l_orders      NUMBER;
  l_listed      NUMBER;
  l_valid       NUMBER;
  l_lines       NUMBER;
  l_cart_items  NUMBER;
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
    RAISE_APPLICATION_ERROR(-20010, 'Order ID/tag khong khop manifest; khong xoa');
  END IF;

  DELETE FROM order_items
  WHERE order_id IN
    (SELECT jt.order_id FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$')) jt);
  l_lines := SQL%ROWCOUNT;
  DELETE FROM orders
  WHERE user_id = :fixture_user_id AND order_id IN
    (SELECT jt.order_id FROM JSON_TABLE(:order_ids, '$[*]' COLUMNS (order_id NUMBER PATH '$')) jt);
  IF SQL%ROWCOUNT <> l_orders THEN RAISE_APPLICATION_ERROR(-20011, 'So Order xoa khong khop'); END IF;

  DELETE FROM cart_items WHERE cart_id = :fixture_cart_id;
  l_cart_items := SQL%ROWCOUNT;
  DELETE FROM carts WHERE cart_id = :fixture_cart_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20012, 'Cart xoa khong khop'); END IF;
  DELETE FROM product_inventory WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  IF SQL%ROWCOUNT <> 3 THEN RAISE_APPLICATION_ERROR(-20013, 'Inventory xoa khong khop'); END IF;
  DELETE FROM products WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  IF SQL%ROWCOUNT <> 3 THEN RAISE_APPLICATION_ERROR(-20014, 'Product xoa khong khop'); END IF;
  DELETE FROM categories WHERE category_id = :fixture_category_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20015, 'Category xoa khong khop'); END IF;
  DELETE FROM users WHERE user_id = :fixture_user_id;
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20016, 'User xoa khong khop'); END IF;

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('CLEANUP_COMMITTED RUN_ID=' || l_run_id
    || ' ORDERS=' || l_orders || ' ORDER_ITEMS=' || l_lines
    || ' CART_ITEMS=' || l_cart_items || ' PRODUCTS=3');
END;
/
