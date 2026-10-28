# A2 — Oracle UNIQUE, FK, CHECK

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:47:29 +07. P0 xác nhận ba constraint dùng trong script là `ENABLED`, `VALIDATED`, `NOT DEFERRABLE`, `IMMEDIATE`.
- Mục tiêu: lấy mã lỗi do chính Oracle phát sinh trên bảng ShopFlash.
- Setup: fixture `native01`; `native01-A2-before.txt` có Cart ba dòng, ba tồn 100, không có Order.
- Execute: `oracle/experiments/A2.sql` trong một Oracle session; lần lượt INSERT trùng `(cart_id, product_id)`, INSERT Order Item tham chiếu Order `-1`, UPDATE số lượng Cart Item về 0. Script bắt lỗi của từng statement, ghi SQLCODE/SQLERRM, rồi `ROLLBACK`.
- Expected/actual: `ORA-00001` từ `UK_CART_ITEMS_CART_PRODUCT`; `ORA-02291` từ `FK_ORDER_ITEMS_ORDER`; `ORA-02290` từ `CK_CART_ITEMS_QUANTITY`, đúng `native01-A2-output.txt`. Lần thử đầu (`native01-A2-first-run.txt`) đặt Inventory âm và Oracle báo `CK_INVENTORY_RES_LE_ON`; sau đó chọn CHECK giỏ để đối chiếu một constraint xác định. Cả hai lần đều rollback.
- Cleanup/verify: `native01-A2-after.txt` giống byte với snapshot trước; không có Order Item mồ côi, Cart/Inventory không đổi. `NOT_APPLICABLE` không áp dụng vì cả ba constraint đều hiện diện và immediate.
- Kết luận: Oracle tự chặn ba loại vi phạm tại statement; bản thân mã lỗi không hàm ý tự rollback toàn transaction.

## Giải thích kết quả

Ba lỗi trong [output SQL](native01-A2-output.txt) đến từ ba ràng buộc khác nhau của schema thật. Thêm lại cùng cặp `(cart_id, product_id)` vi phạm `UNIQUE` và trả `ORA-00001`. Tạo Order Item trỏ tới Order không tồn tại vi phạm khóa ngoại và trả `ORA-02291`. Đặt số lượng Cart Item bằng 0 vi phạm `CHECK` và trả `ORA-02290`. P0 đã xác nhận các constraint này ở chế độ `IMMEDIATE`, nên lỗi xuất hiện tại chính câu SQL vi phạm.

Một lỗi constraint hủy **câu SQL sai**, không có nghĩa Oracle tự hủy mọi câu SQL đã thành công trước đó trong transaction. Script gọi `ROLLBACK` rõ ràng sau khi ghi mã lỗi. [Snapshot trước](native01-A2-before.txt) và [sau](native01-A2-after.txt) trùng nhau, chứng minh fixture không còn ghi dở. [Lượt thử ban đầu](native01-A2-first-run.txt) dùng CHECK Inventory và được giữ làm bằng chứng quá trình chọn constraint xác định, không tính thành case mới.
