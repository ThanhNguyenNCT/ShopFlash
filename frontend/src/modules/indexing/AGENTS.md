# Module indexing (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/indexing/AGENTS.md`.

## Màn hình

- Trang chủ (danh sách sản phẩm, ô tìm kiếm, bộ lọc), chi tiết sản phẩm. TV2 đã đồng ý để TV4 nối nút "Thêm vào giỏ" từ module `transaction` khi Listing/Detail dùng ID sản phẩm Oracle thật. Trang chủ hiện còn dữ liệu minh họa, không gửi các ID mẫu tới Cart API.

Điểm nối F10: `frontend/src/modules/transaction/components/AddToCartButton.jsx` cho trang chi tiết và `addProductToCart(user, productId, quantity)` trong `frontend/src/modules/transaction/api.js` cho `ProductCard` ở danh sách. API này yêu cầu token local và kiểm tra danh tính với backend trước khi gửi lệnh thêm.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `ProductCard` (lưới sản phẩm), `PriceTag` (trang chi tiết).
