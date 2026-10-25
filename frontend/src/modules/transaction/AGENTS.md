# Module transaction (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md`.

Hợp đồng F10/F7/F8 giữa hai phía: xem `backend/src/main/java/com/shopflash/modules/transaction/CONTRACT.md`. Route `/cart` đã được bàn giao từ module `concurrency` cho TV4; giao diện thật và điểm tích hợp Product Listing/Detail sẽ hoàn thiện ở milestone 2.

## Màn hình

- Giỏ hàng, Thanh toán, Đơn hàng của tôi.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `OrderStatusTag` (đơn hàng của tôi), `PriceTag` (giỏ hàng, thanh toán, tổng tiền).
