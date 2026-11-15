-- Module concurrency (F9 Flash sale). Thiết kế: backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md.

-- Admin sửa chương trình phải gửi version đang thấy (optimistic lock); lệch thì 409 FLASH_SALE_CHANGED.
ALTER TABLE flash_sales ADD (version NUMBER(10) DEFAULT 0 NOT NULL);

-- Job đổi trạng thái và GET /api/flash-sales/active lọc theo status rồi start_at.
CREATE INDEX ix_flash_sales_status_start ON flash_sales (status, start_at);

-- Module giữ hàng trong kho từ lúc SCHEDULED (không đợi ACTIVE) để đến giờ mở bán chắc chắn còn hàng,
-- nên bất biến DATABASE_DESIGN.md §14 tính cả chương trình SCHEDULED. Đúng thì view không có dòng nào.
CREATE OR REPLACE VIEW v_inventory_invariant_check AS
SELECT COALESCE(i.product_id, a.product_id) AS product_id,
       i.reserved_flash_quantity,
       NVL(a.expected_reserved, 0) AS expected_reserved
FROM   product_inventory i
FULL JOIN (
  SELECT fi.product_id,
         SUM(fi.allocated_quantity - fi.sold_quantity) AS expected_reserved
  FROM   flash_sale_items fi
  JOIN   flash_sales fs ON fs.flash_sale_id = fi.flash_sale_id
  WHERE  fs.status IN ('SCHEDULED', 'ACTIVE')
  AND    fi.status = 'ACTIVE'
  GROUP BY fi.product_id
) a ON a.product_id = i.product_id
WHERE  i.product_id IS NULL
OR     i.reserved_flash_quantity <> NVL(a.expected_reserved, 0);
