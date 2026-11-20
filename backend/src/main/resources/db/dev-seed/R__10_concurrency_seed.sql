-- Dữ liệu mẫu module concurrency (F9), chỉ chạy ở profile dev, sau R__00_catalog_seed (Flyway chạy repeatable theo tên).
-- ID 9001-9500 (khách), 9101 (danh mục cho test), 9111-9112 (chương trình), 9111-9114 (sản phẩm trong chương trình).
-- Chạy lại không xóa suất đã bán và đơn đã tạo trong lúc demo: chỉ thêm khi chưa có.
--
-- Kịch bản (giữ đúng bất biến DATABASE_DESIGN.md §14, view v_inventory_invariant_check không có dòng nào):
--   Chương trình 9111 ACTIVE từ 2026-10-01 đến hết 2026-11-30 trên 3 sản phẩm có ảnh của seed catalog: Apple Airpods
--   (10 suất), Boxed Blender (50), Lunch Box (100). Qua ngày đó trang flash sale sẽ trống: muốn demo tiếp thì tạo
--   chương trình mới ở trang quản trị, hoặc lùi end_at ở đây kèm lệnh cập nhật cho DB đã có chương trình 9111.
--   Chương trình 9112 DRAFT (12.12) với Microwave Oven, để demo màn hình quản trị; DRAFT chưa giữ hàng.
--   Lúc thêm sản phẩm vào chương trình đang chạy, seed nhập thêm đúng số suất vào kho (on_hand += quota) rồi giữ
--   chừng đó cho flash sale (reserved += quota). Số hàng bán thường của các module khác vì vậy không đổi.
--   Không dùng sản phẩm 2101 và 2123: seed lõi dùng chúng cho đơn mẫu và Flash Sale #1.
--   500 khách 9001-9500 (cc001@shopflash.local ... cc500@shopflash.local, mật khẩu ShopFlash123!) để thử giới hạn
--   mỗi người và test tải.
--
-- Bản seed trước dùng sản phẩm riêng 9101-9104 không có ảnh (chương trình 9101 ACTIVE, 9102 DRAFT). DB đã chạy bản đó
-- được dọn ở cuối file.

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

-- Test tích hợp của module (FlashAdminIT, FlashCheckoutIT) tạo sản phẩm tạm trong danh mục này. INACTIVE để cửa hàng
-- không hiện danh mục rỗng.
MERGE INTO categories target
USING (SELECT 9101 category_id, 'Flash sale (test)' name, 'cc-flash-sale-test' slug FROM dual) source
ON (target.category_id = source.category_id)
WHEN MATCHED THEN UPDATE SET
  target.name = source.name, target.slug = source.slug,
  target.description = 'Danh mục cho test tích hợp module concurrency (dev)', target.status = 'INACTIVE',
  target.updated_at = SYSTIMESTAMP
WHEN NOT MATCHED THEN INSERT
  (category_id, name, slug, description, status, created_at, updated_at)
VALUES
  (source.category_id, source.name, source.slug, 'Danh mục cho test tích hợp module concurrency (dev)', 'INACTIVE',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n
  FROM   products p JOIN product_inventory i ON i.product_id = p.product_id
  WHERE  p.product_id IN (2051, 2065, 2066, 2100) AND p.status = 'ACTIVE';
  IF n <> 4 THEN
    RAISE_APPLICATION_ERROR(-20101, 'Chay catalog seed truoc concurrency seed (thieu san pham 2051, 2065, 2066, 2100)');
  END IF;
END;
/

MERGE INTO flash_sales target
USING (
  SELECT 9111 flash_sale_id, 'Flash sale cuối tuần' name, 'Săn deal giá sốc, mỗi khách một suất' description,
         '2026-10-01T09:00:00 +07:00' start_at, '2026-11-30T23:59:59 +07:00' end_at, 'ACTIVE' status FROM dual
  UNION ALL
  SELECT 9112, 'Flash sale 12.12', 'Chương trình mẫu chưa lên lịch (dev)',
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
  SELECT 9111 flash_sale_id, 1 max_quantity_per_user, 60 reservation_timeout_seconds FROM dual
  UNION ALL SELECT 9112, 2, 120 FROM dual
) source
ON (target.flash_sale_id = source.flash_sale_id)
WHEN NOT MATCHED THEN INSERT
  (flash_sale_id, max_quantity_per_user, reservation_timeout_seconds, allow_cancel, created_at, updated_at)
VALUES
  (source.flash_sale_id, source.max_quantity_per_user, source.reservation_timeout_seconds, 'N',
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
   TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'));

-- Thêm item và tồn kho trong cùng một bước, chỉ khi item chưa có, để chạy lại không cộng kho lần nữa.
DECLARE
  PROCEDURE add_item(p_item NUMBER, p_sale NUMBER, p_product NUMBER, p_price NUMBER, p_quota NUMBER) IS
    v_status flash_sales.status%TYPE;
  BEGIN
    INSERT INTO flash_sale_items
      (flash_sale_item_id, flash_sale_id, product_id, sale_price, allocated_quantity, sold_quantity, status,
       created_at, updated_at)
    SELECT p_item, p_sale, p_product, p_price, p_quota, 0, 'ACTIVE',
           TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM'),
           TO_TIMESTAMP_TZ('2026-10-01T08:00:00 +07:00', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
    FROM   dual
    WHERE  NOT EXISTS (SELECT 1 FROM flash_sale_items WHERE flash_sale_item_id = p_item);
    IF SQL%ROWCOUNT = 0 THEN
      RETURN;
    END IF;
    SELECT status INTO v_status FROM flash_sales WHERE flash_sale_id = p_sale;
    IF v_status IN ('SCHEDULED', 'ACTIVE') THEN
      UPDATE product_inventory
      SET    on_hand_quantity = on_hand_quantity + p_quota,
             reserved_flash_quantity = reserved_flash_quantity + p_quota,
             version = version + 1, updated_at = SYSTIMESTAMP
      WHERE  product_id = p_product;
    END IF;
  END;
BEGIN
  add_item(9111, 9111, 2100, 1990000, 10);   -- Apple Airpods, giá thường 3.250.000
  add_item(9112, 9111, 2051, 490000, 50);    -- Boxed Blender, giá thường 1.000.000
  add_item(9113, 9111, 2065, 129000, 100);   -- Lunch Box, giá thường 325.000
  add_item(9114, 9112, 2066, 1490000, 20);   -- Microwave Oven, giá thường 2.250.000 (DRAFT, chưa giữ hàng)
END;
/

-- Dọn dữ liệu của bản seed trước (không có dòng nào thì các lệnh dưới không làm gì).
-- Chương trình 9101: chỉ đặt end_at = bây giờ, như admin dừng sớm. Cửa hàng thôi hiện ngay; job của backend chuyển
-- sang ENDED sau thời gian giữ suất + mốc an toàn, trả phần chưa bán về kho và xóa runtime Redis đúng quy trình.
UPDATE flash_sales
SET    end_at = SYSTIMESTAMP, version = version + 1, updated_at = SYSTIMESTAMP
WHERE  flash_sale_id = 9101 AND status = 'ACTIVE' AND end_at > SYSTIMESTAMP;

UPDATE flash_sales
SET    status = 'CANCELLED', version = version + 1, updated_at = SYSTIMESTAMP
WHERE  flash_sale_id = 9102 AND status = 'DRAFT';

-- Sản phẩm cũ không có ảnh: ngừng bán (giữ lại vì đơn cũ còn tham chiếu).
UPDATE products
SET    status = 'INACTIVE', updated_at = SYSTIMESTAMP
WHERE  product_id BETWEEN 9101 AND 9104 AND sku LIKE 'CC-91%' AND status = 'ACTIVE';
