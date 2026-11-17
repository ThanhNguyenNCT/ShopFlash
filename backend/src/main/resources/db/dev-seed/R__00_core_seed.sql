-- Dữ liệu demo ứng dụng, chỉ chạy với profile dev, sau R__00_catalog_seed.sql.
-- Tài khoản/giỏ chỉ tạo khi chưa có; không tự thêm CART_ITEMS hoặc đặt lại tồn kho.
-- Hai đơn mẫu riêng (ID 1/2, CORE-*) lấy snapshot tên/SKU/giá từ catalog:
--   NORMAL/COMPLETED: AirPods Max (2101), giá catalog.
--   FLASH_SALE/CONFIRMED: iPhone 13 Pro (2123), giá sale = 80% giá catalog.
-- Flash Sale #1 đã ENDED, allocated=5/sold=1, reserved=0; không tạo runtime sale Redis.
-- Stock catalog là tồn hiện có sau dữ liệu demo; không trừ lại khi seed chạy lại.
-- Chỉ hai đơn mẫu được cập nhật khi file seed đổi; không đồng bộ lại đơn thật của khách.
-- Tài khoản local: admin@shopflash.local / customer@shopflash.local, ShopFlash123!.

DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM products p JOIN categories c ON c.category_id=p.category_id
  JOIN product_inventory i ON i.product_id=p.product_id
  WHERE p.product_id IN (2101, 2123) AND p.status='ACTIVE' AND c.status='ACTIVE' AND p.base_price>0;
  IF n <> 2 THEN RAISE_APPLICATION_ERROR(-20001, 'Chay catalog seed truoc core seed'); END IF;
  SELECT COUNT(*) INTO n FROM orders WHERE order_id IN (1,2)
    AND (user_id <> 2 OR (order_id=1 AND order_code<>'CORE-NORMAL-001')
         OR (order_id=2 AND order_code<>'CORE-FLASH-001'));
  IF n <> 0 THEN RAISE_APPLICATION_ERROR(-20002, 'ID don demo bi du lieu khac chiem; khong ghi de'); END IF;
END;
/

MERGE INTO users target
USING (
  SELECT 1 user_id, 'admin@shopflash.local' email, '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O' password_hash, 'ShopFlash Admin' full_name, '0900000001' phone, 'ADMIN' role, 'ACTIVE' status FROM dual
  UNION ALL
  SELECT 2, 'customer@shopflash.local', '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O', 'ShopFlash Customer', '0900000002', 'CUSTOMER', 'ACTIVE' FROM dual
) source
ON (target.user_id = source.user_id)
WHEN NOT MATCHED THEN INSERT
  (user_id, email, password_hash, full_name, phone, role, status, created_at, updated_at)
VALUES
  (source.user_id, source.email, source.password_hash, source.full_name, source.phone, source.role, source.status,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO carts target
USING (SELECT 1 cart_id, 2 user_id FROM dual) source
ON (target.cart_id = source.cart_id)
WHEN NOT MATCHED THEN INSERT (cart_id, user_id, created_at, updated_at)
VALUES (source.cart_id, source.user_id,
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
  TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sales target
USING (SELECT 1 flash_sale_id, 'Flash Sale iPhone 13 Pro' name, 'Demo catalog checkout' description, 'ENDED' status FROM dual) source
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
USING (SELECT 1 flash_sale_item_id, 1 flash_sale_id, p.product_id,
              ROUND(p.base_price * 0.8) sale_price, 5 allocated_quantity, 1 sold_quantity, 'ACTIVE' status
       FROM products p WHERE p.product_id=2123) source
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
  SELECT 1 order_id, 'CORE-NORMAL-001' order_code, 2 user_id, 'NORMAL' order_type,
         'COMPLETED' status, p.base_price subtotal, 0 discount_amount, p.base_price total_amount,
         'core-normal-001' idempotency_key,
         TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM') created_at
  FROM products p WHERE p.product_id=2101
  UNION ALL
  SELECT 2, 'CORE-FLASH-001', 2, 'FLASH_SALE', 'CONFIRMED', f.sale_price, 0, f.sale_price,
         'core-flash-001', TO_TIMESTAMP_TZ('2026-10-01T09:30:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
  FROM flash_sale_items f WHERE f.flash_sale_item_id=1 AND f.product_id=2123
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
  SELECT o.order_id order_item_id, o.order_id, p.product_id,
         CASE WHEN o.order_type='FLASH_SALE' THEN 1 END flash_sale_item_id,
         p.name product_name, p.sku product_sku, o.total_amount unit_price
  FROM orders o JOIN products p ON p.product_id=CASE WHEN o.order_id=1 THEN 2101 ELSE 2123 END
  WHERE o.order_id IN (1,2)
) source
ON (target.order_item_id = source.order_item_id)
WHEN MATCHED THEN UPDATE SET
  target.order_id = source.order_id, target.product_id = source.product_id,
  target.flash_sale_item_id = source.flash_sale_item_id, target.product_name = source.product_name,
  target.product_sku = source.product_sku, target.unit_price = source.unit_price,
  target.quantity = 1, target.line_total = source.unit_price
WHEN NOT MATCHED THEN INSERT
  (order_item_id, order_id, product_id, flash_sale_item_id, product_name, product_sku, unit_price, quantity, line_total)
VALUES
  (source.order_item_id, source.order_id, source.product_id, source.flash_sale_item_id,
   source.product_name, source.product_sku, source.unit_price, 1, source.unit_price);

-- Hậu kiểm riêng dữ liệu demo, không kiểm tra/ghi lại snapshot đơn người dùng khác.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM orders o
  WHERE o.order_id IN (1,2) AND o.subtotal=(SELECT SUM(oi.line_total) FROM order_items oi WHERE oi.order_id=o.order_id)
    AND o.total_amount=o.subtotal-o.discount_amount;
  IF n<>2 THEN RAISE_APPLICATION_ERROR(-20003, 'Tong don demo khong khop order items'); END IF;
  SELECT COUNT(*) INTO n FROM order_items oi JOIN products p ON p.product_id=oi.product_id
  WHERE oi.order_id IN (1,2) AND oi.product_name=p.name AND oi.product_sku=p.sku AND oi.quantity=1
    AND ((oi.order_id=1 AND oi.product_id=2101 AND oi.flash_sale_item_id IS NULL AND oi.unit_price=p.base_price)
      OR (oi.order_id=2 AND oi.product_id=2123 AND oi.flash_sale_item_id=1
          AND oi.unit_price=(SELECT sale_price FROM flash_sale_items WHERE flash_sale_item_id=1)));
  IF n<>2 THEN RAISE_APPLICATION_ERROR(-20004, 'Snapshot don demo khong khop catalog'); END IF;
END;
/
