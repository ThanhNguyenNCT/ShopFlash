# F7 — Đặt hàng từ giỏ Oracle

F7 chỉ tạo đơn `NORMAL/PENDING` cho khách `CUSTOMER` có JWT hợp lệ. Giỏ hàng là dữ liệu Oracle của F10; việc thêm hàng không giữ tồn. F8 xem và hủy đơn được triển khai ở milestone sau.

## API

`GET /api/checkout/preview` trả `cartVersion`, `lines` và `totalAmount`. Mỗi dòng có `productId`, `name`, `sku`, `quantity`, `unitPrice`, `lineTotal`, `status`, `availableQuantity = on_hand_quantity - reserved_flash_quantity`. `warnings` chứa `CART_EMPTY`, `PRODUCT_UNAVAILABLE` hoặc `OUT_OF_STOCK` khi cần. API chỉ đọc, không giữ hàng; giá/tồn phải được kiểm tra lại khi xác nhận.

`POST /api/orders` cần header `Idempotency-Key` là UUID và body đúng thông tin vừa xác nhận:

```json
{
  "cartVersion": 0,
  "recipientName": "Khách thử",
  "recipientPhone": "0900000000",
  "shippingAddress": "Địa chỉ giao hàng",
  "expectedLines": [{ "productId": 1, "quantity": 1, "unitPrice": "100000.00" }]
}
```

Phản hồi thành công là `{orderId, orderCode, status, totalAmount}`. Khóa và nội dung đã chuẩn hóa giống nhau trả lại cùng đơn. Tên/số điện thoại/địa chỉ được chuẩn hóa Unicode NFC và bỏ khoảng trắng ở hai đầu; danh sách mặt hàng sắp theo ID, giá dùng hai chữ số thập phân trước khi băm SHA-256. Khóa thuộc yêu cầu hoặc khách khác trả `409 IDEMPOTENCY_CONFLICT`, không tiết lộ đơn.

Các lỗi trước khi ghi gồm `CART_EMPTY`, `CART_CHANGED`, `PRODUCT_UNAVAILABLE`, `PRICE_CHANGED`, `OUT_OF_STOCK`. Giao dịch khóa `CARTS` → toàn bộ `PRODUCTS` theo ID tăng → toàn bộ `PRODUCT_INVENTORY` theo ID tăng, rồi mới ghi đơn, dòng hàng, giảm tồn có điều kiện và dọn giỏ. Tiền dùng `BigDecimal`, ảnh chụp tên/SKU/giá nằm trong `ORDER_ITEMS`. Mọi bước cùng `COMMIT` hoặc cùng `ROLLBACK`.

`LOCK_TIMEOUT`, `DEADLOCK`, `RETRY_SAME_REQUEST` và `ORDER_CODE_CONFLICT` cho phép gửi lại **cùng khóa và body** sau khi giao dịch lỗi đã rollback. Mất phản hồi quanh `COMMIT` không chứng minh rollback: frontend giữ bản ghi `{principalUserId, idempotencyKey, body}` trong `sessionStorage` trước khi gửi; tải lại cùng tab hoặc hết thời gian chờ thì gửi lại đúng bản ghi đó. Backend trả đơn đã lưu hoặc thử lại sau rollback. Frontend chỉ xóa bản ghi khi có `orderId` hoặc lỗi nghiệp vụ xác định chưa commit. Nếu đổi tài khoản, phải đăng nhập lại đúng chủ yêu cầu trước khi gửi lại. Đóng tab sẽ mất `sessionStorage`; kiểm tra lịch sử đơn khi F8 có mặt trước khi bắt đầu yêu cầu mới.

## Kiểm thử

Trên schema Oracle đã có Flyway V2 và Redis local:

```bash
cd backend
./mvnw test -Dtest=CheckoutCoreTest
./mvnw test -Dtest=CheckoutOracleIT
cd ../frontend
npm run build
```

`CheckoutOracleIT` dùng fixture riêng, không bọc cả bài test trong `@Transactional`; kiểm tra trạng thái sau `COMMIT`/`ROLLBACK` qua kết nối Oracle độc lập. Test dùng điểm đồng bộ có thời hạn cho request đồng thời và bean `CheckoutProbe` chỉ đăng ký trong test để gây lỗi sau từng giai đoạn ghi. Bài test có thể chạy trên schema Oracle tạm để tránh dữ liệu nhóm khác. Hướng dẫn kiểm tra chức năng giao diện ở `frontend/src/modules/transaction/CHECKOUT_UI_CHECKLIST.md`.
