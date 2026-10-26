# Hợp đồng Transaction (F10, F7, F8)

Tài liệu này chốt giao diện giữa backend và frontend của module Transaction theo OpenSpec change `transaction-checkout-orders-study`. Oracle là nguồn dữ liệu nghiệp vụ. F10 và route `/cart` đã được bàn giao cho TV4. F0 cũng được TV5 bàn giao cho TV4 và dùng JWT; F9 và thanh toán nằm ngoài phạm vi.

## Danh tính và lỗi

- Mọi API dưới đây lấy khách hàng từ danh tính **đã được backend kiểm tra**. Không nhận `userId` trong body, query hoặc header do trình duyệt tự khai. Thiếu danh tính trả `401`; tài khoản không phải `CUSTOMER` đang `ACTIVE` bị từ chối.
- F0 cấp JWT qua `POST /api/auth/login`; Spring Security xác minh token, rồi tra `USERS` Oracle để kiểm tra vai trò/trạng thái hiện tại. Frontend lấy hồ sơ qua `GET /api/auth/me` và tự gắn Bearer cho Cart/Checkout/Order. Không có token local hoặc endpoint đối chiếu danh tính giả lập.
- Lỗi dùng envelope chung `{ "error": { "code", "message", "details" } }` của `GlobalExceptionHandler`; thông điệp tiếng Việt và không chứa lỗi gốc Oracle/Redis. `400 VALIDATION_ERROR` cho dữ liệu sai; `401 AUTH_REQUIRED` cho thiếu/sai token hoặc tài khoản bị vô hiệu hóa; `403 ACCESS_DENIED` cho sai vai trò ở Spring Security; `404` cho mặt hàng/đơn không tìm thấy hoặc không thuộc khách; `409` cho xung đột nghiệp vụ. Hết thời gian chờ khóa và deadlock có mã riêng `LOCK_TIMEOUT`/`DEADLOCK`, chỉ hướng dẫn thử lại khi giao dịch lỗi đã rollback.
- Mọi SQL có tham số dùng biến bind qua `NamedParameterJdbcTemplate`/JDBC; không nối giá trị đầu vào vào SQL.

## F10 — Giỏ hàng

Ví dụ yêu cầu/phản hồi, giới hạn và cách xử lý timeout: xem [CART.md](CART.md).

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/cart` | Không có `userId` | `{version, items, totalAmount}`; giỏ chưa có trả `version=0`, `items=[]`, `totalAmount=0` mà không ghi Oracle |
| `POST` | `/api/cart/items` | `X-Cart-Version`, `{productId, quantity}` | Tạo giỏ khi cần; nếu đã có sản phẩm thì cộng số lượng; trả giỏ và phiên bản mới |
| `PATCH` | `/api/cart/items/{productId}` | `X-Cart-Version`, `{quantity}` | Đặt số lượng tuyệt đối, trả giỏ và phiên bản mới |
| `DELETE` | `/api/cart/items/{productId}` | `X-Cart-Version` | Xóa một dòng, trả giỏ và phiên bản mới |
| `DELETE` | `/api/cart/items` | `X-Cart-Version` | Xóa mọi dòng, giữ `CARTS` để duy trì phiên bản; trả giỏ |

`items` chứa `productId`, tên/SKU, `quantity`, giá và thành tiền **tham khảo** từ Oracle. Số lượng mỗi dòng từ 1 đến 999; thêm sản phẩm đã có mà tổng vượt 999 bị từ chối. Mọi lệnh sửa giỏ là giao dịch Oracle: khóa `CARTS FOR UPDATE`, kiểm tra `X-Cart-Version`, sửa dòng và tăng `CARTS.version` một lần. Phiên bản cũ trả `409 CART_CHANGED`, không ghi đè; giỏ chưa có dùng phiên bản 0. Sản phẩm thêm vào phải tồn tại và `ACTIVE`; giá và tồn kho không được giữ khi thêm giỏ. Sau timeout của lệnh **cộng** hàng, client tải lại giỏ trước khi quyết định gửi lệnh khác.

## F7 — Đặt hàng thông thường

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/checkout/preview` | Không có `userId` | `{cartVersion, lines, totalAmount, warnings}`; chỉ đọc giá, trạng thái và tồn kho Oracle, không giữ hàng |
| `POST` | `/api/orders` | `Idempotency-Key` là UUID; `{cartVersion, recipientName, recipientPhone, shippingAddress, expectedLines:[{productId,quantity,unitPrice}]}` | `{orderId, orderCode, status, totalAmount}`; cùng khóa và cùng nội dung trả cùng đơn |

Checkout kiểm tra lại giỏ, giá, trạng thái và `on_hand_quantity - reserved_flash_quantity`. Thứ tự khóa trong giao dịch là **`CARTS` → `PRODUCTS` theo `product_id` tăng dần → `PRODUCT_INVENTORY` theo `product_id` tăng dần**. F2 cập nhật Product/Inventory hiện có dùng khóa/cột `version`; F9 cần thống nhất thứ tự trước khi tích hợp. Đơn `NORMAL/PENDING`, dòng hàng, giảm tồn kho, xóa giỏ và tăng phiên bản giỏ cùng `COMMIT` hoặc cùng `ROLLBACK`; tiền tính bằng `BigDecimal` với hai chữ số thập phân.

Các lỗi nghiệp vụ cần phân biệt: `CART_EMPTY`, `CART_CHANGED`, `PRODUCT_UNAVAILABLE`, `PRICE_CHANGED`, `OUT_OF_STOCK`, `IDEMPOTENCY_CONFLICT`. Khóa đã thuộc khách khác cũng trả `IDEMPOTENCY_CONFLICT` mà không tiết lộ đơn. Mất phản hồi quanh `COMMIT` không được coi là rollback: giao diện giữ nguyên khóa **và body đã gửi** để hỏi lại; sau khi đã có đơn hoặc lỗi xác định chưa commit mới được tạo yêu cầu mới.

## F8 — Đơn hàng của khách

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/orders` | `page` bắt đầu từ 1, `size` mặc định 20/tối đa 100, `status` tùy chọn | `{page, size, total, items}`; mới nhất trước, đồng hạng theo `orderId` giảm dần |
| `GET` | `/api/orders/{orderId}` | Không có `userId` | Chi tiết đơn và snapshot tên, SKU, giá, số lượng lúc mua |
| `POST` | `/api/orders/{orderId}/cancel` | Không có `userId` | Trạng thái `CANCELLED`, `cancelledAt`; hoàn tồn kho đúng một lần |

Mọi truy vấn đơn lọc `user_id` ngay trong SQL; đơn của khách khác trả cùng `404` như đơn không tồn tại. Chỉ đơn `NORMAL/PENDING` được hủy. Giao dịch hủy khóa `ORDERS` rồi `PRODUCT_INVENTORY` theo `product_id` tăng dần, tăng `version` tồn kho; hủy lặp hoặc trạng thái sai trả `409 ORDER_NOT_CANCELLABLE`, không hoàn hàng lần nữa. Đơn `FLASH_SALE` thuộc F9. Giao diện dùng đúng trạng thái Oracle: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`.

## Kiểm thử và ranh giới tích hợp

- Unit test không cần DB: `*CoreTest`. Integration test Oracle/Redis thật: `*IT` và gọi đích danh qua Maven Surefire; không đặt `@Transactional` bao ngoài bài test cần quan sát `COMMIT`/`ROLLBACK`.
- Dữ liệu thử nghiệm dùng user/product/giỏ/đơn riêng và Redis `txlab:*`; không sửa seed nền, khóa Flash Sale hoặc projection của module khác. Các bài test tranh chấp dùng hai kết nối và điểm đồng bộ có thời hạn, không dựa vào `Thread.sleep`.
- Route `/cart` đã được bàn giao cho TV4. Backend Cart/Checkout/Order dùng F0/JWT và có thể triển khai trước khi TV2 gắn nút thêm vào giỏ. Việc sửa module `indexing` vẫn cần thống nhất với TV2.
