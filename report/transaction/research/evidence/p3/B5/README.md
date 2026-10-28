# B5 — Lua giữ quota khi nhiều client tranh suất

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:57:08 +07.
- Mục tiêu: bốn client tranh quota 2 bằng một Lua script kiểm tra–ghi trên một key fixture.
- Setup: `quota` có `limit=2`, `remaining=2`, `held=0`. `redis/experiments/B5.py native01` phát bốn `redis-cli EVAL` qua barrier; mã quyết định ở `redis/lua/B5-quota.lua`.
- Expected/actual: `native01-B5-clients.txt` có đúng hai kết quả thành công (`remaining` trả 1 và 0) và hai `ERR QUOTA_EXHAUSTED`; `native01-B5-after.txt` từ client độc lập có `remaining=0`, `held=2`, tổng bằng `limit=2`.
- Cleanup/verify: preview/reset sáu key theo manifest (`native01-B5-reset-preview.txt`, `native01-B5-reset.txt`); `native01-B5-after-reset.txt` giống đầu kỳ.
- Kết luận: một lần EVAL tuần tự hóa kiểm tra và ghi quota; invariant vẫn cần được viết đúng trong Lua.

## Giải thích kết quả

Quota đầu kỳ là `limit=2`, `remaining=2`, `held=0`. Bốn client gần như cùng lúc gọi một Lua script. [Output từng client](native01-B5-clients.txt) có hai kết quả thành công và hai lỗi `QUOTA_EXHAUSTED`; [snapshot cuối](native01-B5-after.txt) là `remaining=0`, `held=2`. Không có client thứ ba lấy được suất và `remaining` không âm.

Điểm cần chứng minh là toàn bộ đoạn đọc quota, kiểm tra và giảm/tăng được thực thi trong **một lần EVAL**; Redis không chen lệnh của client khác vào giữa hai thao tác này. Tính đúng vẫn phụ thuộc điều kiện và phép cập nhật trong Lua; script đã kiểm tra `remaining + held = limit` trước khi ghi. [Reset](native01-B5-after-reset.txt) đưa quota về 2/2/0.
