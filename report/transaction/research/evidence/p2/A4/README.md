# A4 — Redis MULTI/EXEC và DISCARD

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:50:32 +07.
- Mục tiêu: kiểm tra hàng đợi lệnh trong transaction Redis và hủy hàng đợi trước khi chạy.
- Setup: năm key fixture từ P1; key `txresearch:native01:order` đã ghi trong manifest trước case. Snapshot `native01-A4-before.txt`.
- Execute: `redis/experiments/A4.sh exec native01 10528 12941` gửi `MULTI`, ba lệnh `HSET`/`HINCRBY`/`HDEL`, rồi `EXEC` trên **một kết nối**. Sau khi đọc trạng thái bằng client độc lập, reset theo manifest và chạy cùng script ở chế độ `discard` trên một kết nối mới.
- Expected/actual: `native01-A4-exec.txt` có ba phản hồi `QUEUED`, `EXEC` trả `4`, `99`, `1`; `native01-A4-after-exec.txt` cho thấy Order hash tồn tại, tồn P1 là 99 và Cart không còn P1. `native01-A4-discard.txt` trả `OK` thay cho kết quả thực thi; `native01-A4-after-discard.txt` giống byte với `native01-A4-before.txt`.
- Cleanup/verify: `native01-A4-reset-preview.txt`, `native01-A4-reset.txt`, `native01-A4-after-reset.txt` chứng minh reset đúng sáu key và trả trạng thái đầu kỳ; sau DISCARD không có ghi dở. Fixture còn để dùng các case kế tiếp.
- Kết luận: `EXEC` chạy hàng đợi theo thứ tự, còn `DISCARD` chỉ bỏ hàng đợi chưa chạy.

## Giải thích kết quả

Trong [nhánh EXEC](native01-A4-exec.txt), `QUEUED` chỉ nói Redis đã đưa lệnh vào hàng đợi; lúc đó key chưa thay đổi. Sau `EXEC`, một client khác thấy [Order Hash được tạo, tồn P1 còn 99 và Cart không còn P1](native01-A4-after-exec.txt). Ba lệnh có kết quả riêng theo thứ tự đã xếp. Sau reset, [nhánh DISCARD](native01-A4-discard.txt) cũng nhận `QUEUED`, nhưng `DISCARD` bỏ hàng đợi trước khi thực thi, nên [snapshot sau DISCARD](native01-A4-after-discard.txt) vẫn bằng đầu kỳ.

`DISCARD` vì vậy hữu ích khi muốn hủy các lệnh **chưa chạy**. Nó không hoàn tác `EXEC` đã hoàn thành; nhánh đó phải reset fixture bằng danh sách key trong manifest.
