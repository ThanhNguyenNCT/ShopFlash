# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/docs/transaction/CONTRACT.md`. Route `/cart` dùng API Oracle thật. Khách thêm sản phẩm từ trang chủ, `/search` hoặc trang chi tiết bằng `addProductToCart`; không còn form nhập ID hay danh sách ID mẫu trong CartPage. Giỏ mới trống, seed không tự thêm sản phẩm. `components/AddToCartButton.jsx` và `addProductToCart` là điểm tích hợp với module indexing. API giỏ vẫn hỗ trợ cập nhật số lượng, xóa dòng/xóa giỏ và kiểm tra phiên bản.

F7 dùng `GET /api/checkout/preview` và `POST /api/orders`. `CheckoutPage` giữ `{principalUserId, idempotencyKey, body}` trong `sessionStorage` trước khi gửi đơn; khi kết quả chưa rõ, chỉ gửi lại đúng khóa và body cũ. Đổi tài khoản không xóa yêu cầu đang chờ hoặc cho gửi bằng tài khoản khác. Hợp đồng backend và cách kiểm thử: xem `backend/src/main/java/com/shopflash/modules/transaction/CHECKOUT.md` và [CHECKOUT_UI_CHECKLIST.md](CHECKOUT_UI_CHECKLIST.md).

F8 dùng `GET /api/orders`, `GET /api/orders/{orderId}` và `POST /api/orders/{orderId}/cancel`; backend lọc danh tính theo JWT và chỉ cho hủy đơn `NORMAL/PENDING`. `OrdersPage` và `OrderDetailPage` dùng nhãn trạng thái Oracle cục bộ trong module vì `OrderStatusTag` dùng chung chưa hỗ trợ đủ `CONFIRMED/COMPLETED`. Xem [ORDERS_UI_CHECKLIST.md](ORDERS_UI_CHECKLIST.md) và tài liệu `backend/src/main/java/com/shopflash/modules/transaction/ORDER.md`.

Kiểm tra chức năng F0/F10 theo từng mốc và rà soát hình thức cuối change: xem [CART_UI_CHECKLIST.md](CART_UI_CHECKLIST.md).

## Màn hình

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định), đặc biệt `PriceTag` cho giỏ hàng, thanh toán và đơn hàng. Trạng thái đơn dùng `OracleOrderStatus` trong module Transaction cho đúng bốn trạng thái của Oracle; không sửa `OrderStatusTag` dùng chung khi chưa có bàn giao.
