# DEMO RUNBOOK — Transaction trên Oracle và Redis

Tài liệu cho người trực tiếp quay video demo (~2 phút 30 giây). Mọi lệnh chạy từ thư mục:

```bash
cd report/transaction/research/demo
```

Mọi giá trị trên màn hình đều đọc từ DBMS thật. Cột **EXPECTED** trong tài liệu này là kết quả nghiên cứu đã ghi trong evidence, dùng để đối chiếu khi quay; kit không bao giờ in giá trị đó như thể là dữ liệu. ID của P1/P2/P3 thay đổi sau mỗi lần cleanup, nhưng không cần nhập: kit tự đọc ID từ manifest.

---

## A. Quick Start

### A1. Chuẩn bị môi trường (một lần, trước buổi quay)

```bash
./demo.sh init
```

Lệnh này:

1. Sinh mật khẩu ngẫu nhiên vào `runtime/demo.env`.
2. Khởi động project Docker **`shopflashdemo`** (Oracle `127.0.0.1:1522`, Redis `127.0.0.1:6381`). Container, network và volume đều riêng.
3. Chạy Flyway qua backend chỉ trỏ vào DB demo, rồi tắt backend.
4. Tạo fixture: P1, P2, P3 tồn 100, reserved 0, version 0; Cart 3 dòng, mỗi dòng 1; chưa có Order; quota Redis 2.

Môi trường ShopFlash chính (Oracle `:1521`, Redis `:6380`) vẫn chạy song song, kit không dừng hay ghi vào nó. Nếu máy thiếu RAM, xem mục D5.

### A2. Kiểm tra đúng database trước khi thao tác

```bash
./demo.sh status
```

Phải thấy `Target đã xác minh: project shopflashdemo`, hai container `shopflashdemo-*` ở trạng thái healthy, port 1522/6381, Flyway V1–V5, fixture ở baseline. Dòng `Oracle demo SERVER_HOST = <mã>` là tên máy của container Oracle demo. Trong DBeaver, cột `SERVER_HOST` ở STEP 0/1 phải ra đúng mã này. Nếu ra mã khác, editor đang nối nhầm DB.

### A3. Kết nối DBeaver với Oracle demo

```bash
./demo.sh dbeaver
```

1. **Database → New Database Connection → Oracle**.
   - Host `localhost`, Port `1522`, Database `FREEPDB1`, chọn **Service Name**.
   - Authentication: Oracle Database Native, User `SHOP`.
   - Password: dòng `ORACLE_APP_PASSWORD` trong `runtime/demo.env`. Mở file bằng editor rồi copy, **không** gõ mật khẩu vào terminal đang quay.
2. Đặt tên **`ShopFlash DEMO :1522`** để không nhầm với kết nối ShopFlash chính.
3. **Connection settings → Initialization**: bỏ chọn **Auto-commit**. Trên thanh công cụ, chế độ commit phải hiển thị **Manual Commit**. Tắt *Smart commit* nếu đang bật.
4. Giữ tùy chọn mặc định *Open separate connection for each editor*: mỗi SQL Editor là một session riêng. STEP 0/1 cho thấy SID khác nhau.
5. Nếu bản DBeaver có tùy chọn tự đóng transaction rảnh (*Window → Preferences → Connections → Connection types → Auto-close transactions*), hãy tắt hoặc đặt thời gian dài để transaction không bị đóng giữa lúc quay.
6. *Window → Preferences → Editors → SQL Editor → SQL Processing → Error handling*: chọn **Stop**, không chọn *Stop + rollback*. Phòng trường hợp lỡ bấm Alt+X với cả file.

**Mở file SQL:** *File → Open File…* → chọn file trong `runtime/oracle/` mà `prepare` in ra. Gán kết nối cho editor bằng ô chọn datasource trên thanh editor (hoặc Ctrl+9) → `ShopFlash DEMO :1522`.

**Chạy:** đặt con trỏ trong câu lệnh, bấm **Ctrl+Enter** (Execute SQL Statement). Mỗi câu cách nhau một dòng trống. Không dùng Alt+X cho cả file. Lỗi ORA-02290 trong scene 3, 4, 6 là dự kiến: DBeaver báo lỗi, transaction vẫn mở (bộ đếm pending transaction vẫn còn) cho tới khi bạn ROLLBACK.

> Nếu DBeaver mở hộp thoại *Bind parameters* (do chuỗi `'TXRESEARCH_DEMO:A1C'` có dấu `:`), bấm **Ignore**, hoặc tắt *SQL Processing → Parameters → Enable parameters* với editor này.

### A4. Công cụ Redis

- **Cách chính (miễn phí):** terminal chạy `./demo.sh play <scene>`. Kit giữ một kết nối `redis-cli` liên tục cho mỗi client, in từng lệnh `A> …` / `B> …` và phản hồi nguyên văn của redis-cli (`QUEUED`, `(nil)`, `(error) …`).
- **Xem nhanh trạng thái:** `./demo.sh show redis`. **Gõ tay:** `./demo.sh redis-cli`.
- **GUI (tùy chọn):** bản desktop RedisInsight → *Add database* → `127.0.0.1:6381`, password là dòng `REDIS_PASSWORD` trong `runtime/demo.env`. Lọc key `txresearch:demo:*`. Nếu RedisInsight chạy trong Docker, nó không thấy được `127.0.0.1` của máy host: dùng bản desktop, hoặc tự nối container RedisInsight vào network `shopflashdemo_default` rồi dùng host `redis`, port `6379`. DBeaver Community không có driver Redis (chỉ có ở bản PRO). Không cần mua license: terminal là đủ.

---

## B. Hướng dẫn từng scene

Mỗi scene theo cùng một chu trình:

```bash
./demo.sh prepare <scene>   # reset baseline + in file cần mở và thứ tự STEP
# ... thực hiện các STEP ...
./demo.sh show oracle|redis # (tùy chọn) xem trạng thái đã COMMIT / trạng thái Redis bằng client độc lập
./demo.sh prepare <scene>   # quay lại từ đầu, làm lại bao nhiêu lần cũng được
```

`prepare` không bao giờ tự chạy sau khi scene kết thúc, nên trạng thái cuối được giữ nguyên để bạn quan sát.

### Scene 1 — Oracle Transaction Lifecycle (COMMIT / ROLLBACK)

- **Mục tiêu:** thay đổi trong transaction chỉ session đó thấy. COMMIT làm thay đổi bền vững và session khác thấy được. ROLLBACK hủy toàn bộ thay đổi chưa COMMIT, không ảnh hưởng phần đã COMMIT trước đó.
- **Nguồn:** `oracle/experiments/A1-commit.sql`, `A1-rollback.sql`. Khối PL/SQL được tách thành từng câu với cùng bảng, cùng giá trị và cùng điều kiện tồn kho.
- **Chuẩn bị:** `./demo.sh prepare 1`.
- **Mở:** `runtime/oracle/1-lifecycle-A.sql` (Editor A, session giao dịch) và `runtime/oracle/1-lifecycle-B.sql` (Editor B, session độc lập). Nên đặt hai editor cạnh nhau: kéo tab sang nửa phải.
- **Quan sát:** Result Grid `ORDERS | ORDER_ITEMS | CART_LINES | P1_STOCK | P2_STOCK | P1_CART | P2_CART | P3_CART`, và lưới Order `ORDER_ID | ORDER_CODE | STATUS`.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 0 | A và B chạy truy vấn session | SID khác nhau, `SERVER_HOST` = mã ở A2 | Hai session thật, đúng DB demo |
| 1 | A: SUMMARY | `0 0 3 100 100 100 1 1 1` | Baseline |
| 2 | A: 4 câu Checkout P1 | Mỗi câu `Updated Rows: 1` (SQL*Plus: `1 row created/updated/deleted`) | Đã ghi 4 bảng, chưa COMMIT |
| 3 | A: SUMMARY · B: SUMMARY | A: `1 1 2 99 100 100 - 1 1` · B: `0 0 3 100 100 100 1 1 1` | Chưa COMMIT: chỉ A thấy |
| 4 | A: `COMMIT` | Commit complete | Kết thúc transaction 1 |
| 5 | B: SUMMARY + Orders | `1 1 2 99 100 100 - 1 1`, Order `…_A1C` | Session độc lập thấy dữ liệu đã COMMIT |
| 6 | A: 4 câu Checkout P2 | ×4 | Transaction 2 |
| 7 | A: SUMMARY + Orders | `2 2 1 99 99 100 - - 1`, thấy `…_A1R` | Trạng thái trung gian |
| 8 | A: `ROLLBACK` | Rollback complete | Hủy transaction 2 |
| 9 | A và B: SUMMARY + Orders | `1 1 2 99 100 100 - 1 1`, chỉ còn `…_A1C` | Order 2 biến mất, P2 = 100, Order đã COMMIT vẫn còn |

- **Câu cần giải thích:** `UPDATE … WHERE on_hand_quantity - reserved_flash_quantity >= 1` (trừ tồn có điều kiện), `COMMIT`, `ROLLBACK`.
- **Reset:** `./demo.sh prepare 1`. Order `_A1C` đã COMMIT được tìm theo user fixture, rồi `reset.sql` đối chiếu chủ sở hữu và mã trước khi xóa.
- **Câu thoại:** *"Session A checkout P1: chính A thấy Order và tồn 99, nhưng session B vẫn thấy 100. Sau COMMIT, B thấy ngay. Lần checkout P2 kết thúc bằng ROLLBACK: Order thứ hai biến mất, P2 về 100, còn đơn đã COMMIT vẫn nguyên."*

### Scene 2 — Redis Transaction Lifecycle (MULTI/EXEC, DISCARD)

- **Mục tiêu:** MULTI chỉ xếp lệnh vào hàng đợi (`QUEUED`). EXEC chạy cả nhóm liền một mạch. DISCARD bỏ cả nhóm.
- **Nguồn:** `redis/experiments/A4.sh`.
- **Chuẩn bị:** `./demo.sh prepare 2`. **Chạy:** `./demo.sh play 2` (Enter = lệnh tiếp theo).

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | BEFORE | P1 100, Cart P1 = 1, Order không tồn tại | Baseline |
| 2 | `A> MULTI`, HSET Order, HINCRBY, HDEL | `OK`, `QUEUED` ×3 | Chỉ xếp hàng |
| 3 | Trạng thái trước EXEC (client độc lập) | Vẫn baseline | Chưa lệnh nào chạy |
| 4 | `A> EXEC` | `1) (integer) 4`, `2) (integer) 99`, `3) (integer) 1` | Chạy tuần tự cả ba |
| 5 | AFTER EXEC | P1 99, Cart P1 `-`, Order tồn tại | Đã ghi |
| 6 | Reset (kit tự gọi) | Baseline | Chuẩn bị nhánh 2 |
| 7 | `A> MULTI` … `A> DISCARD` | `QUEUED` ×3, `OK` | Bỏ hàng đợi |
| 8 | AFTER DISCARD | P1 100, Order không tồn tại | Không ghi gì |

- **Reset:** `./demo.sh prepare 2`.
- **Câu thoại:** *"Trong MULTI, Redis chỉ trả QUEUED; client khác vẫn thấy dữ liệu cũ. EXEC chạy cả ba lệnh liền nhau. Với DISCARD, không lệnh nào được thực thi."*

### Scene 3 — Oracle SAVEPOINT

- **Mục tiêu:** `ROLLBACK TO SAVEPOINT` chỉ hoàn tác phần sau savepoint, transaction vẫn tiếp tục. `ROLLBACK` hoàn tác toàn bộ.
- **Nguồn:** `oracle/experiments/A3.sql`.
- **Chuẩn bị:** `./demo.sh prepare 3`. **Mở:** `runtime/oracle/3-savepoint-A.sql` (một editor).
- **Quan sát:** Result Grid `P1_STOCK | P2_STOCK | P1_CART` trong cùng session.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | STOCK | `100 100 1` | Baseline |
| 2 | UPDATE P1 = 99 | `Updated Rows: 1` | |
| 3 | `SAVEPOINT before_p2` | Savepoint created | Đánh dấu mốc |
| 4 | UPDATE P2 = 98, STOCK | `99 98 1` | |
| 5 | (Tùy chọn) UPDATE quantity = 0, STOCK | ORA-02290, vẫn `99 98 1` | Chỉ câu lỗi bị hoàn tác (statement-level rollback) |
| 6 | Khối `BEGIN ROLLBACK TO SAVEPOINT before_p2; END;` (bôi đen từ `BEGIN` đến `/`, Ctrl+Enter), rồi STOCK | `99 100 1` | Hoàn tác một phần. Phải bọc BEGIN/END vì DBeaver chạy câu `ROLLBACK …` trần thành rollback toàn bộ |
| 7 | `ROLLBACK`, STOCK | `100 100 1` | Hoàn tác toàn bộ |

- **Khi quay:** bỏ STEP 5 (hoặc cắt), tập trung vào STEP 3 → 6 → 7.
- **Câu thoại:** *"Sau savepoint, P2 thành 98. ROLLBACK TO SAVEPOINT chỉ trả P2 về 100, P1 vẫn 99. ROLLBACK toàn phần đưa cả hai về 100."*

### Scene 4 — Oracle Atomicity & Error Handling

- **Mục tiêu:** khi lỗi xảy ra giữa Checkout, Oracle chỉ hoàn tác **câu lệnh lỗi**. Các ghi trước đó vẫn nằm trong transaction cho tới khi ứng dụng ROLLBACK. ROLLBACK đưa cả bốn bảng về trạng thái ban đầu: tất cả hoặc không gì cả.
- **Nguồn:** `oracle/experiments/D1.sql`. Hai mặt hàng; ghi Order, Order Items, Inventory, Cart; lỗi CHECK trên P3.
- **Chuẩn bị:** `./demo.sh prepare 4`. **Mở:** `runtime/oracle/4-atomicity-A.sql`.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | SUMMARY (BEFORE) | Orders 0, Items 0, Cart Lines 3, Stock 100/100/100 | Baseline |
| 2 | 4 câu ghi | `Updated Rows`: 1, 2, 2, 2 (Order, 2 Item, 2 Inventory, 2 dòng Cart) | Ghi 4 bảng |
| 3 | UPDATE quantity P3 = 0 | **ORA-02290** CK_CART_ITEMS_QUANTITY | Lỗi giữa transaction. **Chưa ROLLBACK.** |
| 4 | SUMMARY (AFTER_FAILED_STATEMENT) | Orders 1, Items 2, Cart Lines 1, Stock 99/99/100, P3_CART 1 | Chỉ câu lỗi bị hủy |
| 5 | `ROLLBACK` | Rollback complete | |
| 6 | SUMMARY (AFTER_FULL_ROLLBACK) | Orders 0, Items 0, Cart Lines 3, Stock 100/100/100 | Toàn bộ Checkout bị hủy |

- **Câu thoại:** *"Checkout đã ghi Order, hai Item, trừ tồn và xóa Cart thì gặp lỗi CHECK. Oracle chỉ hủy câu lỗi, các ghi trước vẫn còn trong transaction. Ứng dụng ROLLBACK, và cả bốn bảng quay về đúng ban đầu."*

### Scene 5 — Redis Atomicity & Error Handling

- **Mục tiêu:** Redis không rollback. Lệnh lỗi lúc chạy (WRONGTYPE) chỉ làm hỏng chính nó; các lệnh trước và sau trong EXEC vẫn được áp dụng.
- **Nguồn:** `redis/experiments/A5.sh`, nhánh `runtime`.
- **Chuẩn bị:** `./demo.sh prepare 5`. **Chạy:** `./demo.sh play 5`.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | BEFORE | P1 100, Order không tồn tại | |
| 2 | MULTI, HINCRBY, HSET Order, LPUSH Order, HDEL Cart | `QUEUED` ×4 | Lỗi kiểu chưa được phát hiện lúc xếp hàng |
| 3 | `A> EXEC` | `1) 99`, `2) 2`, `3) (error) WRONGTYPE…`, `4) 1` | Lệnh 3 lỗi, lệnh 1, 2, 4 vẫn chạy |
| 4 | AFTER | P1 99, Order tồn tại, Cart mất P1 | Không có rollback |

- **Câu thoại:** *"LPUSH vào một Hash gây lỗi WRONGTYPE, nhưng stock vẫn giảm còn 99, Order vẫn được tạo và Cart vẫn mất P1. Atomicity của Redis chỉ có nghĩa là không bị chen ngang, không phải tất cả hoặc không gì cả."*

### Scene 6 — Consistency: CHECK constraint và Lua

**Phía Oracle:** `./demo.sh prepare oracle-consistency`, mở `runtime/oracle/6-consistency-A.sql`.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | Đọc `USER_CONSTRAINTS` | `CK_CART_ITEMS_QUANTITY · CART_ITEMS · C · quantity > 0 · ENABLED · VALIDATED · NOT DEFERRABLE · IMMEDIATE` | Ràng buộc khai báo, kiểm tra ngay tại từng statement |
| 2 | (Tùy chọn) UPDATE quantity = 0, SUMMARY | ORA-02290, dữ liệu không đổi | DBMS tự chặn |
| 3 | `ROLLBACK` | | |

Nếu đã quay scene 4, có thể dùng lại lỗi ORA-02290 ở đó và chỉ cần chạy STEP 1 để giải thích.

**Phía Redis:** `./demo.sh prepare redis-lua`, rồi:

```bash
./demo.sh lua 101     # quantity vượt tồn khả dụng
./demo.sh lua 1       # hợp lệ
./demo.sh show redis  # (tùy chọn) xem lại
```

hoặc `./demo.sh play redis-lua` để chạy cả hai lần liên tiếp. Mở `report/transaction/research/redis/lua/C2-validate.lua` trong editor code: kit in ra dải dòng validation và dòng của lệnh ghi đầu tiên.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | `lua 101` | `(error) ERR OUT_OF_STOCK`; BEFORE = AFTER (P1 100, Order không tồn tại, Cart 1) | Validation chặn trước mọi lệnh ghi |
| 2 | `lua 1` | `"OK"`; P1 99, version 1, Order `product_id=P1 quantity=1 status=PENDING`, Cart mất P1 | Ba lệnh ghi chạy trong một lần thực thi script |

- **Câu cần giải thích:** dòng `EVAL … 4 <KEYS> <ARGV>` (4 key: cart, order, product, inventory; 3 argument: prefix, product_id, quantity). Trong source: mọi `if … return reject(...)` đứng **trước** `redis.call('HSET'…)` đầu tiên.
- **Reset:** `./demo.sh prepare redis-lua` (hoặc `./demo.sh prepare 6` để reset cả Oracle và Redis).
- **Câu thoại:** *"Oracle giữ consistency bằng constraint khai báo. Redis không có constraint, nên ta kiểm tra trong Lua trước khi ghi: quantity 101 bị từ chối và dữ liệu không đổi; quantity 1 thì cả ba thay đổi được ghi trong một lần chạy script."*

### Scene 7 — Oracle Isolation (READ COMMITTED)

- **Mục tiêu:** READ COMMITTED không đọc bẩn. B không thấy thay đổi chưa COMMIT của A, nhưng thấy ngay sau khi A COMMIT, kể cả khi vẫn đang trong cùng một transaction.
- **Nguồn:** `oracle/experiments/two-session.py`, hàm `b1`.
- **Chuẩn bị:** `./demo.sh prepare 7`. **Mở:** `runtime/oracle/7-isolation-A.sql` và `7-isolation-B.sql` ở hai editor cạnh nhau.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | A và B: truy vấn session | SID khác nhau | Hai session vật lý |
| 2 | A và B: `ROLLBACK`; `SET TRANSACTION ISOLATION LEVEL READ COMMITTED` | Transaction set | |
| 3 | B: đọc P1 | `100`, có TXN_ID | |
| 4 | A: UPDATE P1 = 99 (chưa COMMIT) | `Updated Rows: 1` | |
| 5 | B: đọc P1 | `100`, cùng TXN_ID | Không đọc bẩn |
| 6 | A: `COMMIT` | | |
| 7 | B: đọc P1 | `99`, cùng TXN_ID | Mỗi câu đọc thấy dữ liệu đã COMMIT mới nhất |
| 8 | B: `ROLLBACK` | | Kết thúc transaction của B |
| 9 | A: khôi phục 100, COMMIT (không quay) | | Như B1 gốc |

- **Câu thoại:** *"Hai session khác SID. A sửa tồn thành 99 nhưng chưa COMMIT: B vẫn đọc 100. A COMMIT xong, B đọc lại được 99. TXN_ID của B không đổi qua ba lần đọc, nghĩa là vẫn cùng một transaction READ COMMITTED."*

### Scene 8 — Redis Isolation (WATCH, conflict và retry)

- **Mục tiêu:** WATCH là optimistic locking. Nếu key bị client khác sửa sau WATCH, EXEC trả `(nil)` và không ghi đè; client đọc lại rồi retry.
- **Nguồn:** `redis/experiments/B4.py`, nhánh B4.2 hiện hành (revision-20261109).
- **Chuẩn bị:** `./demo.sh prepare 8`. **Chạy:** `./demo.sh play 8`.

| STEP | ACTION | EXPECTED | INTERPRETATION |
|---|---|---|---|
| 1 | `A> WATCH`, `A> HGET` | `OK`, `"100"`; A tính 99 | A theo dõi key |
| 2 | `B> HINCRBY … -1` (kết nối khác) | `(integer) 99` | B đổi key |
| 3 | `A> MULTI`, `HSET … 99`, `EXEC` | `QUEUED`, **`(nil)`** | Conflict: transaction của A bị hủy |
| 4 | `A> HGET` | `"99"` | Giá trị của B được giữ |
| 5 | `A> WATCH`, `HGET` → tính 98, `MULTI`, `HSET 98`, `EXEC` | `"99"`, `1) (integer) 0` | Retry thành công. HSET trả 0 = cập nhật field đã có, **không phải lỗi** |
| 6 | AFTER | P1 = 98 | Không mất cập nhật |

- **Câu thoại:** *"A đọc 100 và định ghi 99, nhưng B giảm tồn trước. EXEC của A trả nil, Redis từ chối ghi đè. A đọc lại 99, tính 98 và thử lại thành công. Kết quả 98 là đúng."*

---

## C. Quy trình quay video

### C1. Bố trí màn hình

```text
┌───────────────────────────────┬──────────────────────────────┐
│ DBeaver                       │ Terminal (font ≥ 16pt)       │
│  Editor A  │  Editor B        │  ./demo.sh play <scene>      │
│  (SQL)     │  (SQL)           │  ./demo.sh lua <q>           │
│────────────┴──────────────────│                              │
│  Result Grid                  │  (scene 6: editor code mở    │
│                               │   C2-validate.lua ở tab khác)│
└───────────────────────────────┴──────────────────────────────┘
```

- DBeaver: tăng font editor và Result Grid (*Preferences → Appearance → Colors and Fonts*). Đóng panel Database Navigator để lưới kết quả rộng hơn.
- Scene 1 và 7: kéo tab Editor B sang nửa phải để thấy hai session cùng lúc.
- Terminal: `clear` trước mỗi scene. Đặt cửa sổ đủ rộng (~140 cột) để dòng `EVAL` không bị xuống dòng quá nhiều.

### C2. Làm trước khi quay (không quay)

1. `./demo.sh status`: target đúng, fixture baseline.
2. Mở sẵn kết nối DBeaver và **cả 6 file** `runtime/oracle/*.sql`, đã gán kết nối `ShopFlash DEMO :1522`.
3. Chạy STEP 0/1 (kiểm tra session) của từng editor một lần để DBeaver mở sẵn kết nối.
4. Trước **mỗi** scene: `./demo.sh prepare <scene>`, kiểm tra lại không có cảnh báo transaction mở.

### C3. Kịch bản thời lượng (mục tiêu 2:30)

| Thời gian | Nội dung | Quay | Có thể cắt |
|---|---|---|---|
| 0:00–0:20 | Scene 1 Oracle: STEP 2 → 3 (A và B) → 4 → 5 → 8 → 9 | STEP 3, 5, 9 | STEP 0, 6–7 (tua nhanh) |
| 0:20–0:35 | Scene 2 Redis: MULTI/QUEUED → trạng thái trước EXEC → EXEC → AFTER; DISCARD → AFTER | EXEC, DISCARD | đoạn reset giữa hai nhánh |
| 0:35–0:50 | Scene 3: STEP 3 → 4 → 6 → 7 | cả bốn | STEP 1–2, 5 |
| 0:50–1:10 | Scene 4: STEP 2 (lướt) → 3 → 4 → 5 → 6 | STEP 3, 4, 6 | STEP 1 |
| 1:10–1:25 | Scene 5: EXEC → AFTER | phản hồi EXEC 4 dòng | các dòng QUEUED |
| 1:25–1:35 | Scene 6 Oracle: STEP 1 metadata | | STEP 2 |
| 1:35–2:00 | Scene 6 Redis: source Lua (3 s) → `lua 101` → `lua 1` | RESPONSE và AFTER | dòng KEYS/ARGV |
| 2:00–2:15 | Scene 7: STEP 3 → 4 → 5 → 6 → 7 | 3 lần đọc của B | STEP 1–2, 8–9 |
| 2:15–2:30 | Scene 8: EXEC `(nil)` → retry → 98 | EXEC nil, AFTER | BEFORE |

Gợi ý: quay từng scene thành clip riêng rồi ghép. Nếu một scene sai, chỉ cần `./demo.sh prepare <scene>` và quay lại clip đó.

---

## D. Reset, cleanup và phục hồi

### D1. Quay lại baseline

```bash
./demo.sh prepare <scene>        # baseline của DB dùng trong scene
./demo.sh reset all              # cả Oracle và Redis
./demo.sh show oracle            # kiểm tra: 0 0 3 100 100 100 1 1 1, không có Order
./demo.sh show redis             # kiểm tra: P1/P2/P3 100 0 0 1, Order không tồn tại
```

### D2. Còn transaction mở hoặc session giữ khóa

`reset`/`prepare` sẽ dừng với thông báo `Có session SHOP khác còn transaction mở` kèm SID. Cách xử lý:

1. Trong editor DBeaver còn pending transaction: chạy `ROLLBACK;` (hoặc bấm nút Rollback trên thanh công cụ), rồi chạy lại `prepare`.
2. Không tìm được editor nào: `./demo.sh unlock`. Lệnh này kill các session SHOP còn transaction, **chỉ trong container demo**, và Oracle tự rollback chúng. Editor DBeaver bị kill sẽ báo mất kết nối, bấm reconnect.
3. Nếu `play` (CLI) bị treo vì chờ khóa, bấm Ctrl+C. Kết nối đóng thì Oracle rollback transaction dở, Redis hủy MULTI/WATCH.

### D3. Fixture có dữ liệu lạ

- Oracle: `reset.sql` khôi phục tồn, reserved, version, Cart, và xóa mọi Order **của user fixture có mã `TXRESEARCH_DEMO_*`**. Nếu user fixture có Order mang mã khác, script từ chối xóa (an toàn): xem Order đó trong DBeaver trước khi quyết định.
- Redis: reset `DEL` đúng 7 key trong `runtime/redis-keys.txt` rồi dựng lại. Nếu có key `txresearch:demo:*` nằm ngoài manifest, reset dừng và in tên key; xóa đúng key đó bằng `./demo.sh redis-cli` rồi chạy lại. Kit không xóa theo wildcard.

### D4. Cleanup

```bash
./demo.sh cleanup     # Oracle preview → cleanup → VERIFY_ABSENT=PASS; Redis cleanup → preview (mọi key none)
./demo.sh down        # dừng container demo, giữ volume (./demo.sh up để chạy lại)
./demo.sh destroy     # xóa container + volume CHỈ của shopflashdemo (phải gõ xác nhận)
```

Sau `cleanup`, chạy `./demo.sh reset all` nếu muốn tạo fixture mới (ID sẽ khác, kit tự cập nhật manifest).

### D5. Quay về môi trường ShopFlash chính

- Kit không dừng, không ghi và không mount volume của project chính. ShopFlash chính vẫn ở `localhost:1521` / `localhost:6380`. Trong DBeaver, chỉ cần dùng lại kết nối ShopFlash cũ.
- Khi quay xong: `./demo.sh down` để giải phóng RAM. Dữ liệu demo vẫn nằm trong volume `shopflashdemo_*`.
- Nếu máy không đủ RAM chạy cả hai bộ DB: tự dừng project chính bằng `docker compose stop` ở root repo (giữ nguyên volume), quay demo, rồi `./demo.sh down` và `docker compose up -d --wait` để chạy lại project chính. Kit không tự làm bước này.

---

## E. Kiểm thử đã thực hiện

- `./demo.sh selftest`: 8 scene × 2 lượt trên DBMS thật, kiểm từng mốc của từng STEP; thêm các trường hợp transaction mở → `unlock`, mất kết nối giữa chừng, fixture bị sửa đã COMMIT, key lạ, rồi cleanup và verify.
- `./demo.sh reference`: chạy lại nguyên văn A1, A3, D1, B1, A4, A5, B4, C2 trên DB demo, transcript lưu ở `runtime/reference/`.
- Chưa kiểm thử qua giao diện DBeaver bằng tự động hóa. Các file SQL đã chạy thật từng câu qua SQL*Plus, đúng thứ tự STEP, trên hai session như hai editor. Nên chạy thử scene 1 trong DBeaver một lần trước khi quay.
