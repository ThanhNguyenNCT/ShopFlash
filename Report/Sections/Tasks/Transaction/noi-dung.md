<!-- Bản Markdown hiện hành cho mục 2.4. Mục 2.4.1 dùng bản đầy đủ
3.1 trong Bao_cao_Transaction_Muc_3_1-3_3_MD/; mục 2.4.2 dùng bản 3.2 cập nhật, mục 2.4.3 giữ bản rút gọn
Bao_cao_Transaction_3_1-3_3_Rut_gon/. Chỉ chuẩn hóa heading, caption và liên kết.
Các nguồn local không là dependency của bản này.
LaTeX/PDF trong Report/ đã đồng bộ cấu trúc ba phần và số mục Transaction 2.4.
-->

<a id="transaction-2-4"></a>

## 2.4. Transaction

<a id="tx-section-2-4-1"></a>

### 2.4.1. Tổng quan và phương pháp nghiên cứu

<a id="tx-section-2-4-1-1"></a>

#### 2.4.1.1. Mục tiêu và phạm vi nghiên cứu

Transaction là một trong những cơ chế quan trọng của hệ quản trị cơ sở dữ liệu (DBMS), cho phép tổ chức và kiểm soát nhiều thao tác dữ liệu trong một đơn vị xử lý logic. Việc bảo đảm tính đúng đắn của transaction không chỉ liên quan đến kết quả khi thực thi thành công mà còn bao gồm hành vi khi phát sinh lỗi, khả năng duy trì các ràng buộc dữ liệu và cách xử lý những thao tác truy cập đồng thời.

Trong phần này, nhóm nghiên cứu và đối chiếu cơ chế Transaction của **Oracle Database** và **Redis**, hai hệ quản trị có mô hình lưu trữ và phương thức xử lý dữ liệu khác nhau. Nội dung nghiên cứu tập trung vào bốn tính chất ACID:

- **Atomicity (Tính nguyên tử):** Khả năng xác nhận hoặc hoàn tác các thao tác thuộc một transaction.
- **Consistency (Tính nhất quán):** Khả năng duy trì các ràng buộc và trạng thái dữ liệu hợp lệ.
- **Isolation (Tính cô lập):** Cách các transaction và client tương tác khi truy cập dữ liệu đồng thời.
- **Durability (Tính bền vững):** Khả năng duy trì những thay đổi đã được xác nhận.

Đối với mỗi tính chất, nhóm tìm hiểu cơ sở lý thuyết, phân tích cơ chế mà Oracle và Redis cung cấp, sau đó xây dựng các thí nghiệm để kiểm chứng hành vi thực tế. Việc so sánh tập trung vào **ngữ nghĩa, phạm vi bảo đảm và giới hạn của từng cơ chế**, thay vì chỉ đối chiếu kết quả thành công hoặc thất bại.

Bên cạnh các thí nghiệm chức năng, nhóm thực hiện benchmark nhằm đánh giá thời gian xử lý và thông lượng của hai cách triển khai transaction dưới những mức tải xác định. Các kết quả được sử dụng để phân tích ảnh hưởng của số lượng thao tác và mức độ đồng thời đến hiệu năng.

Một số kịch bản được xây dựng dựa trên nghiệp vụ quản lý giỏ hàng, tồn kho và đặt hàng của hệ thống thương mại điện tử ShopFlash. Đây là **ngữ cảnh ứng dụng được lựa chọn để minh họa và kiểm chứng kiến thức DBMS**, không phải đối tượng đánh giá chính của nghiên cứu.

Phạm vi nghiên cứu gồm các thí nghiệm transaction trực tiếp trên Oracle và Redis, phép đo hiệu năng cấp cơ sở dữ liệu và một phép đo bổ trợ ở cấp ứng dụng. Nghiên cứu không triển khai transaction phân tán giữa hai DBMS và không thực hiện kiểm thử phục hồi dữ liệu sau sự cố hệ thống (crash recovery).

<a id="tx-section-2-4-1-2"></a>

#### 2.4.1.2. Môi trường, dữ liệu và phương pháp thực nghiệm

**Môi trường thực nghiệm.** Oracle và Redis được triển khai trong môi trường local thông qua Docker Compose. Nhóm sử dụng SQL/PLSQL và SQL*Plus để thực hiện các thao tác trên Oracle; Redis CLI và Lua script để kiểm chứng các cơ chế của Redis. Những thí nghiệm có nhiều session hoặc client được điều phối bằng Bash/Python nhằm kiểm soát thứ tự thực thi và ghi nhận kết quả.

Các thí nghiệm sử dụng một mô hình dữ liệu thu gọn từ nghiệp vụ đặt hàng. Những đối tượng chính và các trường dữ liệu liên quan được trình bày trong Bảng 2.4.1.

| Đối tượng | Biểu diễn trong Oracle/Redis | Dữ liệu và ý nghĩa |
|---|---|---|
| Product | Bảng `PRODUCTS` / Product key | `product_id` định danh sản phẩm; `status` thể hiện trạng thái kinh doanh |
| Inventory | Bảng `PRODUCT_INVENTORY` / Inventory Hash | `on_hand_quantity` hoặc `on_hand` là tồn kho; `reserved_flash_quantity` hoặc `reserved` là số lượng đã giữ; `version` là phiên bản dữ liệu |
| Cart | Bảng `CARTS`, `CART_ITEMS` / Cart Hash | `cart_id` xác định giỏ hàng; `product_id` và `quantity` xác định sản phẩm cùng số lượng trong giỏ |
| Order | Bảng `ORDERS`, `ORDER_ITEMS` / Order key | Thông tin đơn hàng, sản phẩm được mua, số lượng và trạng thái xử lý |
| Quota | Redis Quota Hash | `limit` là tổng số suất; `remaining` là số suất còn lại; `held` là số suất đã giữ |

*Bảng 2.4.1. Các đối tượng và trường dữ liệu chính trong thí nghiệm.*

Các thí nghiệm chức năng sử dụng bộ dữ liệu khởi tạo (*fixture*) gồm một người dùng, một giỏ hàng với ba sản phẩm P1, P2 và P3. Mỗi sản phẩm có tồn kho ban đầu là 100, lượng tồn đã giữ bằng 0 và số lượng trong giỏ hàng bằng 1. Đối với thí nghiệm quota, giới hạn ban đầu là hai suất (`limit = 2`, `remaining = 2`, `held = 0`).

Các giá trị này được lựa chọn nhằm tạo điều kiện quan sát rõ sự thay đổi dữ liệu và kiểm tra kết quả. Tùy mục tiêu, từng thí nghiệm có thể điều chỉnh fixture hoặc sử dụng mô hình dữ liệu bổ sung; những thay đổi đó sẽ được nêu tại phần thực nghiệm tương ứng.

**Phương pháp kiểm chứng.** Nhóm xây dựng tổng cộng 14 thí nghiệm, được phân thành bốn nhóm:

| Nhóm | Thí nghiệm | Nội dung kiểm chứng |
|---|---|---|
| A | A1–A5 | COMMIT, ROLLBACK, SAVEPOINT, MULTI/EXEC và xử lý lỗi |
| B | B1–B5 | Isolation, locking, deadlock, WATCH và xử lý đồng thời bằng Lua |
| C | C1–C2 | Ràng buộc toàn vẹn và cơ chế bảo vệ Consistency |
| D | D1–D2 | Transaction gồm nhiều thao tác trong nghiệp vụ Checkout |

*Bảng 2.4.2. Phân nhóm các thí nghiệm Transaction.*

Mỗi thí nghiệm được thực hiện theo quy trình: **khởi tạo dữ liệu → ghi nhận trạng thái ban đầu → thực thi kịch bản → thu thập kết quả → đối chiếu và khôi phục dữ liệu**.

Kết quả được kiểm chứng thông qua phản hồi trực tiếp từ DBMS, mã lỗi, trạng thái dữ liệu trước/sau (*snapshot*) và các điều kiện kiểm tra tự động (*assertion*). Một thí nghiệm chỉ được xem là đạt khi kết quả quan sát phù hợp với hành vi dự kiến, không chỉ dựa trên việc chương trình thực thi kết thúc thành công.

**Phương pháp đánh giá hiệu năng.** Nhóm sử dụng hai cấp độ benchmark:

- **Database-level:** Java sử dụng JDBC/Jedis để thực hiện transaction trực tiếp trên Oracle và Redis, đo thời gian thực thi và thông lượng.
- **Application-level:** k6 tạo tải HTTP lên các API nghiệp vụ của ShopFlash, dùng để đánh giá bổ trợ hiệu năng ở cấp ứng dụng.

Các phép đo khảo sát kích thước giỏ hàng và số lượng tác vụ đồng thời ở những mức cấu hình xác định. Những tham số workload, cách thu thập mẫu, công thức tính latency, p50, p95, throughput và kết quả đo được trình bày ở [Mục 2.4.3](#tx-section-2-4-3).

Cách tổ chức này cho phép tách biệt hai mục tiêu: **kiểm chứng cơ chế Transaction ở [Mục 2.4.2](#tx-section-2-4-2)** và **đánh giá chi phí thực thi trong những workload cụ thể ở [Mục 2.4.3](#tx-section-2-4-3)**.

<a id="tx-section-2-4-2"></a>

### 2.4.2. Cơ sở lý thuyết và thực nghiệm các tính chất ACID

Phần này đối chiếu các tính chất ACID trên Oracle và Redis thông qua cơ chế DBMS, mã nguồn và kết quả thực nghiệm. Trọng tâm là **Atomicity**, **Consistency** và Checkout nhiều bước; **Isolation** và **Durability** được trình bày ở mức cần thiết, còn phân tích chuyên sâu thuộc các topic Concurrency Control và Backup & Recovery.

<a id="tx-section-2-4-2-1"></a>

#### 2.4.2.1. Atomicity — Tính nguyên tử

**Cơ sở lý thuyết**

Atomicity quy định rằng các thao tác thuộc một transaction phải được xử lý như một đơn vị logic thống nhất. Trong mô hình transaction hỗ trợ rollback đầy đủ, khi transaction được xác nhận thành công, toàn bộ thay đổi của nó được ghi nhận; nếu transaction bị hủy, các thay đổi chưa commit phải được hoàn tác để dữ liệu trở về trạng thái trước transaction.

Giả sử một transaction gồm ba thao tác:

- Tạo một đơn hàng (*Order*).
- Giảm số lượng tồn kho (*Inventory*).
- Xóa sản phẩm đã mua khỏi giỏ hàng (*Cart*).

Nếu thao tác cuối thất bại, việc chỉ giữ lại hai thao tác đầu có thể khiến trạng thái dữ liệu không còn phù hợp với kết quả nghiệp vụ. Atomicity giải quyết vấn đề này bằng cách kiểm soát phạm vi xác nhận và hoàn tác các thay đổi.

Tuy nhiên, cần phân biệt **khả năng hoàn tác một transaction** với **tính nguyên tử trong quá trình thực thi**. Hai khái niệm này liên quan nhưng không đồng nhất:

- **Khả năng hoàn tác (Rollback capability):** Cho phép hủy các thay đổi chưa được xác nhận, kể cả khi một số câu lệnh trước đó đã thực hiện thành công. Chẳng hạn, Oracle có thể thực hiện ba lệnh UPDATE trong cùng transaction; nếu bước cuối thất bại, ứng dụng gọi `ROLLBACK` để khôi phục cả những thay đổi của hai lệnh đầu.
- **Tính nguyên tử trong thực thi (Atomic execution):** Bảo đảm một thao tác hoặc nhóm lệnh được thực thi như một đơn vị không bị thao tác từ client khác chen ngang. Chẳng hạn, Redis thực thi các command trong `MULTI/EXEC` hoặc một Lua script mà không xử lý command khác xen vào giữa. Tuy nhiên, nếu một lệnh đã ghi dữ liệu thành công rồi lệnh tiếp theo gặp lỗi runtime, Redis không tự hoàn tác các thay đổi trước đó.

Như vậy, một nhóm lệnh có thể được thực thi liên tục, không bị lệnh khác chen ngang, nhưng vẫn không có khả năng khôi phục toàn bộ thay đổi khi phát sinh lỗi. Ngược lại, khả năng rollback của Oracle không có nghĩa mọi transaction phải được thực thi tuần tự; Oracle vẫn cho phép nhiều transaction chạy đồng thời và kiểm soát xung đột thông qua cơ chế isolation và locking.

Sự khác biệt cốt lõi nằm ở câu hỏi được bảo đảm: **Atomic execution bảo vệ quá trình thực thi khỏi sự xen ngang, còn rollback bảo vệ khả năng khôi phục trạng thái dữ liệu khi transaction bị hủy.** Đây là điểm đặc biệt quan trọng khi đối chiếu Oracle với Redis.

**Cơ chế COMMIT và ROLLBACK của Oracle — Thí nghiệm A1**

Oracle quản lý nhiều câu lệnh thay đổi dữ liệu trong cùng một transaction. Hai lệnh điều khiển cơ bản là `COMMIT`, dùng để xác nhận transaction, và `ROLLBACK`, dùng để hủy những thay đổi chưa được commit.

Thí nghiệm A1 sử dụng hai nhánh độc lập. Nhánh thứ nhất thực hiện Checkout cho sản phẩm P1 và kết thúc bằng `COMMIT`; nhánh thứ hai thực hiện thao tác tương tự với P2 nhưng kết thúc bằng `ROLLBACK`.

Đoạn SQL trọng tâm được trích từ nhánh COMMIT:

```sql
UPDATE product_inventory
SET on_hand_quantity = on_hand_quantity - 1,
    version = version + 1,
    updated_at = SYSTIMESTAMP
WHERE product_id = :p1_id
  AND on_hand_quantity - reserved_flash_quantity >= 1;

-- Kiểm tra số dòng cập nhật thành công (lược)

DELETE FROM cart_items
WHERE cart_id = :fixture_cart_id
  AND product_id = :p1_id;

-- Kiểm tra số dòng xóa thành công (lược)

COMMIT;
```

*Mã nguồn 2.4.1. Trích các thao tác cập nhật và xác nhận transaction trong A1.*

Trong đó, `:p1_id` là định danh sản phẩm P1; `:fixture_cart_id` là định danh giỏ hàng; `on_hand_quantity` là tồn kho hiện có; `reserved_flash_quantity` là số lượng đã giữ. Điều kiện trong `WHERE` chỉ cho phép giảm tồn khi lượng khả dụng còn ít nhất một đơn vị. Biến `version` được tăng sau khi cập nhật tồn kho.

Trước đoạn mã trên, transaction còn thực hiện hai lệnh `INSERT` để tạo Order và Order Item. Sau mỗi thao tác thay đổi dữ liệu quan trọng, chương trình kiểm tra `SQL%ROWCOUNT` nhằm xác nhận số dòng bị ảnh hưởng đúng với dự kiến.

Ở nhánh ROLLBACK, nhóm thực hiện cùng chuỗi thao tác với P2 nhưng thay `COMMIT` bằng `ROLLBACK`. Ngay trước rollback, Order mới được tạo vẫn có thể được truy vấn trong chính session đang thực hiện transaction; sau rollback, Order này không còn tồn tại.

Kết quả được quan sát từ một kết nối Oracle độc lập:

| Chỉ tiêu | Trạng thái ban đầu | Sau COMMIT P1 | Sau ROLLBACK P2 |
|---|---:|---:|---:|
| Số Order | 0 | 1 | 1 |
| Số Order Item | 0 | 1 | 1 |
| Số dòng Cart | 3 | 2 | 2 |
| Tồn kho P1 | 100 | 99 | 99 |
| Tồn kho P2 | 100 | 100 | 100 |

*Bảng 2.4.3. Trạng thái dữ liệu sau hai nhánh COMMIT và ROLLBACK của A1.*

Nhánh COMMIT tạo một Order, giảm tồn P1 từ 100 xuống 99 và xóa P1 khỏi Cart. Các thay đổi này vẫn tồn tại khi được truy vấn từ kết nối mới.

Ở nhánh ROLLBACK, transaction đã tạo một Order khác và thay đổi P2, nhưng toàn bộ thay đổi chưa commit của nhánh này bị hủy. Vì vậy, snapshot cuối vẫn chỉ ghi nhận Order đã commit ở nhánh trước, trong khi tồn kho P2 và dòng Cart tương ứng không thay đổi.

Kết quả chứng minh Oracle có thể xác nhận hoặc hoàn tác toàn bộ những thao tác chưa commit thuộc cùng một transaction, thay vì chỉ xác nhận từng câu SQL riêng lẻ.

**Các phạm vi hoàn tác của Oracle — Thí nghiệm A3**

A3 kiểm chứng ba phạm vi hoàn tác của Oracle. `SAVEPOINT` đánh dấu một mốc trong transaction để có thể quay lại mà không hủy các thay đổi trước mốc đó:



- *Statement rollback:* Khi một câu SQL thất bại trong quá trình thực thi, Oracle hoàn tác tác động của chính câu SQL đó. Những câu lệnh thành công trước đó trong transaction không mặc nhiên bị hủy.
- *Rollback to savepoint:* Chỉ hoàn tác các thay đổi được thực hiện sau một savepoint đã xác định.
- *Full rollback:* Hủy toàn bộ thay đổi chưa commit trong transaction hiện tại.

A3 kiểm chứng ba phạm vi này bằng cách lần lượt cập nhật tồn kho P1, P2 và cố tình tạo một lỗi ràng buộc.

Đoạn SQL trọng tâm:

```sql
UPDATE product_inventory
SET on_hand_quantity = 99
WHERE product_id = :p1_id;

SAVEPOINT before_p2;

UPDATE product_inventory
SET on_hand_quantity = 98
WHERE product_id = :p2_id;

UPDATE cart_items
SET quantity = 0
WHERE cart_id = :fixture_cart_id
  AND product_id = :p1_id;
```

*Mã nguồn 2.4.2. Các thao tác chính tạo trạng thái trung gian trong A3; phần cập nhật timestamp, kiểm tra row count và xử lý exception được lược bỏ.*

Câu `UPDATE` gán `quantity = 0` vi phạm constraint `CK_CART_ITEMS_QUANTITY`. Script ghi nhận trạng thái sau lỗi, gọi `ROLLBACK TO before_p2` rồi thực hiện full `ROLLBACK`.

Kết quả SQL*Plus:

```text
STATEMENT_ERROR=-2290 ORA-02290: check constraint
(SHOP.CK_CART_ITEMS_QUANTITY) violated

AFTER_FAILED_STATEMENT P1=99 P2=98 CART_QTY=1
AFTER_ROLLBACK_TO P1=99 P2=100
AFTER_FULL_ROLLBACK P1=100 P2=100
```

*Kết quả 2.4.1. Các trạng thái ghi nhận trong A3, trích từ transcript thực nghiệm.*

Trong đó, `P1` và `P2` là số lượng tồn kho tương ứng; `CART_QTY` là số lượng P1 trong giỏ hàng.

| Thời điểm | Tồn P1 | Tồn P2 | Ý nghĩa |
|---|---:|---:|---|
| Ban đầu | 100 | 100 | Chưa phát sinh cập nhật |
| Sau câu SQL lỗi | 99 | 98 | Chỉ câu UPDATE Cart bị hoàn tác |
| Sau `ROLLBACK TO before_p2` | 99 | 100 | Hoàn tác phần cập nhật P2 |
| Sau full `ROLLBACK` | 100 | 100 | Hoàn tác toàn bộ thay đổi chưa commit |

*Bảng 2.4.4. So sánh ba phạm vi hoàn tác trong Oracle.*

Kết quả đầu tiên cho thấy lỗi `ORA-02290` không tự hủy những câu UPDATE Inventory đã thành công trước đó. Ở mốc thứ hai, P2 được khôi phục về 100 trong khi P1 vẫn bằng 99, phù hợp với vị trí của savepoint. Cuối cùng, full rollback đưa cả hai giá trị trở về trạng thái ban đầu.

Điểm cần nhấn mạnh là **một câu SQL thất bại không đồng nghĩa toàn bộ transaction tự động bị hủy**. Để bảo đảm một nghiệp vụ nhiều bước được hoàn tác đầy đủ, chương trình phải quản lý phạm vi transaction và xử lý lỗi phù hợp.

**Cơ chế MULTI/EXEC và DISCARD của Redis — Thí nghiệm A4**

Với Redis, `MULTI` xếp hàng các command và trả `QUEUED`. `EXEC` thực thi hàng đợi theo thứ tự, còn `DISCARD` hủy các command chưa chạy. A4 đối chiếu hai cách kết thúc trên cùng fixture:

```redis
MULTI
HSET order user_id 10528 status PENDING product_id 12941 quantity 1
HINCRBY inventory:12941 on_hand -1
HDEL cart 12941
EXEC
```

*Mã nguồn 2.4.3. Các Redis command cốt lõi trong nhánh EXEC của A4. Tên key được rút gọn để dễ theo dõi; script thực tế sử dụng prefix `txresearch:native01:`.*

Ở đây, `HSET` tạo các field của Order Hash; `HINCRBY` giảm tồn P1 một đơn vị; `HDEL` xóa field của P1 trong Cart Hash.

Kết quả Redis CLI:

```text
OK
QUEUED
QUEUED
QUEUED
4
99
1
```

*Kết quả 2.4.2. Phản hồi của nhánh MULTI/EXEC trong A4.*

Phản hồi `OK` đầu tiên xác nhận bắt đầu transaction. Ba dòng `QUEUED` cho biết ba command được xếp hàng thành công. Sau `EXEC`, Redis trả kết quả của từng command theo đúng thứ tự:

- `4`: `HSET` thêm bốn field mới vào Order Hash.
- `99`: `HINCRBY` trả tồn kho sau khi giảm từ 100 xuống 99.
- `1`: `HDEL` xóa thành công một field Cart.

Snapshot sau `EXEC` xác nhận Order được tạo, tồn kho P1 bằng 99 và P1 không còn trong Cart.

Ở nhánh đối chứng, nhóm giữ nguyên ba command `HSET`, `HINCRBY`, `HDEL` và chỉ thay **dòng cuối `EXEC` bằng `DISCARD`**:

```redis
DISCARD
```

*Mã nguồn 2.4.4. Lệnh cuối thay thế EXEC trong nhánh DISCARD của A4; các lệnh phía trước giữ nguyên Mã nguồn 2.4.3.*

Kết quả thực tế từ Redis CLI:

```text
OK
QUEUED
QUEUED
QUEUED
OK
```

*Kết quả 2.4.3. Phản hồi của nhánh MULTI/DISCARD trong A4.*

Sau `DISCARD`, Redis chỉ trả `OK` mà không trả kết quả của ba command, vì hàng đợi chưa được thực thi. Đối chiếu snapshot của hai nhánh:

| Dữ liệu kiểm tra | Sau EXEC | Sau DISCARD |
|---|---|---|
| Order | Tồn tại dưới dạng Hash | Không tồn tại (`TYPE=none`) |
| Inventory P1 (`on_hand`) | 99 | 100 |
| Cart P1 | Đã xóa khỏi Cart | Vẫn có Quantity = 1 |

*Bảng 2.4.5. Đối chiếu trạng thái dữ liệu của hai nhánh EXEC và DISCARD trong A4.*

Hai nhánh được thực hiện trên fixture đã được khôi phục về cùng trạng thái ban đầu. Vì vậy, sự khác biệt trong kết quả xuất phát từ lệnh kết thúc transaction: `EXEC` thực thi các command đã xếp hàng, còn `DISCARD` hủy hàng đợi mà không làm thay đổi dữ liệu.

Thí nghiệm chứng minh `MULTI/EXEC` giúp thực hiện một nhóm command theo thứ tự, không bị các command từ client khác chen vào trong quá trình thực thi hàng đợi. Tuy nhiên, **DISCARD chỉ hủy những command chưa được chạy; nó không phải cơ chế hoàn tác các thay đổi đã thực hiện thành công**.

**Giới hạn xử lý lỗi trong Redis — Thí nghiệm A5**

A5 tiếp tục kiểm tra hành vi khi command hoặc Lua script phát sinh lỗi ở những thời điểm khác nhau:

| Tình huống | Thời điểm lỗi | Hành vi được kiểm chứng |
|---|---|---|
| Queue-time error | Trước `EXEC`, khi xếp command | `EXECABORT`, hàng đợi không được thực thi |
| Runtime error | Trong quá trình thực thi `EXEC` | Command lỗi trả lỗi; các command khác vẫn có thể thực hiện |
| Lua runtime error | Sau khi Lua đã thực hiện một số lệnh ghi | Script báo lỗi nhưng không tự hoàn tác các ghi trước đó |

*Bảng 2.4.6. Ba loại lỗi được kiểm chứng trong A5.*

**Trường hợp 1 — Lỗi khi xếp hàng (queue-time error)**

Chương trình cố tình gửi một command không tồn tại, `TXRESEARCH_INVALID_COMMAND`, sau khi đã xếp một lệnh giảm tồn kho hợp lệ:

```redis
MULTI
HINCRBY inventory:12941 on_hand -1
TXRESEARCH_INVALID_COMMAND
EXEC
```

*Mã nguồn 2.4.5. Nhánh tạo queue-time error trong A5. Tên Inventory key được rút gọn; script thực tế sử dụng prefix của fixture.*

Kết quả Redis CLI:

```text
OK
QUEUED
ERR unknown command 'TXRESEARCH_INVALID_COMMAND', with args beginning with:

EXECABORT Transaction discarded because of previous errors.
```

*Kết quả 2.4.4. Phản hồi khi command không hợp lệ được gửi trong giai đoạn xếp hàng của A5.*

Lệnh `HINCRBY` hợp lệ được xếp hàng và trả `QUEUED`, nhưng `TXRESEARCH_INVALID_COMMAND` bị Redis từ chối ngay khi nhận lệnh. Do Redis đã ghi nhận lỗi trong giai đoạn queue, lệnh `EXEC` tiếp theo trả `EXECABORT` và không thực thi cả lệnh giảm tồn kho đã được xếp hàng trước đó.

Snapshot xác nhận Inventory P1 vẫn bằng 100, Cart giữ nguyên ba sản phẩm và Order chưa tồn tại. Như vậy, lỗi queue-time khiến toàn bộ hàng đợi bị hủy trước khi bất kỳ thao tác ghi nào được thực hiện.

**Trường hợp 2 — Lỗi trong quá trình EXEC (runtime error)**

Nhánh runtime sử dụng `LPUSH` lên key `order` đã được tạo dưới dạng Hash:

```redis
MULTI
HINCRBY inventory:12941 on_hand -1
HSET order status PENDING product_id 12941
LPUSH order impossible
HDEL cart 12941
EXEC
```

*Mã nguồn 2.4.6. Nhánh tạo lỗi runtime trong A5; tên key được rút gọn.*

`LPUSH` là command thêm phần tử vào List, trong khi `order` đang là Hash. Khi `EXEC` chạy đến lệnh này, Redis trả lỗi `WRONGTYPE`.

Các phản hồi sau `EXEC`:

```text
99
2
WRONGTYPE Operation against a key holding the wrong kind of value
1
```

*Kết quả 2.4.5. Phản hồi khi một command trong MULTI/EXEC phát sinh lỗi runtime.*

`LPUSH` thất bại vì `order` là Hash, không phải List. Tuy nhiên, các command còn lại vẫn chạy: P1 giảm còn 99, hai field Order được tạo và P1 bị xóa khỏi Cart. Redis **không hoàn tác các ghi thành công** khi một command trong `EXEC` gặp lỗi runtime.

**Trường hợp 3 — Lỗi Lua sau khi đã ghi dữ liệu**

Nhánh Lua tiếp tục kiểm chứng giới hạn tương tự:

```lua
redis.call('HINCRBY', KEYS[1], 'on_hand', -1)
redis.call('HSET', KEYS[2], 'status', 'PENDING')
return redis.call('LPUSH', KEYS[2], 'impossible')
```

*Mã nguồn 2.4.7. Toàn bộ nội dung Lua tạo lỗi sau ghi trong A5.*

`KEYS[1]` là Inventory Hash, còn `KEYS[2]` là Order Hash. Hai lệnh đầu cập nhật thành công trước khi lệnh thứ ba gây lỗi `WRONGTYPE`.

Kết quả thực tế cho thấy Lua trả lỗi ở dòng thứ tư, nhưng snapshot sau đó vẫn ghi nhận tồn kho P1 bằng 99 và Order có trạng thái `PENDING`. Khác với nhánh runtime của `EXEC`, Cart không bị thay đổi bởi script Lua này vì nó không có lệnh cập nhật Cart.

Lua trong Redis được thực thi như một đơn vị không bị command khác chen ngang, nhưng cơ chế đó **không cung cấp full rollback khi script đã ghi dữ liệu rồi gặp lỗi runtime**.

**Đối chiếu kết quả Atomicity**

**Oracle**

A1 và A3 cho thấy Oracle hỗ trợ `COMMIT`, `ROLLBACK` và `SAVEPOINT`. Khi một câu SQL lỗi, các lệnh thành công trước đó chưa tự bị hủy; ứng dụng lựa chọn rollback một phần hoặc toàn bộ transaction.

**Redis**

A4 xác nhận `MULTI/EXEC` thực thi hàng đợi và `DISCARD` hủy hàng đợi chưa chạy. A5 cho thấy lỗi runtime trong `EXEC` hoặc Lua có thể để lại thay đổi một phần vì Redis không tự khôi phục những ghi đã hoàn thành.

**Nhận xét chung**

Oracle cung cấp **khả năng hoàn tác nhiều câu SQL**, còn Redis cung cấp **thực thi không bị xen ngang** nhưng thiếu full rollback tương đương cho lỗi runtime sau ghi. Nghiệp vụ Redis nhiều bước vì vậy cần validation trước ghi và xử lý lỗi phù hợp.

<a id="tx-section-2-4-2-2"></a>

#### 2.4.2.2. Consistency — Tính nhất quán

**Cơ sở lý thuyết**

Consistency yêu cầu transaction duy trì dữ liệu trong trạng thái hợp lệ theo tập hợp các quy tắc đã được xác định. Nếu trạng thái trước transaction thỏa mãn các ràng buộc, việc thực hiện transaction thành công phải bảo toàn những ràng buộc cần thiết trong trạng thái sau cùng.

Các quy tắc này có thể được chia thành hai nhóm.

Thứ nhất là **ràng buộc toàn vẹn dữ liệu** (*integrity constraints*), chẳng hạn khóa chính không được trùng, khóa ngoại phải tham chiếu tới bản ghi hợp lệ và số lượng sản phẩm không được âm. Những ràng buộc này có thể được khai báo trực tiếp trong schema quan hệ.

Thứ hai là **bất biến nghiệp vụ** (*business invariants*), tức các điều kiện cần luôn được duy trì trong quá trình thực hiện một nghiệp vụ. Ví dụ, số lượng mua phải phù hợp với giỏ hàng, sản phẩm phải ở trạng thái có thể bán và lượng tồn khả dụng phải đủ để đáp ứng yêu cầu.

Việc bảo đảm Consistency phụ thuộc vào cả DBMS và cách triển khai nghiệp vụ. Hệ quản trị có thể tự động thực thi những ràng buộc đã được khai báo, nhưng không thể tự suy ra tất cả quy tắc kinh doanh nếu các quy tắc đó chưa được biểu diễn trong database hoặc logic ứng dụng.

**Oracle — Bảo vệ dữ liệu bằng Integrity Constraints**

Oracle hỗ trợ `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `CHECK` và `NOT NULL`. DBMS tự từ chối câu SQL vi phạm constraint đang có hiệu lực. Để xác nhận các ràng buộc trong schema, C1 truy vấn hai metadata view:

```sql
SELECT c.table_name,
       c.constraint_name,
       c.constraint_type,
       c.status,
       c.validated,
       c.deferrable,
       c.deferred,
       LISTAGG(cc.column_name, ', ')
         WITHIN GROUP (ORDER BY cc.position) AS columns
FROM user_constraints c
LEFT JOIN user_cons_columns cc
  ON cc.constraint_name = c.constraint_name
```

*Mã nguồn 2.4.8. Trích phần truy vấn metadata trong C1; điều kiện lọc, cột CHECK và GROUP BY được lược bỏ.*

`USER_CONSTRAINTS` cho biết loại và trạng thái ràng buộc; `USER_CONS_COLUMNS` xác định cột liên quan. Các trường `status`, `validated`, `deferrable`, `deferred` thể hiện hiệu lực và thời điểm kiểm tra constraint. Mã `constraint_type` được giải thích như sau:

| Mã | Loại constraint | Ý nghĩa |
|---|---|---|
| `P` | Primary Key | Khóa chính, xác định duy nhất từng bản ghi |
| `R` | Referential Integrity / Foreign Key | Khóa ngoại, bảo đảm giá trị tham chiếu phù hợp với khóa được tham chiếu |
| `U` | Unique | Ràng buộc duy nhất, ngăn các giá trị trùng nhau theo quy tắc của constraint |
| `C` | Check / Not Null | Ràng buộc điều kiện trên giá trị cột; Oracle cũng biểu diễn NOT NULL bằng loại `C` |

*Bảng 2.4.7. Ý nghĩa các mã phân loại constraint trong metadata Oracle.*

C1 ghi nhận **29 constraint** trên các bảng `ORDERS`, `ORDER_ITEMS`, `PRODUCTS` và `PRODUCT_INVENTORY`. Những ràng buộc được phân tích đều ở trạng thái `ENABLED`, `VALIDATED`, `NOT DEFERRABLE`, `IMMEDIATE`. Một số ví dụ:

| Constraint | Loại (mã) | Bảng | Quy tắc được bảo vệ |
|---|---|---|---|
| `PK_ORDERS` | Primary Key (`P`) | `ORDERS` | Mỗi Order có khóa chính duy nhất |
| `FK_ORDER_ITEMS_ORDER` | Foreign Key (`R`) | `ORDER_ITEMS` | Order Item phải tham chiếu tới Order hợp lệ |
| `CK_ORDER_ITEMS_QUANTITY` | Check (`C`) | `ORDER_ITEMS` | Số lượng sản phẩm phải lớn hơn 0 |
| `CK_ORDERS_TOTAL` | Check (`C`) | `ORDERS` | `total_amount = subtotal - discount_amount` |
| `CK_INVENTORY_RES_LE_ON` | Check (`C`) | `PRODUCT_INVENTORY` | Tồn đã giữ không được lớn hơn tồn hiện có |
| `UK_ORDERS_IDEMPOTENCY` | Unique (`U`) | `ORDERS` | Không cho phép trùng giá trị idempotency key theo ràng buộc đã khai báo |

*Bảng 2.4.8. Một số constraint tiêu biểu được xác nhận từ metadata Oracle trong C1.*

C1 kiểm tra metadata; A2 kiểm chứng phản hồi thực tế khi dữ liệu vi phạm constraint.

**Kiểm chứng phản hồi constraint — Thí nghiệm A2**

Ba tình huống được tạo có chủ đích:

| Thao tác | Constraint | Mã lỗi Oracle |
|---|---|---|
| Thêm lại cùng cặp `(cart_id, product_id)` đã có trong `CART_ITEMS` | `UK_CART_ITEMS_CART_PRODUCT` | `ORA-00001` |
| Tạo Order Item tham chiếu tới `order_id = -1`, không tồn tại | `FK_ORDER_ITEMS_ORDER` | `ORA-02291` |
| Cập nhật Quantity của Cart Item thành 0 | `CK_CART_ITEMS_QUANTITY` | `ORA-02290` |

*Bảng 2.4.9. Các vi phạm được tạo có chủ đích và kết quả của A2.*

Đoạn SQL tạo lỗi CHECK constraint:

```sql
UPDATE cart_items
SET quantity = 0
WHERE cart_id = :fixture_cart_id
  AND product_id = :p1_id;
```

*Mã nguồn 2.4.9. Thao tác cố tình vi phạm CHECK constraint trong A2.*

Do Quantity hợp lệ của Cart Item phải lớn hơn 0, Oracle từ chối câu UPDATE. Kết quả SQL*Plus ghi nhận:

```text
UNIQUE SQLCODE=-1 ORA-00001: unique constraint
(SHOP.UK_CART_ITEMS_CART_PRODUCT) violated

FK SQLCODE=-2291 ORA-02291: integrity constraint
(SHOP.FK_ORDER_ITEMS_ORDER) violated

CHECK SQLCODE=-2290 ORA-02290: check constraint
(SHOP.CK_CART_ITEMS_QUANTITY) violated
```

*Kết quả 2.4.6. Ba mã lỗi constraint được ghi nhận trong A2; các phần mô tả bổ trợ của SQL*Plus được lược bỏ.*

Trong đó, `SQLCODE` là mã lỗi dạng số mà PL/SQL ghi nhận, còn mã `ORA-xxxxx` là mã lỗi được Oracle trả về để xác định loại vi phạm.

Cả ba thao tác đều bị từ chối. Snapshot sau khi kết thúc thí nghiệm khớp với trạng thái ban đầu: Cart và Inventory không thay đổi, không xuất hiện Order Item tham chiếu sai.

Từ C1 và A2 có thể kết luận rằng Oracle thực sự bảo vệ những ràng buộc đã khai báo trong schema. Tuy nhiên, constraint không thay thế toàn bộ logic nghiệp vụ. Chẳng hạn, khóa ngoại có thể bảo đảm một Product ID tồn tại nhưng không tự bảo đảm sản phẩm đang ở trạng thái `ACTIVE`; hoặc một CHECK constraint về tồn kho không tự xác nhận yêu cầu mua đáp ứng đúng số lượng còn khả dụng tại thời điểm cập nhật.

Những điều kiện còn lại cần được triển khai trong transaction thông qua validation và thao tác cập nhật phù hợp.

**Redis — Bảo vệ Consistency bằng Guarded Write**

Khác với schema quan hệ của Oracle, các Redis Hash và key độc lập không tự áp dụng FOREIGN KEY hoặc CHECK giữa những đối tượng dữ liệu. Một client có quyền ghi có thể tạo field hoặc cập nhật giá trị mà không phải vượt qua các ràng buộc nghiệp vụ được khai báo tương tự Oracle.

Vì vậy, để bảo vệ tính nhất quán cho một luồng xử lý, ứng dụng phải chủ động kiểm tra dữ liệu và kiểm soát những thao tác ghi được phép thực hiện.

Thí nghiệm C2 tập trung kiểm chứng hướng tiếp cận này bằng cơ chế **guarded write**: yêu cầu Checkout chỉ được phép thay đổi dữ liệu sau khi toàn bộ điều kiện cần thiết đã được xác nhận hợp lệ.

C2 sử dụng Lua script chạy bằng `EVAL`. Một request Checkout NORMAL cho một sản phẩm được biểu diễn bằng bốn key:

| Ký hiệu | Redis key | Ý nghĩa |
|---|---|---|
| `KEYS[1]` | `cart` | Giỏ hàng và Quantity của sản phẩm |
| `KEYS[2]` | `order` | Đơn hàng được tạo nếu Checkout hợp lệ |
| `KEYS[3]` | `product:<id>` | Thông tin định danh và trạng thái sản phẩm |
| `KEYS[4]` | `inventory:<id>` | Tồn kho, lượng đã giữ và version |

*Bảng 2.4.10. Các key được sử dụng trong Lua Checkout của C2; prefix của lần chạy được lược bỏ.*

Ngoài các key trên, script nhận ba đối số: `ARGV[1]` là prefix của bộ dữ liệu, `ARGV[2]` là Product ID và `ARGV[3]` là Quantity được yêu cầu mua.

Cơ chế kiểm tra gồm các nhóm điều kiện chính: key phải có cấu trúc và kiểu dữ liệu phù hợp; Product phải tồn tại và ở trạng thái `ACTIVE`; Inventory key phải thuộc đúng Product ID; Quantity phải là số nguyên dương; tồn kho phải hợp lệ và đủ số lượng; Cart phải chứa Quantity phù hợp; đồng thời Order chưa được tạo trước đó.

Một trong những điều kiện trọng tâm là kiểm tra sự tương ứng giữa Product ID và Inventory key:

```lua
if KEYS[1] ~= prefix..'cart' or KEYS[2] ~= prefix..'order'
   or KEYS[3] ~= prefix..'product:'..id
   or KEYS[4] ~= prefix..'inventory:'..id then
  return reject('PRODUCT_INVENTORY_MISMATCH')
end
```

*Mã nguồn 2.4.10. Kiểm tra tên các key được truyền vào Lua trong C2.*

`prefix` là namespace, `id` là Product ID. Kiểm tra này từ chối yêu cầu trỏ đến Inventory của sản phẩm khác. Sau khi xác nhận kiểu dữ liệu và tồn kho hợp lệ, script kiểm tra tiếp:

```lua
if stock - reserved < quantity then
  return reject('OUT_OF_STOCK')
end

if integer(redis.call('HGET', KEYS[1], id)) ~= quantity then
  return reject('CART_MISMATCH')
end
```

*Mã nguồn 2.4.11. Kiểm tra tồn khả dụng và Quantity trong Cart của C2.*

`stock` là số lượng tồn hiện có, `reserved` là lượng đã giữ và `quantity` là số lượng yêu cầu mua. Hàm `integer()` kiểm tra giá trị đầu vào có đúng biểu diễn số nguyên trong giới hạn cho phép của script hay không.

Nếu tồn khả dụng nhỏ hơn Quantity, script trả `OUT_OF_STOCK`. Nếu Quantity trong Cart không khớp yêu cầu, script trả `CART_MISMATCH`. Các nhánh lỗi tương ứng đều kết thúc **trước lệnh ghi đầu tiên**.

Chỉ khi toàn bộ validation thành công, Lua mới thực hiện:

```lua
redis.call('HSET', KEYS[2],
  'product_id', id,
  'quantity', quantity,
  'status', 'PENDING')

redis.call('HSET', KEYS[4],
  'on_hand', stock - quantity,
  'version', version + 1)

redis.call('HDEL', KEYS[1], id)

return 'OK'
```

*Mã nguồn 2.4.12. Toàn bộ phần ghi dữ liệu sau validation trong Lua Checkout của C2.*

Các lệnh ghi tạo Order, giảm Stock, tăng Version và xóa dòng Cart. Vì validation và ghi đều nằm trong **một lần `EVAL`**, client khác không thể chen vào giữa hai giai đoạn.

**Kết quả kiểm chứng C2**

Thí nghiệm xây dựng 15 test case, gồm 14 trường hợp không hợp lệ và một trường hợp Checkout hợp lệ. Mỗi case được chuẩn bị fixture riêng và có snapshot trước/sau để so sánh.

Các trường hợp không hợp lệ được phân thành những nhóm:

| Nhóm kiểm tra | Ví dụ test case | Kết quả |
|---|---|---|
| Product reference | Product không tồn tại; Inventory key sai sản phẩm | Từ chối |
| Quantity | Quantity bằng 0, âm hoặc thập phân | Từ chối |
| Inventory | Không đủ tồn, stock âm, version sai kiểu, reserved lớn hơn stock | Từ chối |
| Cart | Quantity trong Cart không khớp | Từ chối |
| Redis key type | Cart, Product hoặc Inventory có kiểu dữ liệu sai | Từ chối |
| Order | Order đã tồn tại | Từ chối |
| Checkout hợp lệ | Product ACTIVE, Cart và Inventory hợp lệ | Cho phép ghi |

*Bảng 2.4.11. Các nhóm trường hợp được kiểm chứng trong C2.*

Dưới đây là một số kết quả tiêu biểu, trích phần nội dung assertion sau timestamp:

```text
ASSERT mismatch
expected=PRODUCT_INVENTORY_MISMATCH
actual=ERR PRODUCT_INVENTORY_MISMATCH
snapshot_match=True PASS

ASSERT out_of_stock
expected=OUT_OF_STOCK
actual=ERR OUT_OF_STOCK
snapshot_match=True PASS

ASSERT cart_mismatch
expected=CART_MISMATCH
actual=ERR CART_MISMATCH
snapshot_match=True PASS

ASSERT valid
expected=OK
actual=OK
snapshot_match=True PASS
```

*Kết quả 2.4.7. Một số assertion đại diện của C2.*

`expected` là kết quả dự kiến, `actual` là phản hồi Redis; `snapshot_match=True` xác nhận trạng thái dữ liệu khớp kỳ vọng.

**Các trường hợp bị từ chối**

Cả 14 case không hợp lệ đều bị từ chối trước ghi. Với case cố tình tạo dữ liệu sai để thử validation, snapshot được so sánh **ngay trước và sau lời gọi Lua**, không phải trước khi chuẩn bị fixture.

**Trường hợp Checkout hợp lệ**

Khi toàn bộ điều kiện được đáp ứng, dữ liệu được cập nhật như sau:

| Đối tượng | Trước Checkout | Sau Checkout |
|---|---|---|
| Order | Chưa tồn tại | Hash chứa Product ID, Quantity = 1 và `PENDING` |
| Inventory on_hand | 100 | 99 |
| Inventory reserved | 0 | 0 |
| Inventory version | 0 | 1 |
| Cart Item của P1 | Quantity = 1 | Đã xóa |
| Cart Item của P2/P3 | Quantity = 1 | Không thay đổi |

*Bảng 2.4.12. Trạng thái dữ liệu trước và sau Checkout hợp lệ trong C2.*

Bộ thí nghiệm được thực hiện trong năm lượt kiểm chứng. Các transcript ghi nhận mỗi lượt đều có 14 case bị từ chối đúng điều kiện, một case Checkout thành công và kết quả kiểm tra snapshot phù hợp với kỳ vọng.

C2 chứng minh các bất biến nghiệp vụ được bảo vệ trong **luồng Checkout đi qua Lua**: nhánh không hợp lệ dừng trước ghi, còn nhánh hợp lệ tạo các cập nhật đúng kỳ vọng. Phạm vi bảo vệ phụ thuộc vào việc những đường ghi liên quan đều tuân theo cơ chế validation này. Giới hạn rollback khi Lua gặp lỗi sau ghi đã được phân tích tại A5.

**Đối chiếu kết quả Consistency**

**Oracle**

C1 và A2 chứng minh Oracle áp dụng constraint ngay tại schema và từ chối những câu SQL vi phạm trên các đường ghi thuộc phạm vi ràng buộc.

**Redis**

C2 bảo vệ bất biến bằng Lua: từ chối yêu cầu không hợp lệ trước ghi và cập nhật nhiều key khi đủ điều kiện. Quy tắc chỉ áp dụng cho **đường ghi đi qua script**, không tự áp dụng với mọi Redis client.

**Nhận xét chung**

Oracle đặt ràng buộc ở tầng DBMS; Redis trong thí nghiệm dựa vào validation ở tầng lệnh ghi. Cả hai đều cần khai báo hoặc lập trình rõ những quy tắc nghiệp vụ phải duy trì.

<a id="tx-section-2-4-2-3"></a>

#### 2.4.2.3. Isolation — Tính cô lập

**Cơ sở lý thuyết**

Isolation kiểm soát cách các transaction tương tác khi truy cập đồng thời. Những hiện tượng cần quan tâm gồm *Dirty Read* (đọc chưa commit), *Non-repeatable Read* (hai lần đọc cho giá trị khác nhau), *Lost Update* (mất cập nhật) và *Deadlock* (chờ khóa theo vòng).

Các DBMS có thể dùng khóa hoặc phát hiện xung đột trước khi ghi để bảo vệ những thao tác phụ thuộc vào dữ liệu đang thay đổi.

**Kết quả trên Oracle — B1, B2 và B3**

Ở `READ COMMITTED`, Oracle dùng read consistency để SELECT đọc dữ liệu đã commit. B1 ghi nhận B đọc 100 khi A chưa commit và đọc 99 ở câu SELECT sau khi A commit. Vì vậy, không có Dirty Read trong tình huống này nhưng có thể xảy ra Non-repeatable Read.

B2 tập trung vào thời điểm lấy khóa trong chuỗi *đọc → quyết định → ghi*. Hai session cùng giảm tồn kho từ 100 xuống 98. Khi sử dụng SELECT thông thường, cả hai đều tính quyết định ưu đãi từ giá trị 100. Với `SELECT FOR UPDATE`, session B phải đợi A commit rồi mới đọc được giá trị 99, nhờ đó quyết định không còn dựa trên dữ liệu cũ. Thí nghiệm này không chứng minh Lost Update trong phép giảm tồn, mà chứng minh ý nghĩa của việc khóa trước thời điểm ra quyết định.

B3 ghi nhận lỗi chờ khóa quá hạn `ORA-00054` và deadlock `ORA-00060`. Ứng dụng cần kết thúc hoặc rollback transaction phù hợp để giải phóng khóa.

**Kết quả trên Redis — B4 và B5**

Ở B4, hai lệnh `HINCRBY` đồng thời đều được ghi nhận, đưa Stock từ 100 xuống 98.

Đối với chuỗi thao tác cần đọc dữ liệu, tính toán rồi ghi, Redis cung cấp `WATCH/MULTI/EXEC` để phát hiện xung đột. Trong B4, client A đọc tồn kho 100, nhưng client B đã giảm còn 99 trước khi A gọi EXEC. Redis hủy hàng đợi của A; A đọc lại giá trị 99, tính lại và retry thành công, đưa tồn kho xuống 98. `WATCH` không khóa key mà phát hiện key đã thay đổi.

B5 dùng Lua giữ Quota: bốn client tranh hai suất, chỉ hai yêu cầu thành công; cuối cùng `remaining = 0`, `held = 2`.

**Tổng hợp kết quả thực nghiệm**

| Thí nghiệm | Cơ chế được kiểm chứng | Kết quả chính |
|---|---|---|
| **B1 — Oracle** | `READ COMMITTED` | Không Dirty Read; câu SELECT sau commit có thể đọc giá trị mới |
| **B2 — Oracle** | SELECT thường / `SELECT FOR UPDATE` | Tồn cuối đều bằng 98, nhưng số quyết định ưu đãi hợp lệ lần lượt là 2 và 1 |
| **B3 — Oracle** | Row lock, timeout, deadlock | Phát hiện chờ khóa quá hạn và deadlock qua các mã lỗi Oracle |
| **B4 — Redis** | `HINCRBY`, `WATCH/MULTI/EXEC` | Giảm tồn nguyên tử; WATCH conflict làm EXEC bị hủy và có thể retry |
| **B5 — Redis** | Lua kiểm soát Quota | Hai trong bốn client giữ thành công hai suất giới hạn |

*Bảng 2.4.13. Tổng hợp kết quả các thí nghiệm Isolation B1–B5.*

**Đối chiếu kết quả Isolation**

**Oracle**

Read consistency bảo vệ câu SELECT khỏi dữ liệu chưa commit. Khi quyết định cần dựa trên giá trị hiện hành, `SELECT FOR UPDATE` giúp lấy khóa trước bước quyết định.

**Redis**

Redis cung cấp `HINCRBY` cho phép tăng/giảm nguyên tử, `WATCH` để phát hiện xung đột và Lua để kiểm tra–ghi trong một lần thực thi.

**Phạm vi kết luận**

B1–B5 kiểm chứng hành vi concurrency trong các tình huống đã thiết kế. Cơ chế khóa, retry và đánh giá tranh chấp chi tiết thuộc topic **Concurrency Control (2.5)**.

<a id="tx-section-2-4-2-4"></a>

#### 2.4.2.4. Durability — Tính bền vững

**Cơ sở lý thuyết**

Durability là khả năng lưu giữ những thay đổi đã xác nhận theo chính sách lưu bền của DBMS. **Đọc lại dữ liệu sau COMMIT** chưa đủ để chứng minh dữ liệu sẽ được phục hồi sau khi tiến trình hoặc máy chủ gặp sự cố.

**Oracle — Redo Log và cơ chế phục hồi**

Oracle sử dụng *Redo Log* để ghi nhận thay đổi phục vụ recovery. Với COMMIT đồng bộ mặc định, tiến trình LGWR ghi redo và thông tin commit vào Online Redo Log trước khi xác nhận thành công; các data block chưa nhất thiết được ghi ngay xuống datafile. Khi phục hồi, Redo hỗ trợ tái áp dụng thay đổi, còn Undo giúp xử lý transaction chưa hoàn tất.

**Redis — RDB và AOF**

Redis hỗ trợ **RDB** (snapshot dữ liệu) và **AOF** (ghi lại thao tác thay đổi) để phục hồi trạng thái. Audit môi trường xác nhận AOF đang bật với `appendfsync everysec`, đồng bộ định kỳ, thông thường mỗi giây. Do đó, một số thay đổi gần thời điểm sự cố có thể chưa được lưu bền.

**Đối chiếu và phạm vi kiểm chứng**

**Oracle:** Đã kiểm chứng trạng thái dữ liệu sau COMMIT qua kết nối độc lập; cơ chế lưu bền dựa trên Redo Log và chính sách commit.

**Redis:** Đã xác minh cấu hình AOF `everysec`; khả năng lưu bền phụ thuộc chính sách persistence.

**Giới hạn:** Nhóm chưa thực hiện crash-recovery test trong topic Transaction, nên không so sánh định lượng mức mất dữ liệu hay thời gian khôi phục. Nội dung này được nghiên cứu sâu hơn tại **Data Backup & Recovery (2.6)**.

<a id="tx-section-2-4-2-5"></a>

#### 2.4.2.5. Transaction trong nghiệp vụ Checkout nhiều bước

**Bài toán thực nghiệm**

Để kiểm chứng transaction trong nghiệp vụ nhiều bước, nhóm thực hiện hai thí nghiệm Checkout liên quan đến Order, Order Items, Inventory, Cart và Quota.

**D1** kiểm tra Oracle rollback khi lỗi xuất hiện **sau nhiều thao tác ghi**; **D2** kiểm tra Redis Lua từ chối yêu cầu **trước thao tác ghi** nếu dữ liệu không hợp lệ. Đây là hai tình huống khác nhau, không phải phép thử lỗi đối xứng.

**Oracle — Multi-table Checkout và Full Rollback (D1)**

D1 thực hiện Checkout P1 và P2 trong một Oracle transaction. Ban đầu Cart có P1–P3 (mỗi Quantity = 1), Stock P1/P2 = 100 và chưa có Order. Các bước trong cùng session gồm:

1. Tạo một Order có trạng thái `PENDING`.
2. Thêm hai Order Item tương ứng P1 và P2.
3. Giảm tồn kho của P1 và P2, mỗi sản phẩm một đơn vị.
4. Xóa P1 và P2 khỏi Cart.
5. Cố tình cập nhật Quantity của P3 thành 0 để gây lỗi CHECK constraint.

Các lệnh giảm tồn và xóa Cart tuân theo nguyên tắc A1; chương trình kiểm tra số dòng bị ảnh hưởng. Sau các bước ghi thành công nhưng chưa COMMIT, script tạo lỗi:

```sql
BEGIN
  UPDATE cart_items
  SET quantity = 0
  WHERE cart_id = :fixture_cart_id
    AND product_id = :p3_id;

  RAISE_APPLICATION_ERROR(-20075, 'CHECK khong chan quantity=0');

EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE <> -2290 THEN
      RAISE;
    END IF;

    DBMS_OUTPUT.PUT_LINE(
      'FAILED_STATEMENT=' || SQLCODE || ' ' || SQLERRM
    );
END;

-- Ghi snapshot sau lỗi, kiểm tra các giá trị (lược).

ROLLBACK;
```

*Mã nguồn 2.4.13. Tạo lỗi CHECK, xử lý exception và thực hiện full rollback trong D1.*

Lỗi `ORA-02290` xuất hiện khi Cart Item bị gán `quantity = 0`. Script ghi snapshot trong transaction trước khi rollback.

Output chính:

```text
BEFORE ORDERS=0 ITEMS=0 CART_LINES=3
P1_STOCK=100 P2_STOCK=100 P3_CART=1

TRANSIENT_ORDER_ID=28461

FAILED_STATEMENT=-2290 ORA-02290:
check constraint (SHOP.CK_CART_ITEMS_QUANTITY) violated

AFTER_FAILED_STATEMENT ORDERS=1 ITEMS=2 CART_LINES=1
P1_STOCK=99 P2_STOCK=99 P3_CART=1

AFTER_FULL_ROLLBACK ORDERS=0 ITEMS=0 CART_LINES=3
P1_STOCK=100 P2_STOCK=100 P3_CART=1
```

*Kết quả 2.4.8. Ba mốc trạng thái của Checkout Oracle trong D1; các dòng dài được ngắt để trình bày.*

`ORDERS` là số Order thuộc lần chạy; `ITEMS` là số Order Item liên quan; `CART_LINES` là số dòng còn lại trong Cart; `P1_STOCK`, `P2_STOCK` là tồn kho của hai sản phẩm; `P3_CART` là số lượng của P3 trong Cart.

Giá trị `TRANSIENT_ORDER_ID = 28461` là ID được Oracle cấp cho Order tạm thời. Order này chưa được commit và sẽ không còn tồn tại sau full rollback. Việc rollback cũng không có nghĩa giá trị sequence/identity đã cấp nhất thiết được tái sử dụng.

Snapshot sau lỗi được thực hiện trong chính session chứa các thay đổi chưa commit, vì vậy có thể nhìn thấy Order và Inventory đã được cập nhật. Điều này không có nghĩa transaction khác đã nhìn thấy những thay đổi đó.

Sau full rollback, toàn bộ thay đổi chưa commit được khôi phục: Order và các Order Item không còn, Cart trở lại ba dòng và Inventory P1/P2 trở về 100. Snapshot độc lập sau khi script kết thúc cũng xác nhận kết quả phù hợp với baseline.

D1 chứng minh Oracle có thể sử dụng một transaction để quản lý nhiều thao tác trên các bảng khác nhau và hoàn tác toàn bộ nếu nghiệp vụ không hoàn tất.

**Redis — Multi-key Checkout và Pre-validation (D2)**

D2 mô phỏng Checkout P1 và P2 bằng Lua trên năm Redis key:

| Key | Vai trò |
|---|---|
| `KEYS[1]` — Cart | Chứa Product ID và Quantity trong giỏ |
| `KEYS[2]` — Order | Lưu thông tin đơn hàng được tạo |
| `KEYS[3]` — Inventory P1 | Tồn kho của P1 |
| `KEYS[4]` — Inventory P2 | Tồn kho của P2 |
| `KEYS[5]` — Quota | Số suất còn lại và đã giữ |

*Bảng 2.4.14. Các key được sử dụng trong D2.*

Lua nhận thêm `ARGV[1]` là User ID, `ARGV[2]` và `ARGV[3]` là Product ID của P1/P2, `ARGV[4]` và `ARGV[5]` là số lượng yêu cầu mua tương ứng.

Khác với D1, D2 không tạo lỗi sau khi đã ghi. Thay vào đó, Lua kiểm tra trước các điều kiện: Quantity hợp lệ, Order chưa tồn tại, đủ tồn kho, Quota còn suất và Cart khớp với số lượng yêu cầu.

Đoạn kiểm tra tồn kho P2:

```lua
local stock2 = tonumber(redis.call('HGET', KEYS[4], 'on_hand'))
local reserved2 = tonumber(redis.call('HGET', KEYS[4], 'reserved'))

if not stock2 or not reserved2
   or stock2 - reserved2 < q2 then
  return redis.error_reply('OUT_OF_STOCK_P2')
end
```

*Mã nguồn 2.4.14. Kiểm tra tồn kho P2 trước khi ghi trong D2.*

`stock2 - reserved2` là tồn khả dụng P2; nếu nhỏ hơn `q2`, Lua trả lỗi trước ghi. Khi validation hợp lệ, script thực hiện:

```lua
redis.call('HSET', KEYS[2], 'user_id', ARGV[1],
  'status', 'PENDING',
  'product_1', ARGV[2], 'product_2', ARGV[3],
  'quantity_1', q1, 'quantity_2', q2)

redis.call('HINCRBY', KEYS[3], 'on_hand', -q1)
redis.call('HINCRBY', KEYS[4], 'on_hand', -q2)
redis.call('HDEL', KEYS[1], ARGV[2], ARGV[3])
redis.call('HINCRBY', KEYS[5], 'remaining', -1)
redis.call('HINCRBY', KEYS[5], 'held', 1)

return 'OK'
```

*Mã nguồn 2.4.15. Toàn bộ phần ghi dữ liệu sau validation trong D2.*

Nhánh không hợp lệ yêu cầu P2 = 101 trong khi Stock chỉ bằng 100:

Kết quả:

```text
MODE=invalid RUN_ID=native01 QUANTITY_P1=1 QUANTITY_P2=101
ERR OUT_OF_STOCK_P2
```

*Kết quả 2.4.9. Checkout bị từ chối do không đủ tồn kho P2.*

Snapshot xác nhận không tạo Order, không giảm Stock, không thay Cart hay Quota. Yêu cầu P2 = 101 cũng lệch Cart, nhưng script kiểm tra Stock trước nên trả `OUT_OF_STOCK_P2` là **lỗi đầu tiên**, không suy ra các điều kiện sau đã hợp lệ.

Ở nhánh hợp lệ, P1 và P2 đều có Quantity = 1. Lua trả:

```text
MODE=valid RUN_ID=native01 QUANTITY_P1=1 QUANTITY_P2=1
OK
```

*Kết quả 2.4.10. Checkout hợp lệ được Lua xác nhận trong D2.*

Snapshot sau thành công ghi nhận:

| Đối tượng | Trước Checkout | Sau Checkout |
|---|---|---|
| Order | Chưa tồn tại | Hash chứa User ID, P1, P2, Quantity và `PENDING` |
| Inventory P1 | 100 | 99 |
| Inventory P2 | 100 | 99 |
| Inventory P3 | 100 | 100 |
| Cart | P1, P2, P3 | Chỉ còn P3 |
| Quota remaining | 2 | 1 |
| Quota held | 0 | 1 |

*Bảng 2.4.15. Trạng thái Redis sau Checkout hợp lệ trong D2.*

Quota chỉ giảm một suất dù Order có hai sản phẩm, bởi quy tắc được mô phỏng trong D2 là một Checkout giữ một suất Quota.

Kết quả chứng minh rằng những điều kiện không hợp lệ được Lua phát hiện trước khi thực hiện ghi; còn khi validation thành công, các key liên quan được cập nhật theo đúng logic của script.

Sau thí nghiệm, chương trình thực hiện cleanup và tạo lại fixture. Đây là thao tác khôi phục dữ liệu phục vụ nghiên cứu, không phải rollback của Redis.

**Đối chiếu kết quả D1 và D2**

Hai thí nghiệm cùng xử lý Checkout nhiều thao tác, nhưng điểm phát sinh lỗi khác nhau nên kết quả không thể dùng để so sánh khả năng rollback theo điều kiện hoàn toàn tương đương.

| Tiêu chí | D1 — Oracle | D2 — Redis |
|---|---|---|
| Đối tượng dữ liệu | Nhiều bảng quan hệ | Nhiều Redis key |
| Cách phối hợp | Transaction SQL | Một Lua EVAL |
| Thời điểm tạo lỗi | Sau nhiều thao tác ghi thành công | Trong giai đoạn validation trước ghi |
| Khi phát hiện lỗi | Ứng dụng gọi full ROLLBACK | Lua trả lỗi trước khi thực hiện ghi |
| Kết quả | Dữ liệu được khôi phục về baseline | Nhánh invalid không làm thay đổi dữ liệu |

*Bảng 2.4.16. Đối chiếu kết quả hai thí nghiệm Checkout nhiều bước.*

**Oracle**

D1 chứng minh khả năng hoàn tác các ghi trên Order, Order Items, Inventory và Cart sau khi một bước cuối thất bại.

**Redis**

D2 chứng minh Lua có thể từ chối yêu cầu trước ghi; nếu hợp lệ, nhiều key được cập nhật trong một lần `EVAL`.

**Ý nghĩa đối chiếu**

D1 kiểm chứng **rollback sau ghi**, còn D2 kiểm chứng **validation trước ghi**. Không thể dùng D2 để khẳng định Redis có rollback tương đương Oracle; giới hạn đó đã được minh họa ở A5.

<a id="tx-section-2-4-3"></a>

### 2.4.3. Đánh giá hiệu năng và lựa chọn DBMS

Phần này so sánh hiệu năng transaction của **Oracle và Redis ở cấp cơ sở dữ liệu**, đồng thời đánh giá thời gian xử lý một luồng nghiệp vụ ShopFlash trên **backend sử dụng Oracle**. Hai phép đo được phân tích riêng vì không có cùng đơn vị công việc.

<a id="tx-section-2-4-3-1"></a>

#### 2.4.3.1. Thiết kế benchmark và các chỉ số đánh giá

Benchmark được thực hiện ngày **27/10/2026** trên máy Ryzen AI 7 PRO 350 (8 nhân/16 luồng), RAM 26 GiB, sử dụng Oracle Database Free, Redis 7.4.7, Java 21 và k6 0.55.2. Các kết quả phản ánh môi trường local, chưa được cô lập tài nguyên như production.

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `cart_size` | 1, 5 | Số dòng sản phẩm trong một Checkout |
| `concurrency` | 1, 10 | Số worker hoặc người dùng ảo thực thi đồng thời |
| `warmup` | 50 | Số lượt làm nóng, không tính vào kết quả |
| `rounds` | 5 | Số lượt đo cho mỗi tổ hợp |
| `samples_per_round` | 200 | Tổng số transaction hoặc flow trong một lượt |
| `fixture_stock` | 100 | Tồn kho khởi tạo trong database benchmark |
| `quantity_per_line` | 1 | Số lượng mua của mỗi dòng sản phẩm |

<a id="tx-table-10"></a>

*Bảng 2.4.17. Các tham số cấu hình workload của benchmark.*

Mỗi tổ hợp có **5 × 200 = 1.000 mẫu chính thức**. Mức `concurrency = 10` nghĩa là mười worker/VU cùng xử lý tổng cộng 200 mẫu mỗi round, không phải mỗi worker xử lý 200 mẫu. Theo đó, phép đo cấp cơ sở dữ liệu có **8.000 transaction** (2 DBMS × 2 Cart Size × 2 Concurrency × 1.000); phép đo HTTP trên Oracle có **4.000 flow** (2 Cart Size × 2 Concurrency × 1.000).

Các chỉ số chính gồm **p50/p95 latency** (ms) — thời gian mà lần lượt 50%/95% mẫu thành công hoàn tất không chậm hơn; **TPS** — số transaction thành công mỗi giây; và **Flow/s** — số luồng nghiệp vụ hoàn thành mỗi giây. Benchmark đồng thời ghi nhận lỗi, yêu cầu bị từ chối và **invariant violations** để kiểm tra tính đúng đắn. Các phân vị trong bảng được tính trên các mẫu thành công; độ dao động giữa năm round được thể hiện qua trung bình và độ lệch chuẩn (SD).

<a id="tx-section-2-4-3-2"></a>

#### 2.4.3.2. Kết quả Database-level Benchmark

Benchmark thực thi Checkout mô phỏng **trực tiếp qua JDBC/Jedis**, không qua HTTP. Oracle sử dụng bảng quan hệ và transaction; Redis sử dụng các **String key** với `WATCH/MULTI/EXEC`, không phải Lua/Hash của [Mục 2.4.2](#tx-section-2-4-2).

| Đặc điểm | Oracle | Redis |
|---|---|---|
| Dữ liệu | Các bảng Cart, Order, Order Item và Inventory | String key mô phỏng Cart, Order, Price và Stock |
| Kiểm soát transaction | Constraint, row lock, `COMMIT` | Java validation, `WATCH/MULTI/EXEC` |
| Kết nối | JDBC connection pool | Jedis connection riêng cho mỗi worker |
| Hậu kiểm | Order/Order Items, Inventory và Cart | Các key Order, Stock và Cart |

<a id="tx-table-11"></a>

*Bảng 2.4.18. Khác biệt giữa hai implementation trong database benchmark.*

Hai implementation có cùng mục tiêu Checkout nhưng khác cấu trúc dữ liệu và chi phí xử lý. Đáng chú ý, thời gian đo Oracle bao gồm bước lấy connection từ pool, còn Jedis connection đã được chuẩn bị trước, nên kết quả chỉ phản ánh **hai cách triển khai trong điều kiện khảo sát**, không phải so sánh hai engine với chi phí hoàn toàn tương đương.

| Cart Size | Concurrency | DBMS | p50 (ms) | p95 (ms) | TPS |
|---:|---:|---|---:|---:|---:|
| 1 | 1 | Oracle | 0,8712 | 1,3416 | 847,36 |
| 1 | 1 | Redis | 0,1303 | 0,2109 | 4.517,08 |
| 1 | 10 | Oracle | 2,2116 | 3,0110 | 3.503,15 |
| 1 | 10 | Redis | 0,5125 | 0,6970 | 12.288,94 |
| 5 | 1 | Oracle | 2,0081 | 2,6394 | 404,74 |
| 5 | 1 | Redis | 0,2995 | 0,4590 | 1.787,83 |
| 5 | 10 | Oracle | 4,5749 | 6,1164 | 1.753,74 |
| 5 | 10 | Redis | 0,9906 | 1,4019 | 5.438,86 |

<a id="tx-table-12"></a>

*Bảng 2.4.19. Kết quả Database-level Benchmark. p50/p95 tính trên 1.000 mẫu thành công của mỗi tổ hợp; TPS dựa trên tổng thời gian thực của năm round. Nguồn: [`results/summary-20261027/database-summary.csv`](../../../../report/transaction/results/summary-20261027/database-summary.csv).*

<a id="tx-figure-1"></a>

[Xem biểu đồ PDF vector](../../../Pictures/Task_4/4.3-01-database-p95.pdf)

*Hình 2.4.1. So sánh p95 latency Oracle–Redis theo Cart Size và Concurrency; dữ liệu từ bảng Database-level Benchmark, đơn vị ms.*

<a id="tx-figure-2"></a>

[Xem biểu đồ PDF vector](../../../Pictures/Task_4/4.3-02-database-tps.pdf)

*Hình 2.4.2. So sánh TPS Oracle–Redis theo bốn tổ hợp tải; dữ liệu từ bảng Database-level Benchmark.*

**Nhận xét kết quả.** Khi tăng Cart Size từ 1 lên 5, **p95 tăng và TPS giảm** ở cả hai implementation do một transaction phải xử lý nhiều dòng sản phẩm hơn. Khi tăng Concurrency từ 1 lên 10, **TPS tăng nhưng p95 cũng tăng**, phản ánh sự đánh đổi giữa thông lượng và thời gian xử lý từng giao dịch.

Redis có **p95 thấp hơn và TPS cao hơn trong cả bốn tổ hợp**. Ở Cart Size 5, Concurrency 10, Oracle đạt **6,1164 ms và 1.753,74 TPS**, trong khi Redis đạt **1,4019 ms và 5.438,86 TPS**. Kết quả này ghi nhận ưu thế của implementation Redis trong workload đã đo, nhưng chưa đủ để khẳng định Redis nhanh hơn Oracle đối với mọi transaction.

| Cart Size | Concurrency | Oracle p95 TB ± SD (ms) | Redis p95 TB ± SD (ms) |
|---:|---:|---:|---:|
| 1 | 1 | 1,2784 ± 0,2425 | 0,1869 ± 0,0451 |
| 1 | 10 | 3,0004 ± 0,1745 | 0,7042 ± 0,0370 |
| 5 | 1 | 2,6187 ± 0,0733 | 0,4494 ± 0,0388 |
| 5 | 10 | 5,7613 ± 0,5800 | 1,3381 ± 0,2104 |

<a id="tx-table-13"></a>

*Bảng 2.4.20. Trung bình và độ lệch chuẩn của p95 qua năm round. Nguồn: [`results/summary-20261027/database-summary.csv`](../../../../report/transaction/results/summary-20261027/database-summary.csv).*

Chênh lệch giữa các round không đảo ngược xu hướng hiệu năng trên. Toàn bộ **8.000/8.000 transaction thành công**, không phát hiện invariant violation trong phạm vi hậu kiểm. Tuy nhiên, fixture chủ yếu độc lập giữa các worker, nên benchmark **chưa đo mức tranh chấp cao trên cùng sản phẩm**; khác biệt về schema, kết nối và durability cũng cần được xét khi diễn giải kết quả.

<a id="tx-section-2-4-3-3"></a>

#### 2.4.3.3. Kết quả Application-level Benchmark

Phép đo HTTP sử dụng **k6 → Vite proxy → Spring Boot → Oracle**, nhằm quan sát hiệu năng nghiệp vụ thực tế, **không so sánh Oracle với Redis ở tầng ứng dụng**. Một flow gồm đọc Cart, thêm 1 hoặc 5 sản phẩm, xem Checkout Preview, tạo Order, truy vấn danh sách/chi tiết Order và hủy Order. Do đó, **4.000 flow** tương ứng **36.000 HTTP request nghiệp vụ** (7 request/flow với Cart Size 1; 11 request/flow với Cart Size 5), không tính warm-up và login.

Bảng kết quả phân biệt **Checkout API** — riêng request tạo Order — với **Full Flow** — toàn bộ chuỗi thao tác nói trên.

| Cart Size | Concurrency | Checkout p50 (ms) | Checkout p95 (ms) | Full Flow p50 (ms) | Full Flow p95 (ms) | Flow/s |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 2,9084 | 4,0406 | 14 | 18 | 68,34 |
| 1 | 10 | 6,5273 | 8,9241 | 33 | 42 | 296,08 |
| 5 | 1 | 4,9161 | 10,2897 | 30 | 62 | 27,73 |
| 5 | 10 | 11,1444 | 14,7888 | 61 | 74 | 157,57 |

<a id="tx-table-14"></a>

*Bảng 2.4.21. Kết quả Application-level Benchmark. Latency lấy từ các mẫu thành công; Flow/s tính theo số flow thành công trên tổng thời gian thực của năm round. Nguồn: [`results/summary-20261027/http-summary.csv`](../../../../report/transaction/results/summary-20261027/http-summary.csv).*

<a id="tx-figure-3"></a>

[Xem biểu đồ PDF vector](../../../Pictures/Task_4/4.3-03-http-p95.pdf)

*Hình 2.4.3. So sánh p95 Checkout API và Full Flow theo Cart Size/Concurrency; dữ liệu từ bảng HTTP benchmark, đơn vị ms.*

**Nhận xét kết quả.** Khi Cart Size tăng từ 1 lên 5 ở Concurrency 1, Checkout p95 tăng từ **4,0406 lên 10,2897 ms**, Full Flow p95 tăng từ **18 lên 62 ms**. Nguyên nhân là Checkout phải xử lý nhiều sản phẩm và flow có thêm các request thêm Cart Item. Khi Concurrency tăng từ 1 lên 10, throughput tăng ở cả hai Cart Size (tương ứng **68,34 → 296,08** và **27,73 → 157,57 flow/s**), nhưng latency cũng tăng.

**Full Flow p95 cao hơn Checkout p95** vì chứa nhiều bước nghiệp vụ; không thể lấy chênh lệch hai phân vị làm chi phí chính xác của những API còn lại. Cả **4.000/4.000 flow hoàn tất**, không phát hiện invariant violation theo hậu kiểm. Kết quả cho thấy backend Oracle vận hành được dưới các mức tải đã thử, nhưng môi trường local và việc chưa tạo tranh chấp mạnh không đủ để suy ra hiệu năng production. **TPS database và Flow/s HTTP không thể so sánh trực tiếp** do khác đơn vị công việc.

<a id="tx-section-2-4-3-4"></a>

#### 2.4.3.4. Đối chiếu tổng hợp và lựa chọn DBMS phù hợp

Từ các thí nghiệm về ACID và kết quả benchmark, có thể nhận thấy Oracle và Redis đều cung cấp những cơ chế hỗ trợ xử lý transaction, nhưng khác nhau đáng kể về phạm vi bảo đảm, cách kiểm soát lỗi và hiệu năng của các cách triển khai được khảo sát.

Bảng 2.4.22 tổng hợp những khác biệt có ảnh hưởng trực tiếp đến việc lựa chọn DBMS.

| Tiêu chí | Oracle | Redis |
|---|---|---|
| **Atomicity** | Hỗ trợ COMMIT, ROLLBACK và SAVEPOINT; cho phép hoàn tác các thay đổi chưa commit trên nhiều bảng (A1, A3, D1). | MULTI/EXEC và Lua thực thi không bị command khác chen ngang, nhưng không tự hoàn tác những ghi thành công trước lỗi runtime (A4, A5). |
| **Consistency** | DBMS trực tiếp thực thi các constraint như PK, FK, UNIQUE và CHECK (C1, A2). | Các ràng buộc nghiệp vụ giữa những key phải được kiểm tra và bảo vệ trong đường ghi, chẳng hạn Lua guarded Checkout (C2). |
| **Isolation và Concurrency** | READ COMMITTED, row lock và SELECT FOR UPDATE hỗ trợ kiểm soát truy cập đồng thời; có cơ chế phát hiện deadlock (B1–B3). | Atomic command, WATCH và Lua hỗ trợ xử lý xung đột; cần lựa chọn cơ chế phù hợp với từng thao tác (B4, B5). |
| **Durability** | Sử dụng Redo Log và cơ chế phục hồi transaction. | Hỗ trợ RDB/AOF; môi trường khảo sát sử dụng AOF `everysec`. Chưa thực hiện crash-recovery benchmark để so sánh trực tiếp. |
| **Hiệu năng** | Có latency cao hơn và TPS thấp hơn trong workload database đã đo. | Có latency thấp hơn và TPS cao hơn trong cùng các tổ hợp khảo sát, nhưng hai implementation chưa tương đương hoàn toàn về ràng buộc và chi phí thực thi. |

<a id="tx-table-15"></a>

*Bảng 2.4.22. Tổng hợp kết quả nghiên cứu Transaction trên Oracle và Redis.*

**Đánh giá và quyết định đối với Oracle**

Trong bối cảnh thương mại điện tử, các nghiệp vụ Checkout, tạo đơn hàng và quản lý tồn kho đòi hỏi nhiều thao tác dữ liệu có quan hệ chặt chẽ. Oracle được đánh giá phù hợp với các yêu cầu này dựa trên ba cơ sở:

1. **Khả năng quản lý transaction nhiều bước:** Hỗ trợ thực hiện và hoàn tác nhiều thay đổi trên các bảng trong cùng transaction, được kiểm chứng qua D1.
2. **Bảo vệ tính toàn vẹn dữ liệu:** Các constraint được thực thi tại DBMS, giúp bảo vệ dữ liệu trên nhiều đường ghi khác nhau (A2, C1).
3. **Kiểm soát truy cập đồng thời:** Row-level locking và SELECT FOR UPDATE hỗ trợ bảo vệ thao tác đọc–quyết định–ghi trong những nghiệp vụ có tranh chấp (B1–B3).

**Quyết định:** Nhóm lựa chọn **Oracle làm nguồn dữ liệu chính (source of truth)** cho ShopFlash. Các nghiệp vụ Normal Checkout và Order Cancellation được thiết kế thực hiện trong Oracle transaction để bảo đảm việc tạo/cập nhật Order, Order Items, Inventory và Cart được xác nhận hoặc hoàn tác nhất quán. Dữ liệu giao dịch và kết quả mua Flash Sale thành công cũng được lưu trữ chính thức tại Oracle.

**Đánh giá và quyết định đối với Redis**

Redis thể hiện ưu thế về thời gian xử lý trong benchmark và cung cấp những cơ chế phù hợp với các thao tác có phạm vi dữ liệu hẹp, yêu cầu xử lý đồng thời cao:

1. **Atomic Command:** Các thao tác như HINCRBY cho phép cập nhật bộ đếm nguyên tử mà không cần triển khai khóa riêng cho từng phép tăng/giảm (B4).
2. **WATCH và Lua:** Hỗ trợ phát hiện xung đột hoặc kết hợp kiểm tra và cập nhật nhiều key trong một lần thực thi, phù hợp với việc kiểm soát quota (B4, B5).
3. **Giới hạn transaction:** Không có full rollback tương đương Oracle khi phát sinh lỗi runtime sau ghi, nên cần validation trước ghi và cơ chế xử lý lỗi phù hợp (A5, C2).

**Quyết định:** Nhóm lựa chọn **Redis cho Flash Sale runtime**, bao gồm kiểm soát quota, giới hạn mua theo người dùng và reservation tạm thời bằng Lua. Ngoài ra, Redis được sử dụng cho các dữ liệu dẫn xuất phục vụ tìm kiếm và thống kê, không thay thế Oracle làm nguồn dữ liệu giao dịch chính.

**Kết luận lựa chọn**

ShopFlash được thiết kế theo hướng kết hợp hai DBMS: **Oracle bảo đảm tính đúng đắn của dữ liệu giao dịch chính, Redis hỗ trợ các thao tác runtime cần tốc độ cao**. Đây là lựa chọn phù hợp với yêu cầu nghiệp vụ và những cơ chế đã nghiên cứu.
