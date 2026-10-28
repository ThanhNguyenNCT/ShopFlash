-- Hậu kiểm sau cleanup theo đúng ID manifest; không phụ thuộc business key LIKE.
SET DEFINE OFF
SET SERVEROUTPUT ON
DECLARE
  l_count NUMBER;
  l_total NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO l_count FROM order_items oi
  JOIN orders o ON o.order_id = oi.order_id WHERE o.user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDER_ITEMS=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM orders WHERE user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('ORDERS=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM cart_items WHERE cart_id = :fixture_cart_id;
  DBMS_OUTPUT.PUT_LINE('CART_ITEMS=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM carts WHERE cart_id = :fixture_cart_id;
  DBMS_OUTPUT.PUT_LINE('CARTS=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM product_inventory
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  DBMS_OUTPUT.PUT_LINE('INVENTORY=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM products
  WHERE product_id IN (:p1_id, :p2_id, :p3_id);
  DBMS_OUTPUT.PUT_LINE('PRODUCTS=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM categories WHERE category_id = :fixture_category_id;
  DBMS_OUTPUT.PUT_LINE('CATEGORIES=' || l_count); l_total := l_total + l_count;
  SELECT COUNT(*) INTO l_count FROM users WHERE user_id = :fixture_user_id;
  DBMS_OUTPUT.PUT_LINE('USERS=' || l_count); l_total := l_total + l_count;
  IF l_total <> 0 THEN RAISE_APPLICATION_ERROR(-20020, 'Con row fixture sau cleanup'); END IF;
  DBMS_OUTPUT.PUT_LINE('VERIFY_ABSENT=PASS');
END;
/
