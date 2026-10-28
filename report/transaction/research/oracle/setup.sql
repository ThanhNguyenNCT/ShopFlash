-- P1: fixture riêng trong SHOP. Caller phải khai báo/gán bind :run_id (chữ thường/số, tối đa 12).
-- Một lần thực thi = một SQL session; không dùng DDL hoặc dữ liệu ngoài tag của run.
SET DEFINE OFF
SET SERVEROUTPUT ON

DECLARE
  l_run_id      VARCHAR2(12) := LOWER(TRIM(:run_id));
  l_tag         VARCHAR2(30);
  l_email       VARCHAR2(255);
  l_slug        VARCHAR2(180);
  l_user_id     NUMBER;
  l_category_id NUMBER;
  l_cart_id     NUMBER;
  l_product_id  NUMBER;
  l_count       NUMBER;
  l_result      VARCHAR2(10);
  l_sku         VARCHAR2(80);
BEGIN
  IF l_run_id IS NULL OR NOT REGEXP_LIKE(l_run_id, '^[a-z0-9]{1,12}$') THEN
    RAISE_APPLICATION_ERROR(-20001, 'run_id phai gom 1..12 chu thuong/so');
  END IF;

  l_tag   := 'TXRESEARCH_' || UPPER(l_run_id);
  l_email := 'txresearch_' || l_run_id || '@shopflash.local';
  l_slug  := 'txresearch-' || l_run_id;

  SELECT COUNT(*) INTO l_count FROM users WHERE email = l_email;
  IF l_count = 0 THEN
    l_result := 'CREATED';
    INSERT INTO users (email, password_hash, full_name, role, status, created_at, updated_at)
    VALUES (l_email,
            '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O',
            l_tag || ' Customer', 'CUSTOMER', 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
    RETURNING user_id INTO l_user_id;

    INSERT INTO categories (name, slug, status, created_at, updated_at)
    VALUES (l_tag || ' Category', l_slug, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
    RETURNING category_id INTO l_category_id;

    FOR n IN 1..3 LOOP
      l_sku := l_tag || '_' || TO_CHAR(n);
      INSERT INTO products (category_id, sku, name, slug, base_price, status,
                            created_at, updated_at)
      VALUES (l_category_id, l_sku, l_tag || ' Product ' || TO_CHAR(n),
              l_slug || '-' || TO_CHAR(n), 100, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
      RETURNING product_id INTO l_product_id;

      INSERT INTO product_inventory (product_id, on_hand_quantity,
                                     reserved_flash_quantity, version, updated_at)
      VALUES (l_product_id, 100, 0, 0, SYSTIMESTAMP);
    END LOOP;

    INSERT INTO carts (user_id, created_at, updated_at, version)
    VALUES (l_user_id, SYSTIMESTAMP, SYSTIMESTAMP, 0)
    RETURNING cart_id INTO l_cart_id;

    FOR n IN 1..3 LOOP
      l_sku := l_tag || '_' || TO_CHAR(n);
      SELECT product_id INTO l_product_id FROM products WHERE sku = l_sku;
      INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
      VALUES (l_cart_id, l_product_id, 1, SYSTIMESTAMP, SYSTIMESTAMP);
    END LOOP;
  ELSE
    l_result := 'EXISTS';
    SELECT user_id INTO l_user_id FROM users WHERE email = l_email;
    SELECT category_id INTO l_category_id FROM categories WHERE slug = l_slug;
    SELECT cart_id INTO l_cart_id FROM carts WHERE user_id = l_user_id;
  END IF;

  SELECT COUNT(*) INTO l_count
  FROM products p JOIN product_inventory i ON i.product_id = p.product_id
  WHERE p.category_id = l_category_id
    AND p.status = 'ACTIVE' AND p.base_price = 100
    AND i.on_hand_quantity = 100 AND i.reserved_flash_quantity = 0;
  IF l_count <> 3 THEN
    RAISE_APPLICATION_ERROR(-20002, 'fixture product/inventory khong o trang thai dau ky; reset truoc');
  END IF;

  SELECT COUNT(*) INTO l_count FROM cart_items
  WHERE cart_id = l_cart_id AND quantity = 1;
  IF l_count <> 3 THEN
    RAISE_APPLICATION_ERROR(-20003, 'fixture cart khong o trang thai dau ky; reset truoc');
  END IF;

  SELECT COUNT(*) INTO l_count FROM orders WHERE user_id = l_user_id;
  IF l_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20004, 'fixture con order; reset truoc');
  END IF;

  COMMIT;
  DBMS_OUTPUT.PUT_LINE('RESULT=' || l_result);
  DBMS_OUTPUT.PUT_LINE('RUN_ID=' || l_run_id);
  DBMS_OUTPUT.PUT_LINE('USER_ID=' || TO_CHAR(l_user_id));
  DBMS_OUTPUT.PUT_LINE('CATEGORY_ID=' || TO_CHAR(l_category_id));
  DBMS_OUTPUT.PUT_LINE('CART_ID=' || TO_CHAR(l_cart_id));
  FOR n IN 1..3 LOOP
    l_sku := l_tag || '_' || TO_CHAR(n);
    SELECT product_id INTO l_product_id FROM products WHERE sku = l_sku;
    DBMS_OUTPUT.PUT_LINE('PRODUCT_' || TO_CHAR(n) || '_ID=' || TO_CHAR(l_product_id));
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('CART_ITEMS=3; PRODUCT_1/2 la hang mua, PRODUCT_3 la hang giu lai');
END;
/
