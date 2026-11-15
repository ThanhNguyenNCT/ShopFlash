# Module concurrency (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

## Màn hình

| Path | File | Nội dung |
|---|---|---|
| `/flash-sale` | `pages/FlashSalePage.jsx` | Chương trình đang chạy và sắp chạy: đồng hồ đếm ngược, số suất còn lại (hỏi lại `/stock` mỗi 2 s), nút "Mua ngay" giữ suất. Chưa đăng nhập thì chuyển tới `/login?next=/flash-sale` |
| `/flash-sale/checkout/:reservationId` | `pages/FlashCheckoutPage.jsx` (mới) | Đếm ngược tới `expiresAt`, nhập thông tin nhận hàng, đặt đơn flash sale; hủy giữ suất |
| `/admin/flash-sales` | `pages/AdminFlashSalesPage.jsx` | Quản lý chương trình, theo dõi runtime và đối chiếu Oracle với Redis |
| `/admin/flash-sales/lab` | `pages/ConcurrencyLabPage.jsx` (mới) | Concurrency Lab, menu nhóm "Flash sale", `order: 2` |

Route `/cart` thuộc module `transaction` (TV4). `pages/CartPage.jsx` của module này còn sót lại từ trước khi bàn giao, không có route nào dùng; xóa khi làm frontend.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `FlashCountdown`, `StockBar`, `ProductCard` có `sold`/`quota` (flash sale).

Gọi API qua client trong `src/shared/api` (đã tự gắn JWT). Đếm ngược theo `serverTime` trong response, không theo đồng hồ máy khách.
