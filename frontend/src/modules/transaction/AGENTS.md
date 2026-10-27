# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/src/main/java/com/shopflash/modules/transaction/CONTRACT.md`. Route `/cart` đã được bàn giao từ module `concurrency` cho TV4; CartPage dùng API Oracle thật. Trong giai đoạn kiểm thử Transaction, nhập ID sản phẩm ngay tại `/cart`, không phụ thuộc trang Listing/Detail của TV2.

F10 dùng `api.js` gọi năm Cart API; `shared/api/client.js` tự gắn JWT của tài khoản đã đăng nhập. CartPage có ô nhập `productId` và số lượng, gửi `POST` với phiên bản giỏ đã đọc. Chọn nhanh ID mẫu trên `/cart` chỉ hiện khi Vite chạy dev và trỏ tới dữ liệu Oracle của `R__10_transaction_seed.sql`. `components/AddToCartButton.jsx` và `addProductToCart` vẫn là điểm nối có thể dùng về sau; không gắn vào `SAMPLE_PRODUCTS` của TV2 vì ID minh họa không ánh xạ tới Oracle. Không dùng giỏ mock chỉ nằm trong trình duyệt cho bài kiểm tra giao dịch.

F7 dùng `GET /api/checkout/preview` và `POST /api/orders`. `CheckoutPage` giữ `{principalUserId, idempotencyKey, body}` trong `sessionStorage` trước khi gửi đơn; khi kết quả chưa rõ, chỉ gửi lại đúng khóa và body cũ. Đổi tài khoản không xóa yêu cầu đang chờ hoặc cho gửi bằng tài khoản khác. Hợp đồng backend và cách kiểm thử: xem `backend/src/main/java/com/shopflash/modules/transaction/CHECKOUT.md` và [CHECKOUT_UI_CHECKLIST.md](CHECKOUT_UI_CHECKLIST.md).

F8 dùng `GET /api/orders`, `GET /api/orders/{orderId}` và `POST /api/orders/{orderId}/cancel`; backend lọc danh tính theo JWT và chỉ cho hủy đơn `NORMAL/PENDING`. `OrdersPage` và `OrderDetailPage` dùng nhãn trạng thái Oracle cục bộ trong module vì `OrderStatusTag` dùng chung chưa hỗ trợ đủ `CONFIRMED/COMPLETED`. Xem [ORDERS_UI_CHECKLIST.md](ORDERS_UI_CHECKLIST.md) và tài liệu `backend/src/main/java/com/shopflash/modules/transaction/ORDER.md`.

Kiểm tra chức năng F0/F10 theo từng mốc và rà soát hình thức cuối change: xem [CART_UI_CHECKLIST.md](CART_UI_CHECKLIST.md).

## Màn hình

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định), đặc biệt `PriceTag` cho giỏ hàng, thanh toán và đơn hàng. Trạng thái đơn dùng `OracleOrderStatus` trong module Transaction cho đúng bốn trạng thái của Oracle; không sửa `OrderStatusTag` dùng chung khi chưa có bàn giao.
