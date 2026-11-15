-- Dữ liệu mẫu module concurrency (F9), chỉ chạy ở profile dev. ID 9001-9500 (khách), 9101-9104 (danh mục,
-- sản phẩm, chương trình, sản phẩm trong chương trình). Module transaction dùng ID 101-106.
-- Chỉ thêm khi chưa có (WHEN NOT MATCHED), để chạy lại không xóa suất đã bán và đơn đã tạo trong lúc demo.
--
-- Kịch bản (giữ đúng bất biến DATABASE_DESIGN.md §14, view v_inventory_invariant_check không có dòng nào):
--   Chương trình 9101 ACTIVE từ 2026-10-01 đến 2027-12-31, 3 sản phẩm với quota 10, 50, 100, chưa bán suất nào,
--   nên reserved_flash_quantity của sản phẩm 9101-9103 bằng đúng quota. Khung giờ dài để lần chạy nào cũng còn hiệu lực.
--   Chương trình 9102 DRAFT (12.12) với sản phẩm 9104, để demo màn hình quản trị; DRAFT chưa giữ hàng.
--   500 khách 9001-9500 (cc001@shopflash.local ... cc500@shopflash.local, mật khẩu ShopFlash123!) để thử giới hạn
--   mỗi người và test tải. Không dùng sản phẩm 1 của seed lõi: seed lõi đặt lại tồn kho của nó mỗi khi chạy lại.

MERGE INTO users target
USING (
  SELECT 9000 + LEVEL user_id,
         'cc' || LPAD(LEVEL, 3, '0') || '@shopflash.local' email,
         '$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O' password_hash,
         'Khách thử flash sale ' || LPAD(LEVEL, 3, '0') full_name
  FROM dual CONNECT BY LEVEL <= 500
) source
ON (target.user_id = source.user_id)
WHEN NOT MATCHED THEN INSERT
  (user_id, email, password_hash, full_name, phone, role, status, created_at, updated_at)
VALUES
  (source.user_id, source.email, source.password_hash, source.full_name, NULL, 'CUSTOMER', 'ACTIVE',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO categories target
USING (SELECT 9101 category_id, 'Gia dụng và phụ kiện' name, 'cc-gia-dung-phu-kien' slug FROM dual) source
ON (target.category_id = source.category_id)
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, 'Dữ liệu mẫu Flash sale (dev)', 'ACTIVE',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO products target
USING (
  SELECT 9101 product_id, 'CC-9101' sku, 'Nồi chiên không dầu 5L' name, 'cc-noi-chien-khong-dau-5l' slug, 1990000 base_price FROM dual
  UNION ALL SELECT 9102, 'CC-9102', 'Tai nghe chống ồn', 'cc-tai-nghe-chong-on', 2490000 FROM dual
  UNION ALL SELECT 9103, 'CC-9103', 'Bình giữ nhiệt 500ml', 'cc-binh-giu-nhiet-500ml', 390000 FROM dual
  UNION ALL SELECT 9104, 'CC-9104', 'Bàn phím cơ không dây', 'cc-ban-phim-co-khong-day', 1290000 FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
VALUES
  (source.product_id, 9101, source.sku, source.name, source.slug, 'Dữ liệu mẫu Flash sale (dev)',
   source.base_price, NULL, 'ACTIVE',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

-- reserved_flash_quantity = quota của chương trình 9101 (chưa bán suất nào); 9104 chỉ nằm trong chương trình DRAFT.
MERGE INTO product_inventory target
USING (
  SELECT 9101 product_id, 200 on_hand_quantity, 10 reserved_flash_quantity FROM dual
  UNION ALL SELECT 9102, 300, 50 FROM dual
  UNION ALL SELECT 9103, 500, 100 FROM dual
  UNION ALL SELECT 9104, 100, 0 FROM dual
) source
ON (target.product_id = source.product_id)
WHEN NOT MATCHED THEN INSERT
  (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
VALUES
  (source.product_id, source.on_hand_quantity, source.reserved_flash_quantity, 0,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sales target
USING (
  SELECT 9101 flash_sale_id, 'Flash sale cuối tuần' name, 'Chương trình mẫu đang chạy (dev)' description,
         '2026-10-01T09:00:00 +07:00' start_at, '2027-12-31T23:59:59 +07:00' end_at, 'ACTIVE' status FROM dual
  UNION ALL
  SELECT 9102, 'Flash sale 12.12', 'Chương trình mẫu chưa lên lịch (dev)',
         '2026-12-12T09:00:00 +07:00', '2026-12-12T11:00:00 +07:00', 'DRAFT' FROM dual
) source
ON (target.flash_sale_id = source.flash_sale_id)
WHEN NOT MATCHED THEN INSERT
  (flash_sale_id, name, description, start_at, end_at, status, version, created_at, updated_at)
VALUES
  (source.flash_sale_id, source.name, source.description,
   TO_TIMESTAMP_TZ(source.start_at, 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ(source.end_at, 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   source.status, 0,
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sale_configs target
USING (
  SELECT 9101 flash_sale_id, 1 max_quantity_per_user, 60 reservation_timeout_seconds FROM dual
  UNION ALL SELECT 9102, 2, 120 FROM dual
) source
ON (target.flash_sale_id = source.flash_sale_id)
WHEN NOT MATCHED THEN INSERT
  (flash_sale_id, max_quantity_per_user, reservation_timeout_seconds, allow_cancel, created_at, updated_at)
VALUES
  (source.flash_sale_id, source.max_quantity_per_user, source.reservation_timeout_seconds, 'N',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

MERGE INTO flash_sale_items target
USING (
  SELECT 9101 flash_sale_item_id, 9101 flash_sale_id, 9101 product_id, 990000 sale_price, 10 allocated_quantity FROM dual
  UNION ALL SELECT 9102, 9101, 9102, 1290000, 50 FROM dual
  UNION ALL SELECT 9103, 9101, 9103, 149000, 100 FROM dual
  UNION ALL SELECT 9104, 9102, 9104, 690000, 20 FROM dual
) source
ON (target.flash_sale_item_id = source.flash_sale_item_id)
WHEN NOT MATCHED THEN INSERT
  (flash_sale_item_id, flash_sale_id, product_id, sale_price, allocated_quantity, sold_quantity, status,
   created_at, updated_at)
VALUES
  (source.flash_sale_item_id, source.flash_sale_id, source.product_id, source.sale_price,
   source.allocated_quantity, 0, 'ACTIVE',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));
