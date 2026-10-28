# A5 — Lỗi khi EXEC và Lua đã ghi

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:51:56 +07.
- Mục tiêu: phân biệt lỗi xếp hàng, lỗi runtime trong `EXEC` và lỗi Lua sau ghi.
- Setup: Redis fixture đầu kỳ (`native01-A4-before.txt`), `order` nằm trong danh sách key manifest.
- Execute: `redis/experiments/A5.sh` ba lần với `runtime`, `queue`, `lua`. Hai nhánh `MULTI` giữ một kết nối cho tới `EXEC`; Lua chạy bằng `EVAL` với hai key fixture. Reset theo manifest giữa các nhánh có ghi.
- Expected/actual: `native01-A5-runtime.txt` có bốn `QUEUED`; `EXEC` trả `99`, `2`, `WRONGTYPE`, `1`. `native01-A5-runtime-after.txt` cho thấy tồn P1=99, Order hash đã tạo, Cart đã xóa P1. `native01-A5-queue.txt` có lỗi unknown command lúc xếp hàng rồi `EXECABORT`; `native01-A5-queue-after.txt` giống đầu kỳ. `native01-A5-lua.txt` có `WRONGTYPE` tại dòng 4; `native01-A5-lua-after.txt` vẫn có tồn P1=99 và Order status `PENDING`.
- Cleanup/verify: preview/reset theo sáu key manifest ở `native01-A5-*-reset*.txt`; `native01-A5-after-reset.txt` giống byte đầu kỳ.
- Kết luận: lỗi runtime trong `EXEC` hoặc Lua không tự rollback các lệnh đã ghi thành công. Lỗi queue-time khiến `EXEC` hủy hàng đợi trước khi chạy.

## Giải thích kết quả

A5 thử ba thời điểm lỗi. Ở [lỗi queue-time](native01-A5-queue.txt), một tên lệnh không tồn tại làm `EXEC` trả `EXECABORT`; [snapshot](native01-A5-queue-after.txt) không đổi vì chưa lệnh nào chạy. Ở [lỗi runtime](native01-A5-runtime.txt), mọi lệnh đều được xếp hàng, nhưng `LPUSH` gặp Order đang là Hash và trả `WRONGTYPE` trong khi `EXEC` đang thực thi. [Snapshot sau EXEC](native01-A5-runtime-after.txt) vẫn có các ghi thành công trước và sau lệnh sai: tồn P1=99, Order tồn tại và Cart mất P1.

[Lua cố ý lỗi](native01-A5-lua.txt) cũng ghi tồn và Order trước khi gọi `LPUSH` sai kiểu; [snapshot sau Lua](native01-A5-lua-after.txt) giữ các ghi đó. Vậy Redis không cung cấp full rollback cho lỗi runtime đã xảy ra sau một lệnh ghi. Muốn tránh trạng thái dở, script nghiệp vụ phải kiểm tra mọi điều kiện có thể biết **trước lệnh ghi đầu tiên**, như D2.
