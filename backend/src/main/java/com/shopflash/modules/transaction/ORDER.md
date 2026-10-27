# F8 — Lịch sử và hủy đơn của khách hàng

Mọi API dùng JWT của tài khoản `CUSTOMER/ACTIVE`; `userId` được backend xác minh từ principal, không lấy từ URL, query hay body. Oracle là nguồn dữ liệu cho đơn và tồn kho. Không cần Redis để xem hoặc hủy đơn.

## API và dữ liệu trả về

| API | Đầu vào | Kết quả |
|---|---|---|
| `GET /api/orders` | `page` từ 1 (mặc định 1), `size` từ 1 đến 100 (mặc định 20), `status` tùy chọn | `{page,size,total,items}`; `items` chỉ chứa đơn của khách hiện tại, sắp `created_at DESC, order_id DESC` |
| `GET /api/orders/{orderId}` | ID đơn dương | Chi tiết đơn của khách, kể cả snapshot các mặt hàng lúc mua |
| `POST /api/orders/{orderId}/cancel` | ID đơn dương, không có body | Chi tiết đơn sau khi chuyển thành `CANCELLED` và hoàn tồn kho |

`status` chỉ nhận `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`. Danh sách trả `orderId`, `orderCode`, `orderType`, `status`, `createdAt`, `totalAmount` và `itemCount` (tổng số đơn vị sản phẩm). Trang vượt cuối trả `items: []` và vẫn có `total` chính xác. Chi tiết gồm người nhận, số điện thoại, địa chỉ, tiền, thời điểm đặt/hủy và các dòng `productName`, `productSku`, `unitPrice`, `quantity`, `lineTotal` đã lưu trong `ORDER_ITEMS`; thay đổi Product sau khi đặt không sửa lịch sử.

Ví dụ rút gọn cho một đơn thường chờ xử lý:

```json
{"page":1,"size":20,"total":1,"items":[{"orderId":10001,"orderCode":"SF-0123456789abcdef0123456789abcdef","orderType":"NORMAL","status":"PENDING","createdAt":"2026-10-26T21:00:00+07:00","totalAmount":200000.00,"itemCount":2}]}
```

Ví dụ `GET /api/orders/10001` hoặc phản hồi hủy thành công có thêm `recipientName`, `recipientPhone`, `shippingAddress`, `subtotal`, `discountAmount`, `cancelledAt` và `items`. Mỗi dòng trong `items` có `orderItemId`, `productId`, `productName`, `productSku`, `unitPrice`, `quantity`, `lineTotal`. Sau hủy, `status="CANCELLED"` và `cancelledAt` khác `null`.

## Quy tắc hủy

Chỉ **chủ đơn `NORMAL/PENDING`** được hủy. Giao dịch khóa `ORDERS` theo `order_id` và `user_id`, rồi khóa toàn bộ `PRODUCT_INVENTORY` theo `product_id` tăng dần. Mỗi dòng được hoàn đúng số lượng đã mua; `on_hand_quantity` và `version` tăng trong cùng giao dịch với `status='CANCELLED'` và `cancelled_at`. Không đổi `reserved_flash_quantity`, không đưa mặt hàng về giỏ. Nếu bước nào lỗi, Oracle rollback cả trạng thái và tồn kho. Hủy lặp hoặc hai yêu cầu đồng thời chỉ cho một yêu cầu thành công.

| Loại/trạng thái lúc giữ khóa | Kết quả API |
|---|---|
| `NORMAL/PENDING` | `200`, chi tiết đơn `CANCELLED` |
| `NORMAL/CONFIRMED`, `NORMAL/COMPLETED`, `NORMAL/CANCELLED` | `409 ORDER_NOT_CANCELLABLE` |
| Mọi trạng thái `FLASH_SALE` | `409 ORDER_NOT_CANCELLABLE`; hoàn suất Flash Sale thuộc F9/TV5 |
| Đơn không tồn tại hoặc thuộc khách khác | Cùng `404 ORDER_NOT_FOUND` |

`page`, `size`, `status` hoặc ID sai trả `400 VALIDATION_ERROR`; thiếu JWT trả `401 AUTH_REQUIRED`, vai trò sai trả `403 ACCESS_DENIED`. Hết thời gian chờ khóa trả `409 LOCK_TIMEOUT`, deadlock trả `409 DEADLOCK` sau rollback. Khi mất phản hồi của lệnh hủy, client **đọc lại chi tiết đơn trước khi quyết định gửi lại**: `CANCELLED` nghĩa là lệnh trước đã hoàn tất; còn `PENDING` có thể thử lại. Không coi timeout là bằng chứng rollback. Giao diện chỉ hiển thị nút hủy cho `NORMAL/PENDING`, nhưng backend luôn kiểm tra lại trạng thái khi nhận lệnh.

## Kiểm thử

`OrderOracleIT` chạy trên Oracle thật với schema đã áp Flyway V1/V2 và fixture khách/sản phẩm riêng; không dùng giao dịch bao ngoài bài test. Các case bao gồm lịch sử phân trang/lọc, hai khách, snapshot sản phẩm, hủy thành công/lặp, sai trạng thái, Flash Sale, tranh chấp và rollback sau khi đã hoàn một dòng tồn kho. Hậu kiểm dùng kết nối Oracle độc lập.

```bash
cd backend
./mvnw test -Dtest=OrderOracleIT
```

Với profile `dev`, đăng nhập `customer@shopflash.local` rồi mở `/orders`. Seed nền có đơn thường `COMPLETED` và đơn Flash Sale `CONFIRMED`; muốn thử hủy đơn `NORMAL/PENDING`, đặt đơn mới từ `/checkout`. Xem [checklist giao diện F8](../../../../../../../../frontend/src/modules/transaction/ORDERS_UI_CHECKLIST.md) để đối chiếu trạng thái trên Oracle.
