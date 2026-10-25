# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/src/main/java/com/shopflash/modules/transaction/CONTRACT.md`. Route `/cart` đã được bàn giao từ module `concurrency` cho TV4; CartPage dùng API Oracle thật. Điểm tích hợp Product Listing/Detail cần nguồn dữ liệu sản phẩm thật của TV2.

F10 dùng `api.js` để gắn bearer token local, kiểm tra email/vai trò qua `/api/transaction/local-identity` và gọi năm Cart API. `components/AddToCartButton.jsx` nhận `productId` Oracle thật và `quantity` (mặc định 1); `addProductToCart(user, productId, quantity)` phục vụ `ProductCard` ở trang danh sách khi TV2 có dữ liệu thật. Không gắn nút vào các `SAMPLE_PRODUCTS` hiện tại vì ID minh họa không ánh xạ tới Oracle.

Kiểm tra giao diện mobile và danh tính: xem [CART_UI_CHECKLIST.md](CART_UI_CHECKLIST.md).

## Màn hình

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `OrderStatusTag` (đơn hàng của tôi), `PriceTag` (giỏ hàng, thanh toán, tổng tiền).
