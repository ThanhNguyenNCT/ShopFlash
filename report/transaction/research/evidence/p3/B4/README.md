# B4 — Atomic HINCRBY và WATCH/HSET conflict–retry

- Bản hiện hành: revision-20261109, run `rev1109b`, năm lượt correctness. [Tổng hợp và cleanup](../../revision-20261109/README.md).
- [Timeline/snapshot lượt 1](../../revision-20261109/1-B4.txt); các lượt 2–5 tương ứng.
- B4.1: hai worker barrier mở hai redis-cli connection độc lập, mỗi client HINCRBY on_hand -1, không WATCH. Assertion so tập phản hồi {99,98} và stock 98, không cố định thứ tự client.
- Reset stock=100 trước B4.2. A WATCH/HGET=100, tính 99; B HINCRBY thành 99. A MULTI/HSET giá trị đã tính/EXEC trả nil (`''` trong raw), HGET và snapshot từ connection độc lập vẫn 99. A WATCH lại/HGET=99/tính 98/MULTI/HSET/EXEC thành công. HSET trả 0 vì field cũ, stock 98.
- A giữ nguyên connection qua cả conflict và retry; cuối cùng UNWATCH/QUIT. Snapshot mỗi mốc ở timeline; snapshot đầy đủ sau nhánh và sau reset có file riêng.
- Một command HINCRBY nguyên tử; MULTI/EXEC thực thi nguyên tử hàng đợi; WATCH phát hiện key đổi trong cửa sổ đọc–EXEC, không tự retry. Không có nhánh GET/SET không WATCH.

---

## Hồ sơ cũ — SUPERSEDED

Các kết quả dưới đây chỉ mô tả phiên bản cũ; không dùng để nghiệm thu code hiện hành. Mọi transcript cũ giữ nguyên để truy vết.

# B4 — WATCH cạnh tranh trên Redis

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:56:20 +07.
- Mục tiêu: client A giữ cùng kết nối từ `WATCH` qua đọc/`MULTI` đến `EXEC`, client B sửa key trên kết nối khác.
- Setup: Inventory P1 Redis=100. `redis/experiments/B4.py native01 12941` điều phối hai `redis-cli` process, không thay cơ chế DBMS.
- Execute/observe: `native01-B4-timeline.txt`: A `WATCH`, đọc 100, xếp `HINCRBY -1`; B giảm còn 99; A `EXEC` trả nil (`''` với `--raw`), tồn vẫn 99. A retry đúng một lần bằng kết nối còn mở, đọc 99, `EXEC` trả 98.
- Expected/actual: hàng đợi của lượt WATCH bị từ chối không chạy; kết quả retry xác định 98. `native01-B4-after.txt` từ client độc lập xác nhận 98.
- Cleanup: `native01-B4-reset-preview.txt`, `native01-B4-reset.txt`, `native01-B4-after-reset.txt`; snapshot sau reset giống đầu kỳ. Kết nối A đóng sau `UNWATCH/QUIT`.
- Kết luận: `WATCH` cung cấp kiểm soát lạc quan trên key được theo dõi; client cần đọc lại và quyết định retry sau nil.

## Giải thích kết quả

Trong [timeline B4](native01-B4-timeline.txt), A giữ **một kết nối Redis** từ `WATCH` đến `EXEC`. A đọc tồn 100 và xếp lệnh giảm 1. B dùng kết nối khác giảm còn 99 trước khi A gọi `EXEC`. Redis trả nil (`''` ở chế độ `--raw`) cho `EXEC` của A; [snapshot sau nhánh](native01-B4-after.txt) không có lần giảm của A ở bước đó.

A không gửi lại hàng đợi cũ. A đọc lại 99, `WATCH` lại và retry một lần; `EXEC` trả 98. Đây là optimistic concurrency: `WATCH` phát hiện thay đổi, còn việc đọc lại và giới hạn số lần thử thuộc trách nhiệm client. [Reset](native01-B4-after-reset.txt) đưa key về 100.
