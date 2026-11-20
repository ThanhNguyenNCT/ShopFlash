-- Xóa dữ liệu do 01_generate.sql sinh ra (bảng con trước), KHÔNG commit.
-- 01_generate.sql gọi file này để xóa + sinh lại trong cùng một transaction: phiên khác vẫn thấy
-- dữ liệu cũ (read consistency) cho tới khi commit cuối cùng, không thấy trạng thái trống giữa chừng.
-- Muốn chỉ xóa dữ liệu sinh: chạy 99_cleanup.sql (có commit).
-- Dấu hiệu nhận biết: order_code 'GEN-%', sku/slug 'gen-%', email '%@gen.shopflash.local'.
-- Nếu ứng dụng đã tạo giỏ hàng/đơn/flash sale tham chiếu tới dữ liệu sinh, lệnh DELETE sẽ báo lỗi khóa ngoại
-- và script dừng: dữ liệu đó không do script sinh nên script không tự xóa.
prompt == Xoa du lieu sinh cu ==
DELETE FROM order_items
WHERE  order_id IN (SELECT order_id FROM orders WHERE order_code LIKE 'GEN-%');
DELETE FROM orders WHERE order_code LIKE 'GEN-%';
DELETE FROM product_inventory
WHERE  product_id IN (SELECT product_id FROM products WHERE sku LIKE 'gen-%');
DELETE FROM products   WHERE sku   LIKE 'gen-%';
DELETE FROM categories WHERE slug  LIKE 'gen-%';
DELETE FROM users      WHERE email LIKE '%@gen.shopflash.local';
