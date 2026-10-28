# P4 — Tổng hợp bằng chứng và cleanup cuối

Sau khi P2/P3 chạy xong, [chỉ mục](../INDEX.md) liên kết đủ 14 hồ sơ với transcript và snapshot gốc. [Báo cáo Transaction](../../../SECTION_TRANSACTION.md) giải thích các kết quả theo mạch lý thuyết → Oracle → Redis → so sánh → lựa chọn cho ShopFlash. Các bảng benchmark cũ trong báo cáo được giữ nguyên.

## Dọn fixture `native01`

Dữ liệu đã về baseline sau từng case, nhưng user/product/cart Oracle và năm key Redis nền vẫn còn để chạy case tiếp theo. Cleanup cuối mới xóa chúng. Trước khi xóa, [Oracle preview](native01-final-oracle-preview.txt) ghi đúng 1 user, 1 category, 1 cart, 3 Product/Inventory, 3 Cart Items và 0 Order; [Redis preview](native01-final-redis-preview.txt) liệt kê năm Hash tồn tại cùng Order key đang `none`. Các ID/key này khớp [manifest](../p1/native01/native01-manifest.md).

Script [Oracle cleanup](native01-final-oracle-cleanup.txt) xóa theo thứ tự FK rồi commit; [hậu kiểm Oracle](native01-final-oracle-verify.txt) cho mọi loại row fixture bằng 0 và `VERIFY_ABSENT=PASS`. Script [Redis cleanup](native01-final-redis-cleanup.txt) chỉ `DEL` sáu key trong manifest (năm key tồn tại, Order key không tồn tại); [hậu kiểm Redis](native01-final-redis-verify.txt) cho mọi key `none` và `ALL_RUN_KEYS` rỗng. Không có session chờ hoặc khóa fixture còn lại sau B3.

## Kết quả và giới hạn

14/14 case có trạng thái `PASS` dựa trên output thực; không có case `FAIL`, `BLOCKED` hoặc `NOT_APPLICABLE` trong môi trường đã audit. Bộ này giải thích cơ chế DBMS trên local, không thử crash, mất điện hoặc tải sản xuất. Redis Order là Hash mô phỏng và không tạo Order Oracle. P5 chỉ rà soát benchmark cũ, không chạy lại hay thay dữ liệu thô. Không có blocker P4.
