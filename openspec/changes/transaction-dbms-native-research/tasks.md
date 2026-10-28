# Tasks

## 1. P0 — Audit chỉ đọc

- [x] 1.1 Tạo `research/oracle/audit.sql`, lệnh audit Redis và runbook kết nối SQLcl/SQL*Plus/DBeaver/`redis-cli`; ghi schema/user/PDB, V1/V2, PK/FK/UNIQUE/CHECK cùng `STATUS`, `VALIDATED`, `DEFERRABLE`, `DEFERRED`, thay đổi kiểm tra constraint ở session nếu có, auto-commit, Redis version/persistence/index/prefix. Đầu ra: bản đồ metadata không chứa secret. Kiểm chứng: chạy chỉ đọc trên local ShopFlash, đối chiếu V1/V2 và ghi mọi khác biệt hoặc `BLOCKED` trước thao tác ghi.
- [x] 1.2 Đối chiếu Checkout `NORMAL`, Redis model và JUnit/benchmark/k6 hiện có vào bảng phạm vi case A1–D2; ghi điều kiện local/dev và rủi ro dữ liệu dùng chung. Đầu ra: mục P0 trong README với 14 ID, tài sản tái sử dụng và dẫn `report/transaction/RESEARCH_PLAN.md` làm nguồn quyết định; OpenSpec quản lý phân rã/triển khai. Kiểm chứng: mỗi case trỏ tới bảng/key/constraint hay key mô phỏng đúng và P0 liệt kê file, lệnh, evidence, blocker.

## 2. P1 — Fixture và an toàn

- [x] 2.1 Viết setup Oracle bằng bind/PLSQL trên `SHOP`: user bcrypt local, category, ít nhất ba product/inventory, cart có dòng mua/không mua; sinh manifest run ID/PK và snapshot chuẩn. Đầu ra: `research/oracle/setup.sql` và hướng dẫn tham số. Kiểm chứng: chạy setup trên local đã qua P0, kiểm tra FK/giá/tồn/giỏ và chạy lại không nhân đôi fixture.
- [x] 2.2 Viết setup Redis dưới `txresearch:<runId>:*` và manifest key cụ thể, không đụng `txlab:*` hay prefix ứng dụng. Đầu ra: lệnh CLI/script setup và snapshot đầu kỳ. Kiểm chứng: đọc key bằng client khác; so sánh danh sách key ngoài manifest trước/sau và chạy lại được.
- [x] 2.3 Viết reset/cleanup hai kho theo manifest, thứ tự FK, bước xem phạm vi DELETE trước commit và xử lý cleanup một phần. Đầu ra: script và runbook cứu hộ. Kiểm chứng: tạo–reset–dọn fixture thử hai lần, hậu kiểm không còn row/key của run, dữ liệu ngoài manifest không đổi; ghi file/lệnh/evidence/blocker P1.

## 3. P2 — Semantics A1–A5 và C1–C2

- [x] 3.1 Viết và chạy A1 trên Order/Order Items fixture, giữ một Oracle session cho từng transaction đến `COMMIT`/`ROLLBACK`, kiểm chứng bằng session độc lập. Đầu ra: SQL, transcript và snapshot. Kiểm chứng: chỉ bản ghi commit hiển thị sau transaction, case có trạng thái thực và cleanup.
- [x] 3.2 Viết và chạy A2 với UNIQUE, FK, CHECK thật; ghi `ORA-00001`/`ORA-02291`/`ORA-02290` hoặc `NOT_APPLICABLE` theo metadata. Đầu ra: DML lỗi và evidence. Kiểm chứng: từng mã lỗi và thời điểm phát sinh khớp trạng thái immediate/deferred đã audit, không thêm constraint hay để lại ghi dở.
- [x] 3.3 Viết và chạy A3 với statement lỗi, `SAVEPOINT`, `ROLLBACK TO` và full `ROLLBACK` trong cùng Oracle session. Đầu ra: SQL và ảnh trạng thái sau từng bước. Kiểm chứng: chứng minh được ranh giới ba phạm vi hoàn tác, hậu kiểm fixture về đầu kỳ.
- [x] 3.4 Viết và chạy A4 `MULTI/EXEC` và `DISCARD` trên key Cart/Order/Inventory mô phỏng, giữ một kết nối `redis-cli` cho mỗi chuỗi. Đầu ra: CLI transcript. Kiểm chứng: đọc từ client độc lập thấy đúng thứ tự ghi khi `EXEC` và không đổi sau `DISCARD`.
- [x] 3.5 Viết và chạy A5 với lỗi runtime trong `EXEC` trên cùng kết nối Redis và Lua lỗi sau ghi, ghi lỗi queue-time riêng. Đầu ra: CLI/Lua và trạng thái key. Kiểm chứng: ghi thành công trước lỗi vẫn tồn tại; không mô tả đó là rollback.
- [x] 3.6 Viết và chạy C1 từ metadata Oracle, lập ma trận Order–Order Item–Product/Inventory: DBMS bảo vệ gì, validation/conditional `UPDATE` bảo vệ gì. Đầu ra: ma trận dẫn constraint thật. Kiểm chứng: tên/loại/cột khớp audit và không chép lại toàn bộ lỗi A2.
- [x] 3.7 Viết và chạy C2 bằng CLI ghi tham chiếu/giá trị giả rồi Lua validation. Đầu ra: lệnh và evidence. Kiểm chứng: ghi CLI không bị FK/CHECK quan hệ chặn, Lua từ chối đầu vào sai theo logic mô phỏng; dọn key đúng manifest.
- [x] 3.8 Tổng hợp P2 với trạng thái, expected/actual, output gốc và cleanup cho A1–A5/C1–C2. Đầu ra: mục evidence P2 và danh sách file/lệnh/blocker. Kiểm chứng: đủ bảy ID, không case nào `PASS` nếu chưa chạy thật.

## 4. P3 — Concurrency B1–B5 và multi-step D1–D2

- [x] 4.1 Viết và chạy B1 bằng hai Oracle Session A/B cố định tại `READ COMMITTED`. Đầu ra: timeline SQL và snapshot trước/sau A commit. Kiểm chứng: B không thấy dirty read, ghi hai lần đọc khác nhau nếu A commit.
- [x] 4.2 Viết và chạy B2: đối chứng đọc–sửa–ghi rủi ro trên fixture, reset, rồi `FOR UPDATE` và conditional `UPDATE`. Đầu ra: timeline, tồn khả dụng và trạng thái hai session. Kiểm chứng: nhánh bảo vệ không oversell/mất cập nhật, mọi nhánh được reset/cleanup.
- [x] 4.3 Viết và chạy B3 thành hai lượt trên fixture: timeout hữu hạn; deadlock với A khóa dòng 1, B khóa dòng 2, A chờ dòng 2 rồi B chờ dòng 1. Đầu ra: mã lỗi/transcript có thời điểm và lệnh giải cứu. Kiểm chứng: phân biệt rollback statement/transaction, chủ động `ROLLBACK` cả A/B hoặc giải cứu session quá hạn, xác nhận hết khóa rồi mới reset fixture.
- [x] 4.4 Viết và chạy B4: client A giữ một kết nối từ `WATCH` qua đọc/`MULTI` tới `EXEC`, client B ở kết nối khác sửa key; hướng dẫn retry giới hạn. Đầu ra: timeline CLI và key cuối. Kiểm chứng: `EXEC` bị từ chối không ghi hàng đợi, retry có kết quả xác định.
- [x] 4.5 Viết và chạy B5 Lua quota với các client tranh suất trong namespace nghiên cứu. Đầu ra: script và kết quả từng client. Kiểm chứng: số suất giữ không vượt quota, remaining không âm và không có chen ngang kiểm tra–ghi.
- [x] 4.6 Viết và chạy D1 Checkout `NORMAL` nhiều mặt hàng bằng DML trong cùng Oracle session, cố ý gây lỗi constraint sau ghi một phần rồi full `ROLLBACK`. Đầu ra: SQL và snapshot Order/Items/Cart/Inventory ở ba mốc. Kiểm chứng: thời điểm lỗi khớp trạng thái immediate/deferred; sau lỗi statement còn phân biệt được với sau full rollback, dòng giỏ không mua được giữ đúng.
- [x] 4.7 Viết và chạy D2 Lua nhiều key, tiền kiểm hàng/quota trước ghi, thử nhánh hợp lệ và không hợp lệ. Đầu ra: script và snapshot. Kiểm chứng: nhánh lỗi không ghi dở, invariant đúng; ghi rõ đây là mô phỏng không tạo Order Oracle.
- [x] 4.8 Tổng hợp P3 với timeline, timeout/cứu hộ, expected/actual, hậu kiểm và cleanup cho B1–B5/D1–D2. Đầu ra: mục evidence P3 và danh sách file/lệnh/blocker. Kiểm chứng: đủ bảy ID, không còn session chờ hoặc key/row sai trạng thái.

## 5. P4 — Evidence và báo cáo

- [x] 5.1 Chuẩn hóa `research/README.md` và evidence index cho đủ A1–D2: mục tiêu, tiền điều kiện, setup, execute, observe, verify, cleanup, kết luận; dùng `PASS`/`FAIL`/`BLOCKED`/`NOT_APPLICABLE` có lý do. Đầu ra: 14 hồ sơ có run ID và liên kết transcript. Kiểm chứng: đối chiếu từng hồ sơ với lệnh, output gốc, metadata, expected/actual và hậu kiểm cleanup; không có output/số liệu giả.
- [x] 5.2 Cập nhật `SECTION_TRANSACTION.md` bằng nhận định có dẫn chứng DBMS-native, giới hạn và lựa chọn Oracle/Redis cho ShopFlash; giữ bảng benchmark cũ nguyên trạng. Đầu ra: báo cáo Transaction và danh sách file/lệnh/evidence/blocker P4. Kiểm chứng: mọi kết luận cơ chế dẫn tới case, tách database-only benchmark và HTTP/JUnit khỏi nghiên cứu native.

## 6. P5 — Rà soát benchmark hiện có

- [x] 6.1 Đọc `TransactionDbBenchmarkIT`, k6, config/raw results và phép tổng hợp hiện có; lập bảng workload Oracle–Redis/HTTP về payload, số thao tác, correctness, network/client overhead, commit/durability, khóa, isolation và cấu hình mẫu. Đầu ra: đánh giá có căn cứ cùng danh sách file/lệnh/evidence/blocker P5. Kiểm chứng: xác nhận hoặc bác bỏ từ file thực tế mốc 50 warm-up, 5 × 200, giỏ 1/5, đồng thời 1/10; chỉ đề xuất đo/sửa riêng khi nêu được sai lệch cụ thể, không sửa runner/k6/raw results.
