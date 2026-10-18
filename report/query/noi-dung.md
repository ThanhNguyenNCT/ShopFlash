# 3.3 Query processing

> Bản chính để ghép báo cáo là `noi-dung.tex` (LaTeX, có hình). File này là bản đọc nhanh trên GitHub.

Người viết: Nguyễn Công Thành

Phần này so sánh cách Oracle và Redis xử lý cùng một truy vấn báo cáo, qua hai chức năng của ShopFlash: F5 (báo cáo doanh thu theo tháng, theo danh mục) và F6 (top sản phẩm bán chạy, khách VIP, sản phẩm chưa từng bán). Mỗi báo cáo được cài trên cả hai DBMS và trả cùng một kết quả, nên khác biệt đo được chỉ đến từ cách DBMS xử lý truy vấn.

## Môi trường đo

| Thành phần | Giá trị |
|---|---|
| Máy | AMD Ryzen 5 7535HS, 15,3 GB RAM, SSD NVMe |
| Hệ điều hành, Docker | Windows 11 (10.0.26300), Docker 29.5.2 (cấp 12 CPU, 7,8 GB RAM) |
| Oracle | Oracle AI Database 26ai Free 23.26.3, image `container-registry.oracle.com/database/free@sha256:f988b0c0…8fba`. Oracle Free giới hạn 2 CPU, 2 GB RAM, 12 GB dữ liệu |
| Redis | Redis Stack 7.4.7 (RediSearch, RedisJSON), image `redis/redis-stack-server@sha256:798ab84d…742a` |
| Dữ liệu | 100.000 đơn, trong đó 87.141 đơn được tính (CONFIRMED, COMPLETED) với 215.807 dòng hàng; 10.001 sản phẩm, 51 danh mục, 20.002 người dùng. Redis: 87.141 document đơn và 215.807 document dòng hàng |

Mỗi phép đo: chạy khởi động 1 lần không tính, sau đó đo 5 lần liên tiếp (không chạy song song), báo cáo trung bình ± độ lệch chuẩn, kèm p50 và p95. Thời gian đo trong backend, quanh lời gọi tới DB (gồm mạng tới container và đọc kết quả, không gồm HTTP tới trình duyệt). Khoảng thời gian của mọi báo cáo: 12 tháng gần nhất (01/11/2025 đến 18/10/2026). Số liệu thô: `results/benchmark-*.csv`. Kế hoạch thực thi đầy đủ: `results/plans/`. Cách chạy lại: `README.md`.

## 3.3.1 Lý thuyết

Xử lý truy vấn là quá trình DBMS biến một câu truy vấn khai báo ("muốn gì") thành một chuỗi thao tác cụ thể ("làm thế nào") rồi thực thi nó. Theo giáo trình, quá trình gồm ba bước:

1. **Phân tích và dịch:** kiểm tra cú pháp, ngữ nghĩa, dịch câu SQL sang biểu thức đại số quan hệ.
2. **Tối ưu:** sinh nhiều kế hoạch thực thi tương đương và chọn kế hoạch có chi phí ước lượng thấp nhất.
3. **Thực thi:** chạy kế hoạch đã chọn, trả kết quả.

Các khái niệm dùng trong phần này:

- **Kế hoạch thực thi (execution plan):** cây các toán tử (quét bảng, quét index, join, gom nhóm, sắp xếp). Cùng một câu SQL có thể có nhiều kế hoạch cho cùng kết quả nhưng chi phí rất khác nhau.
- **Ba thuật toán join:**
  - *Nested loops:* với mỗi dòng của bảng ngoài, tìm các dòng khớp ở bảng trong (thường qua index). Tốt khi bảng ngoài ít dòng.
  - *Hash join:* dựng bảng băm trên bảng nhỏ hơn rồi quét bảng kia một lần. Tốt khi cả hai bảng lớn và điều kiện là phép bằng.
  - *Sort-merge join:* sắp hai bảng theo khóa join rồi trộn. Tốt khi dữ liệu đã có thứ tự sẵn.
- **Tối ưu dựa trên chi phí (cost-based optimization):** chi phí của một kế hoạch được ước lượng từ **thống kê** (số dòng, số giá trị khác nhau, phân bố dữ liệu). Ước lượng số dòng (cardinality) sai thì có thể chọn sai kế hoạch.
- **Biến đổi truy vấn:** trước khi chọn kế hoạch, bộ tối ưu có thể viết lại truy vấn: gom nhóm sớm trước khi join, biến subquery `NOT EXISTS` thành anti-join, chỉ tính subquery không phụ thuộc một lần.
- **Pipeline có sẵn, không có bộ tối ưu:** một số hệ thống (như Redis) chỉ thực thi các bước đúng theo thứ tự người viết lệnh, không tự chọn hay đổi kế hoạch.

## 3.3.2 Trên Oracle

Oracle có bộ tối ưu dựa trên chi phí (CBO). Với mỗi câu SQL, CBO dùng thống kê trong data dictionary để ước lượng số dòng ở từng bước, thử các thứ tự join, thuật toán join và cách truy cập (quét bảng hay index), rồi chọn kế hoạch rẻ nhất. Kế hoạch được lưu trong shared pool để dùng lại cho các lần chạy sau.

Cách xem kế hoạch trong phần này: thêm hint `GATHER_PLAN_STATISTICS` vào câu SQL, chạy, rồi gọi `DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST')` trên cùng session. Cách này cho cả số dòng ước lượng (`E-Rows`) lẫn số dòng thực tế (`A-Rows`), thời gian thực tế (`A-Time`) và số block đọc (`Buffers`) của từng bước. Toàn bộ câu SQL: `oracle/queries.sql`.

### Demo 1: Kế hoạch CBO tự chọn cho báo cáo join 4 bảng

- **Mục đích:** cho thấy CBO tự chọn thuật toán join và tự viết lại truy vấn để giảm khối lượng công việc.
- **Cách làm:** chạy báo cáo top sản phẩm (F6). Câu SQL join `orders`, `order_items`, `products`, `categories`, gom nhóm theo sản phẩm, lấy 10 sản phẩm bán nhiều nhất. Không thêm hint nào.
- **Kết quả:** kế hoạch `2314863829`, 155,2 ± 23,6 ms, đọc 5.104 block.

  ```text
  | Id | Operation                  | Name        | E-Rows | A-Rows | Buffers |
  |  4 |     HASH JOIN              |             |   9004 |   9001 |    5104 |
  |  5 |      TABLE ACCESS FULL     | CATEGORIES  |     51 |     51 |       6 |
  |  6 |      HASH JOIN             |             |   9004 |   9001 |    5098 |
  |  7 |       VIEW                 | VW_GBC_17   |   9001 |   9001 |    4851 |
  |  8 |        HASH GROUP BY       |             |   9001 |   9001 |    4851 |
  | 10 |          HASH JOIN         |             |    213K|    215K|    4851 |
  | 11 |           TABLE ACCESS FULL| ORDERS      |  87141 |  87141 |    2139 |
  | 12 |           TABLE ACCESS FULL| ORDER_ITEMS |    247K|    247K|    2712 |
  | 13 |       TABLE ACCESS FULL    | PRODUCTS    |  10001 |  10001 |     247 |
  ```

- **Nhận xét:**
  - CBO chọn **hash join** cho cả ba phép join và quét toàn bảng, vì báo cáo cần gần hết dữ liệu (87.141 trên 100.000 đơn). Dùng index để lấy từng dòng khi đó sẽ đắt hơn.
  - CBO đã **viết lại truy vấn**. View `VW_GBC_17` là phép biến đổi *group-by placement*: gom 215 nghìn dòng hàng theo sản phẩm *trước* (còn 9.001 dòng), rồi mới join với `products` và `categories`. Câu SQL gốc viết join trước rồi mới `GROUP BY`.
  - Số dòng ước lượng gần đúng tuyệt đối (9.004 so với 9.001), nhờ thống kê đã thu thập.

### Demo 2: Ép phương pháp join bằng hint

- **Mục đích:** chứng minh lựa chọn của CBO là đúng, bằng cách ép các thuật toán join khác và đo.
- **Cách làm:** chạy lại báo cáo doanh thu theo danh mục, top sản phẩm và khách VIP với 4 biến thể: AUTO (không hint) và 3 hint `LEADING(o oi p c) USE_NL(oi p c)`, `USE_HASH`, `USE_MERGE`. `LEADING` giữ cố định thứ tự join, để hint thuật toán áp lên đúng từng bảng.
- **Kết quả:**

  | Báo cáo | AUTO | NL | HASH | MERGE |
  |---|---|---|---|---|
  | Doanh thu theo danh mục | **208,5 ± 40,7** | 1.180,9 ± 227,9 | 233,2 ± 50,6 | 411,9 ± 82,4 |
  | Top sản phẩm | **155,2 ± 23,6** | 852,9 ± 117,1 | 233,5 ± 28,6 | 573,5 ± 87,7 |
  | Khách VIP (join 2 bảng) | 193,7 ± 24,4 | 271,7 ± 16,9 | **187,2 ± 6,8** | 250,6 ± 36,9 |

  Đơn vị: ms. Kế hoạch NL của top sản phẩm (`2442890914`) đọc **681 nghìn block**, gấp 133 lần kế hoạch AUTO (5.104 block):

  ```text
  | Id | Operation                          | Name                 | Starts | A-Rows | Buffers |
  | 10 |           TABLE ACCESS FULL        | ORDERS               |      1 |  87141 |    2139 |
  | 12 |            INDEX RANGE SCAN        | IX_ORDER_ITEMS_ORDER |  87141 |    215K|   26868 |
  | 14 |           INDEX UNIQUE SCAN        | PK_PRODUCTS          |    215K|    215K|    215K |
  | 16 |        TABLE ACCESS BY INDEX ROWID | CATEGORIES           |    215K|    215K|    215K |
  ```

  Ảnh: `images/3.3-04-d2-optimizer.png`.

- **Nhận xét:**
  - Ép nested loops làm hai báo cáo join 4 bảng chậm đi 5,5–5,7 lần. Cột `Starts` cho thấy lý do: index của `order_items` được dò 87.141 lần, index của `products` và `categories` mỗi cái 215 nghìn lần, mỗi lần đọc ít nhất một block.
  - Kế hoạch AUTO nhanh nhất hoặc ngang với kế hoạch tốt nhất ở mọi báo cáo. Ở báo cáo khách VIP, HASH nhanh hơn AUTO 6,5 ms nhưng nằm trong độ lệch chuẩn, vì AUTO cũng chọn hash join. Lập trình viên chỉ viết câu SQL, CBO tự chọn được cách chạy tốt.

### Demo 3: Thống kê quyết định kế hoạch

- **Mục đích:** cho thấy CBO phụ thuộc vào thống kê: thống kê mất thì ước lượng sai, kế hoạch đổi và chậm hơn.
- **Cách làm:** với báo cáo top sản phẩm:
  1. Lấy kế hoạch khi có thống kê.
  2. Gọi `DBMS_STATS.DELETE_TABLE_STATS` cho 5 bảng rồi lấy lại kế hoạch.
  3. Gọi `DBMS_STATS.GATHER_TABLE_STATS` rồi lấy lại kế hoạch.

  Cả hai thủ tục đều phải truyền `no_invalidate => FALSE`. Nếu để mặc định, Oracle dùng lại kế hoạch cũ đang lưu trong shared pool và không thấy thay đổi gì.

- **Kết quả:**

  | Trạng thái | Plan hash | Phép join chính | Ước lượng sau gom nhóm (thực tế 9.001) | Buffers | A-Time |
  |---|---|---|---|---|---|
  | Có thống kê | 2314863829 | HASH JOIN | 9.004 | 5.104 | 0,18 s |
  | Đã xóa thống kê | **56634007** | **MERGE JOIN** | **285.000** | **90.978** | **0,58 s** |
  | Thu thập lại | 2314863829 | HASH JOIN | 9.003 | 5.104 | 0,21 s |

  Trích kế hoạch khi đã xóa thống kê:

  ```text
  | Id | Operation                              | Name                     | Starts | E-Rows | A-Rows | Buffers |
  |  5 |      MERGE JOIN                        |                          |      1 |    285K|   9001 |   90978 |
  | 12 |             TABLE ACCESS BY INDEX ROWID| ORDERS                   |      2 |  89730 |  87141 |   88010 |
  | 13 |              INDEX RANGE SCAN          | IX_ORDERS_STATUS_CREATED |      2 |      2 |  87141 |     916 |
  Note
     - dynamic statistics used: dynamic sampling (level=2)
  ```

  File: `results/plans/stats-{before-delete,after-delete,after-gather}-top-products.txt`. Ảnh: `images/3.3-05-d3-thong-ke.png`.

- **Nhận xét:**
  - Không có thống kê, Oracle phải lấy mẫu nhanh lúc parse (dynamic sampling). Ước lượng lệch nhiều:
    - Index `IX_ORDERS_STATUS_CREATED` được ước lượng trả về 2 dòng, thực tế 87.141.
    - Kết quả gom nhóm được ước lượng 285 nghìn dòng, thực tế 9.001.
  - Do tin rằng điều kiện lọc rất chọn lọc, CBO đi qua index rồi đọc từng dòng của `orders` (88.010 block, hơn 17 lần cả kế hoạch cũ) và chuyển sang merge join. Thời gian tăng hơn 3 lần.
  - Thu thập lại thống kê thì kế hoạch quay về đúng như ban đầu. Kế hoạch tốt phụ thuộc vào thống kê đúng, nên trong vận hành cần để job thu thập thống kê tự động của Oracle chạy, đặc biệt sau khi nạp nhiều dữ liệu.

### Demo 4: Subquery được biến đổi thế nào

- **Mục đích:** cho thấy Oracle chạy được subquery ngay trong SQL và biến đổi nó thành phép toán hiệu quả.
- **Cách làm:** hai báo cáo F6:
  - **Khách VIP:** khách có tổng chi lớn hơn mức trung bình, dùng subquery trong `HAVING`.
  - **Sản phẩm chưa từng bán:** dùng `NOT EXISTS`.
- **Kết quả:**

  | Báo cáo | Thời gian (ms) | Toán tử chính trong kế hoạch |
  |---|---|---|
  | Khách VIP | 193,7 ± 24,4 | `FILTER` + `SORT AGGREGATE` chạy 1 lần (`Starts = 1`) |
  | Sản phẩm chưa từng bán | 139,3 ± 22,8 | `HASH JOIN ANTI` + `INDEX FAST FULL SCAN` trên `IX_ORDER_ITEMS_PRODUCT` |

  Thời gian gồm 2 câu: câu lấy trang dữ liệu và câu tóm tắt (tổng số dòng, chi tiêu trung bình) để hiển thị và phân trang. Ảnh: `images/3.3-07-d5-subquery.png`.

- **Nhận xét:**
  - Với khách VIP, subquery tính trung bình không phụ thuộc dòng ngoài, nên Oracle chỉ tính **một lần** rồi dùng để lọc 19.274 khách, còn lại 6.049 khách.
  - Với sản phẩm chưa từng bán, Oracle biến `NOT EXISTS` thành **anti-join** dạng hash. Nó chỉ quét index trên `order_items.product_id` (không cần đọc bảng) để tìm 953 sản phẩm chưa xuất hiện trong dòng hàng nào.
  - Người viết không cần biết các biến đổi này, CBO tự làm.

## 3.3.3 Trên Redis

Redis là kho key-value, không có SQL, không có join và không có bộ tối ưu. Để làm báo cáo, ShopFlash dùng module **RediSearch** trên dữ liệu JSON (RedisJSON):

- **Read model đã denormalize.** Dữ liệu được dựng lại từ Oracle thành hai loại document:
  - `order:analytics:{orderId}`: một đơn, đã kèm tên và email khách.
  - `order:line:{orderItemId}`: một dòng hàng, đã kèm tên sản phẩm, mã và tên danh mục, trạng thái và ngày của đơn.

  Join được làm sẵn một lần lúc dựng, không làm lúc truy vấn. Dựng lại 100.000 đơn mất khoảng 20–35 giây (`POST /api/admin/reports/analytics/rebuild`).
- **Index RediSearch** `idx:order-analytics` và `idx:order-line-analytics` (tạo trong `db/redis/init.lua`): `status` là TAG, `createdAt` là NUMERIC, các trường dùng để gom nhóm, cộng dồn hoặc trả về khai báo `SORTABLE`.
- **`FT.AGGREGATE`** chạy một pipeline cố định: lọc theo index, rồi `GROUPBY`/`REDUCE`, `SORTBY`, `APPLY`/`FILTER`, `LIMIT`, đúng theo thứ tự các bước trong lệnh.

Toàn bộ lệnh: `redis/commands.txt`. Để so sánh, mỗi báo cáo trên Redis được cài theo hai cách:

- **AGGREGATE:** để Redis tự gom nhóm và sắp xếp, 1 lệnh.
- **CLIENT:** Redis chỉ lọc theo index và trả document thô theo từng trang (`WITHCURSOR`, 10.000 document mỗi trang). Ứng dụng Java tự gom nhóm và sắp xếp. Đây là cách làm khi coi Redis là kho key-value thuần.

### Demo 5: Cùng kết quả trên hai nguồn

- **Mục đích:** đảm bảo các phép so sánh hiệu năng là công bằng: hai nguồn phải trả đúng cùng một kết quả.
- **Cách làm:** API `/compare` chạy một báo cáo trên Oracle và Redis rồi so từng dòng. Kiểm tra tự động bằng `tests/contract_test.py` (146 kiểm tra) trên cả 4 báo cáo Redis hỗ trợ.
- **Kết quả:** cả 4 báo cáo khớp từng dòng, kể cả tổng tiền (VND). Ảnh: `images/3.3-03-d1-ket-qua-khop.png`.
- **Nhận xét:** kết quả khớp, nên mọi khác biệt về thời gian ở các demo sau đến từ cách xử lý truy vấn, không phải từ việc tính khác nhau.

### Demo 6: Redis chạy đúng như lệnh viết (FT.PROFILE)

- **Mục đích:** cho thấy Redis không có bộ tối ưu: kế hoạch chính là thứ tự các bước người viết lệnh đặt ra.
- **Cách làm:** chạy `FT.PROFILE … AGGREGATE` cho báo cáo doanh thu theo danh mục:

  ```text
  FT.AGGREGATE idx:order-line-analytics '@status:{CONFIRMED|COMPLETED} @createdAt:[1761930000 (1792342800]' TIMEOUT 0
    GROUPBY 2 @categoryId @categoryName
    REDUCE SUM 1 @quantity AS quantity REDUCE SUM 1 @lineTotal AS revenue
    SORTBY 4 @revenue DESC @categoryId ASC
  ```

- **Kết quả (trích `results/plans/revenue-by-category-redis-AGGREGATE.txt`):**

  ```text
  Iterators profile:
    Type=INTERSECT, Counter=215807          giao hai điều kiện lọc
      Type=UNION, Query type=TAG, Counter=215807       status: confirmed 61.781 + completed 154.026
      Type=UNION, Query type=NUMERIC, Counter=215807   createdAt: hợp nhiều khoảng số
  Result processors profile:
    Type=Index,   Counter=215807
    Type=Grouper, Counter=51
    Type=Sorter,  Counter=51
  ```

- **Nhận xét:**
  - Kết quả profile chỉ là danh sách các bước theo đúng thứ tự trong lệnh: lọc theo index, gom nhóm, sắp xếp. Không có lựa chọn thuật toán hay biến đổi nào.
  - Muốn chạy khác đi (ví dụ lọc thêm điều kiện trước khi gom), người viết phải tự viết lại lệnh.
  - Mọi thứ CBO tự làm trong Demo 1 đều đã được lập trình viên làm trước: join lúc dựng read model, chọn trường `SORTABLE` lúc tạo index.

### Demo 7: Đẩy tính toán xuống Redis hay kéo về ứng dụng (round-trip)

- **Mục đích:** đo cái giá của việc kéo dữ liệu về ứng dụng để tính, so với để Redis tự tính.
- **Cách làm:** chạy 4 báo cáo theo hai cách AGGREGATE và CLIENT, đếm số round-trip tới Redis.
- **Kết quả:**

  | Báo cáo | AGGREGATE (ms) | CLIENT (ms) | Round-trip CLIENT | CLIENT chậm hơn |
  |---|---|---|---|---|
  | Doanh thu theo tháng | 199,9 ± 22,1 | 820,1 ± 55,2 | 9 | 4,1 lần |
  | Doanh thu theo danh mục | 373,4 ± 48,5 | 2.597,1 ± 382,3 | 22 | 7,0 lần |
  | Top sản phẩm | 850,7 ± 32,4 | 4.851,2 ± 1.463,0 | 22 | 5,7 lần |
  | Khách VIP | 875,4 ± 76,8 | 2.362,9 ± 517,2 | 9 | 2,7 lần |

  Ảnh: `images/3.3-06-d4-round-trip.png`.

- **Nhận xét:**
  - Theo cách CLIENT, toàn bộ 215.807 dòng hàng (hoặc 87.141 đơn) phải đi qua mạng về ứng dụng, qua 9–22 lần gọi, chỉ để ra 51 dòng kết quả.
  - Để Redis gom nhóm ngay tại chỗ chứa dữ liệu thì chỉ cần 1 lần gọi và trả về đúng số dòng cần.
  - Báo cáo có ít nhóm (theo danh mục, 51 nhóm) được lợi nhiều nhất. Độ lệch chuẩn của CLIENT cũng lớn hơn hẳn, vì thời gian phụ thuộc vào mạng và bộ nhớ của ứng dụng.

### Demo 8: Không có subquery

- **Mục đích:** cho thấy giới hạn về khả năng biểu đạt của Redis so với SQL.
- **Cách làm:**
  - **Khách VIP:** `FT.AGGREGATE` không có subquery, nên phải chạy 2 lượt. Lượt 1 tính trung bình tổng chi mỗi khách. Lượt 2 gom nhóm theo khách và `FILTER '@totalSpent > <trung bình>'`, với giá trị trung bình do ứng dụng chèn vào lệnh.
  - **Sản phẩm chưa từng bán:** cần anti-join.
- **Kết quả:**
  - Khách VIP: 875,4 ± 76,8 ms cho 2 lượt (Oracle: 193,7 ± 24,4 ms).
  - Sản phẩm chưa từng bán: **không làm được**. API trả `meta.supported = false`. Ảnh: `images/3.3-07-d5-subquery.png`.
- **Nhận xét:**
  - Ứng dụng phải tự tách truy vấn lồng nhau thành nhiều bước. Giữa hai lượt, dữ liệu có thể thay đổi, nên hai lượt có thể không nhìn cùng một trạng thái.
  - "Sản phẩm chưa từng bán" cần so tập sản phẩm với tập dòng hàng. Read model chỉ chứa dòng hàng đã bán, Redis không có phép anti-join. Muốn làm phải dựng thêm một read model khác cho đúng câu hỏi này.

### Demo 9: Read model không tự cập nhật

- **Mục đích:** cho thấy cái giá của việc tách bản đọc riêng: dữ liệu báo cáo trên Redis bị trễ so với Oracle.
- **Cách làm:**
  1. Chèn 1 đơn mới vào Oracle bằng SQL.
  2. Gọi `GET /analytics/status` và `/revenue-by-month/compare`.
  3. Dựng lại read model.
  4. So lại.

  Kịch bản này nằm trong `tests/contract_test.py` (phần D6).
- **Kết quả:** sau khi chèn, `inSync = false` và compare báo lệch ở tháng hiện tại. Sau khi dựng lại thì `inSync = true` và hai nguồn khớp. Ảnh `images/3.3-08-d6-nhat-quan.png` chụp tab Nhất quán ở trạng thái đã đồng bộ.
- **Nhận xét:** truy vấn trên Oracle luôn đọc dữ liệu mới nhất đã commit. Read model Redis chỉ đúng tới lần dựng gần nhất, và người vận hành phải tự quyết định khi nào dựng lại.

### Điều phát hiện khi cài đặt

- **Phải tự khai báo `SORTABLE`.** Nếu trường dùng để `GROUPBY` hoặc `REDUCE` không được khai báo `SORTABLE`, `FT.AGGREGATE` phải đọc lại JSON của từng document. Báo cáo theo danh mục khi đó mất khoảng 1,5 s, khai báo xong còn 0,15–0,33 s (đo phía server bằng `INFO commandstats`). Bên Oracle, CBO tự chọn cách truy cập; bên Redis, lựa chọn này là việc thiết kế của lập trình viên.
- **Phải kèm `TIMEOUT 0`.** Redis Stack mặc định `TIMEOUT 500` với `ON_TIMEOUT return`: lệnh quá 500 ms sẽ trả **kết quả thiếu mà không báo lỗi**. Trong lúc thử nghiệm, có lần số đơn theo trạng thái chỉ ra 2.299 vì lý do này.
- **Redis chạy từng lệnh một.** Trang Báo cáo gọi 5 báo cáo cùng lúc thì các lệnh `FT.AGGREGATE` phải xếp hàng chờ nhau. Vì vậy số liệu so sánh chỉ lấy từ các lần đo chạy lần lượt.
- **Giới hạn số.** `SUM` và `AVG` của RediSearch in ra 12 chữ số có nghĩa. Dữ liệu hiện tại là VND tròn và mỗi tổng nhỏ hơn 10^12 nên vẫn khớp Oracle, nhưng tổng lớn hơn sẽ mất độ chính xác.

## 3.3.4 So sánh

Thời gian của các báo cáo, 5 lần đo, đơn vị ms:

| Báo cáo | Oracle AUTO | Oracle NL (ép) | Redis AGGREGATE | Redis CLIENT |
|---|---|---|---|---|
| Doanh thu theo tháng | **78,6 ± 17,4** | – | 199,9 ± 22,1 | 820,1 ± 55,2 |
| Doanh thu theo danh mục | **208,5 ± 40,7** | 1.180,9 ± 227,9 | 373,4 ± 48,5 | 2.597,1 ± 382,3 |
| Top sản phẩm | **155,2 ± 23,6** | 852,9 ± 117,1 | 850,7 ± 32,4 | 4.851,2 ± 1.463,0 |
| Khách VIP | **193,7 ± 24,4** | 271,7 ± 16,9 | 875,4 ± 76,8 | 2.362,9 ± 517,2 |
| Sản phẩm chưa từng bán | **139,3 ± 22,8** | – | không hỗ trợ | không hỗ trợ |

p50 và p95 của từng biến thể: `results/benchmark-summary.md`. Biểu đồ vẽ từ bảng này nằm trong bản LaTeX (`noi-dung.tex`).

| Tiêu chí | Oracle | Redis (RediSearch) | Bằng chứng |
|---|---|---|---|
| Ngôn ngữ truy vấn | SQL khai báo: join, subquery, `NOT EXISTS`, Top-N | Pipeline `FT.AGGREGATE` theo thứ tự cố định, không join, không subquery | Demo 4, 6, 8 |
| Bộ tối ưu | CBO chọn thuật toán join, cách truy cập, tự viết lại truy vấn (group-by placement, anti-join) | Không có. Lệnh viết thế nào chạy thế ấy | Demo 1, 6 |
| Kế hoạch tự chọn so với cách tệ | AUTO nhanh hơn NL ép 5,5–5,7 lần | Lập trình viên tự chọn: AGGREGATE nhanh hơn CLIENT 2,7–7 lần | Demo 2, 7 |
| Phụ thuộc thống kê | Mất thống kê: ước lượng 285K thay vì 9K dòng, đổi kế hoạch, đọc gấp 17,8 lần block | Không dùng thống kê. Hiệu năng phụ thuộc thiết kế index (`SORTABLE`) | Demo 3; điều phát hiện |
| Xem kế hoạch | `DBMS_XPLAN.DISPLAY_CURSOR`: E-Rows, A-Rows, Buffers từng bước | `FT.PROFILE`: số document qua từng bước | Demo 1, 6 |
| Join | Lúc truy vấn, 3 thuật toán | Không có. Join sẵn lúc dựng read model (denormalize) | Demo 1, 6 |
| Thời gian báo cáo (100k đơn) | 79–209 ms | 200–875 ms (AGGREGATE) | Bảng trên |
| Subquery | 1 câu, subquery không phụ thuộc chỉ tính 1 lần | VIP: 2 lượt, ứng dụng chuyển giá trị giữa hai lượt. `NOT EXISTS`: không làm được | Demo 4, 8 |
| Độ mới của dữ liệu | Luôn đọc dữ liệu đã commit mới nhất | Chỉ đúng tới lần dựng read model gần nhất (dựng lại mất 20–35 s) | Demo 9 |
| Câu hỏi mới chưa tính trước | Viết thêm câu SQL là chạy | Có thể phải đổi read model, index và dựng lại | Demo 8 |

**Nhận xét chung:**

- Với truy vấn phân tích (lọc gần hết dữ liệu, join nhiều bảng, gom nhóm, subquery), Oracle nhanh hơn Redis ở mọi báo cáo đo được, từ 1,8 đến 5,5 lần, dù Oracle Free bị giới hạn 2 CPU và 2 GB RAM.
- Lý do không nằm ở việc đĩa hay RAM. Oracle có bộ tối ưu dựa trên thống kê, có hash join và gom nhóm bằng bảng băm phù hợp với khối dữ liệu lớn, và tự viết lại truy vấn. Redis xử lý từng lệnh trên một luồng và chỉ có một pipeline cố định.
- Redis mạnh ở truy cập theo key và truy vấn ngắn, không phải ở quét và gom hàng trăm nghìn document.
- Hai điểm khác biệt quan trọng nhất về cách xử lý truy vấn:
  1. Ở Oracle, **DBMS** quyết định cách chạy. Ở Redis, **lập trình viên** quyết định, qua cách thiết kế read model, index và lệnh.
  2. Oracle chạy được những câu hỏi chưa tính trước. Redis chỉ trả lời tốt những câu hỏi đã được chuẩn bị dữ liệu sẵn.

## 3.3.5 Kết luận cho ShopFlash

Báo cáo doanh thu (F5) và top sản phẩm, khách VIP, sản phẩm chưa bán (F6) là truy vấn phân tích trên dữ liệu đơn hàng: cần join nhiều bảng, gom nhóm, subquery, và cần số liệu đúng tới đơn vừa đặt. ShopFlash chạy các báo cáo này trên **Oracle**:

- Trang Tổng quan quản trị và trang Báo cáo mặc định đọc từ Oracle.
- Oracle nhanh hơn ở mọi báo cáo đo được.
- Oracle làm được cả báo cáo cần anti-join.
- Oracle không cần một bản sao dữ liệu phải dựng lại.
- Khi nhóm thêm báo cáo mới, chỉ cần viết thêm câu SQL.

Read model analytics trên Redis vẫn được giữ trong ứng dụng để phục vụ trang So sánh Oracle–Redis. Nó minh họa cái giá của cách làm "tính sẵn cho câu hỏi đã biết": phải denormalize, phải tự thiết kế index `SORTABLE`, phải dựng lại khi dữ liệu đổi. Nếu sau này có một chỉ số cần đọc rất nhiều lần với độ trễ thấp (ví dụ doanh thu đang chạy trong flash sale), cách hợp lý trên Redis là cộng dồn sẵn vào một key mỗi khi có đơn, đọc theo key, chứ không phải chạy `FT.AGGREGATE` trên toàn bộ đơn hàng.

## Tài liệu tham khảo

[1] A. Silberschatz, H. F. Korth, and S. Sudarshan, *Database System Concepts*, 7th ed. McGraw-Hill, 2020, ch. 15–16.

[2] Oracle, "Query Optimizer Concepts," *Oracle Database SQL Tuning Guide, 23ai*. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/23/tgsql/

[3] Oracle, "Optimizer Statistics Concepts" and "DBMS_STATS," *Oracle Database SQL Tuning Guide* and *PL/SQL Packages and Types Reference, 23ai*. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/23/

[4] Oracle, "DBMS_XPLAN," *Oracle Database PL/SQL Packages and Types Reference, 23ai*. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/

[5] Redis, "Aggregations," *Redis Query Engine Documentation*. [Online]. Available: https://redis.io/docs/latest/develop/interact/search-and-query/advanced-concepts/aggregations/

[6] Redis, "FT.PROFILE" and "FT.AGGREGATE," *Redis Commands*. [Online]. Available: https://redis.io/docs/latest/commands/
