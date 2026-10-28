# A3 — Phạm vi rollback Oracle

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:48:09 +07; một SQL*Plus session/transaction, autocommit OFF.
- Mục tiêu: phân biệt statement lỗi, `ROLLBACK TO` và full `ROLLBACK`.
- Setup: fixture đầu kỳ sau A2; `native01-A2-before.txt` có tồn Product 1/2 là 100 và Cart quantity 1.
- Execute: `oracle/experiments/A3.sql` cập nhật Product 1→99, đặt savepoint, cập nhật Product 2→98, cố ý UPDATE Cart quantity 0 gây `ORA-02290`, đọc trạng thái, `ROLLBACK TO before_p2`, đọc lại, full `ROLLBACK`, đọc lại.
- Expected/actual trong `native01-A3-output.txt`: sau statement lỗi `P1=99, P2=98, CART_QTY=1`; sau savepoint `P1=99, P2=100`; sau full rollback `P1=100, P2=100`. Script tự kiểm tra từng mốc.
- Cleanup/verify: `native01-A3-after.txt` từ kết nối độc lập giống byte với snapshot đầu kỳ; không còn ghi dở, không cần xóa thêm fixture.
- Kết luận: lỗi của một statement không tự hoàn tác các statement thành công trước đó; savepoint và full rollback có phạm vi khác nhau.

## Giải thích kết quả

[Output A3](native01-A3-output.txt) cho thấy lỗi `ORA-02290` của câu sửa Cart không xóa hai cập nhật Inventory đã thành công: ngay sau lỗi, P1 vẫn là 99 và P2 vẫn là 98. `ROLLBACK TO before_p2` chỉ hoàn tác các lệnh sau savepoint, nên P2 trở về 100 trong khi P1 vẫn 99. Full `ROLLBACK` cuối cùng đưa cả P1 về 100.

Có thể đọc ba mốc như ba phạm vi ngày càng rộng: câu lệnh sai, phần sau savepoint, rồi toàn transaction. [Snapshot độc lập sau case](native01-A3-after.txt) xác nhận trạng thái cuối bằng đầu kỳ. Phân biệt này quan trọng cho Checkout: ứng dụng phải chủ động chọn full rollback nếu muốn bỏ mọi bước đặt hàng đã làm trước lỗi.
