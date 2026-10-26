# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/src/main/java/com/shopflash/modules/transaction/CONTRACT.md`. Route `/cart` đã được bàn giao từ module `concurrency` cho TV4; CartPage dùng API Oracle thật. Trong giai đoạn kiểm thử Transaction, nhập ID sản phẩm ngay tại `/cart`, không phụ thuộc trang Listing/Detail của TV2.

F10 dùng `api.js` gọi năm Cart API; `shared/api/client.js` tự gắn JWT của tài khoản đã đăng nhập. CartPage có ô nhập `productId` và số lượng, gửi `POST` với phiên bản giỏ đã đọc. `components/AddToCartButton.jsx` và `addProductToCart` vẫn là điểm nối có thể dùng về sau; không gắn vào `SAMPLE_PRODUCTS` của TV2 vì ID minh họa không ánh xạ tới Oracle. Không dùng giỏ mock chỉ nằm trong trình duyệt cho bài kiểm tra giao dịch.

Kiểm tra chức năng F0/F10 theo từng mốc và rà soát hình thức cuối change: xem [CART_UI_CHECKLIST.md](CART_UI_CHECKLIST.md).

## Màn hình

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `OrderStatusTag` (đơn hàng của tôi), `PriceTag` (giỏ hàng, thanh toán, tổng tiền).
