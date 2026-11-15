# Module concurrency

Chủ đề: Concurrency control.

> Thiết kế đã chốt với TV4, TV1 (2026-11-15). Riêng số kết nối tối đa của pool Oracle và Redis còn chờ nhóm (mục Concurrency Lab).

## Chức năng

- **F9 Flash sale**
  - Khách: xem chương trình đang chạy và sắp chạy, giữ suất, hủy giữ suất, đặt đơn flash sale trước khi suất hết hạn.
  - Admin: tạo, sửa, lên lịch, hủy, dừng sớm chương trình; theo dõi suất còn lại và đối chiếu Oracle với Redis.
- **Concurrency Lab** (admin): chạy cùng một kịch bản "N người tranh M suất" bằng nhiều chiến lược trên Oracle và Redis, đo số suất bán vượt, throughput, độ trễ. Đây là phần demo cho báo cáo mục 3.5, nằm ngay trong ứng dụng.

F10 Giỏ hàng và F0 Đăng nhập đã bàn giao cho TV4 (`transaction`, `common/security`) ngày 26/10/2026. Module này không đọc, không ghi `CARTS`, `CART_ITEMS`. F9 giữ trách nhiệm trạng thái và suất Flash Sale; với Checkout, hai module thống nhất thứ tự khóa `PRODUCT_INVENTORY` (xem "Thứ tự khóa").

## Quyết định đã chốt

- **Oracle là nguồn gốc** của chương trình, policy, giá, suất (`allocated_quantity`, `sold_quantity`), tồn kho và đơn flash sale. **Redis giữ trạng thái runtime** từ lúc chương trình được lên lịch: suất còn lại, số lượng mỗi người đã giữ, reservation. Reservation chỉ có trên Redis.
- **Mất dữ liệu Redis vẫn không bán vượt.** Runtime dựng lại từ Oracle: `remaining = allocated - sold`, `user_qty` đếm từ `order_items` của các đơn chưa hủy. Chỉ mất các reservation đang chờ, nghĩa là suất đó được mở bán lại. Lúc khởi động và trong job định kỳ, chương trình `SCHEDULED`/`ACTIVE` nào thiếu key runtime thì được nạp lại.
- **Giữ hàng trong kho từ lúc `SCHEDULED`**, không đợi đến `ACTIVE`. Lên lịch thì `reserved_flash_quantity += allocated_quantity` ngay, để đến giờ mở bán chắc chắn còn hàng. Vì vậy migration của module sửa `v_inventory_invariant_check` thành `fs.status IN ('SCHEDULED','ACTIVE')` (DATABASE_DESIGN §14 cho phép).
- **Giờ mở/đóng do Lua quyết định bằng đồng hồ Redis** (`redis.call('TIME')`), không phụ thuộc job. Job chỉ đổi trạng thái trong Oracle và có thể trễ vài giây mà không ảnh hưởng người mua.
- **Giữ suất là một Lua script duy nhất**: kiểm tra và trừ suất trong cùng một lệnh nên không có race. Không dùng khóa phân tán (`SET NX`) cho việc này; khóa phân tán chỉ nhắc trong báo cáo để so sánh.
- **Hoàn tất mua theo kiểu Try–Confirm–Cancel**, không dùng XA giữa Oracle và Redis. Reservation đi theo `PENDING → CLAIMED → CONFIRMED | RELEASED`, hoặc `PENDING → RELEASED` khi hết hạn hay người dùng hủy. Chi tiết ở mục "Luồng mua flash sale".
- **Module tự tạo đơn flash sale.** Hợp đồng của `transaction` (`backend/docs/transaction/CONTRACT.md`) để F9 ngoài phạm vi Checkout, nên module có endpoint đặt đơn riêng: ghi `ORDERS` (`order_type = 'FLASH_SALE'`) và `ORDER_ITEMS` trong cùng transaction với tăng `sold_quantity` và trừ kho. Hai module không gọi code của nhau. Đơn flash vẫn hiện ở "Đơn hàng của tôi" (F8) vì truy vấn của `transaction` không lọc `order_type`.
- **Ghi Oracle bằng điều kiện trong `WHERE`** (compare-and-set). Không đọc giá trị lên Java, tính rồi ghi lại. Đổi trạng thái chương trình dùng `UPDATE flash_sales SET status = :to WHERE flash_sale_id = :id AND status = :from`; nhiều admin hoặc job chạy cùng lúc thì chỉ một bên thắng.
- **Mọi câu `UPDATE product_inventory` đều `version = version + 1`.** Khớp với code đã có: `storage` kiểm tra `version` khi admin sửa tồn kho (optimistic lock), `transaction` tăng `version` khi trừ và hoàn kho. Nếu bên bán flash sale không tăng version, admin đang mở form với số cũ có thể ghi đè lên số vừa bị trừ (lost update).
- **Thứ tự khóa để tránh deadlock**: ở mọi module, `PRODUCT_INVENTORY` luôn khóa sau cùng, nhiều dòng thì theo `product_id` tăng dần.
  - `transaction` (đã chốt trong CONTRACT): Checkout `CARTS → PRODUCTS → PRODUCT_INVENTORY`; hủy đơn `ORDERS → PRODUCT_INVENTORY`.
  - `concurrency`: `FLASH_SALES → FLASH_SALE_ITEMS` (nhiều dòng theo `flash_sale_item_id` tăng dần) `→ PRODUCT_INVENTORY`. Đọc `PRODUCTS` không khóa.
  - Module này không khóa `CARTS`, `PRODUCTS`, `ORDERS`; `transaction` không khóa bảng `FLASH_*`. Vì vậy không có chu trình chờ giữa hai module.
- **Job chạy được nhiều instance**: chọn chương trình cần chuyển trạng thái bằng `FOR UPDATE SKIP LOCKED`; script dọn reservation kiểm tra trạng thái ngay trong Lua nên chạy trùng cũng không sao. `@EnableScheduling` đặt trong config của module.
- **Người dùng hiện tại** lấy từ JWT mà `common/security` đã kiểm tra (`AuthPrincipal` trong `SecurityContext`), theo mẫu `transaction/security/JwtTransactionIdentity`. Không nhận `userId` từ body, query hay header. Giữ suất, hủy giữ suất, đặt đơn chỉ cho vai trò `CUSTOMER`. Test tải bằng k6 đăng nhập trước qua `POST /api/auth/login` trong `setup()` để có token cho từng khách thử.
- **Quyền**: `SecurityConfig` hiện chặn mọi `/api/**` chưa khai báo (`denyAll`). Cần thêm: `GET /api/flash-sales/active` và `GET /api/flash-sales/*/stock` cho mọi người; phần còn lại của `/api/flash-sales/**` cần `CUSTOMER`. `/api/admin/**` đã yêu cầu `ADMIN`. Token gửi qua header `Authorization` nên không cần CSRF.
- **Thời gian:** API dùng ISO-8601 có offset `+07:00`, giống module `query`. Redis lưu epoch mili giây. Response của flash sale có `serverTime` để frontend đếm ngược theo giờ server, không theo đồng hồ máy khách.
- **Client Redis:** `RedisClient` dùng chung. Script nằm ở `src/main/resources/concurrency/redis/*.lua` (`reserve`, `claim`, `confirm`, `release`, `load-item`; `lab-buy` cho Lab). SHA1 tính sẵn lúc khởi động nên backend không cần Redis để khởi động; gọi bằng `EVALSHA`, gặp `NOSCRIPT` thì `SCRIPT LOAD` rồi gọi lại một lần. `WATCH` (chiến lược `REDIS_WATCH` của Lab) phải chạy trên kết nối riêng của transaction (`redis.transaction(false)`).

## Dữ liệu

### Oracle

Dùng bảng nền: `FLASH_SALES`, `FLASH_SALE_CONFIGS`, `FLASH_SALE_ITEMS`. Ghi `ORDERS`, `ORDER_ITEMS` chỉ cho đơn `order_type = 'FLASH_SALE'`. Với `PRODUCT_INVENTORY`, module chỉ sửa `reserved_flash_quantity`, và `on_hand_quantity` khi bán qua flash sale. Đọc `PRODUCTS`, `ORDERS` (tra `idempotency_key` khi dọn reservation).

Migration `V<n>__concurrency_flash_sale.sql` (`n` là số còn trống lúc mở PR; hiện `main` đã có V1–V3):

- `ALTER TABLE flash_sales ADD (version NUMBER(10) DEFAULT 0 NOT NULL)`: admin sửa chương trình phải gửi `version`.
- `CREATE INDEX ix_flash_sales_status_start ON flash_sales (status, start_at)`: phục vụ job và `GET /active`.
- Sửa `v_inventory_invariant_check`: `fs.status IN ('SCHEDULED','ACTIVE')`.

`carts.version` đã có trong `V2__transaction_cart_version_and_checkout_hash.sql` của `transaction`; module này không thêm.

Migration `V<n+1>__concurrency_lab.sql`: bảng `cc_lab_items (lab_item_id PK, allocated, sold, version)` có `CHECK (sold >= 0 AND sold <= allocated)`. Lab chỉ ghi bảng này, không đụng dữ liệu bán hàng.

Seed `db/dev-seed/R__10_concurrency_seed.sql` (dùng `MERGE`, ID 9001–9500; `transaction` dùng 101–106):

- Khách hàng 9001–9500, vai trò `CUSTOMER`, mật khẩu giống seed lõi, để thử giới hạn mỗi người và test tải.
- Sản phẩm 9101–9103, có tồn kho, thuộc danh mục có sẵn trong seed lõi.
- Chương trình 9101 `ACTIVE` từ 2026-10-01 đến 2027-12-31, ba sản phẩm với quota 10, 50, 100. `reserved_flash_quantity` khớp với view bất biến. Khung giờ dài để lần chạy nào cũng còn hiệu lực, vì seed repeatable chỉ chạy lại khi file đổi.
- Chương trình 9102 `DRAFT` để demo màn hình admin.

Không dùng sản phẩm 1 của seed lõi: seed lõi đặt lại `reserved_flash_quantity = 0` cho sản phẩm này mỗi khi file chạy lại.

### Redis

| Key | Kiểu | Nội dung |
|---|---|---|
| `flash:sale:{saleId}` | HASH | `status`, `startAt`, `endAt` (epoch ms), `maxPerUser`, `reservationTtl` (giây) |
| `flash:item:{itemId}` | HASH | `saleId`, `productId`, `allocated`, `remaining`, `status` |
| `flash:item:{itemId}:user_qty` | HASH | `userId` → số lượng đã giữ cộng đã mua |
| `flash:item:{itemId}:pending` | HASH | `userId` → `reservationId` đang `PENDING`/`CLAIMED`. **Thêm so với DATABASE_DESIGN:** người dùng bấm "Mua ngay" hai lần thì nhận lại đúng reservation cũ |
| `flash:reservation:{reservationId}` | HASH | `saleId`, `itemId`, `productId`, `userId`, `quantity`, `status`, `createdAt`, `expiresAt` (ms). Không đặt TTL khi còn `PENDING`/`CLAIMED`, vì key tự mất thì không còn gì để hoàn suất. Sang `CONFIRMED`/`RELEASED` thì `EXPIRE 3600` |
| `flash:reservations:expiry` | ZSET | score = `expiresAt`, member = `reservationId` |
| `cclab:item` | HASH | Chỉ dùng cho Lab: `allocated`, `remaining`, `version` |

`reservationId` là UUID do backend sinh. Kết thúc chương trình thì xóa key runtime bằng `UNLINK`.

## Vòng đời chương trình

| Chuyển | Ai | Điều kiện | Oracle (một transaction) | Redis (sau commit) |
|---|---|---|---|---|
| tạo → `DRAFT` | admin | `end_at > start_at` | insert `flash_sales` + `flash_sale_configs` | — |
| `DRAFT` → `SCHEDULED` | admin `schedule` | `start_at > now`, có ít nhất 1 item | với từng sản phẩm: `reserved += allocated` khi `on_hand - reserved >= allocated`; 0 dòng thì rollback, `409 INSUFFICIENT_STOCK` kèm `productId` | nạp `flash:sale`, `flash:item` |
| `SCHEDULED` → `DRAFT` | admin `unschedule` | còn ít nhất 60 s trước `start_at` | `reserved -= allocated` | xóa key runtime |
| `DRAFT`/`SCHEDULED` → `CANCELLED` | admin `cancel` | `SCHEDULED` thì như dòng trên | như dòng trên nếu đang `SCHEDULED` | xóa key runtime |
| `SCHEDULED` → `ACTIVE` | job | `start_at <= now` | chỉ đổi `status` | `HSET status ACTIVE` |
| `ACTIVE` dừng sớm | admin `stop` | | `end_at = now` | ghi `endAt` vào Redis **trước** để chặn giữ suất ngay |
| `ACTIVE` → `ENDED` | job | `now >= end_at + reservation_timeout_seconds + 120 s` | khóa `flash_sales`, rồi các item; `reserved -= (allocated - sold)` | xóa key runtime |

- Mốc 60 s trước giờ mở bán tránh trường hợp đã hủy lịch trong Oracle mà key Redis chưa kịp xóa đúng lúc giờ mở bán tới.
- `ENDED` chờ thêm một khoảng sau `end_at` để mọi reservation còn hạn được chốt xong. Nếu trả hàng về kho trước, một đơn chốt muộn sẽ trừ `reserved` lần nữa và làm lệch bất biến.
- Sửa chương trình: `DRAFT` sửa được hết (thông tin, policy, danh sách sản phẩm); từ `SCHEDULED` trở đi chỉ sửa tên và mô tả. Muốn đổi giờ hoặc quota thì hủy lịch về `DRAFT` rồi sửa. Chỉ xóa cứng được chương trình `DRAFT`.

## Luồng mua flash sale

### 1. Giữ suất: `reserve.lua`

```text
KEYS: flash:sale:{s}, flash:item:{i}, flash:item:{i}:user_qty, flash:item:{i}:pending,
      flash:reservation:{r}, flash:reservations:expiry
ARGV: userId, quantity, reservationId

now = TIME (ms)
sale có, status ∈ {SCHEDULED, ACTIVE}         sai → SALE_NOT_FOUND
startAt <= now < endAt                        sai → SALE_NOT_STARTED / SALE_ENDED
item có, cùng saleId, status ACTIVE           sai → ITEM_UNAVAILABLE
pending[userId] còn PENDING/CLAIMED           đúng → trả lại reservation đó (idempotent)
remaining >= quantity                         sai → SOLD_OUT
user_qty[userId] + quantity <= maxPerUser     sai → USER_LIMIT_REACHED
remaining -= q; user_qty[userId] += q; pending[userId] = r
HSET reservation (PENDING, expiresAt = now + reservationTtl); ZADD expiry
```

### 2. Đặt đơn: `POST /api/flash-sales/reservations/{reservationId}/checkout`

Body `{ recipientName, recipientPhone, shippingAddress }`, kiểm tra giống Checkout của `transaction`. Một transaction Oracle trong module:

```text
@Transactional(timeout = 10)
  Redis claim.lua: PENDING → CLAIMED nếu đúng chủ và now < expiresAt
        không có hoặc không phải chủ → RESERVATION_NOT_FOUND
        hết hạn                      → RESERVATION_EXPIRED
        đang CLAIMED                 → RESERVATION_ALREADY_USED
        đã CONFIRMED                 → trả lại đơn đã tạo (200), không ghi gì thêm
  đăng ký hook (trước khi ghi Oracle): sau commit → confirm, sau rollback → release
  Oracle, khóa FLASH_SALE_ITEMS rồi PRODUCT_INVENTORY:
    UPDATE flash_sale_items SET sold_quantity = sold_quantity + :q, updated_at = SYSTIMESTAMP
      WHERE flash_sale_item_id = :item AND status = 'ACTIVE' AND sold_quantity + :q <= allocated_quantity
    UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - :q,
           reserved_flash_quantity = reserved_flash_quantity - :q, version = version + 1, updated_at = SYSTIMESTAMP
      WHERE product_id = :p AND reserved_flash_quantity >= :q
    0 dòng ở một trong hai câu → SOLD_OUT (transaction rollback)
    đọc sale_price (flash_sale_items), name, sku (products), không khóa
    INSERT orders (order_type = 'FLASH_SALE', status = 'PENDING', order_code = 'FS-' + UUID không dấu gạch,
                   idempotency_key = 'FS-' || reservationId, subtotal = total_amount = sale_price * q, discount_amount = 0)
    INSERT order_items (flash_sale_item_id = :item, unit_price = sale_price, quantity = q, line_total = sale_price * q)
COMMIT   → confirm: CONFIRMED, ZREM expiry, HDEL pending
ROLLBACK → release: remaining += q, user_qty -= q, RELEASED, ZREM expiry, HDEL pending
```

- Response giống đơn thường của `transaction`: `{ orderId, orderCode, status, totalAmount }`, `201` khi tạo mới.
- Mất phản hồi thì gửi lại cùng `reservationId` là an toàn: reservation đã `CONFIRMED` thì trả lại đơn cũ; nếu lọt qua thì `uk_orders_idempotency` vẫn chặn đơn trùng.

### 3. Job dọn reservation (mỗi 5 s)

`ZRANGEBYSCORE flash:reservations:expiry -inf <now> LIMIT 0 100`, với từng reservation:

- `PENDING` đã hết hạn: release (Lua kiểm tra lại trạng thái và giờ trước khi hoàn).
- `CLAIMED` quá `expiresAt + 30 s` (checkout bị sập giữa chừng): tra `orders.idempotency_key = 'FS-' || reservationId`. Có đơn thì confirm, không có thì release. Mốc 30 s chỉ an toàn khi transaction đặt đơn có timeout ≤ 10 s.

### 4. Hủy đơn flash sale

V1 không hỗ trợ: `allow_cancel` mặc định `N`, và F8 của `transaction` trả `409 ORDER_NOT_CANCELLABLE` cho mọi đơn `FLASH_SALE`.

Mở rộng nếu còn thời gian (cần TV4 chuyển việc hủy đơn flash sang module này): chỉ khi `allow_cancel = 'Y'`, `sold -= q`. Chương trình còn `ACTIVE` thì trả về suất flash (`reserved` giữ nguyên, Redis `remaining += q`); đã `ENDED` thì trả về kho thường (`on_hand += q`). Cả hai trường hợp `user_qty -= q`.

## Concurrency Lab

Kịch bản: một "hot item" có M suất, N người cùng mua, mỗi người 1 suất. Backend chạy N lượt mua bằng virtual thread, giới hạn C lượt đồng thời, có cổng xuất phát chung (`CountDownLatch`) để mọi lượt bắt đầu cùng lúc.

| `strategy` | Cách làm | Kỳ vọng |
|---|---|---|
| `ORACLE_NAIVE` | `SELECT sold` → Java kiểm tra → `UPDATE SET sold = :sold + 1` (READ COMMITTED) | Bán vượt: số người được báo thành công > M, dù `CHECK sold <= allocated` không hề bị vi phạm |
| `ORACLE_PESSIMISTIC` | `SELECT ... FOR UPDATE` → kiểm tra → `UPDATE` | Đúng; các lượt xếp hàng chờ khóa dòng |
| `ORACLE_NOWAIT` | `SELECT ... FOR UPDATE NOWAIT` | Đúng; lượt gặp khóa bị từ chối ngay (`ORA-00054`) |
| `ORACLE_OPTIMISTIC` | đọc `version` → `UPDATE ... WHERE version = :v`, 0 dòng thì thử lại (tối đa 5 lần) | Đúng; thử lại nhiều khi tranh chấp cao |
| `ORACLE_ATOMIC` | một câu `UPDATE ... SET sold = sold + 1 WHERE sold + 1 <= allocated` | Đúng; 1 round-trip |
| `ORACLE_SERIALIZABLE` | như `NAIVE` nhưng mức cô lập SERIALIZABLE | Đúng; nhiều `ORA-08177`, phải thử lại |
| `REDIS_NAIVE` | `HGET remaining` → Java kiểm tra → `HSET remaining` | Bán vượt |
| `REDIS_WATCH` | `WATCH` → `MULTI` → `HINCRBY -1` → `EXEC`, nil thì thử lại | Đúng; thử lại nhiều |
| `REDIS_DECR` | `HINCRBY -1`, âm thì `HINCRBY +1` | Đúng về số bán; bộ đếm có lúc âm |
| `REDIS_LUA` | kiểm tra và trừ trong một script | Đúng; 1 round-trip |

Cài đặt (code ở `lab/`, `repository/oracle/LabOracleCounter`, `repository/redis/LabRedisCounter`, `service/ConcurrencyLabService`):

- `NAIVE`, `PESSIMISTIC`, `NOWAIT` chạy trong một transaction READ COMMITTED; `SERIALIZABLE` trong transaction SERIALIZABLE. `OPTIMISTIC` và `ATOMIC` dùng auto-commit, mỗi câu lệnh tự commit.
- `OPTIMISTIC`, `SERIALIZABLE`, `REDIS_WATCH` thử lại tối đa 5 lần, không chờ giữa các lần. Hết lượt thì lượt đó lỗi `RETRY_EXHAUSTED` (`SERIALIZABLE` ghi `ORA-08177`). `NOWAIT` không thử lại. Vì vậy các chiến lược này có thể bán **ít hơn** M dù còn người mua.
- `REDIS_LUA` dùng `lab-buy.lua`. `REDIS_WATCH` chạy trên kết nối riêng của transaction (`redis.transaction(false)`).
- Mỗi lần chạy (kể cả warmup) đặt lại bộ đếm về `allocated = M`, `sold = 0` trước khi chạy.
- Độ trễ một lượt đo từ lúc lượt được phép chạy (lấy được permit của `Semaphore`) tới lúc xong, nên gồm cả thời gian chờ kết nối từ pool khi C lớn hơn pool; không gồm thời gian chờ permit. Đã kiểm tra bằng `-Djdk.tracePinnedThreads=short` trên JDK 23: JDBC (ojdbc17) và Jedis không ghim virtual thread.
- Chỉ một lần chạy (hoặc `reset`) tại một thời điểm trong một backend.

Số đo mỗi lần chạy: `successes`, `soldOut`, `oversold = max(0, successes - M)`, `finalSold`, `lostUpdates = successes - finalSold`, `retries`, `errors` và `errorCodes` (đếm theo mã, ví dụ `ORA-00054`, `ORA-08177`, `RETRY_EXHAUSTED`), `elapsedMs`, `throughput` (lượt/giây), `p50Ms`, `p95Ms`, `p99Ms` (nearest-rank). Mặc định 1 lần warmup không tính và 5 lần đo; `summary` trả trung bình và độ lệch chuẩn mẫu (yêu cầu của `report/AGENTS.md`).

Số kết nối tối đa ảnh hưởng trực tiếp throughput: Oracle là Hikari `maximumPoolSize` (mặc định 10, `application.properties` chưa đặt), Redis là pool của `RedisClient` (16). Kết quả luôn ghi hai số này. **[Cần chốt: nhóm]** đặt cùng một con số cho hai bên trong `application.properties`.

Các demo hai phiên chạy tay (lost update theo từng bước, lock timeout, deadlock `ORA-00060`, `MULTI/EXEC` không rollback, `WATCH`) đã có trong báo cáo 3.4 của `transaction` (`report/transaction/REPORT.md`, thí nghiệm A4–A5, B1–B5). Báo cáo 3.5 dẫn lại phần đó, chỉ làm thêm phần 3.4 chưa có (`SERIALIZABLE`, `ORA-08177`) dưới dạng script trong `report/concurrency/`, không đưa vào Lab.

## API

Base khách hàng: `/api/flash-sales`. Base admin: `/api/admin/flash-sales`, `/api/admin/concurrency-lab`. Tiền là số (VND).

### Khách: flash sale

| Method, path | Quyền | Mô tả |
|---|---|---|
| `GET /api/flash-sales/active` | mọi người | Chương trình đang chạy và sắp chạy trong 7 ngày tới, kèm sản phẩm. `remaining` lấy từ Redis |
| `GET /api/flash-sales/{flashSaleId}/stock` | mọi người | `{ serverTime, items: [{ itemId, remaining }] }`, chỉ đọc Redis, để frontend hỏi lại mỗi 2 s |
| `POST /api/flash-sales/{flashSaleId}/items/{itemId}/reservations` | `CUSTOMER` | Body `{ "quantity": 1 }`. `201` khi tạo mới, `200` khi trả lại reservation đang chờ |
| `GET /api/flash-sales/reservations/{reservationId}` | `CUSTOMER` | Cho trang đặt đơn đọc lại; không phải chủ thì `404` |
| `DELETE /api/flash-sales/reservations/{reservationId}` | `CUSTOMER` | Hủy giữ suất, chỉ khi `PENDING`; `204` |
| `POST /api/flash-sales/reservations/{reservationId}/checkout` | `CUSTOMER` | Đặt đơn flash sale (mục "Luồng mua flash sale", bước 2) |

```json
{
  "serverTime": "2026-10-24T20:00:00+07:00",
  "sales": [{
    "flashSaleId": 9101, "name": "Flash sale cuối tuần", "phase": "LIVE",
    "startAt": "2026-10-01T09:00:00+07:00", "endAt": "2027-12-31T23:59:59+07:00",
    "maxQuantityPerUser": 1, "reservationTimeoutSeconds": 60,
    "items": [{ "itemId": 9101, "productId": 9101, "productName": "...", "thumbnailUrl": "...",
                "basePrice": 1000000, "salePrice": 499000, "allocated": 10, "remaining": 3 }]
  }]
}
```

`phase`: `UPCOMING` (chưa tới `startAt`) hoặc `LIVE`. Reservation trả về:

```json
{ "reservationId": "3f1c...", "flashSaleId": 9101, "itemId": 9101, "productId": 9101, "quantity": 1,
  "salePrice": 499000, "status": "PENDING", "expiresAt": "2026-10-24T20:01:00+07:00", "serverTime": "2026-10-24T20:00:00+07:00" }
```

### Admin: flash sale

| Method, path | Mô tả |
|---|---|
| `GET /api/admin/flash-sales?page=&size=&status=` | Danh sách kèm `itemCount`, `allocatedTotal`, `soldTotal` |
| `GET /api/admin/flash-sales/{id}` | Chi tiết: thông tin, policy, sản phẩm, `version` |
| `POST /api/admin/flash-sales` | Tạo `DRAFT`: `{ name, description, startAt, endAt, config: { maxQuantityPerUser, reservationTimeoutSeconds, allowCancel } }` |
| `PUT /api/admin/flash-sales/{id}` | Sửa theo quy tắc ở mục "Vòng đời"; body có `version` |
| `PUT /api/admin/flash-sales/{id}/items` | Chỉ `DRAFT`. Thay toàn bộ danh sách `[{ productId, salePrice, allocatedQuantity }]`. Kiểm tra: `salePrice < basePrice`, `allocatedQuantity > 0` và không vượt `on_hand - reserved`, sản phẩm `ACTIVE`, không trùng |
| `DELETE /api/admin/flash-sales/{id}` | Chỉ `DRAFT` |
| `POST /api/admin/flash-sales/{id}/schedule`, `/unschedule`, `/cancel`, `/stop` | Đổi trạng thái |
| `GET /api/admin/flash-sales/eligible-products?q=&page=&size=` | Sản phẩm `ACTIVE` chưa nằm trong chương trình `SCHEDULED`/`ACTIVE` nào trùng giờ (`NOT EXISTS`) |
| `GET /api/admin/flash-sales/{id}/runtime` | Đối chiếu từng sản phẩm: `allocated`, `sold` (Oracle), `remaining`, số reservation đang chờ (Redis), và các dòng lệch của `v_inventory_invariant_check` |
| `POST /api/admin/flash-sales/{id}/runtime/rebuild` | Dựng lại key runtime từ Oracle |

### Admin: Concurrency Lab

| Method, path | Mô tả |
|---|---|
| `POST /api/admin/concurrency-lab/runs` | `{ strategies: [...], buyers: 1000, quota: 100, concurrency: 64, runs: 5, warmup: 1 }`, trường nào bỏ trống thì lấy giá trị này (`strategies` trống là cả 10). Giới hạn `buyers` 1–10000, `quota` 1–`buyers`, `concurrency` 1–256, `runs` 1–10, `warmup` 0–3. Chạy tuần tự từng chiến lược, đồng bộ; đang có lần khác chạy thì `409 LAB_RUN_IN_PROGRESS`. Trả `{ config: { buyers, quota, concurrency, runs, warmup, oraclePoolSize, redisPoolSize, maxRetries }, results: [{ strategy, store, summary: { <số đo>: { mean, sd } }, runs: [<số đo từng lần>] }] }`. (P2, chưa làm) `Accept: text/csv` thì trả mỗi lần chạy một dòng |
| `POST /api/admin/concurrency-lab/attempt?strategy=` | (P2, chưa làm) Một lượt mua, để k6 bắn qua HTTP (token `ADMIN`): `{ success, retries, elapsedMs }` |
| `POST /api/admin/concurrency-lab/reset` | `{ quota }`: đặt lại bộ đếm Oracle và Redis |
| `GET /api/admin/concurrency-lab/state` | `{ oracle: { allocated, sold, version }, redis: { allocated, remaining, version }, running }`; bên nào chưa reset lần nào thì `null` |

### Lỗi

| HTTP | `code` |
|---|---|
| 400 | `VALIDATION_ERROR` |
| 401 | `AUTH_REQUIRED` (do `common/security` trả) |
| 403 | `ACCESS_DENIED` (do `common/security` trả) |
| 404 | `FLASH_SALE_NOT_FOUND`, `FLASH_SALE_ITEM_NOT_FOUND`, `RESERVATION_NOT_FOUND`, `PRODUCT_NOT_FOUND` |
| 409 | `SALE_NOT_STARTED`, `SALE_ENDED`, `ITEM_UNAVAILABLE`, `SOLD_OUT`, `USER_LIMIT_REACHED`, `RESERVATION_EXPIRED`, `RESERVATION_ALREADY_USED`, `INSUFFICIENT_STOCK`, `INVALID_STATUS_TRANSITION`, `FLASH_SALE_CHANGED`, `PRODUCT_UNAVAILABLE`, `LAB_RUN_IN_PROGRESS` |
| 503 | `REDIS_UNAVAILABLE`, `LAB_INTERRUPTED` (backend dừng giữa lần chạy Lab) |

## Loại truy vấn module thể hiện

| Loại | Ở đâu |
|---|---|
| a. Insert | Tạo chương trình (`flash_sales`, `flash_sale_configs`), thêm sản phẩm vào chương trình, tạo đơn flash (`orders`, `order_items`) |
| b. Delete | Xóa chương trình `DRAFT`; thay danh sách sản phẩm của chương trình `DRAFT` |
| c. Update | Sửa chương trình, đổi trạng thái (compare-and-set), tăng `sold_quantity`, giữ/trả `reserved_flash_quantity` |
| d. Một điều kiện | Chương trình theo `flash_sale_id`; đơn theo `idempotency_key` khi dọn reservation |
| e. Nhiều điều kiện | Chương trình đang chạy: `status IN (...) AND start_at <= :now AND end_at > :now` |
| f. Join | `flash_sale_items ⋈ products ⋈ product_inventory` |
| g. Subquery | `eligible-products` (`NOT EXISTS`) |
| h. Aggregate | Tổng suất đã phân bổ và đã bán theo chương trình; dựng lại `user_qty` bằng `SUM(order_items.quantity) GROUP BY user_id` |

## Phụ thuộc

- **Module `transaction` và `common/security` (TV4)**:
  - Thêm quy tắc cho `/api/flash-sales/**` vào `SecurityConfig` (mục "Quyền").
  - Đồng ý để module này tự tạo đơn `FLASH_SALE`. F8 hiển thị đơn này và giữ `409 ORDER_NOT_CANCELLABLE` khi khách hủy.
  - Xác nhận thứ tự khóa ở mục "Thứ tự khóa".
  - Chia thí nghiệm giữa báo cáo 3.4 và 3.5 (mục "Concurrency Lab").
- **Module `storage` (TV1)**: không cho chuyển sản phẩm sang `INACTIVE` khi sản phẩm đang có trong chương trình `SCHEDULED`/`ACTIVE` (`409`). Storage đã chặn `on_hand_quantity < reserved_flash_quantity`, khớp với module này.

## Giao diện

- `/flash-sale`, `/flash-sale/checkout/:reservationId`, `/admin/flash-sales`, `/admin/flash-sales/lab`: xem `frontend/src/modules/concurrency/AGENTS.md`.
- `/admin/flash-sales/lab` (Concurrency Lab) nằm trong nhóm menu "Flash sale", `order: 2`. Không sửa `/admin/reports/lab` của module `query`.
- `/cart` thuộc module `transaction`.
