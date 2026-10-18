-- Dữ liệu mẫu cho máy local, chỉ chạy ở profile dev (spring.flyway.locations có db/dev-seed).
-- Repeatable migration: Flyway chạy sau mọi version và chạy lại khi file đổi; MERGE nên chạy lại vẫn an toàn.
-- ID dưới 10000 dành cho dữ liệu mẫu; ID do ứng dụng sinh bắt đầu từ 10000.
--
-- Kịch bản nhất quán với DATABASE_DESIGN.md §14 (không phụ thuộc ngày chạy):
--   Flash Sale #1 đã kết thúc (ENDED, 2026-10-01 09:00-10:00 +07), allocated 5, sold 1.
--   Sale kết thúc nên 4 suất chưa bán trả về kho thường: reserved_flash_quantity = 0.
--   on_hand_quantity = 20 là tồn kho hiện tại, đã trừ 2 đơn bên dưới.
--   Đơn #1 thường, COMPLETED; đơn #2 flash sale, CONFIRMED, tạo lúc 09:30 trong khung giờ sale.
-- Sửa file này thì Flyway chạy lại nó và đặt lại tồn kho, sold_quantity của sản phẩm 1 về giá trị dưới đây.
-- Tài khoản: admin@shopflash.local / customer@shopflash.local, mật khẩu ShopFlash123! (chỉ dùng local).

MERGE INTO users target
USING (
  SELECT 1 user_id, 'admin@shopflash.local' email, '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O' password_hash, 'ShopFlash Admin' full_name, '0900000001' phone, 'ADMIN' role, 'ACTIVE' status FROM dual
  UNION ALL
  SELECT 2, 'customer@shopflash.local', '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O', 'ShopFlash Customer', '0900000002', 'CUSTOMER', 'ACTIVE' FROM dual
) source
ON (target.user_id = source.user_id)
WHEN MATCHED THEN UPDATE SET
  target.email = source.email, target.password_hash = source.password_hash,
  target.full_name = source.full_name, target.phone = source.phone,
  target.role = source.role, target.status = source.status,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (user_id, email, password_hash, full_name, phone, role, status, created_at, updated_at)
VALUES
  (source.user_id, source.email, source.password_hash, source.full_name, source.phone, source.role, source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO categories target
USING (SELECT 1 category_id, 'Core Products' name, 'core-products' slug, 'Minimal foundation category' description, 'ACTIVE' status FROM dual) source
ON (target.category_id = source.category_id)
WHEN MATCHED THEN UPDATE SET
  target.name = source.name, target.slug = source.slug, target.description = source.description,
  target.status = source.status, target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, source.description, source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (SELECT 1 product_id, 1 category_id, 'CORE-001' sku, 'Core Product' name, 'core-product' slug, 'Minimal foundation product' description, 100000 base_price, '/images/core-product.jpg' thumbnail_url, 'ACTIVE' status FROM dual) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.category_id = source.category_id, target.sku = source.sku, target.name = source.name,
  target.slug = source.slug, target.description = source.description, target.base_price = source.base_price,
  target.thumbnail_url = source.thumbnail_url, target.status = source.status,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, source.category_id, source.sku, source.name, source.slug, source.description,
   source.base_price, source.thumbnail_url, source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO product_inventory target
USING (SELECT 1 product_id, 20 on_hand_quantity, 0 reserved_flash_quantity, 0 version FROM dual) source
ON (target.product_id = source.product_id)
WHEN MATCHED THEN UPDATE SET
  target.on_hand_quantity = source.on_hand_quantity,
  target.reserved_flash_quantity = source.reserved_flash_quantity,
  target.version = source.version,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES
  (source.product_id, source.on_hand_quantity, source.reserved_flash_quantity, source.version,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO carts target
USING (SELECT 1 cart_id, 2 user_id FROM dual) source
ON (target.cart_id = source.cart_id)
WHEN MATCHED THEN UPDATE SET
  target.user_id = source.user_id,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT (cart_id, user_id, created_at, updated_at)
VALUES (source.cart_id, source.user_id,
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO cart_items target
USING (SELECT 1 cart_item_id, 1 cart_id, 1 product_id, 1 quantity FROM dual) source
ON (target.cart_item_id = source.cart_item_id)
WHEN MATCHED THEN UPDATE SET
  target.cart_id = source.cart_id, target.product_id = source.product_id, target.quantity = source.quantity,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT (cart_item_id, cart_id, product_id, quantity, created_at, updated_at)
VALUES (source.cart_item_id, source.cart_id, source.product_id, source.quantity,
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sales target
USING (SELECT 1 flash_sale_id, 'Core Flash Sale' name, 'Minimal relationship fixture' description, 'ENDED' status FROM dual) source
ON (target.flash_sale_id = source.flash_sale_id)
WHEN MATCHED THEN UPDATE SET
  target.name = source.name, target.description = source.description,
  target.start_at = TO_TIMESTAMP_TZ('2026-10-01T09:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
  target.end_at = TO_TIMESTAMP_TZ('2026-10-01T10:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
  target.status = source.status,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (flash_sale_id, name, description, start_at, end_at, status, created_at, updated_at)
VALUES
  (source.flash_sale_id, source.name, source.description,
   TO_TIMESTAMP_TZ('2026-10-01T09:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T10:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'), source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sale_configs target
USING (SELECT 1 flash_sale_id, 1 max_quantity_per_user, 60 reservation_timeout_seconds, 'N' allow_cancel FROM dual) source
ON (target.flash_sale_id = source.flash_sale_id)
WHEN MATCHED THEN UPDATE SET
  target.max_quantity_per_user = source.max_quantity_per_user,
  target.reservation_timeout_seconds = source.reservation_timeout_seconds,
  target.allow_cancel = source.allow_cancel,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (flash_sale_id, max_quantity_per_user, reservation_timeout_seconds, allow_cancel, created_at, updated_at)
VALUES
  (source.flash_sale_id, source.max_quantity_per_user, source.reservation_timeout_seconds, source.allow_cancel,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sale_items target
USING (SELECT 1 flash_sale_item_id, 1 flash_sale_id, 1 product_id, 80000 sale_price, 5 allocated_quantity, 1 sold_quantity, 'ACTIVE' status FROM dual) source
ON (target.flash_sale_item_id = source.flash_sale_item_id)
WHEN MATCHED THEN UPDATE SET
  target.flash_sale_id = source.flash_sale_id, target.product_id = source.product_id,
  target.sale_price = source.sale_price, target.allocated_quantity = source.allocated_quantity,
  target.sold_quantity = source.sold_quantity, target.status = source.status,
  target.updated_at = TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
WHEN NOT MATCHED THEN INSERT
  (flash_sale_item_id, flash_sale_id, product_id, sale_price, allocated_quantity, sold_quantity, status, created_at, updated_at)
VALUES
  (source.flash_sale_item_id, source.flash_sale_id, source.product_id, source.sale_price,
   source.allocated_quantity, source.sold_quantity, source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO orders target
USING (
  SELECT 1 order_id, 'CORE-NORMAL-001' order_code, 2 user_id, 'NORMAL' order_type, 'COMPLETED' status, 100000 subtotal, 0 discount_amount, 100000 total_amount, 'core-normal-001' idempotency_key,
         TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM') created_at FROM dual
  UNION ALL
  SELECT 2, 'CORE-FLASH-001', 2, 'FLASH_SALE', 'CONFIRMED', 80000, 0, 80000, 'core-flash-001',
         TO_TIMESTAMP_TZ('2026-10-01T09:30:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM') FROM dual
) source
ON (target.order_id = source.order_id)
WHEN MATCHED THEN UPDATE SET
  target.order_code = source.order_code, target.user_id = source.user_id,
  target.order_type = source.order_type, target.status = source.status,
  target.subtotal = source.subtotal, target.discount_amount = source.discount_amount,
  target.total_amount = source.total_amount, target.recipient_name = 'ShopFlash Customer',
  target.recipient_phone = '0900000002', target.shipping_address = 'HCMUT, Ho Chi Minh City',
  target.idempotency_key = source.idempotency_key,
  target.created_at = source.created_at,
  target.updated_at = source.created_at,
  target.cancelled_at = NULL
WHEN NOT MATCHED THEN INSERT
  (order_id, order_code, user_id, order_type, status, subtotal, discount_amount, total_amount,
   recipient_name, recipient_phone, shipping_address, idempotency_key, created_at, updated_at, cancelled_at)
VALUES
  (source.order_id, source.order_code, source.user_id, source.order_type, source.status,
   source.subtotal, source.discount_amount, source.total_amount, 'ShopFlash Customer', '0900000002',
   'HCMUT, Ho Chi Minh City', source.idempotency_key,
   source.created_at, source.created_at, NULL);

MERGE INTO order_items target
USING (
  SELECT 1 order_item_id, 1 order_id, 1 product_id, CAST(NULL AS NUMBER(19)) flash_sale_item_id, 100000 unit_price FROM dual
  UNION ALL
  SELECT 2, 2, 1, 1, 80000 FROM dual
) source
ON (target.order_item_id = source.order_item_id)
WHEN MATCHED THEN UPDATE SET
  target.order_id = source.order_id, target.product_id = source.product_id,
  target.flash_sale_item_id = source.flash_sale_item_id, target.product_name = 'Core Product',
  target.product_sku = 'CORE-001', target.unit_price = source.unit_price,
  target.quantity = 1, target.line_total = source.unit_price
WHEN NOT MATCHED THEN INSERT
  (order_item_id, order_id, product_id, flash_sale_item_id, product_name, product_sku, unit_price, quantity, line_total)
VALUES
  (source.order_item_id, source.order_id, source.product_id, source.flash_sale_item_id,
   'Core Product', 'CORE-001', source.unit_price, 1, source.unit_price);
