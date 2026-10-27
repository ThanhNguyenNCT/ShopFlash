# Tiểu mục 3.x — Transaction trên Oracle và Redis

## 1. Lý thuyết và phương pháp

Một giao dịch đặt đơn cần giữ cùng một kết quả cho đơn hàng, các dòng hàng, tồn kho và giỏ. Oracle cung cấp `COMMIT`/`ROLLBACK`, ràng buộc quan hệ và đọc nhất quán theo câu lệnh ở `READ COMMITTED`; khóa hàng của `SELECT ... FOR UPDATE` được giữ đến cuối giao dịch. Redis chạy các lệnh trong `MULTI/EXEC` liên tiếp, không xen lệnh từ client khác; `DISCARD` chỉ bỏ hàng đợi chưa thực thi, `WATCH` hủy `EXEC` nếu khóa đã đổi, và lỗi runtime của một lệnh trong `EXEC` không hoàn tác lệnh khác. Lua chạy không bị chen ngang nhưng lỗi sau khi ghi không tự phục hồi dữ liệu đã ghi. Các mô tả này đối chiếu với [Oracle Data Concurrency and Consistency](https://docs.oracle.com/en/database/oracle/oracle-database/26/cncpt/data-concurrency-and-consistency.html), [Oracle SQL Processing for Application Developers](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/sql-processing-for-application-developers.html), [Redis Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/) và [Redis Scripting with Lua](https://redis.io/docs/latest/develop/programmability/eval-intro/).

Hai lớp đánh giá được tách riêng. **Database-level** dùng Java/JDBC/Jedis mô phỏng cùng chuỗi thao tác giỏ → đơn → dòng hàng → tồn → dọn giỏ trên hai DBMS. **Application-level** dùng k6 qua Vite proxy đo API Cart/Checkout/Order thật của ShopFlash, chỉ chạy nghiệp vụ trên Oracle. Số liệu hai lớp không được đối chiếu trực tiếp. Correctness được kiểm tra trước performance: trạng thái trước, mong đợi, thực tế sau, lệnh kiểm chứng và assertion của từng cơ chế nằm trong CSV bằng chứng; benchmark có hậu kiểm bất biến sau mỗi yêu cầu hoặc lượt.

Môi trường local ngày 27/10/2026: AMD Ryzen AI 7 PRO 350 (8 nhân, 16 luồng), RAM 26 GiB, Java 21.0.12.1, Docker 29.9.2. Oracle Database Free theo image digest trong [`compose.yaml`](../../compose.yaml) (metadata image `23.26.0`), Redis Stack cùng Compose (Redis `7.4.7`); `INFO persistence` cho `aof_enabled:1`, `CONFIG GET appendfsync` cho `everysec`. Backend và frontend chạy trên host, Oracle/Redis trong Docker. Đây là cấu hình quan sát được, không phải phép thử crash/khôi phục để đo độ bền thực tế. Công cụ, kết nối, dataset, reset và lệnh tái chạy ở [README](README.md).

Mỗi tổ hợp gồm giỏ 1/5 sản phẩm × đồng thời 1/10, warm-up 50 thao tác rồi 5 lượt × 200 thao tác. Mỗi yêu cầu database dùng một fixture riêng; mỗi VU HTTP dùng khách và giỏ riêng. Đơn giá 100, số lượng mỗi dòng 1, tồn ban đầu 100/sản phẩm trong runner database. Java đo từ lúc đọc/khóa nghiệp vụ đến khi nhận `COMMIT` hoặc `EXEC`; thời gian reset không nằm trong latency. Thông lượng thành công chia cho thời gian thực toàn lượt, kể cả hậu kiểm. p50/p95 gộp từ toàn bộ mẫu thành công hợp lệ; cột `TB ± SD` tính qua năm lượt. Lỗi, từ chối hợp lệ, lỗi tiêm chủ đích và mẫu không hợp lệ được đếm riêng, không trộn vào phân vị thành công.

## 2. Oracle: demo và số đo

`TransactionSemanticsIT` dùng hai JDBC connection độc lập và rào đồng bộ hữu hạn cho tranh chấp. Trong thí nghiệm commit, trạng thái `orders=0, lines=0, cart=1, stock=100, version=0` trở thành `1,1,0,99,1` sau `COMMIT` và được kết nối mới đọc thấy. Khi tiêm lỗi sau khi đã ghi đơn, dòng hàng, tồn kho và giỏ, `ROLLBACK` đưa cả năm giá trị về trạng thái ban đầu. Ở `READ COMMITTED`, phiên B thấy tồn 100 trước khi A commit và 99 sau đó. Hai phiên tranh khóa giỏ/tồn nhận lock timeout có giới hạn chờ; deadlock có kiểm soát cho một phiên thành công và một phiên nhận `ORA-00060`, không để trạng thái ghi dở. Trạng thái trước, mong đợi, thực tế, truy vấn kiểm chứng và assertion của cả sáu case được lưu trong [bằng chứng Oracle](results/evidence/oracle.csv).

Runner Oracle ghi đầu đơn và dòng trong các bảng có khóa ngoại, `CHECK` và `UNIQUE`, cập nhật tồn có điều kiện, xóa mặt hàng giỏ, rồi `COMMIT`. 4.000/4.000 mẫu đo thành công; 0 từ chối, 0 lỗi hệ thống, 0 vi phạm bất biến. Dữ liệu nguồn: `db-2026-10-27T00-57-08-100088724Z.csv`, JSONL cùng tên, `*-runs.jsonl` và `*-config.json` trong `results/`.

| Giỏ | Đồng thời | p50 gộp (ms) | p95 gộp (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | TPS gộp | TPS/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 0,8712 | 1,3416 | 0,8759 ± 0,0862 | 1,2784 ± 0,2425 | 847,3643 | 863,5904 ± 129,3074 |
| 1 | 10 | 2,2116 | 3,0110 | 2,1865 ± 0,1536 | 3,0004 ± 0,1745 | 3.503,1546 | 3.514,5611 ± 220,4714 |
| 5 | 1 | 2,0081 | 2,6394 | 1,9988 ± 0,1131 | 2,6187 ± 0,0733 | 404,7405 | 405,3090 ± 17,2594 |
| 5 | 10 | 4,5749 | 6,1164 | 4,7007 ± 0,5132 | 5,7613 ± 0,5800 | 1.753,7399 | 1.768,3601 ± 168,0538 |

## 3. Redis: demo và số đo

`RedisTransactionIT` ghi lại cả lệnh và hậu kiểm bằng client riêng. `MULTI/EXEC` hợp lệ đưa `cart=1, stock=100, order=null` thành `cart=null, stock=99, order=1`. `DISCARD` giữ nguyên trạng thái vì các lệnh chỉ mới xếp hàng. Khi `DECR` gặp giá trị không phải số lúc `EXEC`, `SET order` và `DEL cart` vẫn được thực thi: không có rollback sau lỗi runtime. Sau `WATCH cart`, client khác đổi `cart=2` tại điểm đồng bộ; `EXEC` trả nil và lệnh xếp hàng không chạy. Script Lua `SET order` rồi `DECR stock` sai kiểu báo lỗi, nhưng `order=written` vẫn còn. Năm case và kết quả hậu kiểm nằm trong [bằng chứng Redis](results/evidence/redis.csv).

Runner Redis dùng `WATCH` cho giỏ/tồn, đọc và kiểm tra giá/tồn bằng Jedis, rồi `MULTI/EXEC` ghi các key đơn/dòng, giảm tồn, dọn giỏ. 4.000/4.000 mẫu đo thành công; 0 từ chối, 0 lỗi hệ thống, 0 vi phạm bất biến. Đây là mô hình cùng thao tác logic, không có khóa ngoại/CHECK/unique như schema Oracle và không phải Checkout của ShopFlash. Dữ liệu nguồn cùng run ID database ở trên.

| Giỏ | Đồng thời | p50 gộp (ms) | p95 gộp (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | TPS gộp | TPS/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 0,1303 | 0,2109 | 0,1363 ± 0,0256 | 0,1869 ± 0,0451 | 4.517,0791 | 4.619,3097 ± 719,6113 |
| 1 | 10 | 0,5125 | 0,6970 | 0,4927 ± 0,0755 | 0,7042 ± 0,0370 | 12.288,9375 | 12.367,5731 ± 1.114,3235 |
| 5 | 1 | 0,2995 | 0,4590 | 0,3002 ± 0,0174 | 0,4494 ± 0,0388 | 1.787,8341 | 1.792,2325 ± 102,2484 |
| 5 | 10 | 0,9906 | 1,4019 | 0,9826 ± 0,1330 | 1,3381 ± 0,2104 | 5.438,8618 | 5.500,5748 ± 680,4128 |

## 4. Bảng so sánh cơ chế

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

ShopFlash tiếp tục dùng Oracle làm nguồn chính cho giỏ, đơn và tồn kho. Khi đặt/hủy đơn có nhiều bảng và bất biến liên quan, kết quả rollback và các ràng buộc trong thí nghiệm phù hợp với cách triển khai F10/F7/F8 hiện tại. Redis phù hợp với projection tìm kiếm, analytics có thể dựng lại và trạng thái runtime Flash Sale theo kiến trúc nhóm; dùng Redis mô phỏng đặt đơn nhanh hơn trong phép đo này không đủ cơ sở chuyển nghiệp vụ đơn hàng sang Redis. Kết quả HTTP của ứng dụng được trình bày riêng ở phụ lục, không dùng để tính chênh lệch Oracle–Redis.

## Phụ lục: phép đo HTTP của ứng dụng

k6 chạy qua Vite proxy vào backend thật, dùng tài khoản fixture và JWT trong bộ nhớ. Mỗi lượt thực hiện đọc giỏ, thêm 1 hoặc 5 mặt hàng, xem trước đơn, đặt đơn, xem danh sách/chi tiết và hủy đơn. Sau từng lượt, một kết nối Oracle độc lập kiểm tra số đơn, số dòng, trạng thái `CANCELLED`, giỏ rỗng và tồn kho đã hoàn lại. Cả **24/24** lượt warm-up/đo có hậu kiểm `PASS`; năm lượt đo của mỗi tổ hợp có tổng **4.000/4.000 luồng thành công**, **36.000/36.000 yêu cầu nghiệp vụ thành công**, không có từ chối, lỗi hệ thống hay vi phạm bất biến. `auth.login` thuộc setup nên không nằm trong các số này.

| Giỏ | Đồng thời | p50 toàn luồng (ms) | p95 toàn luồng (ms) | p50/lượt TB ± SD | p95/lượt TB ± SD | Luồng thành công/s | Luồng/s/lượt TB ± SD |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 14,0 | 18,0 | 14,6 ± 0,9 | 18,22 ± 1,81 | 68,3352 | 68,5605 ± 4,3291 |
| 1 | 10 | 33,0 | 42,0 | 32,8 ± 0,4 | 41,64 ± 1,09 | 296,0777 | 296,0980 ± 2,7514 |
| 5 | 1 | 30,0 | 62,0 | 30,3 ± 2,4 | 61,45 ± 36,28 | 27,7273 | 28,7781 ± 5,6812 |
| 5 | 10 | 61,0 | 74,0 | 62,6 ± 4,2 | 71,72 ± 2,24 | 157,5689 | 158,0261 ± 9,1660 |

Metric riêng của `checkout.place` có p50/p95 lần lượt là **2,9084/4,0406**, **6,5273/8,9241**, **4,9161/10,2897** và **11,1444/14,7888 ms** theo đúng thứ tự bốn hàng trên. `cart.add` có 5 yêu cầu trong mỗi luồng giỏ 5 mặt hàng nên thông lượng của riêng API này bằng 5 lần thông lượng toàn luồng; các API còn lại xuất hiện một lần mỗi luồng. [Bảng HTTP đầy đủ](results/summary-20261027/http-summary.csv) ghi riêng từng API và toàn luồng, gồm p50/p95, thông lượng, tỷ lệ lỗi/từ chối, số mẫu, trung bình và độ lệch năm lượt. [Mẫu chuẩn hóa](results/summary-20261027/http-samples.csv), [JSON thô theo lượt](results/http-20261027-stable/), [hậu kiểm từng lượt](results/http-20261027-stable/) và [cấu hình môi trường](results/environment-20261027.json) cho phép truy vết số liệu. Thời gian toàn luồng được đo bằng `Date.now()` của k6 ở độ phân giải mili giây; API dùng duration của k6. Biến động p95 của giỏ 5 mặt hàng, đồng thời 1 cần được đọc cùng độ lệch giữa các lượt; máy local chưa được cô lập tải nền. Hai lớp đo dùng ranh giới khác nhau nên số HTTP không so trực tiếp với số JDBC/Jedis.
