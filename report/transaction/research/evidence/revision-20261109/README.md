# B2/B4/C2 — Evidence hiện hành, 09/11/2026

**PASS** — run `rev1109b`, năm lượt correctness trên Oracle/Redis thật. [Manifest](manifest.json), [audit và reuse](audit.md), [assertions tổng hợp](assertions.json), [đối chiếu độc lập](independent-verification.txt). Không dùng thời gian chạy này làm số đo latency/TPS.

| Case | Expected và actual trong cả 5 lượt | Kết quả |
|---|---|---|
| B2.1 SELECT thường | A=100/ưu đãi, A UPDATE chưa commit, B=100/ưu đãi; B UPDATE chờ A COMMIT; cuối 98 | PASS |
| B2.2 FOR UPDATE | A=100/ưu đãi; B SELECT FOR UPDATE WAIT 30 chờ; A UPDATE/COMMIT; B=99/không ưu đãi, UPDATE/COMMIT; cuối 98 | PASS |
| B4.1 HINCRBY | hai client độc lập, tập phản hồi {99,98}, cuối 98, không WATCH | PASS |
| B4.2 WATCH/HSET | A đọc 100/tính 99; B giảm thành 99; A EXEC nil, stock 99; WATCH/HGET lại 99/tính 98, EXEC HSET trả 0, cuối 98 | PASS |
| C2 request/fixture sai | 14 trường hợp/lượt bị guard từ chối trước ghi, snapshot đầy đủ bằng nhau | PASS |
| C2 hợp lệ | Order quantity 1/PENDING; stock 99, reserved 0, version 1; xóa P1 khỏi Cart, giữ P2/P3 và quota | PASS |

Tổng cộng 5 × (2 B2 + 2 B4 + 15 C2) = **95 kết quả**. C2 có **70 lần từ chối**, **5 lần hợp lệ**. Mỗi nhánh có assertion theo dữ liệu DBMS; không suy PASS chỉ từ exit code.

## Đọc transcript

- Lượt 1: [B2](1-B2.txt), [B4](1-B4.txt), [C2](1-C2.txt). Lượt 2–5 dùng tiền tố tương ứng: `2-B2.txt` … `5-C2.txt`.
- B2 ghi SQL, read/decision, mốc chờ, COMMIT và giá trị stock trước/sau mỗi nhánh; `N-oracle-after.txt` là snapshot độc lập sau khôi phục, bằng [baseline Oracle](oracle-before.txt).
- B4 ghi HGETALL trước/sau mỗi nhánh, sau conflict và retry; `N-B4-after.txt` là snapshot đầy đủ, `N-redis-reset.txt` là reset theo manifest.
- C2 ghi `CASE`, command/response, `SNAPSHOT` trước/sau và `ASSERT`. Mỗi case reset riêng; `FIXTURE_INJECTION` chỉ chuẩn bị dữ liệu đầu vào xấu. Mọi mutation của request Checkout đi qua cùng một EVAL validation trước ghi. `N-redis-after.txt` bằng [baseline Redis](redis-before.txt).
- Lua gọi theo source [C2-validate.lua](../../redis/lua/C2-validate.lua); input KEYS/ARGV đầy đủ nằm trong transcript. Miền số nguyên canonical <=1e9, key không TTL. Mô hình Checkout NORMAL một mặt hàng không bao gồm giá/tổng tiền/quota; quota là đối tượng quan sát phải giữ nguyên.

## Khôi phục và phạm vi

- [Oracle cleanup preview](oracle-cleanup-preview.txt) → [cleanup](oracle-cleanup.txt) → [verify](oracle-verify.txt): mọi row fixture về 0, `VERIFY_ABSENT=PASS`.
- [Redis cleanup preview](redis-cleanup-preview.txt) → [cleanup](redis-cleanup.txt) → [verify](redis-verify.txt): bảy key manifest đều none, ALL_RUN_KEYS rỗng. Product Hash bổ sung được dọn sau từng C2.
- Cuối mỗi B2, B lấy lại row lock rồi ROLLBACK. [Audit Oracle cuối](oracle-final-audit.txt) chỉ có một SHOP session ACTIVE là session audit; [audit Redis cuối](redis-final-audit.txt) không còn txresearch key.
- [Scope check](scope-check.json): Harness gốc/B1/B3 và source B5/D1/D2 không đổi. [124 transcript/fixture lịch sử](historical-integrity.json) giữ nguyên so với Git HEAD. [Hash source](source-sha256.json) cho code hiện hành.
- Không chạy lại/sửa các experiment khác, không đổi Java/schema/API/benchmark. Những bản sửa có sẵn ở frontend/package-lock.json và report/transaction.zip không thuộc đợt này.

## Lịch sử, OpenSpec và giới hạn

Transcript B2/B4/C2 trong p2/p3 giữ nguyên và được đánh dấu **SUPERSEDED** trong hồ sơ case. [Lượt đầu FAIL harness](../revision-20261109-attempt1/README.md) lưu lỗi regex parser và cleanup đầy đủ; không tính lượt đó vào PASS.

Change gốc: `local_docs/openspec/changes/revise-transaction-b2-b4-c2` theo convention repo. Vì local_docs bị gitignore, [bản chụp proposal](openspec-plan/proposal.md), [design](openspec-plan/design.md), [spec](openspec-plan/specs/transaction-research-revisions/spec.md), [tasks](openspec-plan/tasks.md) được lưu ở đây để bàn giao. OpenSpec strict validation đạt, chưa archive.

C2 chỉ chứng minh guard của luồng Checkout được thử nghiệm. Redis không tự có FK/CHECK, Lua không có full rollback khi runtime error sau ghi. Không kết luận guard chặn mọi direct write của client khác, OOM/crash, transaction xuyên DBMS hay Checkout production.

Cơ chế được đối chiếu với [Oracle Data Concurrency and Consistency](https://docs.oracle.com/en/database/oracle/oracle-database/26/cncpt/data-concurrency-and-consistency.html) và [Redis Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/); kết quả trong bảng dựa trên transcript local.

## Tái lập

Sau P0 audit local/dev, chạy từ root với run ID mới:

```bash
python3 report/transaction/research/run-revisions.py <run_id_moi> <thu_muc_evidence_moi> --repeat 5
```

Runner dùng fixture/setup/reset/snapshot/preview/cleanup hiện có và lưu ID/key chính xác; không dùng ID trong transcript này cho run mới.
