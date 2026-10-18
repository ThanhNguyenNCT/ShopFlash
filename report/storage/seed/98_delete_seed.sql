-- Delete only the Storage dataset marked with STO-/storage-category-.
-- Do not commit here: the caller controls the complete transaction.
DELETE FROM product_inventory
 WHERE product_id IN (
   SELECT product_id
     FROM products
    WHERE sku LIKE 'STO-%'
 );

DELETE FROM products
 WHERE sku LIKE 'STO-%';

DELETE FROM categories
 WHERE slug LIKE 'storage-category-%';
