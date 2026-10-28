# A1 — Oracle COMMIT và ROLLBACK

- Trạng thái: **PASS**. Run `native01`, Oracle `SHOP`/`FREEPDB1`, constraint `IMMEDIATE`, SQL*Plus autocommit OFF. File COMMIT tạo lúc 2026-10-27 23:11:03 +07, file ROLLBACK lúc 23:11:28 +07; lệnh đầy đủ ở hai file SQL dưới đây.
- Mục tiêu: đối chiếu khả năng nhìn thấy Order/Order Item và trạng thái Cart/Inventory sau `COMMIT` với sau `ROLLBACK`.
- Setup: `oracle/setup.sql` với manifest `native01-manifest.md`; đầu kỳ `cart_items=3`, ba tồn kho 100, `orders=0`.
- Execute: `oracle/experiments/A1-commit.sql` trong một session; đọc `oracle/snapshot.sql` ở session mới; `oracle/experiments/A1-rollback.sql` trong một session khác; đọc snapshot ở session mới. `native01-A1-commit.txt` ghi Order ID `28441`; `native01-A1-rollback.txt` ghi Order tạm `28442` thấy trong chính session trước rollback.
- Expected/actual: Sau COMMIT, `orders=1`, `order_items=1`, Product 1 tồn 99, Cart còn 2. Sau ROLLBACK nhánh thứ hai, các trị này giữ nguyên, Product 2 vẫn tồn 100 và còn trong Cart. `native01-A1-after-commit-snapshot.txt` và `native01-A1-after-rollback-snapshot.txt` khớp.
- Cleanup: `native01-A1-reset-preview.txt` đối chiếu Order ID `28441`; `oracle/reset.sql` với `'[28441]'` và `native01-A1-after-reset-snapshot.txt` xác nhận `orders=0`, `cart_items=3`, tồn cả ba về 100. Manifest ghi Order lịch sử đã dọn.
- Kết luận: Oracle giữ toàn bộ ghi đã commit cho session độc lập và bỏ toàn bộ ghi chưa commit sau rollback; phép thử không đo hiệu năng.

## Giải thích kết quả

Có hai thời điểm cần tách biệt. Sau nhánh `COMMIT`, Oracle đã xác nhận toàn bộ các thay đổi của đơn đầu tiên, nên [session đọc độc lập](native01-A1-after-commit-snapshot.txt) thấy một Order, một Order Item, tồn P1 còn 99 và giỏ mất đúng dòng P1. Trong nhánh tiếp theo, session tạo một Order khác nhưng gọi `ROLLBACK` trước khi xác nhận. [Snapshot sau rollback](native01-A1-after-rollback-snapshot.txt) vẫn giống trạng thái sau commit: Order thứ hai và những thay đổi của nó không xuất hiện. Điều này chứng minh rollback chỉ hủy phần chưa commit trong transaction hiện tại; nó không xóa giao dịch đã commit trước đó.

Sau khi ghi nhận kết quả, Order đã commit được xóa bằng [reset theo đúng ID 28441](native01-A1-reset.txt). [Hậu kiểm](native01-A1-after-reset-snapshot.txt) cho thấy fixture quay lại ba dòng Cart và tồn 100 để dùng cho case sau.
