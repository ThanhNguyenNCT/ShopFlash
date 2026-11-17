# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/docs/transaction/CONTRACT.md`. Route `/cart` dùng API Oracle thật. Khách thêm sản phẩm từ trang chủ, `/search` hoặc trang chi tiết bằng `addProductToCart`; không còn form nhập ID hay danh sách ID mẫu trong CartPage. Giỏ mới trống, seed không tự thêm sản phẩm. `components/AddToCartButton.jsx` và `addProductToCart` là điểm tích hợp với module indexing. API giỏ vẫn hỗ trợ cập nhật số lượng, xóa dòng/xóa giỏ và kiểm tra phiên bản.

F7 dùng `GET /api/checkout/preview` và `POST /api/orders`. `CheckoutPage` giữ `{principalUserId, idempotencyKey, body}` trong `sessionStorage` trước khi gửi đơn; khi kết quả chưa rõ, chỉ gửi lại đúng khóa và body cũ. Đổi tài khoản không xóa yêu cầu đang chờ hoặc cho gửi bằng tài khoản khác. Hợp đồng backend và cách kiểm thử: xem `backend/src/main/java/com/shopflash/modules/transaction/CHECKOUT.md` và [CHECKOUT_UI_CHECKLIST.md](CHECKOUT_UI_CHECKLIST.md).

F8 dùng `GET /api/orders`, `GET /api/orders/{orderId}` và `POST /api/orders/{orderId}/cancel`; backend lọc danh tính theo JWT và chỉ cho hủy đơn `NORMAL/PENDING`. `OrdersPage` và `OrderDetailPage` dùng nhãn trạng thái Oracle cục bộ trong module vì `OrderStatusTag` dùng chung chưa hỗ trợ đủ `CONFIRMED/COMPLETED`. Xem [ORDERS_UI_CHECKLIST.md](ORDERS_UI_CHECKLIST.md) và tài liệu `backend/src/main/java/com/shopflash/modules/transaction/ORDER.md`.

Hai trang đơn hàng chờ khôi phục phiên trước khi quyết định truy cập. Khách chưa
đăng nhập được chuyển tới `/login?next=...` và quay lại đúng pathname/query/hash
sau đăng nhập; Admin vẫn nhận hướng dẫn dùng tài khoản Khách. Chỉ đọc `loaded.data`
khi cả dữ liệu và người dùng tồn tại, đồng thời khớp `userId` để tránh lỗi khi chưa
đăng nhập, hết phiên hoặc đổi tài khoản.

Kiểm tra chức năng F0/F10 theo từng mốc và rà soát hình thức cuối change: xem [CART_UI_CHECKLIST.md](CART_UI_CHECKLIST.md).

## Màn hình

Số điện thoại nhận hàng F7 kiểm tra cùng backend: `0[35789]` + tám chữ số,
hoặc thay `0` đầu bằng `+84`; bỏ khoảng trắng hai đầu, báo lỗi tại ô nhập và
không gửi yêu cầu khi sai. Ô nhập dùng `type="tel"`, giữ được ký tự `0`/`+`.

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Giỏ hàng có nút **Chọn tất cả** phía trên danh sách; khi đã chọn mọi sản phẩm,
nút đổi thành **Bỏ chọn tất cả**. Lựa chọn dùng chung cơ chế lưu theo khách trong
`sessionStorage` với checkbox từng dòng, chỉ phục vụ Checkout và không sửa dữ liệu giỏ.
Sản phẩm được chọn dùng viền xanh lá, nền xanh lá nhạt và checkbox cùng màu từ
token `success`; theme checkbox chỉ áp dụng trong giỏ hàng.

`components/ProductThumbnail.jsx` hiển thị ảnh catalog cạnh tên sản phẩm ở Cart và Order Detail;
ảnh dùng `thumbnailUrl` từ API, có `alt`, lazy loading, khung vuông và trạng thái dự phòng
khi thiếu URL/tải lỗi. Không lấy lại tên hoặc giá catalog để thay snapshot đơn hàng.

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định), đặc biệt `PriceTag` cho giỏ hàng, thanh toán và đơn hàng. Trạng thái đơn dùng `OracleOrderStatus` trong module Transaction cho đúng bốn trạng thái của Oracle; không sửa `OrderStatusTag` dùng chung khi chưa có bàn giao.
