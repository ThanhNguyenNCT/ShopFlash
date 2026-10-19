-- Repeatable dataset for Storage Lab.
-- Delete and regenerate it in one transaction; this does not create schema and is not a Flyway migration.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET FEEDBACK ON
SET SERVEROUTPUT ON

@@00_params.sql
@@98_delete_seed.sql

DECLARE
  v_category_count PLS_INTEGER := &&category_count;
  v_product_count  PLS_INTEGER := &&product_count;
  v_category_id    categories.category_id%TYPE;
  v_product_id     products.product_id%TYPE;
  v_now            TIMESTAMP WITH TIME ZONE :=
    TO_TIMESTAMP_TZ('2026-10-18T20:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM');
BEGIN
  IF v_category_count < 1 OR v_product_count < v_category_count THEN
    RAISE_APPLICATION_ERROR(-20001, 'category_count >= 1 and product_count >= category_count');
  END IF;

  FOR i IN 1 .. v_category_count LOOP
    INSERT INTO categories (name, slug, description, status, created_at, updated_at)
    VALUES (
      'Storage Category ' || TO_CHAR(i, 'FM000'),
      'storage-category-' || TO_CHAR(i, 'FM000'),
      'Repeatable dataset for Storage Lab',
      'ACTIVE', v_now, v_now
    );
  END LOOP;

  FOR i IN 1 .. v_product_count LOOP
    SELECT category_id
      INTO v_category_id
      FROM categories
     WHERE slug = 'storage-category-' || TO_CHAR(MOD(i - 1, v_category_count) + 1, 'FM000');

    INSERT INTO products (
      category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at
    ) VALUES (
      v_category_id,
      'STO-' || TO_CHAR(i, 'FM0000'),
      'Storage Product ' || TO_CHAR(i, 'FM0000'),
      'storage-product-' || TO_CHAR(i, 'FM0000'),
      'Repeatable dataset for Storage Lab',
      100000 + MOD(i * 73000, 4900000),
      NULL,
      'ACTIVE', v_now, v_now
    ) RETURNING product_id INTO v_product_id;

    INSERT INTO product_inventory (
      product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at
    ) VALUES (
      v_product_id, 20 + MOD(i - 1, 50), 0, 0, v_now
    );
  END LOOP;

  DBMS_OUTPUT.PUT_LINE('Generated ' || v_category_count || ' Categories and ' || v_product_count || ' Products.');
END;
/

COMMIT;

SELECT
  (SELECT COUNT(*) FROM categories WHERE slug LIKE 'storage-category-%') AS storage_categories,
  (SELECT COUNT(*) FROM products WHERE sku LIKE 'STO-%') AS storage_products,
  (SELECT COUNT(*)
     FROM product_inventory pi
     JOIN products p ON p.product_id = pi.product_id
    WHERE p.sku LIKE 'STO-%') AS storage_inventory_rows
FROM dual;

EXIT SUCCESS
