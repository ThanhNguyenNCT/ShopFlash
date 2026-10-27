# Module concurrency (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

## Màn hình

- Flash sale (đồng hồ đếm ngược, số suất còn lại). Route `/cart` thuộc module `transaction` (TV4).

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `FlashCountdown`, `StockBar`, `ProductCard` có `sold`/`quota` (flash sale).
