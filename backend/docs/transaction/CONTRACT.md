# Hợp đồng Transaction (F10, F7, F8)

Tài liệu này là hợp đồng API và quy tắc nghiệp vụ hiện hành cho Cart (F10), Checkout (F7) và Order (F8). Oracle là nguồn dữ liệu nghiệp vụ; Redis không lưu giỏ hoặc đơn thường. F9 Flash Sale và thanh toán nằm ngoài phạm vi.

## Danh tính và lỗi

- Mọi API dưới đây lấy khách hàng từ danh tính **đã được backend kiểm tra**. Không nhận `userId` trong body, query hoặc header do trình duyệt tự khai. Thiếu danh tính trả `401`; tài khoản không phải `CUSTOMER` đang `ACTIVE` bị từ chối.
- F0 cấp JWT qua `POST /api/auth/login`; Spring Security xác minh token, rồi tra `USERS` Oracle để kiểm tra vai trò/trạng thái hiện tại. Frontend lấy hồ sơ qua `GET /api/auth/me` và tự gắn Bearer cho Cart/Checkout/Order. Không có token local hoặc endpoint đối chiếu danh tính giả lập.
- Lỗi dùng envelope chung `{ "error": { "code", "message", "details" } }` của `GlobalExceptionHandler`; thông điệp tiếng Việt và không chứa lỗi gốc Oracle/Redis. `400 VALIDATION_ERROR` cho dữ liệu sai; `401 AUTH_REQUIRED` cho thiếu/sai token hoặc tài khoản bị vô hiệu hóa; `403 ACCESS_DENIED` cho sai vai trò ở Spring Security; `404` cho mặt hàng/đơn không tìm thấy hoặc không thuộc khách; `409` cho xung đột nghiệp vụ. Hết thời gian chờ khóa và deadlock có mã riêng `LOCK_TIMEOUT`/`DEADLOCK`, chỉ hướng dẫn thử lại khi giao dịch lỗi đã rollback.
- Mọi SQL có tham số dùng biến bind qua `NamedParameterJdbcTemplate`/JDBC; không nối giá trị đầu vào vào SQL.

## F10 — Giỏ hàng

Giỏ chưa có dòng `CARTS` được trả rỗng mà không ghi Oracle:

```json
{"version":0,"items":[],"totalAmount":0.00}
```

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/cart` | Không có `userId` | `{version, items, totalAmount}`; giỏ chưa có trả `version=0`, `items=[]`, `totalAmount=0` mà không ghi Oracle |
| `POST` | `/api/cart/items` | `X-Cart-Version`, `{productId, quantity}` | Tạo giỏ khi cần; nếu đã có sản phẩm thì cộng số lượng; trả giỏ và phiên bản mới |
| `PATCH` | `/api/cart/items/{productId}` | `X-Cart-Version`, `{quantity}` | Đặt số lượng tuyệt đối, trả giỏ và phiên bản mới |
| `DELETE` | `/api/cart/items/{productId}` | `X-Cart-Version` | Xóa một dòng, trả giỏ và phiên bản mới |
| `DELETE` | `/api/cart/items` | `X-Cart-Version` | Xóa mọi dòng, giữ `CARTS` để duy trì phiên bản; trả giỏ |

`items` chứa `productId`, `name`, `sku`, `quantity`, `unitPrice`, `lineTotal`; `totalAmount` là tổng các dòng. Giá lấy từ `PRODUCTS.base_price` hiện tại và chỉ để **tham khảo**; Checkout kiểm tra lại. Số lượng mỗi dòng từ 1 đến 999; thêm sản phẩm đã có là phép **cộng**, còn `PATCH` là phép **đặt** số lượng tuyệt đối. Tổng sau phép cộng không được vượt 999. Xóa toàn giỏ rỗng không tăng phiên bản. Thêm/sửa giỏ không giữ hàng, không giảm tồn kho hoặc giữ suất Flash Sale.

Mọi lệnh sửa giỏ cần `X-Cart-Version` vừa đọc và chạy trong giao dịch Oracle: khóa `CARTS FOR UPDATE`, kiểm tra phiên bản, sửa dòng rồi tăng `CARTS.version` một lần. Phiên bản cũ hoặc hai yêu cầu cùng tạo giỏ trả `409 CART_CHANGED`; client tải lại và để người dùng quyết định thao tác tiếp. Sản phẩm thêm vào phải tồn tại và `ACTIVE`. Mặt hàng sửa/xóa không có trong giỏ trả `404 CART_ITEM_NOT_FOUND`; sản phẩm không tồn tại trả `404 PRODUCT_NOT_FOUND`, ngừng bán trả `409 PRODUCT_UNAVAILABLE`. Số lượng/ID sai trả `400 VALIDATION_ERROR`.

**Mất phản hồi của `POST` cộng hàng không chứng minh rollback.** Client phải tải lại giỏ trước khi gửi lệnh mới; gửi lại lệnh cộng ngay có thể tăng hai lần. `PATCH` cũng cần phiên bản mới nhất. Tranh khóa trả `409 LOCK_TIMEOUT` hoặc `409 DEADLOCK`.

## F7 — Đặt hàng thông thường

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/checkout/preview` | Không có `userId` | `{cartVersion, lines, totalAmount, warnings}`; chỉ đọc giá, trạng thái và tồn kho Oracle, không giữ hàng |
| `POST` | `/api/orders` | `Idempotency-Key` là UUID; `{cartVersion, recipientName, recipientPhone, shippingAddress, expectedLines:[{productId,quantity,unitPrice}]}` | `{orderId, orderCode, status, totalAmount}`; cùng khóa và cùng nội dung trả cùng đơn |

Mỗi dòng preview có `productId`, `name`, `sku`, `quantity`, `unitPrice`, `lineTotal`, `status`, `availableQuantity = on_hand_quantity - reserved_flash_quantity`. `warnings` có thể chứa `CART_EMPTY`, `PRODUCT_UNAVAILABLE`, `OUT_OF_STOCK`. Preview chỉ đọc; giá và tồn phải được kiểm tra lại khi đặt đơn.

Ví dụ body của `POST /api/orders`:

```json
{"cartVersion":0,"recipientName":"Khách thử","recipientPhone":"0900000000","shippingAddress":"Địa chỉ giao hàng","expectedLines":[{"productId":1,"quantity":1,"unitPrice":"100000.00"}]}
```

`expectedLines` là tập con không rỗng của giỏ; từng dòng phải có cùng số lượng với giỏ tại `cartVersion` đã gửi. Tên/số điện thoại/địa chỉ được chuẩn hóa Unicode NFC và bỏ khoảng trắng hai đầu; các mặt hàng được sắp theo ID, giá có hai chữ số thập phân trước khi băm SHA-256. Khóa và nội dung chuẩn hóa giống nhau trả lại cùng đơn. Khóa thuộc khách hoặc nội dung khác trả `409 IDEMPOTENCY_CONFLICT` mà không tiết lộ đơn.

Checkout kiểm tra lại giỏ, giá, trạng thái và tồn khả dụng. Thứ tự khóa trong giao dịch là **`CARTS` → `PRODUCTS` theo `product_id` tăng dần → `PRODUCT_INVENTORY` theo `product_id` tăng dần**. F2 cập nhật Product/Inventory dùng khóa và cột `version`; F9 cần thống nhất thứ tự khóa trước khi tích hợp. Đơn `NORMAL/PENDING`, dòng hàng, giảm tồn có điều kiện, xóa đúng mặt hàng đã mua khỏi giỏ và tăng phiên bản giỏ cùng `COMMIT` hoặc cùng `ROLLBACK`. Các mặt hàng không chọn vẫn ở giỏ. Tiền dùng `BigDecimal`; tên/SKU/giá lúc mua được lưu trong `ORDER_ITEMS`.

Các lỗi nghiệp vụ cần phân biệt: `CART_EMPTY`, `CART_CHANGED`, `PRODUCT_UNAVAILABLE`, `PRICE_CHANGED`, `OUT_OF_STOCK`. Sau `LOCK_TIMEOUT`, `DEADLOCK`, `RETRY_SAME_REQUEST` hoặc `ORDER_CODE_CONFLICT`, chỉ gửi lại **cùng khóa và body** khi giao dịch lỗi đã rollback. Mất phản hồi quanh `COMMIT` không chứng minh rollback: giao diện lưu `{principalUserId, idempotencyKey, body}` trong `sessionStorage` trước khi gửi, rồi gửi lại đúng bản ghi đó để nhận đơn đã lưu hoặc thử lại. Chỉ xóa bản ghi khi có `orderId` hoặc lỗi nghiệp vụ xác định chưa commit; đổi tài khoản cần đăng nhập lại đúng chủ yêu cầu. Nếu đóng tab làm mất `sessionStorage`, kiểm tra lịch sử đơn trước khi tạo yêu cầu mới.

## F8 — Đơn hàng của khách

| Phương thức | Đường dẫn | Dữ liệu vào | Kết quả |
|---|---|---|---|
| `GET` | `/api/orders` | `page` bắt đầu từ 1, `size` mặc định 20/tối đa 100, `status` tùy chọn | `{page, size, total, items}`; mỗi dòng có `itemCount` là tổng số đơn vị; mới nhất trước, đồng hạng theo `orderId` giảm dần |
| `GET` | `/api/orders/{orderId}` | Không có `userId` | Chi tiết đơn và snapshot tên, SKU, giá, số lượng lúc mua |
| `POST` | `/api/orders/{orderId}/cancel` | Không có `userId` hoặc body | Trả chi tiết đơn với `status=CANCELLED`, `cancelledAt`; hoàn tồn kho đúng một lần |

Mọi truy vấn đơn lọc `user_id` ngay trong SQL; đơn của khách khác trả cùng `404` như đơn không tồn tại. Chỉ đơn `NORMAL/PENDING` được hủy. Giao dịch hủy khóa `ORDERS` rồi `PRODUCT_INVENTORY` theo `product_id` tăng dần, tăng `version` tồn kho; hủy lặp hoặc trạng thái sai trả `409 ORDER_NOT_CANCELLABLE`, không hoàn hàng lần nữa. Đơn `FLASH_SALE` thuộc F9. Giao diện dùng đúng trạng thái Oracle: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`.

`page` bắt đầu từ 1; `size` từ 1 đến 100. `status` chỉ nhận bốn trạng thái trên. Danh sách trả `orderId`, `orderCode`, `orderType`, `status`, `createdAt`, `totalAmount`, `itemCount` (tổng số đơn vị); trang vượt cuối trả `items=[]` và `total` chính xác. Chi tiết có `recipientName`, `recipientPhone`, `shippingAddress`, `subtotal`, `discountAmount`, `totalAmount`, `createdAt`, `cancelledAt` và snapshot từng dòng `orderItemId`, `productId`, `productName`, `productSku`, `unitPrice`, `quantity`, `lineTotal`; Product đổi sau đó không sửa lịch sử.

Khi hủy, mỗi dòng tồn kho được hoàn đúng số lượng đã mua; `on_hand_quantity`, `version`, `status='CANCELLED'` và `cancelled_at` đổi trong cùng giao dịch. Không đổi `reserved_flash_quantity`, không đưa hàng về giỏ. Nếu thiếu Inventory hoặc không thể hoàn tồn hợp lệ, API trả `409 INVENTORY_CONFLICT` và Oracle rollback toàn bộ; hủy đồng thời chỉ cho một yêu cầu thành công.

| Loại/trạng thái khi giữ khóa | Kết quả hủy |
|---|---|
| `NORMAL/PENDING` | `200`, chi tiết đơn `CANCELLED` |
| `NORMAL/CONFIRMED`, `NORMAL/COMPLETED`, `NORMAL/CANCELLED`; mọi đơn `FLASH_SALE` | `409 ORDER_NOT_CANCELLABLE` |
| Đơn không tồn tại hoặc thuộc khách khác | `404 ORDER_NOT_FOUND` |

`page`, `size`, `status` hoặc ID sai trả `400 VALIDATION_ERROR`. Khi mất phản hồi của lệnh hủy, **đọc lại chi tiết đơn trước**: `CANCELLED` nghĩa là lệnh trước đã xong; còn `PENDING` có thể thử lại. Không coi timeout là bằng chứng rollback.

## Kiểm thử và ranh giới tích hợp

- Unit test không cần DB: `*CoreTest`. Integration test Oracle/Redis thật: `*IT` và gọi đích danh qua Maven Surefire; không đặt `@Transactional` bao ngoài bài test cần quan sát `COMMIT`/`ROLLBACK`.
- Dữ liệu thử nghiệm dùng user/product/giỏ/đơn riêng và Redis `txlab:*`; không sửa seed nền, khóa Flash Sale hoặc projection của module khác. Các bài test tranh chấp dùng hai kết nối và điểm đồng bộ có thời hạn, không dựa vào `Thread.sleep`.
- `CheckoutProbe` và `OrderCancellationProbe` chỉ được đăng ký trong integration tests để đồng bộ hoặc gây lỗi có chủ đích; production không đăng ký bean này.
