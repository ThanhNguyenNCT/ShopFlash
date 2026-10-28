# D2 — Lua Checkout mô phỏng nhiều key

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:59:00 +07.
- Mục tiêu: kiểm tra Cart, tồn khả dụng và quota trước ghi; thử nhánh sai/đúng bằng cùng Lua.
- Setup: Redis fixture đầu kỳ; Order key đã nằm trong manifest. `redis/experiments/D2.sh` gọi `redis/lua/D2-checkout.lua` với Cart, Order, hai Inventory và quota.
- Execute/observe: nhánh `invalid` yêu cầu P2=101, script trả `ERR OUT_OF_STOCK_P2`; `native01-D2-after-invalid.txt` giống đầu kỳ, không ghi dở. Nhánh `valid` mua P1/P2 mỗi thứ 1, trả `OK`; `native01-D2-after-valid.txt` cho thấy Inventory 99/99, Cart chỉ còn P3, quota `remaining=1/held=1`, Order Hash mô phỏng có hai dòng.
- Expected/actual: nhánh lỗi không đổi bất cứ key nào, nhánh hợp lệ giữ invariant. Output gốc: `native01-D2-invalid.txt`, `native01-D2-valid.txt`.
- Cleanup/verify: preview/reset sáu key theo manifest ở `native01-D2-reset-preview.txt`, `native01-D2-reset.txt`; `native01-D2-after-reset.txt` giống đầu kỳ.
- Giới hạn: Order Hash Redis này chỉ là mô phỏng trong namespace nghiên cứu, không tạo Order Oracle hay transaction liên DBMS.
- Kết luận: tiền kiểm trước ghi trong một Lua script bảo vệ invariant của mô hình key này; không thay thế ràng buộc quan hệ và rollback Oracle.

## Giải thích kết quả

[Nhánh sai](native01-D2-invalid.txt) yêu cầu 101 đơn vị P2 khi Inventory chỉ có 100. Lua kiểm tra cả hai Inventory, Cart và quota trước khi gọi lệnh ghi đầu tiên; script trả `OUT_OF_STOCK_P2`. [Snapshot sau nhánh sai](native01-D2-after-invalid.txt) trùng đầu kỳ, nên không có Order Hash, trừ tồn, xóa Cart hay giữ quota dở.

[Nhánh đúng](native01-D2-valid.txt) mua một đơn vị P1 và P2. [Snapshot sau nhánh đúng](native01-D2-after-valid.txt) có tồn 99/99, Cart chỉ còn P3, quota remaining 1/held 1 và Order Hash mô phỏng đủ hai sản phẩm. Đây là cách bảo vệ invariant của **mô hình Redis nghiên cứu** khi mọi điều kiện được kiểm tra trước ghi. Nó không chứng minh Lua có full rollback khi script lỗi sau ghi, và không tạo Order Oracle.
