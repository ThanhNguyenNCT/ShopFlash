# Kiểm tra chức năng F8 trên giao diện

Chạy Oracle/Redis bằng Docker, backend profile `dev` và Vite trên host theo [README Transaction](../../../../backend/src/main/java/com/shopflash/modules/transaction/README.md). Kiểm thử tự động dùng schema tạm; khi thử thủ công trên schema nhóm, ghi lại tồn kho trước thao tác và chỉ đặt/hủy đơn do mình tạo.

1. Khi chưa đăng nhập, bấm **Đơn hàng** hoặc mở trực tiếp `/orders`, `/orders/:orderId`: chuyển tới form đăng nhập với `next` là đường dẫn vừa mở (gồm query/hash), không hiện trang lỗi hoặc gọi API đơn hàng. Đăng nhập Customer xong phải trở lại đúng trang đó. Tải lại với phiên còn hiệu lực không bị chuyển hướng vội; phiên hết hạn chuyển về đăng nhập. Admin chỉ nhận hướng dẫn dùng tài khoản Khách, không xem được đơn khách và không bị vòng lặp chuyển hướng. Đăng ký khách mới, đăng nhập rồi mở `/orders`: danh sách rỗng, có đường dẫn về giỏ.
2. Với khách đã có đơn, mở `/orders`: mã đơn, thời điểm, số đơn vị sản phẩm, tổng tiền và trạng thái đúng Oracle. Lọc lần lượt `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`; trang vượt cuối trả rỗng, phân trang giữ thứ tự mới trước. Khách khác không thấy đơn này.
3. Bấm một đơn: `/orders/:orderId` hiển thị người nhận, địa chỉ, tổng tiền và tên/SKU/giá/số lượng lúc mua. Đổi Product sau khi đặt không làm đổi snapshot. ID không tồn tại hoặc của người khác đều báo không tìm thấy.
4. Tạo đơn `NORMAL/PENDING` từ `/checkout`; ở trang chi tiết bấm **Hủy đơn**, xác nhận. Trang báo `Đã hủy`, có thời điểm hủy và không còn nút hủy. Kiểm tra Oracle sau COMMIT bằng kết nối độc lập: `status='CANCELLED'`, `cancelled_at IS NOT NULL`, tồn kho tăng đúng lượng đã mua, giỏ không được nạp lại.
5. Tải lại trang rồi thử gọi API hủy lần hai: nhận `409 ORDER_NOT_CANCELLABLE`, tồn kho không tăng lần hai. Đơn `FLASH_SALE`, `CONFIRMED`, `COMPLETED` không có nút hủy; API cũng trả `409`.
6. Giữ trang chi tiết của đơn `PENDING`, để yêu cầu khác hủy đơn trước rồi bấm hủy ở trang cũ: giao diện báo đơn không còn đủ điều kiện và tải lại trạng thái, không báo hủy thành công sai. Nếu mất phản hồi, trang đọc lại chi tiết trước khi cho quyết định thao tác tiếp.

Đối chiếu bằng bind variable `:orderId` trên **kết nối Oracle khác**:

```sql
SELECT order_id, user_id, order_type, status, cancelled_at
FROM orders WHERE order_id = :orderId;

SELECT oi.product_id, oi.quantity, oi.product_name, oi.product_sku, oi.unit_price,
       i.on_hand_quantity, i.reserved_flash_quantity, i.version
FROM order_items oi JOIN product_inventory i ON i.product_id = oi.product_id
WHERE oi.order_id = :orderId ORDER BY oi.product_id;
```

Rà soát bố cục và màn 375px được thực hiện ở cuối change theo task 6.4; lỗi làm không thể thao tác chức năng cần sửa ngay.
