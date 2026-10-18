-- Delete only the Storage dataset created by 01_seed_catalog.sql.
-- If a Product is referenced by Cart, Order, or Flash Sale, Oracle raises an FK error and rolls back.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET ECHO OFF
SET FEEDBACK ON
SET SERVEROUTPUT ON

@@98_delete_seed.sql
COMMIT;

SELECT COUNT(*) AS remaining_storage_products
  FROM products
 WHERE sku LIKE 'STO-%';

EXIT SUCCESS
