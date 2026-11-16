# Kiểm tra chức năng F7 Checkout

Chạy Oracle/Redis bằng Docker, backend profile `dev` và Vite trên host như [README Transaction](../../../../backend/docs/transaction/README.md). Chỉ dùng dữ liệu thử riêng hoặc schema tạm để tránh sửa giỏ/đơn của nhóm. Giao diện được kiểm tra về chức năng tại mốc F7; bố cục, màu và màn 375px được rà soát cuối change.

1. Đăng nhập khách; trên schema dev mới, `/cart` trống. Thêm ba sản phẩm còn hàng từ Listing/Detail nhưng chưa chọn dòng nào. Mở thẳng `/checkout`: trang yêu cầu quay lại chọn sản phẩm. Chọn hai dòng trên Cart rồi mở Checkout; chỉ hai dòng đó xuất hiện với giá, số lượng, tồn có thể bán và tổng tiền tương ứng.
2. Điền người nhận, số điện thoại, địa chỉ rồi bấm **Xác nhận đặt hàng** một lần. Giao diện báo mã đơn và tổng tiền; dùng kết nối Oracle khác xác nhận một `ORDERS NORMAL/PENDING`, `ORDER_ITEMS` chỉ có hai dòng đã chọn, hai sản phẩm đó bị trừ tồn và biến mất khỏi `CART_ITEMS`, còn dòng thứ ba không chọn giữ nguyên. Không sửa tồn kho thủ công để giả lập kết quả.
3. Khi giỏ, giá, trạng thái sản phẩm hoặc tồn kho thay đổi sau khi xem trước, lệnh xác nhận phải trả lỗi phù hợp và không tạo đơn. Trang tải lại xem trước trước khi cho xác nhận mới.
4. Mô phỏng mất phản hồi sau khi server đã nhận `POST /api/orders`, rồi tải lại `/checkout` **trong cùng tab**. `sessionStorage` vẫn giữ `principalUserId`, `idempotencyKey` và body đã gửi; bấm **Kiểm tra và gửi lại yêu cầu** chỉ gửi đúng khóa/body cũ. Oracle vẫn có nhiều nhất một đơn và tồn kho chỉ giảm một lần.
5. Khi còn yêu cầu đang chờ, đăng xuất hoặc đăng nhập khách khác: trang không gửi lại bằng danh tính mới. Đăng nhập lại đúng khách để giải quyết yêu cầu. Không lưu JWT, mật khẩu hoặc body đang chờ vào `localStorage`, URL hay log.
6. Sau `LOCK_TIMEOUT`/`DEADLOCK`/lỗi mạng, giữ nguyên yêu cầu đang chờ để gửi lại. Sau lỗi nghiệp vụ xác định chưa commit (`CART_CHANGED`, `PRICE_CHANGED`, `OUT_OF_STOCK`, `PRODUCT_UNAVAILABLE`, `CART_EMPTY`), xóa bản ghi chờ, tải lại xem trước rồi mới cho xác nhận nội dung mới.

Để đối chiếu, truy vấn bằng biến bind `:email` và `:productId` trên **kết nối Oracle khác** sau từng bước:

```sql
SELECT order_id, order_code, status, total_amount, idempotency_key
FROM orders WHERE user_id = (SELECT user_id FROM users WHERE email = :email)
ORDER BY order_id DESC;

SELECT c.version, ci.product_id, ci.quantity FROM carts c
LEFT JOIN cart_items ci ON ci.cart_id = c.cart_id
WHERE c.user_id = (SELECT user_id FROM users WHERE email = :email);

SELECT on_hand_quantity, reserved_flash_quantity, version
FROM product_inventory WHERE product_id = :productId;
```
