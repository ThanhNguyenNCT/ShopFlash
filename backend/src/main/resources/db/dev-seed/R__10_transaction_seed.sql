-- Dữ liệu mẫu F10/F7 cho profile dev; ID 101-106 dành riêng cho module Transaction.
-- Chỉ thêm khi chưa có để khỏi ghi đè giá, tồn kho và giỏ hàng đã được sửa thủ công.
-- Giỏ của customer@shopflash.local có 5 mặt hàng (CORE-001 từ R__00 và 101-104).
-- Sản phẩm 105 hết hàng và 106 ngừng bán để thử các nhánh từ chối khi Checkout/Cart.

MERGE INTO categories target
USING (
  SELECT 101 category_id, 'Phụ kiện điện tử' name, 'tx-phu-kien-dien-tu' slug FROM dual
  UNION ALL SELECT 102, 'Màn hình', 'tx-man-hinh' FROM dual
) source
ON (target.category_id = source.category_id)
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, 'Dữ liệu mẫu Transaction (dev)', 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP);

MERGE INTO products target
USING (
  SELECT 101 product_id, 101 category_id, 'TX-101' sku, 'Tai nghe Bluetooth' name,
    'tx-tai-nghe-bluetooth' slug, 450000 base_price, 'ACTIVE' status FROM dual
  UNION ALL SELECT 102, 101, 'TX-102', 'Bàn phím cơ', 'tx-ban-phim-co', 890000, 'ACTIVE' FROM dual
  UNION ALL SELECT 103, 101, 'TX-103', 'Chuột không dây', 'tx-chuot-khong-day', 220000, 'ACTIVE' FROM dual
  UNION ALL SELECT 104, 102, 'TX-104', 'Màn hình 24 inch', 'tx-man-hinh-24-inch', 2790000, 'ACTIVE' FROM dual
  UNION ALL SELECT 105, 101, 'TX-105', 'Sạc dự phòng', 'tx-sac-du-phong', 390000, 'ACTIVE' FROM dual
  UNION ALL SELECT 106, 101, 'TX-106', 'Loa Bluetooth ngừng bán', 'tx-loa-bluetooth-ngung-ban', 590000, 'INACTIVE' FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug,
   'Dữ liệu mẫu Transaction (dev)', source.base_price, NULL, source.status, SYSTIMESTAMP, SYSTIMESTAMP);

MERGE INTO product_inventory target
USING (
  SELECT 101 product_id, 20 on_hand_quantity FROM dual
  UNION ALL SELECT 102, 12 FROM dual
  UNION ALL SELECT 103, 30 FROM dual
  UNION ALL SELECT 104, 7 FROM dual
  UNION ALL SELECT 105, 0 FROM dual
  UNION ALL SELECT 106, 5 FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES
  (source.product_id, source.on_hand_quantity, 0, 0, SYSTIMESTAMP);

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
