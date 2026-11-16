> **BẢN MẪU, KHÔNG PHẢI BÁO CÁO THẬT.**
> File này minh họa cách viết một tiểu mục theo `KHUNG_CHU_DE.md`.
> - **Mọi số liệu đều là số giả lập**, chưa đo trên máy nào, không được chép vào báo cáo.
> - Người làm Indexing tự chọn demo của mình, không bắt buộc phải giống bản này.

# 3.2 Indexing

Người viết: <tên>

## Môi trường đo

| Thành phần | Giá trị |
|---|---|
| Máy | Intel Core i5-1135G7, 16 GB RAM, SSD NVMe |
| Hệ điều hành, Docker | Windows 11, Docker Desktop 4.x (cấp 4 CPU, 8 GB RAM) |
| Oracle | `database/free:latest-lite`, digest `sha256:…` (Oracle Free giới hạn 2 CPU, 2 GB RAM) |
| Redis | `redis-stack-server:latest`, digest `sha256:…` |
| Dữ liệu | 100.000 sản phẩm, 50 danh mục, giá 10.000–5.000.000₫ phân bố đều |

Mỗi phép đo: chạy khởi động 1 lần không tính, sau đó đo 5 lần, báo cáo trung bình ± độ lệch chuẩn. Số liệu thô nằm trong `results/`.

## 3.2.1 Lý thuyết

Index là một cấu trúc dữ liệu phụ giúp tìm các bản ghi thỏa điều kiện mà không phải đọc toàn bộ bảng. Đổi lại, mỗi lần ghi dữ liệu phải cập nhật thêm index, và index tốn thêm dung lượng.

Các khái niệm dùng trong phần này:

- **B+-tree:** cây cân bằng, các nút lá được nối với nhau theo thứ tự khóa. Tìm một giá trị tốn O(log n). Tìm theo khoảng thì đi xuống lá đầu tiên rồi đọc lần lượt sang phải.
- **Index nhiều cột (composite):** sắp theo cột đầu, rồi đến cột sau. Chỉ dùng hiệu quả khi điều kiện truy vấn có cột đầu tiên.
- **Index trên hàm (function-based):** index trên kết quả của một biểu thức, ví dụ `UPPER(name)`, thay vì trên giá trị gốc của cột.
- **Selectivity:** tỉ lệ số dòng thỏa điều kiện trên tổng số dòng. Điều kiện càng lọc được ít dòng thì index càng có lợi so với quét toàn bảng.

## 3.2.2 Trên Oracle

Oracle hỗ trợ sẵn nhiều loại index: B-tree, bitmap, index trên hàm, index nhiều cột. Bộ tối ưu dựa trên chi phí (CBO) dùng thống kê của bảng để quyết định có dùng index hay không.

### Demo 1: Index nhiều cột cho bộ lọc sản phẩm (F4)

- **Mục đích:** cho thấy index `(category_id, base_price)` biến truy vấn lọc từ quét toàn bảng thành quét theo khoảng trên index.
- **Cách làm:** chạy cùng một truy vấn F4 trong 3 trường hợp: không index, chỉ index `category_id`, index nhiều cột `(category_id, base_price)`. Sau mỗi lần tạo index đều gọi `DBMS_STATS.GATHER_TABLE_STATS`.

  ```sql
  SELECT /*+ GATHER_PLAN_STATISTICS */ product_id, name, base_price
  FROM   products
  WHERE  category_id = :cat
  AND    base_price BETWEEN :min_price AND :max_price
  AND    status = 'ACTIVE';

  SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'));
  ```

- **Kết quả (số giả lập):**

  | Trường hợp | Kế hoạch thực thi | Buffer gets | Thời gian (ms) |
  |---|---|---|---|
  | Không index | `TABLE ACCESS FULL` | 1.842 | 41,6 ± 3,2 |
  | Index `category_id` | `INDEX RANGE SCAN` + `TABLE ACCESS BY INDEX ROWID` | 2.015 | 9,8 ± 1,1 |
  | Index `(category_id, base_price)` | `INDEX RANGE SCAN` + `TABLE ACCESS BY INDEX ROWID` | 64 | 1,9 ± 0,3 |

  Trích kế hoạch thực thi của trường hợp 3:

  ```text
  | Id | Operation                           | Name               | A-Rows | Buffers |
  |  0 | SELECT STATEMENT                    |                    |     41 |      64 |
  |  1 |  TABLE ACCESS BY INDEX ROWID BATCHED| PRODUCTS           |     41 |      64 |
  |* 2 |   INDEX RANGE SCAN                  | IX_PRODUCTS_CAT_PR |     41 |       3 |
  ```

- **Nhận xét:** index chỉ có `category_id` vẫn phải đọc 2.000 dòng của danh mục rồi mới lọc giá. Index nhiều cột lọc cả hai điều kiện ngay trên index, nên chỉ đọc đúng 41 dòng cần trả về.

### Demo 2: Tìm theo tên, khi nào index được dùng (F3)

- **Mục đích:** cho thấy index trên hàm `UPPER(name)` chỉ giúp được khi tìm theo **tiền tố**. Tìm "chứa chuỗi" thì không index B-tree nào giúp được.
- **Cách làm:** có index `ix_products_name_upper ON products (UPPER(name))`. So sánh hai điều kiện `UPPER(name) LIKE 'TAI NGHE%'` và `UPPER(name) LIKE '%TAI NGHE%'`.
- **Kết quả (số giả lập):**

  | Điều kiện | Kế hoạch thực thi | Thời gian (ms) |
  |---|---|---|
  | `LIKE 'TAI NGHE%'` | `INDEX RANGE SCAN` trên `IX_PRODUCTS_NAME_UPPER` | 1,4 ± 0,2 |
  | `LIKE '%TAI NGHE%'` | `TABLE ACCESS FULL` | 48,3 ± 4,0 |

- **Nhận xét:** B-tree sắp theo thứ tự từ trái sang phải. Khi `%` đứng đầu, Oracle không biết bắt đầu đọc từ đâu nên phải quét hết bảng. Muốn tìm kiếm toàn văn bản thì cần Oracle Text, nằm ngoài phạm vi phần này.

### Demo 3: Cái giá khi ghi

- **Mục đích:** đo xem mỗi index làm lệnh `INSERT` chậm đi bao nhiêu.
- **Cách làm:** chèn 10.000 sản phẩm bằng `INSERT … SELECT` vào bảng có lần lượt 0, 2 và 5 index phụ.
- **Kết quả (số giả lập):**

  | Số index phụ | Thời gian chèn 10.000 dòng (ms) | Dung lượng index (MB) |
  |---|---|---|
  | 0 | 412 ± 18 | 0 |
  | 2 | 618 ± 25 | 4,1 |
  | 5 | 1.047 ± 41 | 11,6 |

- **Nhận xét:** thời gian ghi tăng gần tuyến tính theo số index. Không nên đánh index cho mọi cột, chỉ đánh cho các cột thực sự xuất hiện trong điều kiện truy vấn thường dùng.

## 3.2.3 Trên Redis

Redis không có index phụ có sẵn. Mỗi key chỉ tìm được nhanh theo chính tên key. Muốn lọc theo giá hay danh mục có ba cách: quét hết, tự dựng index bằng cấu trúc dữ liệu, hoặc dùng module RediSearch.

### Demo 4: Không có index, phải quét toàn bộ

- **Mục đích:** cho thấy nếu không có index thì Redis phải quét mọi key và lọc phía ứng dụng.
- **Cách làm:** 100.000 sản phẩm lưu dạng JSON `product:{id}`. Ứng dụng gọi `SCAN 0 MATCH product:* COUNT 1000` lặp đến hết, đọc từng document rồi lọc theo danh mục và giá.
- **Kết quả (số giả lập):** 912 ± 37 ms, khoảng 100 lượt `SCAN` và 100.000 lượt đọc document.
- **Nhận xét:** chậm hơn quét toàn bảng của Oracle rất nhiều, vì mọi phép lọc diễn ra ở ứng dụng và phải truyền toàn bộ dữ liệu qua mạng.

### Demo 5: Tự dựng index bằng Sorted Set và Set

- **Mục đích:** cho thấy có thể tự làm index bằng cấu trúc dữ liệu của Redis, và cái giá là ứng dụng phải tự giữ cho index đúng.
- **Cách làm:** mỗi lần thêm hoặc sửa sản phẩm, ứng dụng cập nhật thêm hai key:

  ```text
  ZADD idx:product:price <base_price> <product_id>
  SADD idx:product:cat:<category_id> <product_id>
  ```

  Truy vấn F4: lấy tập theo khoảng giá bằng `ZRANGEBYSCORE`, giao với tập danh mục (`ZINTERSTORE` với `WEIGHTS 1 0`), rồi đọc document.
- **Kết quả (số giả lập):** truy vấn 0,9 ± 0,1 ms. Mỗi lần ghi sản phẩm phải chạy thêm 2 lệnh. Nếu ứng dụng quên cập nhật thì index sai mà Redis không báo lỗi.

### Demo 6: RediSearch

- **Mục đích:** cho thấy RediSearch đưa index phụ "thật" vào Redis, tự cập nhật khi document thay đổi.
- **Cách làm:** dùng index `idx:products` có sẵn trong `init.lua`:

  ```text
  FT.SEARCH idx:products "@categoryId:[5 5] @price:[100000 500000] @status:{ACTIVE}"
    SORTBY price ASC LIMIT 0 20
  FT.PROFILE idx:products SEARCH QUERY "@categoryId:[5 5] @price:[100000 500000]"
  ```

- **Kết quả (số giả lập):** truy vấn 1,6 ± 0,2 ms. Index chiếm 14,2 MB RAM (theo `FT.INFO`). Ghi một document chậm hơn khoảng 30% so với không có index.

## 3.2.4 So sánh

| Tiêu chí | Oracle | Redis | Bằng chứng |
|---|---|---|---|
| Index phụ có sẵn | Có, nhiều loại (B-tree, bitmap, hàm, nhiều cột) | Không. Phải tự dựng hoặc dùng RediSearch | Demo 1, 4 |
| Lọc theo nhiều điều kiện | 1,9 ms với index nhiều cột | 0,9 ms tự dựng index, 1,6 ms RediSearch | Demo 1, 5, 6 |
| Không có index | 41,6 ms (quét toàn bảng trong DB) | 912 ms (quét và lọc ở ứng dụng) | Demo 1, 4 |
| Ai giữ index luôn đúng | DBMS tự làm, trong cùng transaction | Tự dựng: ứng dụng tự làm, dễ sai. RediSearch: tự làm | Demo 5, 6 |
| Truy vấn mới, chưa tính trước | Viết SQL là chạy, CBO tự chọn index | Phải có index tương ứng từ trước | Demo 1, 6 |
| Cái giá khi ghi | Tăng theo số index (×2,5 với 5 index) | Tự dựng: thêm lệnh mỗi lần ghi. RediSearch: chậm hơn ~30% | Demo 3, 6 |
| Nơi lưu index | Trên đĩa, có buffer cache | Trên RAM | Demo 3, 6 |

## 3.2.5 Kết luận cho ShopFlash

Bộ lọc sản phẩm của khách (F4) thay đổi nhiều: thêm điều kiện mới, đổi kiểu sắp xếp. Oracle xử lý được mọi tổ hợp điều kiện mà không phải dựng sẵn cấu trúc riêng, nên ShopFlash dùng Oracle làm nguồn chính cho tìm kiếm và lọc, với index `(category_id, base_price)` và `UPPER(name)`.

Ở những trang chịu tải đọc rất cao như trang chủ hay trang danh mục lúc flash sale, ShopFlash đặt thêm một bản chiếu sản phẩm trên Redis có index RediSearch. Lý do là RediSearch nhanh tương đương và không làm Oracle bị quá tải. Bản chiếu này được dựng lại từ Oracle nên nếu lệch thì không mất dữ liệu gốc.

Cách tự dựng index bằng Sorted Set không được dùng trong ứng dụng, vì ứng dụng phải tự giữ cho index đúng, dễ phát sinh lỗi mà không có lợi gì đáng kể so với RediSearch.

## Tài liệu tham khảo

[1] Oracle, "Indexes and Index-Organized Tables," *Oracle Database Concepts, 23ai*. [Online]. Available: https://docs.oracle.com/en/database/

[2] Redis, "Indexing," *Redis Documentation*. [Online]. Available: https://redis.io/docs/

[3] R. Elmasri and S. B. Navathe, *Fundamentals of Database Systems*, 7th ed. Pearson, 2016, ch. 17.
