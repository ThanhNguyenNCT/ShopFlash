-- Dữ liệu mẫu F10/F7 cho profile dev.
-- Sản phẩm 101-106 và danh mục 101-102 của module Transaction nằm trong R__00_catalog_seed.sql.
-- Chỉ thêm khi chưa có để khỏi ghi đè giỏ hàng đã được sửa thủ công.
-- Giỏ của customer@shopflash.local có 5 mặt hàng (CORE-001 và 101-104).
-- Sản phẩm 105 hết hàng và 106 ngừng bán để thử các nhánh từ chối khi Checkout/Cart.

MERGE INTO cart_items target
USING (
  SELECT 1 cart_item_id, 1 cart_id, 1 product_id, 1 quantity FROM dual
  UNION ALL SELECT 101, 1, 101, 1 FROM dual
  UNION ALL SELECT 102, 1, 102, 2 FROM dual
  UNION ALL SELECT 103, 1, 103, 3 FROM dual
  UNION ALL SELECT 104, 1, 104, 1 FROM dual
) source
ON (target.cart_id = source.cart_id AND target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (cart_item_id, cart_id, product_id, quantity, created_at, updated_at)
VALUES
  (source.cart_item_id, source.cart_id, source.product_id, source.quantity, SYSTIMESTAMP, SYSTIMESTAMP);

-- Báo hiệu giỏ đã thay đổi cho client giữ phiên bản trước lần nạp seed này.
UPDATE carts SET version = version + 1, updated_at = SYSTIMESTAMP WHERE cart_id = 1;
