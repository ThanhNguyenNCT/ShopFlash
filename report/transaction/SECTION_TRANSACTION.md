# Tiểu mục 3.x — Transaction trên Oracle và Redis

## 1. Bối cảnh và phương pháp

### 1.1. Giao dịch cần bảo vệ điều gì?

Trong ShopFlash, một lần Checkout `NORMAL` không chỉ ghi một dòng đơn hàng. Hệ thống phải tạo `ORDERS` và `ORDER_ITEMS`, giảm tồn ở `PRODUCT_INVENTORY`, xóa đúng các `CART_ITEMS` đã mua và giữ những dòng giỏ không mua. Nếu một bước thất bại, đơn hàng, tồn kho và giỏ phải trở về trạng thái nhất quán. Đó là lý do nghiệp vụ này cần một ranh giới giao dịch rõ ràng: các thay đổi cùng `COMMIT` hoặc cùng được `ROLLBACK`.

Oracle là nguồn dữ liệu nghiệp vụ chính của ShopFlash. Trên Oracle, ranh giới giao dịch bao gồm nhiều bảng có khóa ngoại và ràng buộc `CHECK`/`UNIQUE`. Redis trong nghiên cứu chỉ lưu các key mô phỏng thao tác giỏ, đơn, tồn và quota. Các key này không tạo đơn Oracle và không thay thế luồng Checkout thật. Mục tiêu là quan sát cách từng DBMS xử lý lỗi, tranh chấp và tính nhất quán trên mô hình phù hợp với nó.

Các thuật ngữ trong phần này được hiểu như sau: **statement** là một câu SQL; **transaction** là toàn bộ các câu SQL từ lúc bắt đầu đến `COMMIT`/`ROLLBACK`; **session/client** là kết nối giữ trạng thái giao dịch. `SAVEPOINT` đánh dấu điểm có thể quay lại trong một transaction. Trên Redis, `MULTI` xếp hàng lệnh, `EXEC` thực thi hàng đợi, `DISCARD` bỏ hàng đợi chưa chạy, còn `WATCH` theo dõi key để từ chối `EXEC` nếu key thay đổi. Lua chạy như một lần thực thi không bị lệnh khác chen vào, nhưng lỗi sau một lệnh ghi không tự hoàn tác ghi đó. Các định nghĩa này được đối chiếu với [tài liệu Oracle về concurrency](https://docs.oracle.com/en/database/oracle/oracle-database/26/cncpt/data-concurrency-and-consistency.html), [xử lý SQL của Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/sql-processing-for-application-developers.html), [Redis Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/) và [Redis Lua](https://redis.io/docs/latest/develop/programmability/eval-intro/).

### 1.2. Ba loại bằng chứng không có cùng mục đích

| Lớp đánh giá | Công cụ | Câu hỏi được trả lời |
|---|---|---|
| Nghiên cứu cơ chế DBMS-native | SQL/PLSQL và SQL*Plus; Redis CLI/Lua | DBMS thực sự làm gì khi commit, rollback, gặp lỗi, chờ khóa hoặc tranh key? |
| Benchmark database-only | Java JDBC/Jedis trong `TransactionDbBenchmarkIT` | Runner cũ mất bao lâu để hoàn thành workload mô phỏng trên mỗi DBMS? |
| Kiểm thử và đo ứng dụng | JUnit luồng nghiệp vụ; k6 qua HTTP | API Cart–Checkout–Order của ShopFlash có đúng và nhanh ra sao trong môi trường local? |

Các thí nghiệm DBMS-native có snapshot và transcript để giải thích cơ chế, không đủ mẫu để kết luận latency hoặc throughput. Benchmark Java và k6 có phép đo lặp nhưng ranh giới, payload và số thao tác khác nhau; không lấy p50 HTTP so trực tiếp với p50 database-only. [Kế hoạch nghiên cứu](RESEARCH_PLAN.md) quy định 14 case; [runbook và chỉ mục bằng chứng](research/evidence/INDEX.md) cho biết cách chạy và đọc output.

### 1.3. Môi trường và cách đọc số đo cũ

Lần benchmark cũ trên local ngày 27/10/2026 dùng AMD Ryzen AI 7 PRO 350 (8 nhân, 16 luồng), RAM 26 GiB, Java 21.0.12.1 và Docker 29.9.2. Oracle Database Free và Redis Stack chạy trong Docker theo image digest ở [`compose.yaml`](../../compose.yaml); Redis server là `7.4.7`, bật AOF với `appendfsync everysec`. Backend và frontend chạy trên host. Cấu hình này mô tả môi trường quan sát được, chưa phải phép thử mất điện hoặc crash recovery.

Mỗi nhánh benchmark có giỏ 1 hoặc 5 sản phẩm, mức đồng thời 1 hoặc 10, 50 thao tác warm-up và 5 lượt × 200 thao tác đo. Java đo từ lúc bắt đầu đọc/khóa nghiệp vụ đến phản hồi `COMMIT` hoặc `EXEC`, không tính reset fixture; thông lượng dùng thời gian thực của toàn lượt, kể cả hậu kiểm. k6 đo API và toàn luồng HTTP riêng; đăng nhập fixture thuộc setup. p50/p95 tính từ các mẫu `success` hợp lệ, còn lỗi/từ chối/vi phạm bất biến được đếm riêng. Chi tiết cấu hình, lệnh tái chạy và dữ liệu thô ở [README Transaction](README.md); [rà soát P5](research/evidence/p5/README.md) xác nhận lại mốc mẫu và chỉ ra giới hạn của phép so.

Trong các bảng dưới, **p50** là thời gian trung vị: khoảng một nửa mẫu thành công hoàn tất không chậm hơn mốc đó. **p95** là mốc mà khoảng 95% mẫu thành công không chậm hơn. **TPS** là số giao dịch thành công mỗi giây theo ranh giới đo của runner. `TB ± SD` là trung bình và độ lệch chuẩn của thống kê qua năm lượt đo; nó cho biết kết quả dao động giữa các lượt ra sao. Warm-up không được tính vào các giá trị này.

## 2. Oracle: demo và số đo

### 2.1. Quan sát trực tiếp bằng SQL/PLSQL

Bộ SQL chạy trên schema `SHOP/FREEPDB1` đã qua [audit chỉ đọc](research/evidence/p0/README.md). Các constraint liên quan đều đang `ENABLED`, `VALIDATED`, `NOT DEFERRABLE` và `IMMEDIATE`: Oracle kiểm tra ngay khi câu SQL vi phạm được thực thi. Các case cùng dùng fixture nghiên cứu `native01` và reset về đầu kỳ giữa những case có ghi; các lệnh trong cùng transaction luôn ở một SQL*Plus session, còn snapshot sau commit/rollback được đọc bằng session độc lập. [Chỉ mục P2/P3](research/evidence/INDEX.md) dẫn đến lệnh và output nguyên gốc.

**Commit, lỗi và rollback.** Ở [A1](research/evidence/p2/A1/README.md), session tạo Order và Order Item rồi `COMMIT`; session khác thấy Order tồn tại, tồn sản phẩm giảm từ 100 xuống 99 và Cart còn hai dòng. Nhánh tạo đơn khác rồi `ROLLBACK` không làm trạng thái đã commit thay đổi. [A2](research/evidence/p2/A2/README.md) cố ý vi phạm ba constraint có thật: Oracle trả `ORA-00001` cho `UNIQUE`, `ORA-02291` cho khóa ngoại và `ORA-02290` cho `CHECK`. Những lỗi này chặn câu SQL vi phạm; chúng không chứng minh rằng toàn transaction đã tự rollback.

[A3](research/evidence/p2/A3/README.md) cho thấy ba phạm vi hoàn tác khác nhau. Sau khi P1 được sửa từ 100 xuống 99 và P2 từ 100 xuống 98, một câu sửa Cart sai `CHECK` bị hủy, nhưng P1=99 và P2=98 vẫn còn trong session. `ROLLBACK TO` savepoint đưa riêng P2 về 100; full `ROLLBACK` mới đưa cả P1 về 100. [D1](research/evidence/p3/D1/README.md) áp dụng ý này cho Checkout hai mặt hàng:

| Thời điểm trong cùng transaction | Order | Order Items | Dòng Cart | Tồn P1/P2 |
|---|---:|---:|---:|---|
| Trước Checkout | 0 | 0 | 3 | 100 / 100 |
| Sau câu SQL cố ý lỗi `ORA-02290` | 1 | 2 | 1 | 99 / 99 |
| Sau full `ROLLBACK` | 0 | 0 | 3 | 100 / 100 |

Dòng Cart thứ ba không được mua và vẫn có số lượng 1 ở cả ba mốc. Bảng này lấy từ [output D1](research/evidence/p3/D1/native01-D1-output.txt), không phải trạng thái suy đoán. Lỗi `CHECK` chỉ hoàn tác câu SQL đặt số lượng Cart về 0; ứng dụng/session phải gọi full `ROLLBACK` để khôi phục cả Checkout.

**Isolation và tranh chấp.** [B1](research/evidence/p3/B1/README.md) dùng hai session tại `READ COMMITTED`: B đọc tồn 100; A sửa còn 99 nhưng chưa commit thì B vẫn đọc 100; sau A commit, câu SELECT tiếp theo của B thấy 99. [B2](research/evidence/p3/B2/README.md) cho A và B lưu riêng tồn đã đọc 100 rồi cùng cập nhật bằng `old_stock - 1`: B phải chờ A commit, nhưng vẫn ghi từ giá trị cũ nên tồn cuối là 99 thay vì 98. Nhánh bảo vệ của B2 dùng `FOR UPDATE` buộc B chờ/từ chối và `UPDATE ... WHERE on_hand_quantity - reserved_flash_quantity >= 1` chỉ cho một lượt trừ tồn khi còn một sản phẩm. [B3](research/evidence/p3/B3/README.md) tách lượt timeout và deadlock: Oracle 26ai trả `ORA-00054`/`ORA-40097` sau `WAIT 2`, còn vòng chờ A giữ P1–B giữ P2 tạo `ORA-00060`. Cả hai session được chủ động rollback, rồi lấy lại khóa P1/P2 thành công. Mã lỗi statement không thay thế bước giải phóng transaction.

**Giới hạn constraint.** [C1](research/evidence/p2/C1/README.md) đối chiếu metadata của 29 constraint trên Order, Order Item, Product và Inventory. FK/CHECK/UNIQUE bảo vệ quan hệ và giá trị trong từng row; DBMS không tự biết mặt hàng đã được chọn trong Cart, giá hiện tại đã được xác nhận hay tổng các Order Items có bằng subtotal của Order. Tồn khả dụng phải được kiểm tra trong cùng giao dịch bằng khóa hoặc conditional UPDATE.

### 2.2. Baseline JUnit và benchmark Java

`TransactionSemanticsIT` dùng hai JDBC connection độc lập và rào đồng bộ hữu hạn cho tranh chấp. Trong thí nghiệm commit, trạng thái `orders=0, lines=0, cart=1, stock=100, version=0` trở thành `1,1,0,99,1` sau `COMMIT` và được kết nối mới đọc thấy. Khi tiêm lỗi sau khi đã ghi đơn, dòng hàng, tồn kho và giỏ, `ROLLBACK` đưa cả năm giá trị về trạng thái ban đầu. Ở `READ COMMITTED`, phiên B thấy tồn 100 trước khi A commit và 99 sau đó. Hai phiên tranh khóa giỏ/tồn nhận lock timeout có giới hạn chờ; deadlock có kiểm soát cho một phiên thành công và một phiên nhận `ORA-00060`, không để trạng thái ghi dở. Trạng thái trước, mong đợi, thực tế, truy vấn kiểm chứng và assertion của cả sáu case được lưu trong [bằng chứng Oracle](results/evidence/oracle.csv).

Runner Oracle ghi đầu đơn và dòng trong các bảng có khóa ngoại, `CHECK` và `UNIQUE`, cập nhật tồn có điều kiện, xóa mặt hàng giỏ, rồi `COMMIT`. 4.000/4.000 mẫu đo thành công; 0 từ chối, 0 lỗi hệ thống, 0 vi phạm bất biến. Dữ liệu nguồn: `db-2026-10-27T00-57-08-100088724Z.csv`, JSONL cùng tên, `*-runs.jsonl` và `*-config.json` trong `results/`.

| Giỏ | Đồng thời | p50 gộp (ms) | p95 gộp (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | TPS gộp | TPS/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 0,8712 | 1,3416 | 0,8759 ± 0,0862 | 1,2784 ± 0,2425 | 847,3643 | 863,5904 ± 129,3074 |
| 1 | 10 | 2,2116 | 3,0110 | 2,1865 ± 0,1536 | 3,0004 ± 0,1745 | 3.503,1546 | 3.514,5611 ± 220,4714 |
| 5 | 1 | 2,0081 | 2,6394 | 1,9988 ± 0,1131 | 2,6187 ± 0,0733 | 404,7405 | 405,3090 ± 17,2594 |
| 5 | 10 | 4,5749 | 6,1164 | 4,7007 ± 0,5132 | 5,7613 ± 0,5800 | 1.753,7399 | 1.768,3601 ± 168,0538 |

## 3. Redis: demo và số đo

### 3.1. Quan sát trực tiếp bằng Redis CLI/Lua

Redis lưu fixture trong namespace `txresearch:native01:*`, gồm Cart, ba Inventory, quota và Order Hash mô phỏng. Đây là dữ liệu nghiên cứu độc lập với Order Oracle. Các chuỗi `MULTI`–`EXEC`/`DISCARD` và `WATCH`–đọc–`MULTI`–`EXEC` được gửi trên cùng kết nối liên tục; client quan sát dùng kết nối khác.

**Xếp hàng và lỗi.** [A4](research/evidence/p2/A4/README.md) cho thấy ba lệnh nhận `QUEUED` sau `MULTI`; `EXEC` thực thi chúng theo thứ tự, tạo Order Hash, giảm tồn P1 còn 99 và xóa P1 khỏi Cart. Khi gửi `DISCARD` thay `EXEC`, không lệnh ghi nào chạy. [A5](research/evidence/p2/A5/README.md) phân biệt thời điểm lỗi: lệnh không tồn tại trong lúc xếp hàng khiến `EXECABORT` hủy cả hàng đợi; lỗi `WRONGTYPE` khi `EXEC` đang chạy không hoàn tác lệnh đã thành công trước hoặc sau nó. Lua cố ý ghi rồi gọi lệnh sai kiểu cũng để lại tồn P1=99 và Order Hash đã tạo. Vì vậy không thể diễn giải lỗi runtime Redis như full rollback của Oracle.

**Tranh key và quota.** Trong [B4](research/evidence/p3/B4/README.md), A `WATCH` Inventory, đọc 100 và xếp lệnh giảm tồn; B sửa key thành 99 trên kết nối khác. `EXEC` của A trả nil, lệnh giảm tồn của A không chạy. A đọc lại rồi retry đúng một lần, kết quả 98. [B5](research/evidence/p3/B5/README.md) cho bốn client đồng thời gọi Lua giữ quota 2: đúng hai client thành công, hai client nhận `QUOTA_EXHAUSTED`; cuối cùng `remaining=0`, `held=2`. Điều này chứng minh đoạn kiểm tra–ghi trong cùng một Lua script không bị client khác chen vào, với điều kiện logic trong script đúng.

**Validation phải viết tường minh.** [C2](research/evidence/p2/C2/README.md) dùng CLI ghi Order trỏ tới Product không tồn tại và tồn kho âm; Redis Hash chấp nhận cả hai giá trị vì không có FK/CHECK quan hệ như Oracle. Lua validation sau đó trả `INVALID_PRODUCT_REFERENCE` và `INVALID_STOCK`. [D2](research/evidence/p3/D2/README.md) viết Lua kiểm tra Cart, tồn khả dụng và quota **trước lệnh ghi đầu tiên**: nhánh yêu cầu P2=101 bị từ chối và key không đổi; nhánh hợp lệ cập nhật đồng thời Cart, hai Inventory, quota và Order Hash mô phỏng. Cách tiền kiểm này bảo vệ invariant của mô hình key đang thử, nhưng không tạo Order Oracle hoặc giao dịch liên DBMS.

### 3.2. Baseline JUnit và benchmark Java

`RedisTransactionIT` ghi lại cả lệnh và hậu kiểm bằng client riêng. `MULTI/EXEC` hợp lệ đưa `cart=1, stock=100, order=null` thành `cart=null, stock=99, order=1`. `DISCARD` giữ nguyên trạng thái vì các lệnh chỉ mới xếp hàng. Khi `DECR` gặp giá trị không phải số lúc `EXEC`, `SET order` và `DEL cart` vẫn được thực thi: không có rollback sau lỗi runtime. Sau `WATCH cart`, client khác đổi `cart=2` tại điểm đồng bộ; `EXEC` trả nil và lệnh xếp hàng không chạy. Script Lua `SET order` rồi `DECR stock` sai kiểu báo lỗi, nhưng `order=written` vẫn còn. Năm case và kết quả hậu kiểm nằm trong [bằng chứng Redis](results/evidence/redis.csv).

Runner Redis dùng `WATCH` cho giỏ/tồn, đọc và kiểm tra giá/tồn bằng Jedis, rồi `MULTI/EXEC` ghi các key đơn/dòng, giảm tồn, dọn giỏ. 4.000/4.000 mẫu đo thành công; 0 từ chối, 0 lỗi hệ thống, 0 vi phạm bất biến. Đây là mô hình cùng thao tác logic, không có khóa ngoại/CHECK/unique như schema Oracle và không phải Checkout của ShopFlash. Dữ liệu nguồn cùng run ID database ở trên.

| Giỏ | Đồng thời | p50 gộp (ms) | p95 gộp (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | TPS gộp | TPS/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 0,1303 | 0,2109 | 0,1363 ± 0,0256 | 0,1869 ± 0,0451 | 4.517,0791 | 4.619,3097 ± 719,6113 |
| 1 | 10 | 0,5125 | 0,6970 | 0,4927 ± 0,0755 | 0,7042 ± 0,0370 | 12.288,9375 | 12.367,5731 ± 1.114,3235 |
| 5 | 1 | 0,2995 | 0,4590 | 0,3002 ± 0,0174 | 0,4494 ± 0,0388 | 1.787,8341 | 1.792,2325 ± 102,2484 |
| 5 | 10 | 0,9906 | 1,4019 | 0,9826 ± 0,1330 | 1,3381 ± 0,2104 | 5.438,8618 | 5.500,5748 ± 680,4128 |

## 4. Bảng so sánh cơ chế

Bảng dưới đặt kết quả thí nghiệm DBMS-native cạnh số benchmark cũ để thấy **cơ chế** và **phép đo** trả lời hai câu hỏi khác nhau. Các hàng latency/TPS chỉ phản ánh workload Java trong môi trường local; chúng không đo cùng mức ràng buộc, cách lưu hay chi phí kết nối của hai DBMS. [Rà soát P5](research/evidence/p5/README.md) phân tích từng sai lệch từ source và raw results.

| Tiêu chí | Oracle | Redis trong thí nghiệm |
|---|---|---|
| Ghi nhiều đối tượng rồi phát sinh lỗi trước commit | `ROLLBACK` đưa đơn, dòng, giỏ và tồn về cùng trạng thái ban đầu | `DISCARD` chỉ bỏ lệnh chưa `EXEC`; lỗi runtime sau `EXEC` hoặc sau ghi Lua để lại ghi đã chạy |
| Tranh chấp | Khóa hàng, chờ hữu hạn; deadlock được phát hiện, một phía rollback | `WATCH` phát hiện key đổi trước `EXEC`, trả nil; client quyết định đọc lại/gửi lại |
| Ràng buộc dữ liệu | Khóa ngoại, `CHECK`, unique và câu SQL giảm tồn có điều kiện trong schema thật | Runner kiểm tra bằng Java/key, không có cùng ràng buộc quan hệ |
| Độ bền theo cấu hình thử | `COMMIT` của Oracle trong image Free; chưa thử mất điện/crash | Redis bật AOF với `appendfsync everysec`; chưa thử crash/khôi phục |
| Workload thành công 1 sản phẩm, đồng thời 1 | p50 0,8712 ms; 847,3643 TPS | p50 0,1303 ms; 4.517,0791 TPS |
| Workload thành công 5 sản phẩm, đồng thời 10 | p50 4,5749 ms; 1.753,7399 TPS | p50 0,9906 ms; 5.438,8618 TPS |

Chênh lệch tốc độ quan sát được chỉ áp dụng cho code mô phỏng và môi trường local này. Số lệnh, định dạng dữ liệu, chi phí quan hệ/ràng buộc, kết nối client và chế độ ghi bền không thể chuẩn hóa hoàn toàn. Các bảng trên không chứng minh hai DBMS có giao dịch hoặc ACID tương đương. Mẫu benchmark mặc định đều thành công; chi phí từ chối/lỗi giữa giao dịch được kiểm chứng về **trạng thái**, chưa có phân phối latency riêng từ mẫu lỗi trong bộ đo mặc định.

## 5. Kết luận theo ShopFlash

**Giỏ hàng, đơn hàng và tồn kho** tiếp tục thuộc Oracle. Checkout và hủy đơn tác động nhiều bảng có quan hệ; [D1](research/evidence/p3/D1/README.md) cho thấy session có thể chủ động rollback toàn bộ khi bước cuối lỗi, còn [A2/C1](research/evidence/INDEX.md) chỉ ra những ràng buộc Oracle tự kiểm tra và những quy tắc nghiệp vụ vẫn phải đặt trong transaction. [B2/B3](research/evidence/INDEX.md) cho thấy khóa và cập nhật có điều kiện cần thiết khi nhiều yêu cầu tranh cùng tồn kho. Đây là cơ sở kỹ thuật phù hợp với cách ShopFlash đang triển khai F10/F7/F8.

**Redis** phù hợp với trạng thái runtime quota, projection tìm kiếm và read model có thể dựng lại theo kiến trúc nhóm. [B4/B5](research/evidence/INDEX.md) cho thấy `WATCH` và Lua có thể kiểm soát tranh key/quota; [A5/C2](research/evidence/INDEX.md) cũng cho thấy ứng dụng phải tự xử lý lỗi sau ghi và tự viết validation. Order Hash trong nghiên cứu chỉ là mô phỏng logic; không có bằng chứng để chuyển Order thật sang Redis chỉ vì runner cũ đo được p50 thấp hơn.

Các kết luận trên dựa trên fixture local và 14 case có transcript. Bộ nghiên cứu chưa thử crash recovery, mất điện, tải sản xuất hoặc transaction xuyên Oracle–Redis. Số benchmark database-only và số HTTP trong phụ lục được giữ theo kết quả đã đo; chúng không thay thế bằng chứng về tính đúng đắn của cơ chế DBMS.

## Phụ lục: phép đo HTTP của ứng dụng

k6 chạy qua Vite proxy vào backend thật, dùng tài khoản fixture và JWT trong bộ nhớ. Mỗi lượt thực hiện đọc giỏ, thêm 1 hoặc 5 mặt hàng, xem trước đơn, đặt đơn, xem danh sách/chi tiết và hủy đơn. Sau từng lượt, một kết nối Oracle độc lập kiểm tra số đơn, số dòng, trạng thái `CANCELLED`, giỏ rỗng và tồn kho đã hoàn lại. Cả **24/24** lượt warm-up/đo có hậu kiểm `PASS`; năm lượt đo của mỗi tổ hợp có tổng **4.000/4.000 luồng thành công**, **36.000/36.000 yêu cầu nghiệp vụ thành công**, không có từ chối, lỗi hệ thống hay vi phạm bất biến. `auth.login` thuộc setup nên không nằm trong các số này.

| Giỏ | Đồng thời | p50 toàn luồng (ms) | p95 toàn luồng (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | Luồng thành công/s | Luồng/s/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 14,0 | 18,0 | 14,6 ± 0,9 | 18,22 ± 1,81 | 68,3352 | 68,5605 ± 4,3291 |
| 1 | 10 | 33,0 | 42,0 | 32,8 ± 0,4 | 41,64 ± 1,09 | 296,0777 | 296,0980 ± 2,7514 |
| 5 | 1 | 30,0 | 62,0 | 30,3 ± 2,4 | 61,45 ± 36,28 | 27,7273 | 28,7781 ± 5,6812 |
| 5 | 10 | 61,0 | 74,0 | 62,6 ± 4,2 | 71,72 ± 2,24 | 157,5689 | 158,0261 ± 9,1660 |

Metric riêng của `checkout.place` có p50/p95 lần lượt là **2,9084/4,0406**, **6,5273/8,9241**, **4,9161/10,2897** và **11,1444/14,7888 ms** theo đúng thứ tự bốn hàng trên. `cart.add` có 5 yêu cầu trong mỗi luồng giỏ 5 mặt hàng nên thông lượng của riêng API này bằng 5 lần thông lượng toàn luồng; các API còn lại xuất hiện một lần mỗi luồng. [Bảng HTTP đầy đủ](results/summary-20261027/http-summary.csv) ghi riêng từng API và toàn luồng, gồm p50/p95, thông lượng, tỷ lệ lỗi/từ chối, số mẫu, trung bình và độ lệch năm lượt. [Mẫu chuẩn hóa](results/summary-20261027/http-samples.csv), [JSON thô theo lượt](results/http-20261027-stable/), [hậu kiểm từng lượt](results/http-20261027-stable/) và [cấu hình môi trường](results/environment-20261027.json) cho phép truy vết số liệu. Thời gian toàn luồng được đo bằng `Date.now()` của k6 ở độ phân giải mili giây; API dùng duration của k6. Biến động p95 của giỏ 5 mặt hàng, đồng thời 1 cần được đọc cùng độ lệch giữa các lượt; máy local chưa được cô lập tải nền. Hai lớp đo dùng ranh giới khác nhau nên số HTTP không so trực tiếp với số JDBC/Jedis.
