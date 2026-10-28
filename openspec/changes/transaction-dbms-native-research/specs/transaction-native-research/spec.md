# Spec Delta

## Purpose

Cung cấp bộ thực nghiệm Transaction chạy trực tiếp trên Oracle và Redis của ShopFlash, với dữ liệu cách ly và chứng cứ đủ để tái lập, kiểm tra, giải thích từng cơ chế DBMS.

## ADDED Requirements

### Requirement: Khảo sát môi trường trước khi ghi
Bộ nghiên cứu SHALL xác minh bằng thao tác chỉ đọc schema `SHOP`, migrations, trạng thái/hiệu lực và tính immediate/deferred của constraint, kết nối, auto-commit, Redis version/persistence và các prefix trước khi tạo fixture.

#### Scenario: Môi trường local phù hợp
- **WHEN** kết nối trỏ tới database phát triển/local ShopFlash và metadata khớp các bảng/key cần thử
- **THEN** nhật ký audit ghi định danh môi trường không chứa secret, các khác biệt so với DDL trong repo và điều kiện đủ để bắt đầu setup

#### Scenario: Môi trường dùng chung hoặc chưa xác minh
- **WHEN** không xác định được môi trường, schema, quyền sở hữu dữ liệu hoặc an toàn cleanup
- **THEN** không chạy lệnh ghi và ghi `BLOCKED` cùng lý do; không chuyển sang schema hay database thay thế

### Requirement: Fixture riêng và dọn an toàn
Mỗi lượt SHALL dùng dữ liệu quan hệ trong `SHOP` có dấu `TXRESEARCH_<runId>` và key Redis `txresearch:<runId>:*`, lưu ID/key thực tế trong manifest. Setup/reset/cleanup SHALL chỉ tác động fixture của lượt đó, có hậu kiểm trước và sau cleanup.

#### Scenario: Chạy lại lượt nghiên cứu
- **WHEN** setup hoặc reset chạy lại với một run ID hợp lệ
- **THEN** Cart, Product, Inventory, Order và key mô phỏng trở về trạng thái đầu vào xác định; ID/key của lượt khác và dữ liệu nhóm không đổi

#### Scenario: Cleanup một lượt
- **WHEN** cleanup được yêu cầu theo manifest
- **THEN** lệnh xóa đúng ID Oracle theo thứ tự FK và đúng danh sách key Redis đã tạo, cho xem phạm vi xóa trước `COMMIT`, rồi xác nhận không còn fixture của lượt

#### Scenario: Cleanup không thể xác minh
- **WHEN** manifest thiếu, dữ liệu không khớp hoặc có session còn giữ khóa
- **THEN** cleanup dừng an toàn, giữ manifest và báo `BLOCKED`; không dùng `TRUNCATE`, `FLUSHDB`, `FLUSHALL`, reset sequence hay xóa theo prefix rộng

### Requirement: Thí nghiệm Atomicity và xử lý lỗi A1–A5
Bộ nghiên cứu SHALL cung cấp lệnh gốc DBMS và hậu kiểm độc lập cho A1–A5. Các bước một Oracle transaction SHALL dùng cùng session đến `COMMIT`/`ROLLBACK`; `MULTI` đến `EXEC`/`DISCARD` SHALL dùng cùng kết nối Redis. Kết quả SHALL phân biệt các phạm vi rollback và ghi đã thực thi.

#### Scenario: A1 Oracle COMMIT và ROLLBACK
- **WHEN** fixture Order/Order Items được ghi rồi lần lượt `COMMIT` và `ROLLBACK`
- **THEN** session độc lập thấy toàn bộ ghi đã commit và không thấy các ghi đã rollback

#### Scenario: A2 Oracle UNIQUE, FK và CHECK
- **WHEN** DML trên bảng ShopFlash vi phạm từng constraint thực sự tồn tại
- **THEN** evidence ghi lỗi Oracle tương ứng `ORA-00001`, `ORA-02291`, `ORA-02290` tại thời điểm kiểm tra thực tế theo trạng thái immediate/deferred; constraint không tồn tại được ghi `NOT_APPLICABLE` kèm metadata, không tự thêm constraint

#### Scenario: A3 Oracle các phạm vi rollback
- **WHEN** một statement lỗi sau ghi hợp lệ, rồi thử `SAVEPOINT`, `ROLLBACK TO` và full `ROLLBACK`
- **THEN** trạng thái sau từng bước chứng minh statement lỗi không tự hoàn tác cả transaction và phân biệt ba phạm vi hoàn tác

#### Scenario: A4 Redis MULTI/EXEC và DISCARD
- **WHEN** lệnh Cart/Order/Inventory được xếp hàng và gọi `EXEC` hoặc `DISCARD`
- **THEN** `EXEC` chạy lệnh theo thứ tự, còn `DISCARD` chỉ hủy lệnh chưa chạy; key cuối được đọc lại bằng client độc lập

#### Scenario: A5 Redis lỗi runtime
- **WHEN** `EXEC` gặp lỗi runtime sau lệnh hợp lệ hoặc Lua báo lỗi sau khi đã ghi
- **THEN** evidence phân biệt lỗi khi xếp hàng và khi thực thi, đồng thời ghi rõ các thay đổi đã chạy không tự rollback

### Requirement: Thí nghiệm isolation và concurrency B1–B5
Bộ nghiên cứu SHALL dùng hai session/client với mốc điều phối, timeout hữu hạn và hậu kiểm fixture cho B1–B5. Mỗi bên SHALL giữ kết nối riêng xuyên suốt transaction; client `WATCH` SHALL giữ cùng kết nối đến `EXEC`. Kết quả SHALL mô tả đúng phạm vi của khóa, `WATCH` và Lua.

#### Scenario: B1 Oracle READ COMMITTED
- **WHEN** session A sửa Inventory chưa commit, session B đọc trước và sau khi A commit
- **THEN** B không đọc giá trị chưa commit và hai câu `SELECT` có thể thấy phiên bản khác nhau

#### Scenario: B2 Oracle tranh cùng Inventory
- **WHEN** hai session cùng mua fixture Inventory bằng `SELECT ... FOR UPDATE` và conditional `UPDATE`
- **THEN** session thứ hai chờ hoặc bị từ chối theo cấu hình, không bán quá `on_hand_quantity - reserved_flash_quantity`; đối chứng đọc–sửa–ghi thiếu bảo vệ chỉ dùng fixture

#### Scenario: B3 Oracle timeout và deadlock
- **WHEN** lượt timeout được tách khỏi lượt A khóa dòng 1, B khóa dòng 2, A chờ dòng 2 rồi B chờ dòng 1 trên fixture
- **THEN** evidence ghi mã lỗi thật và giới hạn chờ, phân biệt rollback statement với transaction; cả A/B được `ROLLBACK` hoặc giải cứu an toàn và không còn session giữ khóa

#### Scenario: B4 Redis WATCH conflict
- **WHEN** client A giữ cùng kết nối từ `WATCH` qua đọc/`MULTI` đến `EXEC`, còn client B trên kết nối khác sửa key trước `EXEC`
- **THEN** `EXEC` của A bị từ chối, không ghi lệnh đã xếp hàng và có hướng dẫn retry có giới hạn

#### Scenario: B5 Redis Lua quota
- **WHEN** các client tranh quota fixture bằng Lua kiểm tra rồi giữ suất
- **THEN** kiểm tra–ghi không bị chen ngang, quota cuối không âm hoặc vượt giới hạn và mọi kết quả được đối chiếu với số suất đầu kỳ

### Requirement: Thí nghiệm consistency C1–C2
Bộ nghiên cứu SHALL chỉ ra ràng buộc do DBMS tự thi hành và quy tắc cần logic nghiệp vụ, dựa trên schema/key thật chứ không suy ra từ tên bảng.

#### Scenario: C1 Ma trận constraint Oracle
- **WHEN** đọc metadata quan hệ Order–Order Item–Product/Inventory
- **THEN** ma trận dẫn tên PK/FK/UNIQUE/CHECK thực tế và tách các quy tắc cần validation hoặc conditional `UPDATE`; không lặp lại toàn bộ lỗi A2

#### Scenario: C2 Giá trị và tham chiếu giả Redis
- **WHEN** CLI ghi key fixture có tham chiếu giả hoặc giá trị không hợp lệ rồi Lua kiểm tra cùng điều kiện
- **THEN** evidence cho thấy Redis không tự áp FK/CHECK quan hệ và chỉ script có validation mới từ chối ghi nghiệp vụ sai

### Requirement: Thí nghiệm checkout nhiều bước D1–D2
Bộ nghiên cứu SHALL dùng Checkout `NORMAL` nhiều mặt hàng làm workload trung tâm, giữ dòng giỏ không mua và đối chiếu khả năng phục hồi của Oracle với bảo vệ invariant trong Lua. Các DML và full `ROLLBACK` của D1 SHALL dùng cùng Oracle session.

#### Scenario: D1 Oracle lỗi DBMS sau ghi một phần
- **WHEN** DML đã ghi một phần Order, Order Items, Inventory và Cart rồi cố ý gây lỗi SQL có thể tái lập
- **THEN** evidence ghi trạng thái sau statement lỗi và sau full `ROLLBACK`; toàn bộ fixture trở về trạng thái trước, gồm các dòng giỏ không mua

#### Scenario: D2 Redis Lua kiểm tra trước khi ghi
- **WHEN** Lua trên key fixture kiểm tra hàng/quota của nhiều mặt hàng trước khi cập nhật Cart/Order/Inventory mô phỏng
- **THEN** nhánh hợp lệ giữ invariant, nhánh không hợp lệ không ghi dở; kết luận không gán cho Lua FK, durability hay transaction xuyên Oracle–Redis

### Requirement: Evidence và báo cáo có thể đối chiếu
Mỗi case SHALL có mục tiêu, tiền điều kiện, setup, lệnh SQL/PLSQL hoặc Redis CLI/Lua đầy đủ, output gốc, expected/actual, hậu kiểm, cleanup và kết luận. Chỉ kết quả chạy thật được đánh dấu `PASS`.

#### Scenario: Case đã chạy
- **WHEN** một case hoàn tất trên database đã xác minh
- **THEN** evidence có run ID, thời điểm, phiên bản, session/client, mã lỗi khi có, trạng thái trước/sau và `PASS` hoặc `FAIL` theo assertion thực tế

#### Scenario: Case không thể chạy hoặc không phù hợp
- **WHEN** thiếu điều kiện chạy hoặc constraint liên quan không tồn tại
- **THEN** case được ghi `BLOCKED` hoặc `NOT_APPLICABLE` cùng bằng chứng/lý do, không điền output hay số đo suy đoán

#### Scenario: Kết luận nghiên cứu
- **WHEN** tổng hợp 14 case vào báo cáo Transaction
- **THEN** mọi nhận định về Oracle, Redis và lựa chọn dữ liệu ShopFlash dẫn tới evidence thật, nêu giới hạn, và tách nghiên cứu DBMS-native khỏi benchmark database và kiểm thử ứng dụng

### Requirement: Rà soát benchmark sau nghiên cứu cơ chế
Sau khi hoàn tất evidence cơ chế, bộ nghiên cứu SHALL rà soát workload, payload, số thao tác, correctness, network/client overhead, commit/durability, khóa và isolation của Java DB benchmark và k6 hiện có, không tự thay số liệu.

#### Scenario: Benchmark cũ đủ căn cứ để đọc
- **WHEN** rà soát cấu hình và dữ liệu thô hiện có
- **THEN** báo cáo ghi rõ kết quả nào là database-only, kết quả nào là HTTP, khác biệt Oracle–Redis và mức tin cậy của phép so sánh; cấu hình 50 warm-up, 5 × 200, giỏ 1/5, concurrency 1/10 chỉ được xác nhận khi đúng file thực tế

#### Scenario: Phát hiện lệch workload hoặc số đo
- **WHEN** tìm thấy khác biệt cụ thể ảnh hưởng kết luận
- **THEN** lập đề xuất sửa hoặc đo lại riêng với tác động đã xác minh; giữ nguyên JUnit, runner, k6 và raw results hiện có
