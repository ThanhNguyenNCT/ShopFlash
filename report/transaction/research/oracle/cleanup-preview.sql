-- P1: chỉ đọc. Caller truyền :run_id và 6 ID từ manifest, :order_ids là JSON array ID.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_run_id VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_count NUMBER;
BEGIN
  IF l_run_id IS NULL OR NOT REGEXP_LIKE(l_run_id, '^[a-z0-9]{1,12}$') THEN
    RAISE_APPLICATION_ERROR(-20001, 'run_id khong hop le');
  END IF;
  DBMS_OUTPUT.PUT_LINE('PREVIEW run=' || l_run_id);
  SELECT COUNT(*) INTO l_count FROM users
  WHERE user_id = :fixture_user_id
    AND email = 'txresearch_' || l_run_id || '@shopflash.local';
  DBMS_OUTPUT.PUT_LINE('USERS=' || l_count || ' ID=' || :fixture_user_id);
  SELECT COUNT(*) INTO l_count FROM categories
  WHERE category_id = :fixture_category_id AND slug = 'txresearch-' || l_run_id;
  DBMS_OUTPUT.PUT_LINE('CATEGORIES=' || l_count || ' ID=' || :fixture_category_id);
  SELECT COUNT(*) INTO l_count FROM carts
  WHERE cart_id = :fixture_cart_id AND user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('CARTS=' || l_count || ' ID=' || :fixture_cart_id);
  SELECT COUNT(*) INTO l_count FROM products
  WHERE category_id = :fixture_category_id
    AND product_id IN (:p1_id, :p2_id, :p3_id);
  DBMS_OUTPUT.PUT_LINE('PRODUCTS=' || l_count || ' IDS=' || :p1_id || ',' || :p2_id || ',' || :p3_id);
  SELECT COUNT(*) INTO l_count FROM product_inventory
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  DBMS_OUTPUT.PUT_LINE('INVENTORY=' || l_count);
  SELECT COUNT(*) INTO l_count FROM cart_items WHERE cart_id = :fixture_cart_id;
  DBMS_OUTPUT.PUT_LINE('CART_ITEMS=' || l_count);
  SELECT COUNT(*) INTO l_count FROM orders WHERE user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDERS_OWNED=' || l_count || ' MANIFEST_IDS=' || :order_ids);
  SELECT COUNT(*) INTO l_count FROM order_items oi
  JOIN orders o ON o.order_id = oi.order_id WHERE o.user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDER_ITEMS=' || l_count);
END;
/
