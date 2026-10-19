-- Deliberately removes data from every business table in SHOP, while retaining
-- the schema and Flyway history so that Data Pump can restore into it.
-- Invoke only through Invoke-BackupRecoveryDemo.ps1 with explicit confirmation.

WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

SET ECHO OFF
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0
SET LINESIZE 200

DELETE FROM order_items;
DELETE FROM cart_items;
DELETE FROM flash_sale_items;
DELETE FROM flash_sale_configs;
DELETE FROM product_inventory;
DELETE FROM orders;
DELETE FROM carts;
DELETE FROM flash_sales;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM users;
COMMIT;

PROMPT DATA_LOSS_COMPLETED|ORACLE
EXIT SUCCESS
