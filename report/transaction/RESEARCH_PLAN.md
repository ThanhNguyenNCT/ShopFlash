# KẾ HOẠCH NGHIÊN CỨU TRANSACTION ORACLE–REDIS — SHOPFLASH

> **Trạng thái:** Tài liệu định hướng và quyết định thiết kế (chưa phải báo cáo kết quả thực nghiệm).  
> **Phạm vi:** Topic Transaction của bài tập lớn DBMS ShopFlash.  
> **Nguồn quyết định:** Tài liệu này chốt phạm vi, 14 thí nghiệm và phương pháp nghiên cứu. OpenSpec change `transaction-dbms-native-research` chỉ quản lý phân rã và triển khai các quyết định đó, không tái thiết kế nghiên cứu.  
> **Ngôn ngữ:** Tài liệu, hướng dẫn thao tác, báo cáo và phần giải thích sử dụng tiếng Việt. Giữ nguyên tên kỹ thuật, SQL, Redis commands và error codes.

## 1. Bối cảnh và mục tiêu

ShopFlash có các luồng nghiệp vụ Authentication, Cart, Checkout, Order và Flash Sale, sử dụng **Oracle làm nguồn dữ liệu nghiệp vụ chính (source of truth)**; Redis phục vụ các workload như tìm kiếm, runtime/quota Flash Sale và read model. Module Transaction đã có Java/JUnit integration tests, Java DB benchmark runner, k6 HTTP benchmark và kết quả/báo cáo ban đầu.

**Vấn đề cần khắc phục:** Phần *nghiên cứu cơ chế DBMS* hiện phụ thuộc nhiều vào Java test. Cần xây dựng bộ thực nghiệm độc lập với Spring Boot để người học có thể **chạy, quan sát và giải thích trực tiếp hành vi của Oracle và Redis** bằng công cụ gốc của từng DBMS.

**Quyết định:** Sử dụng **database/schema ShopFlash hiện có** và đúng mô hình dữ liệu/nghiệp vụ của project, nhưng chỉ tác động lên **fixture riêng của nghiên cứu**, không đụng đến dữ liệu thật của nhóm. SQL/PLSQL và Redis CLI/Lua là phương tiện chứng minh chính; Java/JUnit và k6 giữ vai trò kiểm thử ứng dụng và baseline hiệu năng.

Bốn câu hỏi nghiên cứu:

1. **Atomicity và xử lý lỗi:** Một giao dịch nhiều bước thất bại thì thay đổi nào được giữ, thay đổi nào được hoàn tác?
2. **Isolation và concurrency:** Hai session/client tác động đồng thời lên Cart/Inventory/quota được kiểm soát thế nào?
3. **Consistency:** DBMS tự đảm bảo những ràng buộc nào, và giới hạn nào phải do nghiệp vụ kiểm soát?
4. **Performance (giai đoạn sau):** Các lựa chọn transaction/concurrency ảnh hưởng đến latency, throughput và tính đúng đắn ra sao?

## 2. Phạm vi và nguyên tắc thiết kế

### 2.1. Trong phạm vi

- Thực nghiệm Oracle trên **bảng thực tế** như `USERS`, `CARTS`, `CART_ITEMS`, `PRODUCTS`, `PRODUCT_INVENTORY`, `ORDERS`, `ORDER_ITEMS`, sau khi kiểm tra lại DDL/migrations thực tế.
- Thực nghiệm Redis trên **Redis instance hiện có**, với kiểu dữ liệu, quy ước key và logic tương ứng nghiệp vụ ShopFlash; các key thực nghiệm phải nằm trong namespace riêng.
- Chạy bằng **SQLcl/SQL*Plus/DBeaver** và **redis-cli/Lua**. Các bước Oracle của cùng transaction phải ở cùng session/kết nối đến `COMMIT` hoặc `ROLLBACK`; các chuỗi Redis `MULTI` → `EXEC`/`DISCARD` và `WATCH` → đọc → `MULTI` → `EXEC` phải ở cùng kết nối liên tục. Thí nghiệm hai session/client phải có kịch bản điều phối rõ ràng, thực hiện được thủ công.
- Ghi bằng chứng đầu vào, lệnh đã chạy, thông báo DBMS, trạng thái trước/sau, tiêu chí PASS/FAIL và diễn giải.
- Đối chiếu với benchmark hiện tại; chỉ đề xuất sửa công cụ đo nếu phát hiện vấn đề cụ thể.

### 2.2. Ngoài phạm vi

- Không tái cấu trúc F0/F10/F7/F8/F9; không viết lại Spring Boot hoặc frontend để phục vụ nghiên cứu.
- Không thay thế hoặc xóa JUnit, Java DB benchmark, k6 hay dữ liệu thô đã có.
- Không coi việc chạy thủ công vài lần là benchmark latency/throughput có ý nghĩa thống kê.
- Không tạo schema nghiệp vụ mới thay thế ShopFlash; không tự sửa migrations/constraints để khiến test PASS.
- Không ép Oracle và Redis phải có cùng đảm bảo ACID hoặc cùng cách lưu dữ liệu.

### 2.3. Phân định vai trò

| Nội dung | Quyền quyết định |
|---|---|
| Câu hỏi nghiên cứu, bộ thí nghiệm, kỳ vọng và tiêu chí chứng minh | **Tài liệu này** |
| Khảo sát tên bảng/cột/constraint/key thực tế, công cụ tương thích, đường dẫn script, chi tiết setup | **Agent được tự quyết**, dựa trên repository và DB hiện tại |
| Tổ chức checkpoint, thứ tự lệnh an toàn, tái lập evidence | **Agent được tự quyết** |
| Thay đổi schema, dữ liệu dùng chung, phạm vi nghiên cứu, nghiệp vụ chính hoặc phương pháp so sánh | **Không tự ý thực hiện**; phải báo cáo vướng mắc |
| Số liệu và kết luận | Chỉ từ **kết quả chạy thật**, không suy diễn từ kỳ vọng |

## 3. Workload trung tâm: ShopFlash Checkout NORMAL

Dùng một nghiệp vụ thống nhất để giải thích nhiều cơ chế:

1. Khách có Cart và Cart Items.
2. Kiểm tra sản phẩm còn `ACTIVE`, giá hiện tại và tồn kho có thể bán thường.
3. Tạo Order và Order Items.
4. Giảm `PRODUCT_INVENTORY.on_hand_quantity`, tôn trọng `reserved_flash_quantity`.
5. Xóa **chỉ** các Cart Items đã mua; giữ những dòng không mua.
6. Xác nhận giao dịch hoặc hoàn tác khi thất bại.

**Oracle:** chạy trực tiếp DML trên bảng ShopFlash, sử dụng constraints và khóa có sẵn.  
**Redis:** tạo cấu trúc key/Hash/JSON tương đương *về thao tác logic* trong namespace nghiên cứu; sử dụng `MULTI/EXEC`, `WATCH`, Lua. Redis không phải nguồn dữ liệu chính của Checkout; các bản ghi Redis này **không đồng bộ ngược** với Oracle và không tạo đơn hàng thật trên production flow.

Với `WATCH` và quota Lua, có thể tham chiếu logic Flash Sale/quota đã thiết kế trong dự án, nhưng **không gọi hoặc thay đổi các key runtime thật của TV5**.

## 4. Ma trận 14 thí nghiệm bắt buộc

### Nhóm A — Atomicity, Rollback, Error Handling (5 case)

| ID | DBMS | Kịch bản | Điều cần quan sát / chứng minh |
|---|---|---|---|
| **A1** | Oracle | Giao dịch tạo Order/Order Items, COMMIT và ROLLBACK trên fixture | COMMIT làm dữ liệu có thể đọc được từ session độc lập; ROLLBACK hoàn tác mọi ghi chưa commit |
| **A2** | Oracle | Kích hoạt lỗi `UNIQUE`, `FK`, `CHECK` bằng DML trên bảng ShopFlash | DBMS tự phát hiện vi phạm (`ORA-00001`, `ORA-02291`, `ORA-02290` khi constraint tương ứng thực sự tồn tại) |
| **A3** | Oracle | Statement-level rollback, `SAVEPOINT`, `ROLLBACK TO`, full `ROLLBACK` | SQL statement bị lỗi không đồng nghĩa toàn bộ transaction tự rollback; phân biệt các phạm vi hoàn tác |
| **A4** | Redis | `MULTI/EXEC`, `DISCARD` với key mô phỏng Cart/Order/Inventory | Lệnh được xếp hàng và chạy theo thứ tự khi `EXEC`; `DISCARD` chỉ hủy hàng đợi chưa chạy |
| **A5** | Redis | Lỗi runtime trong `EXEC` và script Lua lỗi sau khi ghi | Các ghi đã thực thi thành công **không tự được rollback** do lỗi runtime ở bước sau |

**Lưu ý A2–A3:** Phải phân biệt SQL error code, statement-level rollback và việc ứng dụng/session **chủ động** gọi `ROLLBACK` cho toàn bộ transaction. Không mô tả mọi lỗi Oracle là tự rollback cả transaction. Đối với `CHECK` chưa tồn tại trong schema thật, ghi `NOT APPLICABLE` kèm bằng chứng khảo sát; không tự thêm constraint.

### Nhóm B — Isolation, Locking, Concurrency (5 case)

| ID | DBMS | Kịch bản | Điều cần quan sát / chứng minh |
|---|---|---|---|
| **B1** | Oracle | Hai SQL sessions, `READ COMMITTED`, đọc cùng Inventory trước/sau phiên khác COMMIT | Không có dirty read; hai câu SELECT có thể thấy phiên bản dữ liệu khác nhau khi giao dịch khác đã COMMIT |
| **B2** | Oracle | Hai session cùng mua một Inventory; `SELECT ... FOR UPDATE` và conditional UPDATE | Session thứ hai phải chờ/bị từ chối theo cấu hình; không bán quá tồn khả dụng; không mất cập nhật |
| **B3** | Oracle | Lock wait/timeout và deadlock có kiểm soát trên fixture | Oracle tự phát hiện và trả mã lỗi; phân biệt rollback của câu lệnh bị lỗi với rollback cả transaction do session thực hiện |
| **B4** | Redis | Hai clients tranh cùng key qua `WATCH` → `MULTI` → `EXEC` | Khi key thay đổi trước `EXEC`, transaction được theo dõi bị từ chối; client có thể retry có kiểm soát |
| **B5** | Redis | Lua kiểm tra quota rồi giữ quota trong một lần thực thi | Không bị thao tác khác chen ngang vào kiểm tra–ghi; quota không âm/không vượt giới hạn với logic đúng |

**B2** phải có đối chứng *mô hình rủi ro* khi đọc–sửa–ghi mà không bảo vệ đúng cách, nhưng chỉ dùng fixture. Không tạo lỗi trên dữ liệu thật. **B3** phải có hướng dẫn `Session A`/`Session B`: trên hai dòng fixture, A khóa dòng 1, B khóa dòng 2, A yêu cầu dòng 2 rồi B yêu cầu dòng 1 để tạo vòng chờ; tách lượt kiểm tra timeout khỏi lượt deadlock. Đặt timeout hữu hạn cho thao tác chờ, ghi nhận lỗi thật, sau đó chủ động `ROLLBACK` cả hai session để nhả mọi khóa còn giữ; nếu một session treo, dùng bước giải cứu có giới hạn rồi hậu kiểm trước cleanup.

### Nhóm C — Consistency, Database Constraints (2 case)

| ID | DBMS | Kịch bản | Điều cần quan sát / chứng minh |
|---|---|---|---|
| **C1** | Oracle | Kiểm tra constraints thực tế của quan hệ Order–Order Item–Product/Inventory và quy tắc nghiệp vụ | Phân định rõ điều DBMS tự bảo đảm bằng PK/FK/UNIQUE/CHECK với điều cần validation/conditional UPDATE |
| **C2** | Redis | Ghi key có tham chiếu giả/giá trị không hợp lệ bằng CLI, sau đó dùng Lua kiểm tra | Redis không tự cung cấp FK/CHECK quan hệ như Oracle; tính nhất quán nghiệp vụ phải được thiết kế trong mã lệnh/script |

**Phân biệt A2 và C1:** A2 là **chạy lỗi DBMS và ghi evidence**; C1 là **đọc constraint thực tế và lập ma trận phạm vi bảo vệ/giới hạn**, không lặp lại nguyên bộ A2.

### Nhóm D — Multi-step Transaction và Recovery (2 case)

| ID | DBMS | Kịch bản | Điều cần quan sát / chứng minh |
|---|---|---|---|
| **D1** | Oracle | Checkout nhiều mặt hàng với DML trực tiếp; cố ý gây **lỗi DBMS** sau khi đã ghi một phần | Full `ROLLBACK` phục hồi toàn bộ Order, Order Items, Cart và Inventory; khác biệt với auto statement rollback |
| **D2** | Redis | Cùng ý tưởng kiểm tra hàng/quota và cập nhật nhiều key bằng Lua, kiểm tra điều kiện trước khi ghi | Lua có thể bảo vệ invariant khi được thiết kế đúng; không đồng nhất với transaction rollback của Oracle |

**D1:** Không dùng Java Probe làm bằng chứng chính. Cố ý tạo lỗi SQL có thể tái lập, sau đó quan sát trạng thái trước và sau khi gọi `ROLLBACK`. **D2:** Không trình bày Lua thay thế FK, durability hoặc transaction đa-DBMS.

## 5. Quy tắc an toàn khi làm trực tiếp trên DB ShopFlash

> **Bắt buộc tuân thủ:** Tài liệu này cho phép chạy trên **môi trường phát triển/local của ShopFlash đã được xác minh**; không được tùy tiện tác động vào shared/prod database hoặc tài nguyên đang có người sử dụng.

1. **Khảo sát chỉ đọc trước:** Xác định connection/schema/user, DB hiện tại, migrations, PK/FK/UNIQUE/CHECK, trạng thái `ENABLED`/`DISABLED`, `VALIDATED`/`NOT VALIDATED`, tính `DEFERRABLE` và chế độ `IMMEDIATE`/`DEFERRED` ban đầu của Oracle constraints; kiểm tra cấu hình session có thay đổi thời điểm kiểm tra constraint hay không. Ghi cơ chế auto-commit, phiên bản Redis, persistence, key prefix và các script đã có. Chưa đủ thông tin thì **không chạy thao tác ghi**.
2. **Fixture cách ly trong cùng schema:** Tạo user, product, inventory, cart, order bằng các khóa định danh dễ truy vết (`TXRESEARCH_<runId>`, SKU/email/order code tương đương) và lưu manifest ID thực tế. Mỗi thí nghiệm có run ID riêng hoặc reset fixture xác định.
3. **Không làm hỏng dữ liệu nhóm:** Không sửa/hủy rows của TV2/TV5, không reset sequence, không `TRUNCATE` bảng nghiệp vụ, không drop index/constraint, không đổi global database setting.
4. **Redis:** Chỉ dùng key trong `txresearch:<runId>:*`; tuyệt đối không `FLUSHDB`/`FLUSHALL` hoặc ghi vào `flash:*`, index search, analytics key. Cleanup theo **danh sách key đã tạo**, không xóa toàn bộ key cùng prefix chung của nhiều lượt chạy.
5. **Oracle cleanup:** Xóa fixture đúng thứ tự quan hệ FK, theo ID manifest chính xác, trong transaction riêng; phải xác nhận phạm vi DELETE trước khi commit. Không dọn dữ liệu bằng điều kiện LIKE rộng có thể trúng rows của nhóm.
6. **DDL và transaction:** Setup/migration nếu cần phải tách khỏi test transactional DML; DDL Oracle có thể gây implicit COMMIT. Không tạo/xóa bảng ngay giữa ca thử ROLLBACK.
7. **Session và kết nối:** Ghi rõ Session A/B, lúc đọc, lúc lấy khóa, lúc chờ, lúc commit/rollback. Không tách các lệnh của một Oracle transaction sang kết nối khác; với Redis, giữ một kết nối client xuyên suốt mỗi chuỗi `MULTI`/`WATCH` đến `EXEC` hoặc `DISCARD`. Đặt timeout hữu hạn; sau lượt deadlock/timeout chủ động `ROLLBACK` các session còn mở, giải phóng khóa và khôi phục fixture.
8. **Trạng thái sau test:** Ghi snapshot kiểm tra dữ liệu trước/sau; không kết luận PASS nếu tồn kho, Order, Cart hoặc key vẫn còn ở trạng thái sai.
9. **Secrets/Git:** Không đưa `.env`, mật khẩu, token, connection string có credential, dữ liệu người dùng hoặc bản dump nhạy cảm vào report/commit.
10. **Bất thường:** Nếu phát hiện DB đang được dùng chung hoặc cleanup không thể đảm bảo an toàn, ngừng thao tác ghi và ghi blocker; không tự chuyển sang database khác hoặc tạo schema thay thế để lách mục tiêu.

## 6. Chuẩn thực thi và ghi evidence

Mỗi case (A1–D2) phải có nội dung tối thiểu:

| Thành phần | Yêu cầu |
|---|---|
| Mục tiêu | Cơ chế DBMS đang nghiên cứu và giả thuyết/kỳ vọng |
| Tiền điều kiện | Schema/keys thật đã xác minh; fixture và version có liên quan |
| Setup | Lệnh chuẩn bị dữ liệu cách ly, có thể chạy lại |
| Execute | **Lệnh SQL/PLSQL hoặc Redis CLI/Lua đầy đủ**, theo thứ tự và session |
| Observe | Output nguyên gốc, mã lỗi, ảnh chụp trạng thái/log khi hữu ích |
| Verify | Trạng thái trước/sau; so sánh `expected` và `actual`, có assertion hoặc checklist rõ ràng |
| Cleanup | Lệnh dọn **chỉ fixture** và bằng chứng đã dọn xong |
| Kết luận | Giải thích điều DBMS làm, ứng dụng cần làm gì thêm, giới hạn của phép thử |

Quy ước báo cáo **PASS / FAIL / BLOCKED / NOT_APPLICABLE**; `PASS` chỉ khi case đã chạy trên DB thực tế và có bằng chứng. Không tự tạo số đo hoặc output cho đẹp báo cáo.

Với Redis, phân biệt lỗi **queue-time** và **runtime tại EXEC**; với Oracle, phân biệt statement-level rollback, savepoint rollback và full transaction rollback. Nếu constraint có thể deferred, ghi rõ thời điểm kiểm tra thực tế thay vì mặc định lỗi phát sinh ngay ở statement. Các câu lệnh demo phải được viết đủ để người đọc chạy thủ công trong đúng session/kết nối, không bắt người đọc hiểu JUnit nội bộ.

## 7. Tổ chức file đề xuất

Agent có thể điều chỉnh tên script và nhóm thư mục cho phù hợp repository, nhưng giữ logic tách biệt nghiên cứu/công cụ đo:

```text
report/transaction/
├── RESEARCH_PLAN.md             # Nguồn quyết định, nằm ngoài research/
├── research/
│   ├── README.md                 # Bắt đầu từ đây; runbook tất cả case
│   ├── oracle/
│   │   ├── setup.sql
│   │   ├── experiments/          # A1 A2 A3 B1 B2 B3 C1 D1
│   │   └── cleanup.sql
│   ├── redis/
│   │   ├── setup.sh              # Chỉ dùng nếu cần; không bắt buộc shell
│   │   ├── experiments/          # A4 A5 B4 B5 C2 D2
│   │   ├── lua/
│   │   └── cleanup.sh
│   └── evidence/
│       ├── INDEX.md             # Chỉ mục 14 case và đường dẫn output
│       ├── p0/ ... p1/          # Audit, manifest và thử fixture
│       ├── p2/<case>/           # A1–A5/C1–C2: hồ sơ và transcript cùng chỗ
│       ├── p3/<case>/           # B1–B5/D1–D2: hồ sơ và timeline cùng chỗ
│       └── p4/ ... p5/          # Cleanup cuối và rà soát benchmark cũ
├── results/                     # Baseline benchmark cũ, giữ nguyên
├── SECTION_TRANSACTION.md       # Báo cáo hiện tại; cập nhật sau khi có chứng cứ
└── README.md
```

- Ưu tiên **ít script nhưng rõ mục đích**, không nhân bản các bước setup/cleanup ở từng nơi.
- README cần có ví dụ chạy SQLcl/DBeaver và redis-cli, hướng dẫn Session A/B và chú thích những thao tác đòi hỏi tương tác người dùng.
- Với chứng cứ không thể chạy tự động (chờ khóa, deadlock), cung cấp transcript có dấu thời gian hoặc các bước tái lập; không ghi PASS chỉ vì dự đoán Oracle/Redis sẽ hoạt động như vậy.
- Nếu cần helper shell/Python để thu log và đối chiếu, được phép dùng; **không thay thế SQL/CLI/Lua bằng Java cho bằng chứng nghiên cứu chính**.

## 8. Quan hệ với benchmark Milestone 5 hiện có

Giữ nguyên Java `TransactionDbBenchmarkIT`, `TransactionSemanticsIT`, `RedisTransactionIT`, k6, raw CSV/JSON, evidence và các tài liệu đã có làm **baseline**, không xóa hoặc tự sửa số liệu.

Khi hoàn thành nghiên cứu cơ chế, đánh giá lại **tính tương đương workload** của runner Oracle–Redis (payload, số thao tác, tính đúng đắn, network/client overhead, commit/durability, khóa và mức độ cô lập). Phân biệt:

- **DBMS correctness (native):** bằng chứng chính là SQL/PLSQL và Redis CLI/Lua.
- **Database performance:** đo lặp tự động, cùng logical workload; baseline hiện tại warm-up 50, 5 lượt × 200 mẫu, giỏ 1/5 sản phẩm, concurrency 1/10 (chỉ giữ nếu đúng cấu hình thực tế sau kiểm tra).
- **Application correctness/performance:** JUnit + k6 trên luồng HTTP Cart–Checkout–Order, **không đem so trực tiếp với latency database-only**.

Chỉ đo/sửa lại benchmark nếu có lý do cụ thể và xác minh tác động; không mở rộng thành dự án benchmarking mới trong khi native research chưa hoàn thành.

## 9. Giai đoạn triển khai / checkpoints

| Checkpoint | Việc làm | Điều kiện hoàn thành |
|---|---|---|
| **P0 — Audit** | Đọc repo/migration/Redis model/report cũ, khảo sát kết nối và constraints bằng lệnh **chỉ đọc** | Có bản đồ bảng/constraint/keys, các khác biệt thực tế và rủi ro trước khi ghi |
| **P1 — Fixture & safety** | Setup/cleanup an toàn, manifest run ID, hướng dẫn kết nối, thử tạo rồi xóa fixture | Không thay đổi rows/keys ngoài fixture; tự chạy lại được |
| **P2 — Semantics** | A1–A5 và C1–C2 | SQL/CLI/Lua chạy thật, có mã lỗi/output và giải thích |
| **P3 — Concurrency & multi-step** | B1–B5 và D1–D2 | Có điều phối hai sessions, hậu kiểm và cleanup; không treo khóa |
| **P4 — Report** | Chuẩn hóa evidence, đối chiếu Oracle–Redis, bổ sung báo cáo | Toàn bộ 14 case có trạng thái thực và dẫn chứng; nêu giới hạn |
| **P5 — Benchmark review** | Đọc runner/k6 cũ, rà soát workload và số đo | Có đánh giá có căn cứ, **chưa buộc phải viết lại benchmark** |

Agent được tự chia thành các task nhỏ trong mỗi checkpoint. OpenSpec change `transaction-dbms-native-research` quản lý các task và tiến độ triển khai; khi có khác biệt, đối chiếu với `report/transaction/RESEARCH_PLAN.md` làm nguồn quyết định phạm vi và phương pháp, không tự thay đổi 14 thí nghiệm. Kết thúc mỗi checkpoint cần cung cấp danh sách file, lệnh đã chạy, bằng chứng, vấn đề còn lại; không commit/push nếu chưa được yêu cầu trong session mới.

## 10. Tiêu chí nghiệm thu cuối

Bộ nghiên cứu được coi là hoàn thiện khi:

- Đã khảo sát **đúng database ShopFlash hiện hữu** và ghi rõ cấu hình thí nghiệm không chứa secrets.
- Có thể chạy thủ công các case bằng Oracle SQL/PLSQL và Redis CLI/Lua, **không cần Spring Boot**.
- 14 case có mục tiêu, lệnh chạy, trạng thái thực tế, expected/actual, explanation và cleanup; case không phù hợp schema được ghi rõ `NOT_APPLICABLE`, không dựng lỗi giả.
- Ít nhất một case trực tiếp chứng minh **Oracle tự phát sinh lỗi constraint**, và một case chứng minh **khác biệt statement rollback với full transaction rollback**.
- Có bằng chứng Oracle xử lý lock/timeout/deadlock và Redis xử lý `WATCH`/Lua; không giả định mọi DBMS error tự rollback toàn giao dịch.
- Mọi fixture đã được dọn hoặc để lại có chủ đích kèm manifest rõ ràng, **không ảnh hưởng dữ liệu nhóm**.
- Tách rõ kết luận **DBMS semantics**, **database benchmark** và **application benchmark**; số liệu cũ không bị sửa/thay thế nếu chưa chạy lại.
- Báo cáo giải thích được vì sao Oracle hợp với Checkout/Order/Inventory, Redis hợp với runtime quota/read workload, và **những trade-off** của mỗi cơ chế.

---

**Nguồn quyết định:** Tài liệu này là định hướng nghiên cứu đã thống nhất. Tên file, lệnh cụ thể và fixture phải đối chiếu với repository/database hiện tại; nếu có mâu thuẫn giữa kế hoạch và schema thật, agent báo cáo khác biệt, không tự lặng lẽ thay đổi mục tiêu.
