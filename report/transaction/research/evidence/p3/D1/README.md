# D1 — Checkout nhiều mặt hàng và full ROLLBACK

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:58:06 +07.
- Mục tiêu: SQL/PLSQL Checkout `NORMAL` hai mặt hàng, lỗi DBMS sau ghi một phần, phân biệt trạng thái sau statement lỗi và full rollback.
- Setup: fixture Oracle đầu kỳ `native01-P3-before.txt`; constraint `CK_CART_ITEMS_QUANTITY` đã audit `ENABLED/VALIDATED/NOT DEFERRABLE/IMMEDIATE`.
- Execute: `oracle/run-sql.sh native01 oracle/experiments/D1.sql 10528 10530 10520 12941 12942 12943 '[]'` dùng một SQL*Plus session. Tạo Order 28461 tạm thời, hai Order Items, giảm P1/P2, xóa hai dòng Cart được mua; sau đó cố ý đặt quantity P3=0 để phát sinh `ORA-02290`, bắt lỗi và đọc trạng thái, rồi full `ROLLBACK`.
- Expected/actual: `native01-D1-output.txt` ghi `BEFORE` 0 Order/0 Item/3 Cart/100–100/P3=1; `AFTER_FAILED_STATEMENT` 1/2/1/99–99/P3=1; `AFTER_FULL_ROLLBACK` 0/0/3/100–100/P3=1. Lỗi phát sinh ngay tại statement vì constraint immediate. Order 28461 không commit.
- Cleanup/verify: full rollback trong cùng session; snapshot kết nối độc lập `native01-D1-after.txt` giống đầu kỳ. Không còn Order ID cần đưa vào danh sách cleanup.
- Kết luận: CHECK immediate chỉ hoàn tác statement vi phạm; full rollback của session mới phục hồi toàn bộ checkout nhiều bảng.

## Giải thích kết quả

[D1 output](native01-D1-output.txt) là ảnh chụp ba mốc **trong cùng một Oracle session**. Trước Checkout, Cart có ba dòng, hai Inventory đều 100, chưa có Order. Script tạo Order 28461 tạm thời, hai Order Items, giảm mỗi tồn xuống 99 và xóa hai dòng Cart được mua. Câu SQL tiếp theo cố ý đặt số lượng dòng Cart thứ ba bằng 0, vi phạm `CK_CART_ITEMS_QUANTITY` và trả `ORA-02290` ngay tại statement vì constraint đang `IMMEDIATE`.

Ngay sau lỗi, các ghi Checkout trước đó vẫn hiện trong session: một Order, hai Items, một dòng Cart, tồn 99/99. Chỉ sau khi script gọi full `ROLLBACK`, mọi giá trị trở về 0/0/3/100/100. Dòng Cart thứ ba vẫn có quantity 1 ở cả ba mốc. [Snapshot từ kết nối độc lập](native01-D1-after.txt) xác nhận Order tạm không tồn tại sau rollback. Điều này giải thích vì sao service Checkout phải rollback toàn transaction khi bất kỳ bước nào thất bại.
