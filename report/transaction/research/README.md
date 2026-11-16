# Nghiên cứu Transaction DBMS-native

Runbook này cùng [chỉ mục bằng chứng](evidence/INDEX.md) mô tả 14 thí nghiệm đã thực hiện. Chỉ chạy trên Docker Compose ShopFlash **local/dev đã xác minh không dùng chung**. Bộ này dùng SQL/PLSQL và Redis CLI/Lua, không cần Spring Boot; không ghi vào `flash:*`, các projection, hoặc fixture `txlab:*`.

## Bắt đầu từ đâu?

1. Đọc P0 và P1 bên dưới trước khi tạo fixture. Mỗi run có ID Oracle và danh sách key Redis riêng; không dùng số ID trong transcript cũ cho run mới.
2. Mở [chỉ mục bằng chứng](evidence/INDEX.md) để chọn case và đọc hồ sơ của case trước khi xem output `.txt`. Hồ sơ giải thích ý nghĩa của từng mốc; transcript giữ nguyên phản hồi DBMS.
3. Đọc [báo cáo Transaction](../SECTION_TRANSACTION.md) để xem kết quả được diễn giải trong bối cảnh ShopFlash. [P5](evidence/p5/README.md) chỉ rà soát benchmark cũ, không phải một thí nghiệm thứ 15.

```text
report/transaction/
├── SECTION_TRANSACTION.md Phần báo cáo để đọc kết luận
└── research/
    ├── README.md           Hướng dẫn chạy và dọn fixture (file này)
    ├── oracle/             SQL/PLSQL và công cụ giữ session Oracle
    │   └── experiments/    A1–A3, B1–B3, C1, D1
    ├── redis/              Redis CLI và Lua
    │   ├── experiments/    A4–A5, B4–B5, C2, D2
    │   └── lua/            Script Lua được gọi trong các case
    └── evidence/
        ├── INDEX.md        Bản đồ 14 case và liên kết output
        ├── p0/             Audit chỉ đọc
        ├── p1/             Manifest và thử setup/reset/cleanup
        ├── p2/<case>/      Semantics: hồ sơ + transcript của từng case
        ├── p3/<case>/      Concurrency/multi-step: hồ sơ + transcript
        ├── p4/             Cleanup cuối và tổng hợp báo cáo
        └── p5/             Rà soát benchmark cũ
```

**Cách đọc một hồ sơ:** `Mục tiêu` là điều muốn kiểm chứng; `Setup` là trạng thái trước; `Execute` là lệnh và thứ tự; `Observe` là phản hồi DBMS; `Expected/actual` đối chiếu dự đoán với quan sát; `Cleanup` chứng minh không còn thay đổi ngoài dự kiến; `Kết luận` nêu cơ chế và giới hạn. `PASS` chỉ dùng khi có output thật và hậu kiểm đạt. `BLOCKED` nghĩa là chưa thể chạy an toàn; `NOT_APPLICABLE` nghĩa là schema không có cơ chế tương ứng.

## P0 — Audit chỉ đọc

Từ root repo, xác nhận `docker compose ps` cho hai service `oracle`, `redis` healthy và port chỉ bind `127.0.0.1`. Mở SQLcl/SQL*Plus/DBeaver bằng user `SHOP` và service `FREEPDB1`. Không đưa mật khẩu vào command line, transcript hay report. Lệnh sau dùng mật khẩu đã có trong môi trường **bên trong Oracle container**, truyền vào SQL*Plus qua stdin; shell trên host không in giá trị đó:

```bash
docker compose exec -T oracle bash -lc '{ printf "whenever sqlerror exit failure\nconnect shop/%s@//localhost:1521/FREEPDB1\n" "$SHOP_PASSWORD"; cat; printf "\nexit\n"; } | sqlplus -s -L /nolog' \
  < report/transaction/research/oracle/audit.sql \
  > report/transaction/research/evidence/p0/p0-oracle-audit.txt
```

Trong SQLcl/SQL*Plus tương tác, kết nối một lần rồi chạy nội dung `oracle/audit.sql`; DBeaver chạy các câu SQL trong một editor và kiểm tra Auto-commit trên giao diện. SQLcl/SQL*Plus dùng `SHOW AUTOCOMMIT`. Ghi mọi `SET CONSTRAINTS` đã dùng; giữ đúng kết nối đó cho các lệnh trong một transaction sau này. Chưa thực hiện setup khi P0 chưa đạt.

```bash
bash report/transaction/research/redis/audit.sh
```

`oracle/audit.sql` đọc user/PDB/version, Flyway, bảng/cột, PK/FK/UNIQUE/CHECK, `STATUS`/`VALIDATED`/`DEFERRABLE`/`DEFERRED`, index và số session SHOP. `redis/audit.sh` đọc version, persistence, module/index, số key theo prefix và số client; không in credential. Lưu output đã loại secret vào `evidence/` kèm thời điểm, client và image digest. Kiểm tra metadata live với `V1__schema.sql`, `V2__transaction_cart_version_and_checkout_hash.sql` và `db/DATABASE_DESIGN.md`; nếu khác, ghi `BLOCKED` hoặc `NOT_APPLICABLE` theo case thay vì sửa schema.

### Bản đồ phạm vi trước khi chạy

| Case | Đối tượng/cơ chế phải xác minh | Căn cứ repo |
|---|---|---|
| A1, A3, D1 | `ORDERS`, `ORDER_ITEMS`, `CARTS`, `CART_ITEMS`, `PRODUCT_INVENTORY`; cùng Oracle session cho mỗi transaction | V1/V2; Checkout `NORMAL` |
| A2, C1 | PK/FK/UNIQUE/CHECK và trạng thái immediate/deferred thực tế | V1/V2; `USER_CONSTRAINTS` |
| B1, B2, B3 | Inventory fixture, `READ COMMITTED`, `FOR UPDATE`, conditional `UPDATE`; hai session cố định | Checkout Repository và TransactionSemanticsIT |
| A4, A5, B4 | Key Cart/Order/Inventory mô phỏng; `MULTI/EXEC`, `DISCARD`, `WATCH` trên kết nối liên tục | RedisTransactionIT; Redis Transactions |
| B5, C2, D2 | Key quota/Cart/Order/Inventory trong `txresearch:<runId>:*`; Lua và validation | `db/DATABASE_DESIGN.md` §17–22; RedisTransactionIT |

Redis là mô hình logic riêng; không ghi Order thật và không đồng bộ ngược Oracle. `TransactionDbBenchmarkIT`, k6, `report/transaction/results/` chỉ dùng để đối chiếu ở P5.

### Điều kiện qua P0

- Xác nhận đây là Compose local của repo, không có người khác dùng DB hoặc fixture liên quan; ghi số session/client và bối cảnh, không chỉ dựa vào port loopback.
- Xác nhận `SHOP` ở đúng PDB, Flyway V1/V2 thành công, các cột/constraint cần cho từng case hiện diện và trạng thái kiểm tra của chúng; ghi mọi khác biệt.
- Xác nhận auto-commit của client và không có `SET CONSTRAINTS` bất ngờ; với constraint deferred, thời điểm lỗi có thể ở `COMMIT`.
- Ghi cấu hình Redis, index/prefix đang có; tuyệt đối không dùng lệnh ghi trong P0.

Nếu bất kỳ điều kiện nào chưa rõ, ghi `BLOCKED` và dừng trước P1. Kết thúc P0 liệt kê file đã tạo, lệnh đã chạy, output, khác biệt và blocker.

## P1 — Fixture Oracle

Chọn run ID mới gồm 1–12 chữ thường/số; không tái dùng ID của người khác. `oracle/run-sql.sh` truyền bind `:run_id` và toàn bộ file vào **một** phiên SQL*Plus; mật khẩu chỉ đọc trong container. Wrapper dùng được cho một script hoàn chỉnh, không dùng để tách các bước đang mở transaction.

```bash
bash report/transaction/research/oracle/run-sql.sh p1trial01 \
  report/transaction/research/oracle/setup.sql
```

`setup.sql` tạo user local bcrypt, category, ba Product/Inventory (giá 100, tồn 100, reserved 0), Cart với ba dòng số lượng 1. Product 1/2 là dòng mua thử; Product 3 để kiểm chứng giữ dòng không mua. Output `RUN_ID`, `USER_ID`, `CATEGORY_ID`, `CART_ID`, `PRODUCT_1/2/3_ID` là manifest phải giữ để reset/cleanup chính xác. Gọi setup lần nữa trước khi thay đổi fixture phải trả `RESULT=EXISTS` và cùng ID; nếu trạng thái đã thay đổi, setup báo lỗi và yêu cầu reset có kiểm soát.

## P1 — Fixture Redis

Dùng ba Product ID **từ manifest Oracle của cùng run**. Ví dụ dưới đây thuộc vòng thử P1 đã dọn; khi chạy lại, thay bằng ID mới do setup của bạn in ra:

```bash
bash report/transaction/research/redis/setup.sh p1trial01 12935 12936 12937
bash report/transaction/research/redis/snapshot.sh p1trial01 12935 12936 12937
```

Setup tạo đúng năm Hash: `cart`, ba `inventory:<productId>` và `quota` dưới `txresearch:<runId>:`. `order` chưa tồn tại. Các tên key thực tế in ra được lưu trong manifest để cleanup bằng key chính xác. Script từ chối run ID/ID sai, trạng thái thiếu một phần hoặc dữ liệu đã đổi; gọi lần hai trên trạng thái đầu kỳ phải trả `RESULT=EXISTS`.

### Reset và cleanup theo manifest

Lưu đúng sáu ID Oracle và danh sách key Redis in ra khi setup. Khi case tạo Order hay key mới, thêm **ID/key thực tế** vào manifest trước khi reset/cleanup. Chạy preview và đối chiếu output với manifest trước khi gọi cleanup; các script từ chối ID hoặc tag không khớp. Oracle `order_ids` là mảng JSON số, ví dụ `'[]'` khi chưa có Order, `'[12345]'` khi đã ghi nhận Order 12345. Không dùng điều kiện xóa theo `LIKE` hay wildcard.

```bash
bash report/transaction/research/oracle/run-sql.sh p1trial01 \
  report/transaction/research/oracle/cleanup-preview.sql \
  10526 10528 10518 12935 12936 12937 '[]'
bash report/transaction/research/redis/preview.sh p1trial01 \
  report/transaction/research/evidence/p1/trial1/p1trial01-redis-keys.txt
```

Sau khi xác nhận ID và số dòng/key ở preview, reset Oracle bằng `oracle/reset.sql` với cùng tham số; reset Redis bằng `redis/reset.sh <runId> <key_file> <p1> <p2> <p3>`. Cleanup cuối dùng `oracle/cleanup.sql` và `redis/cleanup.sh` với cùng manifest. Oracle kiểm tra quan hệ của từng ID, Order ID/code và số dòng trước khi tự `COMMIT`; lỗi sẽ rollback. Redis chỉ `DEL` key trong file, sau đó `SCAN` **chỉ để phát hiện** key còn sót (không xóa). Nếu cleanup một kho thành công còn kho kia lỗi, giữ manifest và chỉ tiếp tục phần chưa dọn; không chạy lại cleanup Oracle sau khi user đã bị xóa.

## P2–P3 — Chạy 14 case

Đọc [chỉ mục bằng chứng](evidence/INDEX.md) để xem mục tiêu, tiền điều kiện, lệnh, output gốc, expected/actual, cleanup và kết luận từng case. Mỗi lần chạy mới: làm P0, setup P1 với run ID mới, lưu sáu ID Oracle và danh sách key, chụp snapshot đầu kỳ. Từng case giả định Cart có ba dòng, tồn P1/P2/P3=100, quota=2 và chưa có Order. Reset theo manifest sau case có ghi; trước reset phải preview, không dùng ID/key từ run khác. Run `native01` trong evidence đã được cleanup cuối P4. B2/B4/C2 hiện dùng [evidence revision-20261109](evidence/revision-20261109/README.md); transcript cũ của ba case chỉ giữ lịch sử superseded.

| Case | Script native từ root repo | Quan sát chính |
|---|---|
| A1 | `oracle/run-sql.sh <run> oracle/experiments/A1-commit.sql <6 ID> '[]'`; snapshot; `A1-rollback.sql`; snapshot; reset với Order ID COMMIT | Order/Item/Cart/Inventory qua session độc lập |
| A2 | `oracle/run-sql.sh <run> oracle/experiments/A2.sql <6 ID> '[]'` | `ORA-00001`, `ORA-02291`, `ORA-02290` ngay statement nếu constraint immediate |
| A3 | `oracle/run-sql.sh <run> oracle/experiments/A3.sql <6 ID> '[]'` | statement lỗi, savepoint, full rollback trong một session |
| A4 | `redis/experiments/A4.sh exec <run> <user_id> <p1>`; snapshot/reset; `A4.sh discard ...` | `QUEUED`, `EXEC` theo thứ tự, `DISCARD` không ghi |
| A5 | `redis/experiments/A5.sh runtime/queue/lua <run> <p1>`; snapshot/reset giữa nhánh ghi | lỗi runtime giữ ghi trước đó; queue-time `EXECABORT` |
| B1–B3 | `python3 oracle/experiments/two-session.py B1/B2/B3 <p1> <p2>` | session cố định; B2 có hai nhánh đọc/quyết định; timeout/deadlock chỉ thuộc B3 |
| B4 | `python3 redis/experiments/B4.py <run> <p1>` | hai HINCRBY độc lập; WATCH/HGET/tính HSET trên connection A, conflict rồi đọc lại/retry |
| B5 | `python3 redis/experiments/B5.py <run>` | bốn redis-cli clients gọi cùng Lua quota |
| C1 | `oracle/run-sql.sh <run> oracle/experiments/C1.sql` | metadata 29 constraint, không ghi |
| C2 | `bash redis/experiments/C2.sh <run> <p1> <p2> <p3>` | 14 case lỗi snapshot không đổi, một Checkout guarded EVAL hợp lệ; reset giữa case |
| D1 | `oracle/run-sql.sh <run> oracle/experiments/D1.sql <6 ID> '[]'` | ba mốc Order/Items/Cart/Inventory; lỗi CHECK rồi full rollback |
| D2 | `redis/experiments/D2.sh invalid/valid <run> <user_id> <p1> <p2>` | Lua tiền kiểm năm key, nhánh lỗi không ghi |

Trong bảng, mọi đường dẫn script nằm dưới `report/transaction/research/`; thay `<6 ID>` bằng `user category cart p1 p2 p3` đúng manifest. Với script Oracle, `run-sql.sh` giữ một kết nối SQL*Plus cho toàn file; không gọi wrapper riêng cho từng statement của một transaction. Các script `.py` chỉ điều phối **SQL*Plus/redis-cli**, ghi lệnh và mốc thời gian, không mô phỏng DBMS bằng Python. Có thể chạy cùng SQL trong hai cửa sổ SQLcl/SQL*Plus/DBeaver và cùng Redis CLI tương tác theo timeline dưới đây.

### Hai Oracle session thủ công cho B1–B3

Mở hai cửa sổ A/B riêng trên `SHOP/FREEPDB1`, tắt autocommit; mỗi cửa sổ khai báo `VARIABLE p1 NUMBER`, `VARIABLE p2 NUMBER` và gán ID từ manifest bằng `EXEC :p1 := <id>;`, `EXEC :p2 := <id>;`. Giữ nguyên kết nối cho đến `COMMIT`/`ROLLBACK`. Chỉ thao tác trên hai Inventory fixture; mọi `WHERE product_id` dùng bind.

- **B1:** A/B chạy `SET TRANSACTION ISOLATION LEVEL READ COMMITTED;`. B `SELECT on_hand_quantity FROM product_inventory WHERE product_id=:p1;` thấy 100. A `UPDATE product_inventory SET on_hand_quantity=99 WHERE product_id=:p1;` chưa commit. B SELECT lại thấy 100. A `COMMIT;`; B SELECT lại thấy 99. B `ROLLBACK;`; A khôi phục 100 rồi `COMMIT;`.
- **B2:** Quy tắc ưu đãi: đọc Stock=100 mới đủ điều kiện. Cả hai session dùng READ COMMITTED và UPDATE tương đối `SET on_hand_quantity=on_hand_quantity-1 WHERE product_id=:p1`. Nhánh thường: A SELECT=100/quyết định, A UPDATE chưa commit; B SELECT=100/quyết định, B UPDATE chờ; A COMMIT, B UPDATE hoàn tất/COMMIT; tồn=98, hai ưu đãi. Reset 100. Nhánh FOR UPDATE: A SELECT FOR UPDATE=100/quyết định; B SELECT FOR UPDATE WAIT 30 chờ; A UPDATE/COMMIT; B lấy khóa đọc 99/quyết định không ưu đãi rồi UPDATE/COMMIT; tồn=98, một ưu đãi. Kiểm tra B chưa hoàn tất trước A COMMIT; không tạo timeout Oracle cố ý. Sau mỗi nhánh khôi phục 100, cuối cùng lấy lại khóa từ B/ROLLBACK. UPDATE thường cũng tự lấy row lock. Harness B2 có assertion read/decision/stock, lỗi SQL làm fail.
- **B3 lượt timeout:** A `SELECT product_id FROM product_inventory WHERE product_id=:p1 FOR UPDATE WAIT 3;`; B cùng câu `WAIT 2`, ghi lỗi thực, B `ROLLBACK;`, A `ROLLBACK;`. **Lượt deadlock:** A khóa P1, B khóa P2 bằng SELECT FOR UPDATE; A gửi SELECT P2 `FOR UPDATE WAIT 10` và để chờ; B gửi SELECT P1 `FOR UPDATE WAIT 10`. Ghi `ORA-00060` ở session nạn nhân; chủ động `ROLLBACK;` session đó để giải phóng khóa đầu tiên, chờ session còn lại kết thúc SELECT rồi `ROLLBACK;`. Dùng giới hạn 13 giây cho lượt chờ; nếu không trả prompt, ngắt client quá hạn để kết nối đóng và Oracle rollback, kiểm tra lại bằng `FOR UPDATE WAIT 2` trên cả P1/P2 trước khi reset. Transcript mẫu và mã lỗi Oracle 26ai ở [B3](evidence/p3/B3/README.md).

### Redis CLI tương tác

Với A4/A5, nhập cả `MULTI`, các lệnh ghi và `EXEC`/`DISCARD` trong **cùng cửa sổ redis-cli**. Với B4, client A giữ một cửa sổ xuyên suốt `WATCH key` → `HGET` → `MULTI` → lệnh xếp hàng → `EXEC`; client B sửa key ở cửa sổ khác trước `EXEC`. Sau nil, A đọc lại và retry tối đa một lần; không tái dùng giá trị cũ. B5/C2/D2 dùng `EVAL` với source Lua và key/argument như script; chỉ dùng namespace `txresearch:<runId>:`. Bản gốc lệnh đầy đủ nằm trong script và transcript, không cần Spring Boot.

### Cleanup cuối

Chỉ cleanup sau khi toàn bộ case hoàn tất và snapshot đã về baseline. Đọc `cleanup-preview.sql` và `redis/preview.sh`; đối chiếu số row/key với manifest. Chạy `oracle/cleanup.sql` với sáu ID và JSON Order ID hiện tồn tại, sau đó `oracle/verify-absent.sql`. Chạy `redis/cleanup.sh` với **file liệt kê key chính xác**, rồi `redis/preview.sh`; mọi key phải `none`, `ALL_RUN_KEYS` rỗng. Bằng chứng cleanup run `native01` nằm trong [manifest](evidence/p1/native01/native01-manifest.md). Nếu một bên lỗi, giữ manifest để chỉ xử lý phần còn lại; không xóa theo wildcard và không tái dùng run ID của người khác.

## Phân định kết quả

14 case trên chứng minh cơ chế **DBMS-native** bằng SQL/PLSQL và Redis CLI/Lua, không cung cấp latency/TPS có ý nghĩa thống kê. Java `TransactionDbBenchmarkIT`/raw results đo hiệu năng database-only của runner riêng; JUnit còn kiểm tra ứng dụng, k6 đo HTTP. [Báo cáo Transaction](../SECTION_TRANSACTION.md) và [đánh giá P5](evidence/p5/README.md) giữ ba lớp này tách biệt.

## Chạy lại riêng B2/B4/C2

Sau audit P0 và xác nhận Compose local/dev, chọn run ID mới. Lệnh từ root repo:

```bash
python3 report/transaction/research/run-revisions.py <run_id> <evidence_directory> --repeat 5
```

Runner reuse setup, reset, snapshot, preview và cleanup hiện có. Manifest ghi ID Oracle và bảy key Redis cụ thể (thêm `product:<p1>` của C2 và Order); không xóa theo wildcard. C2 tạo Product Hash ACTIVE từ ID fixture, không thay Product thật Oracle. Direct mutation chỉ dùng chuẩn bị fixture lỗi, được đánh dấu `FIXTURE_INJECTION`; mọi request Checkout trong C2 gọi một EVAL validation-trước-ghi. Lua giới hạn số nguyên canonical tới 1e9, yêu cầu key không TTL. Không bao gồm giá/tổng tiền/quota trong Checkout NORMAL một mặt hàng này; quota và mặt hàng khác phải giữ nguyên.

Evidence giữ input/output và snapshot mỗi case, năm lượt correctness, hậu kiểm Oracle bằng connection khác và Redis sau reset. Nếu lỗi, đọc transcript/manifest và kết quả finally cleanup trước khi thử run mới. Không xem exit code 0 là đủ: runner kiểm tra các assertion, snapshot equality và cleanup thực tế. `assertions.json` chỉ tổng hợp các assertion đã đạt; cần đối chiếu thêm oracle-verify/redis-verify. Đây không phải benchmark latency/TPS.
