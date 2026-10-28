# Design

## Context

Xem `proposal.md`, `specs/transaction-native-research/spec.md` và nguồn quyết định `report/transaction/RESEARCH_PLAN.md`. OpenSpec quản lý phân rã/triển khai, không đổi phạm vi hay phương pháp đã chốt. Repo hiện có `SHOP` từ V1/V2: `USERS`, `CATEGORIES`, `PRODUCTS`, `PRODUCT_INVENTORY`, `CARTS`, `CART_ITEMS`, `ORDERS`, `ORDER_ITEMS`; `CARTS.version` và `ORDERS.checkout_request_hash` thuộc V2. V1 đã khai báo PK/FK/UNIQUE/CHECK cần cho A2/C1 và view `V_INVENTORY_INVARIANT_CHECK`. Đây là quan sát từ migration, chưa phải xác minh metadata của database đang chạy.

Checkout thật khóa `CARTS` → `PRODUCTS` → `PRODUCT_INVENTORY` theo `product_id`, kiểm tra `on_hand_quantity - reserved_flash_quantity`, ghi đơn/dòng, giảm tồn có điều kiện và chỉ xóa dòng giỏ đã mua. Redis hiện dành cho `product:*`, `flash:*`, `order:analytics:*`, `order:line:*`; `init.lua` tạo index trên các prefix đó. `TransactionSemanticsIT`, `RedisTransactionIT`, `TransactionDbBenchmarkIT`, k6 và `report/transaction/results/` là baseline để đọc, không là bằng chứng chính của change này. Fixture cũ dùng `txlab:*`, nên bộ mới dùng namespace khác.

## Goals / Non-Goals

**Goals:** Có đường chạy thủ công SQLcl/SQL*Plus/DBeaver và `redis-cli`, chứng cứ đúng 14 case, cleanup kiểm soát được, kết luận gắn với database ShopFlash thực tế.

**Non-Goals:** Đổi schema, nghiệp vụ Checkout/F9, API, Java test/runner, k6, index Redis hoặc số liệu cũ; đo latency/throughput từ vài lần chạy thủ công.

## Decisions

### 1. Tách audit chỉ đọc khỏi setup

P0 đọc `USER_TAB_COLUMNS`, `USER_CONSTRAINTS`/`USER_CONS_COLUMNS`, `USER_INDEXES`, `flyway_schema_history`, user/service/PDB và cấu hình client auto-commit; ghi `STATUS`, `VALIDATED`, `DEFERRABLE`, `DEFERRED` của từng constraint liên quan và kiểm tra session có đổi thời điểm kiểm tra hay không. Redis đọc `INFO`, `MODULE LIST`, `FT._LIST` và prefix đang tồn tại. Runbook ghi môi trường, version, persistence, migration, khác biệt với V1/V2 mà không ghi secret. Chỉ mở P1 nếu xác minh đây là local/dev không dùng chung. Cách này giữ nguyên database hiện có; tạo schema riêng sẽ làm sai mục tiêu nghiên cứu. Nếu metadata khác migration, ghi blocker/`NOT_APPLICABLE` theo case, không sửa migration để ép kết quả. A2/D1 phải gắn kỳ vọng mã lỗi với thời điểm kiểm tra constraint đã xác minh.

### 2. Fixture cùng schema, định danh chính xác

Mỗi run dùng ID ngắn chỉ gồm chữ/số, lưu trong các business key dạng `TXRESEARCH_<runId>` (email/slug/SKU/order code/idempotency key theo giới hạn cột) và Redis `txresearch:<runId>:*`. Setup tạo customer với bcrypt hash dành cho local, category, ít nhất ba product/inventory, cart và dòng mua/không mua. Số lượng/giá cố định, `reserved_flash_quantity=0`, nên có thể so snapshot. Manifest ngoài DB ghi run ID, từng PK và danh sách key đã tạo; lệnh SQL dùng bind `:run_id`/`:id` hoặc biến PL/SQL, không nối giá trị đầu vào vào SQL. Không dùng sequence reset hoặc DDL trong transaction thử nghiệm.

Reset/case riêng chỉ dùng ID manifest và snapshot chuẩn. Cleanup Oracle trong transaction riêng: kiểm tra danh sách/mức đếm dự kiến, xóa `ORDER_ITEMS` → `ORDERS` → `CART_ITEMS` → `CARTS` → `PRODUCT_INVENTORY` → `PRODUCTS` → `CATEGORIES`/`USERS`, xem số dòng trước `COMMIT`, rồi truy vấn độc lập. Redis chỉ `DEL` các key đúng trong manifest sau khi đối chiếu run ID; không quét rồi xóa prefix rộng. Nếu hai kho dọn được một bên nhưng bên kia lỗi, giữ manifest và hướng dẫn tiếp tục dọn bên còn lại. Fixture Java `txlab` không được tái dùng vì lệnh reset của nó có thể dọn key bằng prefix riêng và phụ thuộc Spring.

### 3. Script nhóm theo cơ chế, điều phối bằng runbook

Dự kiến `report/transaction/research/README.md`, `oracle/{audit,setup,cleanup}.sql`, `oracle/experiments/`, `redis/{audit,setup,cleanup}` cùng `redis/lua/`, `evidence/`. File thực thi gom các case khi hợp lý, nhưng README có lệnh chính xác cho A1–D2, session/client A/B, mốc dừng và cách đọc snapshot. A1–A3/C1/D1 dùng DML/metadata trên bảng thật. Các bước DML, `SAVEPOINT` và `COMMIT`/`ROLLBACK` của một Oracle transaction chạy trong cùng SQL session/kết nối, tắt auto-commit khi cần; session kiểm chứng độc lập chỉ đọc sau mốc phù hợp. Không dùng một lần gọi SQLcl mới cho mỗi bước của cùng transaction.

B1–B3 dùng hai cửa sổ SQL với kết nối cố định. B3 tách lượt timeout khỏi lượt deadlock: trên hai Inventory fixture, A khóa dòng 1, B khóa dòng 2, A yêu cầu dòng 2 và chờ, rồi B yêu cầu dòng 1 để khép vòng chờ. Mỗi thao tác chờ có giới hạn thời gian ở SQL hoặc client; ghi mã lỗi thật, rồi chủ động `ROLLBACK` cả A và B vì statement lỗi có thể chỉ hoàn tác chính nó, khóa từ câu trước vẫn còn. Nếu quá hạn, thực hiện bước giải cứu được ghi trong runbook, xác nhận cả hai session đã thoát/chấm dứt transaction rồi mới reset/cleanup. B2 có đối chứng đọc–sửa–ghi thiếu bảo vệ trên đúng fixture, rồi reset trước nhánh bảo vệ. `D1` tạo lỗi constraint DBMS sau ghi một phần, quan sát trước khi gọi full `ROLLBACK`; không dùng Java Probe.

Redis dùng Hash/key mô phỏng Cart/Order/Inventory/quota, `MULTI/EXEC`, `DISCARD`, `WATCH`, Lua trên prefix nghiên cứu; không ghi `flash:*` thật. A4/A5 dùng một phiên `redis-cli` liên tục từ `MULTI` đến `EXEC`/`DISCARD`; B4 dùng cùng kết nối A từ `WATCH` qua các bước đọc, `MULTI`, `EXEC`, còn B dùng kết nối khác để sửa key. Không gọi `redis-cli` mới cho từng lệnh trong chuỗi đó. A5 có cả lỗi runtime trong `EXEC` và Lua lỗi sau ghi; lỗi queue-time được ghi riêng để người đọc không nhầm. B5/D2 kiểm tra toàn bộ điều kiện và kiểu dữ liệu trước lệnh ghi trong Lua, rồi hậu kiểm invariant. Các key này là mô hình logic độc lập, không tạo Order thật, không đồng bộ Oracle và không chứng minh durability hay FK của Redis.

### 4. Evidence là đầu ra của lần chạy, không phải kỳ vọng

Mỗi case có mẫu gồm mục tiêu, precondition, setup, câu lệnh đầy đủ, transcript/output nguyên gốc, mã lỗi, expected/actual, assertion, cleanup, kết luận và giới hạn. Evidence mang run ID, timestamp, Oracle/Redis version, session label và snapshot trước/sau; che credentials/token. `PASS` chỉ sau khi chạy thật và cleanup/hậu kiểm thành công; `FAIL`, `BLOCKED`, `NOT_APPLICABLE` có lý do. Output dự kiến trong script là tiêu chí kiểm chứng, không được điền như kết quả quan sát.

### 5. Đánh giá benchmark sau P4

P5 chỉ đọc runner, k6, config/raw results hiện có; lập bảng cùng logical workload và khác biệt payload, thao tác, network/client overhead, commit/durability, khóa, isolation, correctness. Giữ ba lớp kết luận: DBMS-native semantics, database-only performance, application HTTP correctness/performance. Nếu phát hiện vấn đề cụ thể, ghi đề xuất đo/sửa riêng; không viết lại benchmark trong change này khi chưa có căn cứ.

## Risks / Trade-offs

- [Database local thực chất đang dùng chung] → P0 chặn mọi ghi, ghi `BLOCKED`; không tự chuyển môi trường.
- [DDL/auto-commit làm sai rollback] → audit chế độ client, tách setup khỏi case, cấm DDL giữa transaction.
- [Deadlock để lại khóa] → hai lượt timeout/deadlock tách biệt, giới hạn chờ, `ROLLBACK` cả A/B kể cả session vừa nhận lỗi và hậu kiểm trước cleanup.
- [Manifest thiếu hoặc cleanup một phần] → không xóa suy đoán; giữ ID/key đã xác minh và hướng dẫn tiếp tục theo từng kho.
- [Metadata live khác V1/V2] → dùng metadata làm căn cứ của case, báo khác biệt; constraint thiếu được `NOT_APPLICABLE` thay vì tạo mới.
- [Mô phỏng Redis bị hiểu là Checkout thật] → gắn nhãn rõ ở runbook/evidence/report và không so latency thủ công với benchmark cũ.

## Migration Plan

Không có migration/deploy. Triển khai theo P0 → P5; P0 là điều kiện trước mọi ghi, P4 hoàn tất trước P5. Khi dừng giữa chừng, rollback các transaction đang mở, giải phóng khóa, dọn theo manifest và giữ evidence của case đã chạy; có thể xóa các file nghiên cứu mới để quay về trạng thái repo trước change mà không tác động ứng dụng hoặc schema.
