# Sửa khung DB (nhánh `fix/db-foundation`)

Tài liệu này giải thích các thay đổi so với PR #1 (`feat: setup database foundation`): sửa gì, vì sao, và mỗi người cần làm gì sau khi merge.

> **Việc cả nhóm phải làm sau khi merge:** `docker compose down --volumes`, kéo image mới, rồi dựng lại (mục 5). V1 được viết lại nên DB cũ không dùng tiếp được.

## 1. Tóm tắt

| # | Vấn đề ở PR #1 | Đã sửa |
|---|---|---|
| 1 | Seed tự mâu thuẫn: đơn flash sale tạo trước ngày mở sale, `reserved` không khớp số đã bán | Seed viết lại theo kịch bản "sale đã kết thúc", qua mọi ràng buộc và bất biến |
| 2 | 5 khóa ngoại không có index | Thêm 5 index |
| 3 | V1 tự tạo tablespace/user, Flyway chạy bằng SYSTEM | User `SHOP` do script khởi tạo container tạo; Flyway chạy bằng `SHOP` |
| 4 | Redis tính sai doanh thu theo danh mục | Thêm projection theo dòng hàng `order:line:*` và index `idx:order-line-analytics` |
| 5 | `ORDERS`, `ORDER_ITEMS` thiếu CHECK cho tiền và trạng thái hủy | Thêm 5 CHECK |
| 6 | Image dùng tag `:latest` | Ghim theo digest |
| 7 | Tài khoản admin nằm trong migration chính | Seed tách ra, chỉ chạy ở profile `dev` |
| 8 | Tài liệu mâu thuẫn (giỏ hàng ở Redis hay Oracle) | `DATABASE_DESIGN.md` là nguồn chính, sửa `AGENTS.md` theo |
| N1 | README không có bước `docker pull` dù compose đặt `pull_policy: never` | Thêm bước pull theo digest |
| N2 | Email phân biệt hoa thường: `A@x.com` và `a@x.com` thành 2 tài khoản | CHECK `email = LOWER(email)` |
| N3 | Bản Oracle lite không có `expdp`, `impdp`, `rman`, `sqlldr` | Đổi sang image Oracle Free bản đầy đủ |
| N4 | Bản lite không chạy `scripts/setup`, chỉ chạy `scripts/startup` sau khi DB mở | Script khởi tạo đặt ở `startup`, chạy lại nhiều lần vẫn an toàn; healthcheck chờ tới khi `SHOP` đăng nhập được |
| N5 | Máy Windows bật `core.autocrlf` sẽ checkout file `.sh` với CRLF, bash trong container lỗi | Thêm `.gitattributes` ép LF cho `.sh`, `.sql`, `.lua` |
| N6 | Tài liệu ghi Oracle "23ai" nhưng tag `latest-lite` hiện kéo về 26ai (23.26.3) | Ghi đúng phiên bản trong `db/README.md` |
| N7 | Tách seed sang profile `dev` làm Flyway từ chối khởi động khi quay về profile mặc định | `ignore-migration-patterns=*:future,repeatable:missing` |

N1–N7 là vấn đề mới, tìm ra khi chạy thử thật, không có trong review đọc code ban đầu.

## 2. Bằng chứng trước khi sửa

Chạy V1 cũ trên image `latest-lite` (Oracle AI Database 26ai Free 23.26.3) và Redis Stack 7.4.7:

| Kiểm tra | Kết quả trên V1 cũ |
|---|---|
| Câu SQL tìm khóa ngoại thiếu index | 5 dòng: `ORDER_ITEMS.ORDER_ID`, `ORDER_ITEMS.PRODUCT_ID`, `ORDER_ITEMS.FLASH_SALE_ITEM_ID`, `CART_ITEMS.PRODUCT_ID`, `FLASH_SALE_ITEMS.PRODUCT_ID` |
| Chèn đơn `CANCELLED`, `subtotal 100`, `discount 500`, `total −999`, không có `cancelled_at` | Oracle nhận |
| Chèn `Customer@ShopFlash.local` khi đã có `customer@shopflash.local` | Oracle nhận |
| Seed | Sale `SCHEDULED` mở 2026-12-01, nhưng đơn flash tạo 2026-10-01; `allocated 5`, `sold 1`, `reserved 5` |
| Redis: một đơn 2 dòng hàng (danh mục 1 = 100.000, danh mục 2 = 50.000), `GROUPBY @itemCategoryId REDUCE SUM @itemLineTotal` | Chỉ trả danh mục 1 = 100.000. Danh mục 2 mất hẳn |
| `$ORACLE_HOME/bin` của bản lite | Chỉ có `extproc lsnrctl oracle orapwd sqlplus tnslsnr` |
| Mount script vào `/opt/oracle/scripts/setup` của bản lite | Không chạy |

## 3. Chi tiết từng thay đổi và lý do

### 3.1. Viết lại V1 thay vì thêm V2

Muốn sửa #3 thì bảng lịch sử Flyway phải chuyển từ `SYSTEM` sang `SHOP`. Với DB đã chạy V1 cũ, Flyway sẽ thấy schema `SHOP` có bảng mà không có lịch sử, nên chỉ có hai đường: báo lỗi, hoặc baseline (nhận đại một version, che mất trạng thái thật). Nghĩa là dù dùng V2 thì vẫn phải reset. Đằng nào cũng reset, nên viết lại cho sạch luôn.

Lúc này reset gần như không mất gì: DB chỉ có 2 đơn mẫu và chưa module nào có migration riêng.

### 3.2. User `SHOP` tạo ngoài Flyway

File: `backend/src/main/resources/db/oracle-init/01_create_shop_schema.sh`, mount vào `/opt/oracle/scripts/startup`.

- **Vì sao tách khỏi Flyway:**
  - DDL trong Oracle tự commit, không rollback được. V1 cũ lỗi giữa chừng thì tablespace và user đã được tạo thật, chạy lại sẽ chết ngay câu đầu.
  - Backend không còn giữ mật khẩu SYSTEM.
  - Lịch sử Flyway nằm trong `SHOP`, nên khi export schema `SHOP` (chủ đề Backup) thì version của DB đi kèm theo.
- **Vì sao dùng `startup`:** image Oracle chạy thư mục này mỗi lần container khởi động. Vì vậy script phải kiểm tra "đã có thì bỏ qua". Mỗi lần chạy, script cũng đặt lại mật khẩu `SHOP` theo `.env`.
- **Datafile:** đặt cạnh datafile SYSTEM của PDB, đường dẫn đọc từ `dba_data_files`, không ghi cứng cho một image cụ thể.
- **Quyền cấp thêm:**
  - `CREATE MATERIALIZED VIEW`.
  - `SELECT_CATALOG_ROLE`: để Query dùng `DBMS_XPLAN.DISPLAY_CURSOR`.
  - `READ, WRITE ON DIRECTORY DATA_PUMP_DIR`: cho Backup.
- **Healthcheck:** đổi thành "đăng nhập được bằng `SHOP`". Script startup chạy sau khi DB đã mở, nên nếu chỉ kiểm tra DB mở thì `docker compose up --wait` có thể trả về trước khi có user, Flyway chạy ngay sẽ gặp `ORA-01017`.

`application.properties`:
- Flyway dùng `ORACLE_SCHEMA` / `ORACLE_APP_PASSWORD`, `default-schema=SHOP`.
- Đã bỏ `baseline-on-migrate`, vì khi trỏ nhầm DB nó lặng lẽ baseline thay vì báo lỗi.
- Thêm `ignore-migration-patterns=*:future,repeatable:missing`. Lỗi này tìm ra khi chạy thử: DB đã chạy profile `dev` thì lịch sử có seed `R__`, quay về profile mặc định thì Flyway không thấy file đó và từ chối khởi động. Giờ chỉ bỏ qua migration **repeatable** bị thiếu; migration **versioned** bị thiếu vẫn báo lỗi như cũ.

Đổi mật khẩu `SHOP`: sửa `ORACLE_APP_PASSWORD` trong `.env`, rồi chạy `docker compose up -d --wait`. Biến đã `export` trong shell sẽ đè lên `.env`, với cả Compose lẫn Spring, nên đừng export.

### 3.3. Schema: `V1__schema.sql`

Bảng và cột giữ nguyên như V1 cũ. Thêm:

| Thêm | Lý do |
|---|---|
| `ix_order_items_order`, `ix_order_items_product`, `ix_order_items_fs_item`, `ix_cart_items_product`, `ix_fs_items_product` | Khóa ngoại không có index thì xóa/sửa khóa ở bảng cha sẽ khóa **cả bảng con** (admin xóa sản phẩm sẽ chặn mọi đơn đang tạo), và join phải quét toàn bảng. Ai muốn demo "trước khi có index" thì `DROP` rồi `CREATE` trong script demo |
| `ck_orders_subtotal`, `ck_orders_discount`, `ck_orders_total` | Đơn có tiền sai sẽ làm sai mọi báo cáo doanh thu. Đây là ràng buộc trên một dòng, nên đặt ở DB thì chặn được mọi đường ghi: code của 5 module, seed, SQL gõ tay. `NUMBER(15,2)` là số thập phân chính xác nên so sánh bằng được |
| `ck_orders_cancelled` | `cancelled_at` có giá trị khi và chỉ khi `status = 'CANCELLED'` |
| `ck_order_items_line` | `line_total = unit_price * quantity` |
| `ck_users_email_lower` | Ép email về chữ thường ngay lúc ghi, nên `uk_users_email` vẫn dùng index sẵn có cho `WHERE email = :e`. **Module đăng nhập/đăng ký phải hạ chữ thường email trước khi ghi và khi tìm** |
| View `v_inventory_invariant_check` | Bất biến `reserved_flash_quantity = Σ(allocated − sold)` trải trên 3 bảng, CHECK không ép được. View liệt kê sản phẩm đang lệch, kết quả đúng là không có dòng nào |

Không dùng trigger cho bất biến tồn kho: trigger trên `FLASH_SALE_ITEMS` sẽ khóa thêm dòng đúng lúc tranh mua, lại dễ gặp lỗi mutating table. Module `concurrency` giữ bất biến trong transaction của mình (ghi ở `DATABASE_DESIGN.md` §14).

### 3.4. Seed: `dev-seed/R__00_core_seed.sql`

- **Chỉ chạy ở profile `dev`**, để DB dùng đo benchmark không lẫn dữ liệu giả.
- **Dùng `R__` (repeatable migration) thay vì `V1000__`:** Flyway bắt version phải tăng dần. `V1000` đã chạy rồi mà module sau thêm `V2` thì Flyway báo lỗi. Repeatable migration luôn chạy sau mọi version, và chạy lại khi file đổi. Seed viết bằng `MERGE` nên chạy lại vẫn an toàn.
- **Kịch bản "sale đã kết thúc":** Flash Sale #1 `ENDED` (2026-10-01 09:00–10:00 +07), `allocated 5`, `sold 1`, `reserved_flash_quantity 0`, đơn flash tạo lúc 09:30. Chọn sale đã kết thúc vì kịch bản này không phụ thuộc ngày chạy. Một sale `ACTIVE` hay `SCHEDULED` với ngày cố định sẽ tự thành vô lý khi ngày đó trôi qua.

### 3.5. Redis: `redis/init.lua`

- **Thêm `idx:order-line-analytics`** trên prefix `order:line:`, mỗi Order Item là một document. Lý do: RediSearch gom nhóm trường nhiều giá trị (`$.items[*]`) chỉ theo giá trị đầu tiên.
- **Prefix là `order:line:`, không phải `order:analytics:item:`:** mọi key bắt đầu bằng `order:analytics:` đều bị index đơn bắt, document dòng hàng sẽ làm sai số đơn.
- **`idx:order-analytics` bỏ `itemCategoryId`, `itemQuantity`, `itemLineTotal`**, để không ai gom nhóm sai nữa. Giữ `itemProductId` vì **lọc** "đơn có chứa sản phẩm X" vẫn đúng.
- **Máy còn index cũ** thì `init.lua` báo lỗi kèm lệnh sửa (`FT.DROPINDEX`, không mất document), chứ không tự xóa.

### 3.6. Image

| | Trước | Sau |
|---|---|---|
| Oracle | `database/free:latest-lite` | `database/free@<digest>` bản đầy đủ |
| Redis | `redis-stack-server:latest` | `redis-stack-server@<digest>` |

- **Vì sao ghim digest:** PR #1 ghi đã kiểm thử trên "23ai", nhưng 11 ngày sau, kéo cùng tag thì ra 26ai. Phần so sánh trong báo cáo đặt số liệu của 6 chủ đề cạnh nhau, nên cả nhóm phải đo trên cùng một bản.
- **Vì sao bỏ bản lite:** bản lite thiếu công cụ cho chủ đề Backup. Bản đầy đủ còn có Oracle Text (`CTXSYS`) cho tìm kiếm toàn văn. Engine giống nhau (cùng 23.26.3) nên số đo của các chủ đề khác không bị ảnh hưởng.
- **Cái giá:**

  | | Lite | Đầy đủ |
  |---|---|---|
  | Tải về | 0,9 GB | 3,7 GB |
  | Trên ổ đĩa | 1,8 GB | 10 GB |
  | Lần đầu `up --wait` trên volume trống | 1 phút 23 giây | 1 phút 53 giây |

Digest và phiên bản cụ thể ghi trong `db/README.md`.

### 3.7. Tài liệu

| File | Sửa |
|---|---|
| `AGENTS.md` gốc | Redis không giữ giỏ hàng, session hay bảng xếp hạng. Giỏ hàng nằm ở Oracle, khớp với schema và `DATABASE_DESIGN.md` |
| `db/DATABASE_DESIGN.md` | Từ "đề xuất" thành "thiết kế nền chung, module được bổ sung"; thêm §2.2 (ràng buộc và index chung), §14 (người chịu trách nhiệm bất biến), §27.1 và §28.1 (projection theo dòng hàng) |
| `db/README.md`, `README.md` | Bảng phiên bản, bước pull, quy tắc mật khẩu, profile `dev`, hướng dẫn xử lý index Redis cũ |
| `db/AGENTS.md`, `backend/AGENTS.md` | Quy ước migration `V<n>__<module>_<mô tả>.sql`, seed module `R__10_<module>_seed.sql` |
| `.env.example` | Mật khẩu mẫu theo quy tắc mới (chữ, số, `_`), ghi chú biến nào ai dùng |

## 4. Kết quả kiểm thử sau khi sửa

Trước khi mở PR, một agent review độc lập đã đọc toàn bộ thay đổi. Kết luận: không có lỗi Critical; có 2 mục Important và vài mục nhỏ, đều đã sửa:

- **Thứ tự seed:** Flyway chạy repeatable theo thứ tự chữ cái, nên seed module (`R__backup_seed`…) sẽ chạy trước seed lõi và lỗi khóa ngoại. Đổi thành `R__00_core_seed.sql`, seed module là `R__10_<module>_seed.sql`.
- **Port:** chỉ bind `127.0.0.1`. Mật khẩu mẫu trong `.env.example` giờ đã hợp lệ, nên ai không đổi `.env` thì DB sẽ lộ ra mạng wifi chung.
- **Mật khẩu:**
  - Healthcheck Oracle đưa mật khẩu qua stdin; healthcheck Redis dùng `REDISCLI_AUTH`. Không còn mật khẩu trên dòng lệnh.
  - Script tạo user với mật khẩu ngẫu nhiên tạm, rồi đặt mật khẩu thật bằng `ALTER USER` ở cấp cao nhất, nên Oracle che nó trong `V$SQL`.
- **View `v_inventory_invariant_check`:** dùng FULL JOIN để bắt cả sản phẩm có trong sale mà thiếu dòng tồn kho.
- **Tài liệu:** đường dẫn đầy đủ, quy ước đặt tên thống nhất, ghi chú khi `--wait` bị treo, ghi rõ `ORACLE_DATABASE` / `ORACLE_SCHEMA` là cố định.

Chạy ngày 18/10/2026 trên Windows 11, Docker 29.5.2, JDK 21. Mọi bước bắt đầu từ volume trống (`docker compose down --volumes`). Tất cả đều đạt.

| # | Kiểm tra | Kết quả |
|---|---|---|
| T1 | `docker compose up -d --wait` từ volume trống | Healthy sau 83–113 giây (bản đầy đủ); log có `shopflash: user SHOP ready in FREEPDB1` |
| T2 | Tablespace mặc định của `SHOP` | `SHOP_DATA` |
| T3 | Backend ở profile mặc định | Flyway áp V1 bằng `SHOP`; 11 bảng; `users` = 0 dòng (không có seed) |
| T4 | Backend ở profile `dev` | Áp thêm seed lõi: 2 user, 1 sản phẩm, 2 đơn |
| T5 | Chạy lại backend, chuyển qua lại `dev` và mặc định | `Schema "SHOP" is up to date`, không lỗi (sau khi thêm `ignore-migration-patterns`) |
| T6 | `flyway_schema_history` | Nằm trong schema `SHOP` |
| T7 | Câu SQL tìm khóa ngoại thiếu index | Không có dòng nào (trước đây 5 dòng) |
| T8 | Đơn có `total` sai / `discount` lớn hơn `subtotal` | `ORA-02290 CK_ORDERS_TOTAL` / `CK_ORDERS_DISCOUNT` |
| T9 | Hủy đơn không set `cancelled_at`; sửa `line_total` sai | `ORA-02290 CK_ORDERS_CANCELLED` / `CK_ORDER_ITEMS_LINE` |
| T10 | Email có chữ hoa | `ORA-02290 CK_USERS_EMAIL_LOWER` |
| T10b | Đơn hợp lệ (`100 − 20 = 80`) | Ghi được |
| T11 | `v_inventory_invariant_check` trên seed | Không có dòng nào |
| T11b | Sản phẩm nằm trong sale `ACTIVE` nhưng không có dòng `PRODUCT_INVENTORY` | View trả 1 dòng (`reserved = NULL`, `expected = 2`) |
| T12 | `init.lua` chạy 2 lần | `Redis indexes are ready` cả 2 lần |
| T13 | Ca thử doanh thu theo danh mục | Danh mục 1 = 100.000, danh mục 2 = 50.000 (trước đây chỉ ra danh mục 1) |
| T14 | Doc `order:line:*` có lọt vào `idx:order-analytics` không | Không: index đơn 1 doc, index dòng hàng 2 doc |
| T14b | Máy còn `idx:order-analytics` kiểu cũ | `init.lua` báo lỗi kèm lệnh `FT.DROPINDEX` |
| T15 | Cấu hình backend có tham chiếu `ORACLE_ADMIN_PASSWORD` | Không còn |
| T16 | `docker compose config --images` | Cả 2 image ở dạng `@sha256:` |
| T17 | Restart container Oracle nhiều lần | Healthy, script không tạo trùng, `SHOP` vẫn đăng nhập được |
| T18 | Đổi `ORACLE_APP_PASSWORD` trong `.env` rồi `up -d --wait` | Mật khẩu mới đăng nhập được, mật khẩu cũ bị `ORA-01017`, backend chạy bình thường |
| T19 | Volume trống rồi chạy backend ngay sau `up --wait` | Flyway áp V1 và seed, không gặp `ORA-01017` |
| T21 | Port của Oracle và Redis | Chỉ bind `127.0.0.1` |
| T22 | Mật khẩu `SHOP` có xuất hiện trong `V$SQL` không | Không (0 câu lệnh) |
| T20 | Công cụ trong image đầy đủ | Có `expdp`, `impdp`, `rman`, `sqlldr`; có Oracle Text; `SHOP` có `SELECT_CATALOG_ROLE` và `READ, WRITE` trên `DATA_PUMP_DIR` |

## 5. Mỗi người cần làm sau khi merge

```bash
git pull
docker compose down --volumes
# kéo 2 image theo digest: lệnh trong db/README.md, mục Setup
# sửa mật khẩu trong .env theo quy tắc mới nếu đang dùng ký tự khác chữ, số, _
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Lưu ý khi viết code:

- **Email:** hạ chữ thường trước khi ghi và khi tìm.
- **Tạo đơn:** `total_amount = subtotal − discount_amount`, `line_total = unit_price × quantity`. Hủy đơn thì set `cancelled_at`.
- **Migration của module:** `V<n>__<module>_<mô tả>.sql`, lấy số kế tiếp lúc mở PR. Seed của module đặt ở `dev-seed/R__10_<module>_seed.sql`; số `00`/`10` giữ thứ tự chạy, vì Flyway chạy repeatable theo thứ tự chữ cái.
- **Thống kê theo sản phẩm/danh mục trên Redis:** dùng `idx:order-line-analytics`, không gom nhóm trên `items[*]`.

## 6. Quyết định đã chốt (18/10/2026)

| Quyết định | Ảnh hưởng tới |
|---|---|
| Viết lại V1, cả nhóm reset volume một lần | Mọi người (mục 5) |
| Giỏ hàng nằm ở Oracle (`carts`, `cart_items`), không dùng Redis | TV5 (F10), TV4 (checkout đọc giỏ trong cùng transaction). TV1 muốn demo key hết hạn cho Storage thì dùng key khác, không dùng giỏ hàng |
| Module `concurrency` giữ bất biến `reserved_flash_quantity` trong transaction của mình, không dùng trigger; suất được giữ khi Flash Sale `ACTIVE` | TV5 (F9, F11). Muốn giữ suất từ `SCHEDULED` thì sửa view `v_inventory_invariant_check` bằng migration của module |
| Dùng image Oracle Free bản đầy đủ, ghim digest (tải 3,7 GB, chiếm 10 GB) | Mọi người; TV1 dùng `expdp`/`impdp`/`rman` cho Backup |
| `DATABASE_DESIGN.md` là thiết kế nền chung, module bổ sung bằng migration riêng | Mọi người |
