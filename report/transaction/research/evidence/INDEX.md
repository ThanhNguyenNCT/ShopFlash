# Chỉ mục bằng chứng nghiên cứu Transaction

Đây là nơi bắt đầu đọc kết quả. Mỗi thí nghiệm có một thư mục riêng: `README.md` giải thích mục tiêu, cách làm và kết luận; các file `.txt` bên cạnh là output nguyên gốc từ Oracle hoặc Redis. Tất cả thí nghiệm dùng fixture `native01` trên ShopFlash local và đã được dọn sau khi hoàn tất. [RESEARCH_PLAN.md](../../RESEARCH_PLAN.md) quyết định phạm vi và phương pháp; OpenSpec chỉ quản lý công việc triển khai.

## Cấu trúc

```text
research/
├── README.md                 Hướng dẫn chuẩn bị, chạy và dọn fixture
├── oracle/                   SQL/PLSQL, công cụ kết nối và điều phối hai session
├── redis/                    redis-cli, Lua và công cụ điều phối hai client
└── evidence/
    ├── INDEX.md              Trang tra cứu này
    ├── p0/                   Audit schema và cấu hình DBMS (chỉ đọc)
    ├── p1/                   Manifest, setup/reset/cleanup fixture thử
    ├── p2/                   A1–A5, C1–C2: transaction và consistency
    │   └── <case>/           README.md + transcript/snapshot của case
    ├── p3/                   B1–B5, D1–D2: concurrency và nhiều bước
    │   └── <case>/           README.md + timeline/snapshot của case
    ├── p4/                   Tổng hợp và hậu kiểm cleanup cuối
    └── p5/                   Rà soát benchmark cũ; không đo lại
```

Nên đọc theo thứ tự [P0](p0/README.md) → [P1](p1/README.md) → các case P2/P3 → [P4](p4/README.md) → [P5](p5/README.md). [Manifest `native01`](p1/native01/native01-manifest.md) ghi ID và key đã dùng. `PASS` nghĩa là đã chạy thật và có output đối chiếu; `FAIL`, `BLOCKED` hoặc `NOT_APPLICABLE` chỉ được dùng khi có lý do và bằng chứng tương ứng.

## P2 — Transaction và consistency

| Case | Câu hỏi chính | Hồ sơ | Output cần xem |
|---|---|---|---|
| A1 | COMMIT khác ROLLBACK thế nào? | [A1](p2/A1/README.md) | [COMMIT](p2/A1/native01-A1-commit.txt), [ROLLBACK](p2/A1/native01-A1-rollback.txt), [snapshot](p2/A1/native01-A1-after-rollback-snapshot.txt) |
| A2 | Oracle tự chặn UNIQUE, FK, CHECK ra sao? | [A2](p2/A2/README.md) | [Ba lỗi thật](p2/A2/native01-A2-output.txt) |
| A3 | Statement, savepoint và toàn transaction hoàn tác khác nhau thế nào? | [A3](p2/A3/README.md) | [Ba mốc rollback](p2/A3/native01-A3-output.txt) |
| A4 | Redis EXEC và DISCARD làm gì? | [A4](p2/A4/README.md) | [EXEC](p2/A4/native01-A4-exec.txt), [DISCARD](p2/A4/native01-A4-discard.txt) |
| A5 | Lỗi trong EXEC/Lua có hoàn tác ghi trước đó không? | [A5](p2/A5/README.md) | [Lỗi runtime](p2/A5/native01-A5-runtime.txt), [lỗi queue](p2/A5/native01-A5-queue.txt), [lỗi Lua](p2/A5/native01-A5-lua.txt) |
| C1 | Constraint Oracle bảo vệ được đến đâu? | [C1](p2/C1/README.md) | [Metadata 29 constraint](p2/C1/native01-C1-metadata.txt) |
| C2 | Redis có FK/CHECK quan hệ không? | [C2](p2/C2/README.md) | [Trạng thái sau CLI](p2/C2/native01-C2-after-cli.txt), [Lua từ chối](p2/C2/native01-C2-reference.txt) |

## P3 — Concurrency và giao dịch nhiều bước

| Case | Câu hỏi chính | Hồ sơ | Output cần xem |
|---|---|---|---|
| B1 | `READ COMMITTED` có đọc bẩn không? | [B1](p3/B1/README.md) | [Timeline hai session](p3/B1/native01-B1-timeline.txt) |
| B2 | Khi hai session cùng mua hàng, khóa/điều kiện cập nhật giúp gì? | [B2](p3/B2/README.md) | [Timeline mất cập nhật và nhánh bảo vệ](p3/B2/b2redo01-B2-timeline.txt) |
| B3 | Timeout và deadlock xuất hiện theo thứ tự nào? | [B3](p3/B3/README.md) | [Timeline và mã lỗi](p3/B3/native01-B3-timeline.txt) |
| B4 | WATCH xử lý key bị client khác đổi ra sao? | [B4](p3/B4/README.md) | [Timeline WATCH/retry](p3/B4/native01-B4-timeline.txt) |
| B5 | Lua giữ quota khi bốn client tranh hai suất ra sao? | [B5](p3/B5/README.md) | [Kết quả từng client](p3/B5/native01-B5-clients.txt), [quota cuối](p3/B5/native01-B5-after.txt) |
| D1 | Checkout Oracle lỗi sau nhiều ghi thì phục hồi thế nào? | [D1](p3/D1/README.md) | [Ba mốc trạng thái](p3/D1/native01-D1-output.txt) |
| D2 | Lua tiền kiểm trước khi ghi nhiều key ra sao? | [D2](p3/D2/README.md) | [Nhánh lỗi](p3/D2/native01-D2-invalid.txt), [nhánh đúng](p3/D2/native01-D2-after-valid.txt) |

## Hậu kiểm cuối

[P4](p4/README.md) tổng hợp việc báo cáo và cleanup. Oracle [preview](p4/native01-final-oracle-preview.txt) → [cleanup](p4/native01-final-oracle-cleanup.txt) → [verify](p4/native01-final-oracle-verify.txt) cho `VERIFY_ABSENT=PASS`. Redis [preview](p4/native01-final-redis-preview.txt) → [cleanup](p4/native01-final-redis-cleanup.txt) → [verify](p4/native01-final-redis-verify.txt) cho mọi key `none` và không còn key cùng run. Các kết quả benchmark cũ được phân tích riêng ở [P5](p5/README.md).
