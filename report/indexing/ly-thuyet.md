# Ghi chú lý thuyết Indexing (TV2)

Tóm tắt từng bài học, dùng làm nguyên liệu cho phần Lý thuyết của mục 3.2. Chưa phải văn bản báo cáo.

## Bài 1. Index là gì và vì sao nhanh

- Oracle lưu bảng thành các **block** (mặc định 8 KB). Chi phí truy vấn chủ yếu là số block phải đọc.
- Không có index: **Full Table Scan**, đọc mọi block. Chi phí tăng tuyến tính theo kích thước bảng (O(n)).
- **Index** là cấu trúc phụ, **đã sắp xếp** theo cột được đánh index, mỗi mục trỏ về dòng bằng **ROWID** (block nào, dòng nào). Tìm theo B-tree chỉ đi qua vài tầng: chi phí tăng theo log n. Ví dụ 200.000 dòng: khoảng 1.500 block (full scan) so với khoảng 4 block (index).
- **Độ chọn lọc (selectivity):** index chỉ có lợi khi điều kiện lọc ra ít dòng. Full Scan đọc tuần tự nhiều block mỗi lần (rẻ); đi qua index thì đọc từng block lẻ cho mỗi dòng (đắt). Lọc ra phần lớn bảng (vd. `status = 'ACTIVE'` khoảng 95%, `in_stock = 1` khoảng 86%) thì Full Scan nhanh hơn, và optimizer tự chọn Full Scan dù có index.
- **Cái giá:** index tốn dung lượng riêng; mỗi INSERT/UPDATE/DELETE phải cập nhật bảng và mọi index liên quan, chi phí tỉ lệ với số index. Vì vậy chỉ đánh index theo câu hỏi người dùng thường đặt ra.
- Ứng dụng cho ShopFlash: không nên đánh index riêng cho các cột ít giá trị như `status`, `in_stock`.

## Bài 2. B-tree chi tiết

- Cấu tạo: gốc → nhánh → lá. Cây **cân bằng** (mọi lá cách gốc cùng số tầng, Oracle gọi số tầng trên lá là `BLEVEL`). Các lá **nối thành chuỗi**, nên tìm được điểm đầu thì đi ngang để lấy cả khoảng.
- Làm tốt: bằng (`INDEX UNIQUE SCAN`), khoảng và bắt đầu bằng (`INDEX RANGE SCAN`), sắp xếp (đọc theo thứ tự lá, không cần `SORT ORDER BY`, phân trang dừng sớm), MIN/MAX.
- **Index nhiều cột:** xếp theo cột 1, trong cột 1 xếp theo cột 2 (như danh bạ theo Họ rồi Tên). Dùng hiệu quả khi câu hỏi có **cột đầu**. Quy tắc: cột so sánh bằng đặt trước, cột khoảng hoặc sắp xếp đặt sau. Ví dụ F4 "danh mục = 5, giá trong khoảng, sắp theo giá": `(category_id, price)` tốt hơn hẳn `(price, category_id)`. Index skip scan cho phép bỏ cột đầu nhưng chỉ hiệu quả khi cột đầu có rất ít giá trị.
- `ix_products_category_status (category_id, status)` không giúp câu chỉ có `status = 'ACTIVE'`: thiếu cột đầu, lại lọc ra khoảng 95% bảng.
- **Index trên hàm:** `CREATE INDEX ... (UPPER(name))`. Câu truy vấn phải dùng đúng biểu thức `UPPER(name)` thì mới dùng được; `name LIKE 'Tai%'` không dùng index này (và còn khác nghĩa vì phân biệt hoa thường).
- **B-tree bó tay:** `LIKE '%từ%'` (ký tự đại diện ở đầu), bọc cột trong hàm không khớp index, so sánh khác kiểu (ngầm đổi kiểu), phủ định `!=`, `IS NULL` (B-tree không lưu dòng có toàn bộ khóa NULL). Tìm "bắt đầu bằng" thì B-tree trên `UPPER(name)` là đủ; tìm "chứa từ" thì cần index toàn văn.
- Index "đủ cột": nếu mọi cột cần đều có trong index, Oracle không phải ghé bảng (bỏ bước `TABLE ACCESS BY INDEX ROWID`).

## Bài 3. Optimizer quyết định có dùng index hay không

- SQL chỉ nói "muốn gì"; **optimizer** chọn "làm thế nào". Oracle dùng **Cost-Based Optimizer (CBO)**: ước lượng chi phí (chủ yếu số block đọc, cộng CPU) của từng cách và chọn cách rẻ nhất. Có index chưa chắc đã được dùng.
- Ước lượng dựa vào **thống kê** (`DBMS_STATS`): số dòng, số block, số giá trị khác nhau (NDV), min/max, **histogram** khi dữ liệu lệch, và thống kê index (BLEVEL, số lá, clustering factor). Số dòng ước lượng gọi là **cardinality**. Ví dụ `category_id` có 50 giá trị → `category_id = 5` ước khoảng 200.000 / 50 = 4.000 dòng.
- Thống kê sai hoặc cũ → ước lượng sai → kế hoạch sai (TV3 đã chứng minh ở mục 3.3). Cách xử lý: thu thập lại thống kê, thêm histogram cho cột lệch.
- Lấy gần hết bảng (vd. `price` trong toàn bộ khoảng giá) → optimizer tự chọn Full Scan dù có index.
- **Clustering factor:** đo mức các dòng (theo thứ tự index) nằm rải rác trong bảng. Gần bằng số block = gọn, ghé bảng rẻ; gần bằng số dòng = rải rác, ghé bảng đắt, optimizer ngại dùng index.
- **Kế hoạch thực thi:** đọc từ trong (thụt lề sâu) ra ngoài. So `E-Rows` (ước lượng) với `A-Rows` (thực tế); `Buffers` = số block thực đọc (ổn định hơn thời gian, nên dùng làm thước đo chính); `access` = điều kiện dùng để đi trong index, `filter` = đọc ra rồi mới loại. Không có `SORT ORDER BY` nghĩa là index đã cho đúng thứ tự.
- `EXPLAIN PLAN` chỉ là dự định; `DBMS_XPLAN.DISPLAY_CURSOR(..., 'ALLSTATS LAST')` sau câu có `/*+ gather_plan_statistics */` là kế hoạch đã chạy thật.
- **Hint** `/*+ FULL(p) */`, `/*+ INDEX(p ten_index) */` dùng để ép cách truy cập khi thí nghiệm so sánh; ứng dụng thật để optimizer tự chọn.

## Bài 4. Bitmap index và tìm kiếm toàn văn

### Bitmap

- Mỗi giá trị của cột có một chuỗi bit (mỗi bit ứng một dòng). Kết hợp điều kiện = AND/OR trên bit, rất nhanh; nén được rất nhỏ. Hợp với cột ít giá trị.
- Điểm yếu quyết định: khi ghi, Oracle khóa cả một đoạn bitmap chứa nhiều dòng → hai giao dịch sửa hai dòng khác nhau vẫn có thể phải chờ nhau, thậm chí deadlock. Vì vậy bitmap dùng cho kho dữ liệu (ghi theo lô, chủ yếu đọc), không dùng cho bảng OLTP.
- ShopFlash: `in_stock`, `status` ít giá trị nhưng tồn kho đổi theo từng đơn, flash sale có nhiều người mua cùng lúc → **không chọn bitmap**. Cần kiểm tra bản Oracle Free có hỗ trợ bitmap index không trước khi thí nghiệm.

### Tìm kiếm toàn văn

- `LIKE '%pro%'`: B-tree không dùng được (phải Full Scan), không hiểu ranh giới từ ("pro" khớp cả "projector"), không xếp được mức độ liên quan.
- **Inverted index:** tách từ (tokenize), chuẩn hóa (chữ thường…), rồi lưu ngược "từ → danh sách sản phẩm" (posting list). Tìm nhiều từ = lấy phần chung các danh sách. Ví dụ: `tai` → [1, 3], `pro` → [1, 3] ⇒ "tai pro" → [1, 3]. Từ điển sắp xếp sẵn nên tìm tiền tố được. Có điểm liên quan (TF-IDF, BM25: từ hiếm quan trọng hơn, từ lặp nhiều điểm cao hơn). Cái giá: tách từ mỗi lần ghi, tốn thêm chỗ.
- **Oracle Text và RediSearch đều dựa trên inverted index** → so sánh ngang hàng; Oracle lưu trên đĩa trong các bảng phụ `DR$<index>$...` (`$I` là từ điển + posting list), Redis giữ trên RAM.
- **CONTEXT:** cho văn bản dài, có điểm liên quan (`CONTAINS`, `SCORE()`). Không cập nhật trong transaction như B-tree: dữ liệu mới nằm trong bảng nhưng chưa vào index cho tới khi **đồng bộ** (`CTX_DDL.SYNC_INDEX`, định kỳ `SYNC (EVERY)`, hoặc `SYNC (ON COMMIT)` để commit chậm hơn một chút nhưng thấy ngay sau commit). **Từ Oracle AI Database 26ai (bản nhóm dùng), mặc định là *automatic maintenance* + `SYNC (MANUAL)`: Oracle tự đồng bộ ở chế độ nền, kiểm tra mỗi 3 giây** (E6 đo được khoảng 3 giây); trước 26ai, mặc định phải tự gọi đồng bộ. Đồng bộ nhiều lần gây phân mảnh, cần tối ưu định kỳ.
- **CTXCAT:** cho văn bản ngắn và mảnh văn bản (tên, mô tả) kèm lọc cột khác qua index set, toán tử `CATSEARCH`, **tự cập nhật ngay** trong transaction. Tài liệu Oracle không nêu hỗ trợ tính điểm liên quan; trong thí nghiệm dùng không xếp theo điểm. **CTXCAT bị deprecated từ Oracle AI Database 26ai**, Oracle khuyên dùng CONTEXT.
- Chọn CONTEXT hay CTXCAT: CONTEXT có điểm liên quan (`SCORE`) và là loại Oracle khuyên dùng; CTXCAT cập nhật tức thì nhưng đã deprecated, tốn dung lượng và làm ghi rất chậm (E2, E4).
- Tiếng Việt: tách theo dấu cách nên mỗi âm tiết là một token ("điện thoại" → "điện", "thoại"); muốn tìm không dấu cần `base_letter`.

## Bài 5. Cái giá của index

- `PRODUCTS` (V1) có 6 index: `pk_products`, `uk_products_sku`, `uk_products_slug`, `ix_products_category_status`, `ix_products_price`, `ix_products_name_upper`. Thêm 1 sản phẩm = ghi bảng + 6 index (7 cấu trúc), cộng kiểm tra trùng cho 2 index unique và kiểm tra khóa ngoại (đọc `pk_categories`).
- `INSERT`/`DELETE` sửa **mọi** index; `UPDATE` chỉ sửa index chứa cột bị đổi (trong index là xóa mục cũ + chèn mục mới). Vd. đổi danh mục → chỉ `ix_products_category_status`; đổi giá → chỉ `ix_products_price`; đổi tên → `ix_products_name_upper` và index Oracle Text (nếu có). Index Oracle Text `SYNC (ON COMMIT)` làm chậm thêm, đổi tên, xóa sản phẩm; không ảnh hưởng đổi giá.
- Mọi thay đổi trên index đều được ghi nhật ký (undo/redo) như bảng → nhiều index thì nhiều nhật ký.
- **Tách lá:** lá đầy thì chia đôi, cập nhật tầng trên → có lần ghi chậm đột ngột. Khóa tăng dần: chèn vào lá phải cùng, tách 90/10, nhưng nhiều phiên ghi đồng thời tranh một lá (reverse key giải quyết). Khóa ngẫu nhiên: tách 50/50, lá thường đầy 50–70% → index phình.
- **Dung lượng:** giá trị khóa + ROWID mỗi dòng, cộng block nhánh và chỗ trống. Index trên cột dài có thể gần bằng bảng. Oracle Text có thêm bảng `DR$...$` và phân mảnh dần. Chỗ trống do xóa không trả lại ngay. Đo bằng `USER_SEGMENTS`.
- **Oracle tự duy trì index trong cùng transaction:** sửa bảng và index cùng lúc, ghi undo cho cả hai; trước commit người khác không thấy; rollback dùng undo trả cả hai về cũ; sập máy thì redo làm lại phần đã commit, undo hủy phần chưa commit. Bảng và index không bao giờ lệch nhau (trừ Oracle Text CONTEXT chưa đồng bộ). Optimizer tự chọn index, không phải sửa câu truy vấn.
- Nạp dữ liệu lớn: bỏ index → nạp → tạo lại (sắp xếp một lượt, dựng cây từ dưới lên, nhanh và gọn hơn chèn từng mục).

## Bài 6. Redis và cách tự làm index

- Redis là một bảng băm khổng lồ trên RAM, tra **theo tên key** gần như tức thì (O(1)). Đây là cách tra duy nhất có sẵn: **Redis chỉ tìm nhanh theo thứ nằm trong tên key**. Không có `WHERE`, không có optimizer.
- Tìm theo trường bên trong value mà không có index: `SCAN` mọi key, kéo value về ứng dụng rồi lọc → tệ hơn Full Scan (dữ liệu qua mạng, nhiều round-trip).
- Index tự làm bằng kiểu dữ liệu có sẵn:
  - **Set** cho so sánh bằng: `idx:category:5`, `idx:instock`; kết hợp bằng `SINTER` (giống AND của bitmap).
  - **Sorted Set** cho khoảng và sắp xếp: score = giá; `ZRANGE idx:price 1000000 5000000 BYSCORE LIMIT 0 24`. Bên trong là skip list, vai trò giống B-tree (log n để tìm điểm đầu rồi đi ngang).
  - **Key riêng** cho tra duy nhất: `idx:sku:<sku> → productId`; `SET ... NX` để chống trùng.
  - Nhiều điều kiện: tự chọn cách. Vd. "danh mục 5, giá 1–5 triệu, sắp theo giá" → mỗi danh mục một Sorted Set `idx:price:cat:5`. Tương ứng index `(category_id, price)` của Oracle: cột so sánh bằng nằm trong **tên key**, cột khoảng/sắp xếp là **score**. Đổi lại số key nhân theo số tổ hợp.
- Cái giá: mỗi lần ghi, ứng dụng phải **tự cập nhật mọi index**. Quên hoặc sập giữa chừng → index lệch dữ liệu, kết quả sai âm thầm. Ví dụ: đã `JSON.SET product:10001 $.price 3000000` nhưng sập trước `ZADD idx:price` → tìm 2,5–3,5 triệu không thấy 10001, tìm 1,5–2,5 triệu lại thấy nhưng giá hiện 3 triệu. `MULTI/EXEC`, Lua chạy liền một mạch nhưng lỗi giữa chừng không rollback. Ở Oracle không xảy ra vì bảng và index cùng một transaction.
- Mọi index nằm trên RAM (đắt hơn đĩa).

## Bài 7. RediSearch

- Module của Redis Stack. Khai báo một lần bằng `FT.CREATE` (prefix key + trường + kiểu); sau đó **mỗi lệnh ghi** vào key có prefix đó **tự cập nhật index ngay trong chính lệnh** (Redis chạy từng lệnh một) → hết cảnh dữ liệu và index lệch nhau như index tự làm ở bài 6.
- Index của nhóm (`init.lua`): `idx:products ON JSON PREFIX 1 product:` với `name TEXT`, `sku TAG`, `categoryId NUMERIC`, `categoryName TAG`, `price NUMERIC SORTABLE`, `status TAG`, `inStock TAG`.
- Kiểu trường: **TEXT** = inverted index (tách từ, chữ thường, điểm liên quan) ~ Oracle Text; **TAG** = khớp nguyên giá trị ~ Set; **NUMERIC** = khoảng số ~ Sorted Set / B-tree; **SORTABLE** = giữ bản sao giá trị để sắp xếp/trả nhanh, tốn thêm RAM. SKU là TAG vì TEXT sẽ cắt "LAB-0010001" ở dấu `-` thành "lab", "0010001". `status` là TAG vì chỉ cần khớp nguyên giá trị.
- Cú pháp `FT.SEARCH`: `@name:sony`, `@name:(tai nghe)`, tiền tố `pro*`, TAG `@status:{ACTIVE}`, khoảng `@price:[min max]` (dấu `(` = không lấy biên), dấu cách = AND, `|` = OR, `-` = NOT; `SORTBY price ASC`, `LIMIT offset count`, luôn kèm `TIMEOUT 0`. Ví dụ: `FT.SEARCH idx:products "@name:sony @price:[0 (2000000]" SORTBY price ASC LIMIT 0 10 TIMEOUT 0`.
- Trả về tổng số kết quả trong cùng lệnh (Oracle thường cần thêm câu `COUNT(*)`).
- Thực thi bằng cây (INTERSECT/UNION các nhánh), xem bằng `FT.EXPLAIN`, đo bằng `FT.PROFILE`. **Không có optimizer dựa trên thống kê** như CBO.
- Giá và giới hạn: RAM (`FT.INFO`), ghi chậm hơn, phân trang tối đa 10.000 kết quả, quá `TIMEOUT 500` trả một phần kết quả không báo lỗi, không có xử lý riêng cho tiếng Việt (tìm không dấu: thử ở giai đoạn 2).
- Hai tầng nhất quán khác nhau: RediSearch bảo đảm index khớp document trong Redis; document có khớp Oracle hay không là việc của projection (TV1), vd. `inStock` cũ sau checkout.

## Bài 8. Tổng hợp và cách đo

### Giả thuyết so sánh (phải đo ở giai đoạn 2 mới được viết thành kết luận)

| Tiêu chí | Oracle | Redis + RediSearch |
|---|---|---|
| Cấu trúc | B-tree; Oracle Text (inverted index) | NUMERIC, TAG, TEXT (inverted index); Set/ZSET nếu tự làm |
| Tìm SKU | B-tree unique | TAG / key riêng |
| Tìm từ trong tên | Cần Oracle Text (`LIKE '%từ%'` không dùng được B-tree) | TEXT |
| Lọc + sắp xếp | Index nhiều cột, optimizer tự chọn | `FT.SEARCH` tự kết hợp, không có optimizer dựa trên thống kê |
| Cập nhật index | Tự động, cùng transaction (trừ CONTEXT chưa đồng bộ) | RediSearch tự động trong cùng lệnh; tự làm thì ứng dụng tự lo |
| Nhất quán với nguồn gốc | Index cùng dữ liệu gốc | Bản sao của Oracle, có thể cũ |
| Nơi lưu | Đĩa + buffer cache | Toàn bộ RAM |
| Không dấu tiếng Việt | `base_letter` (cần `CTXAPP`) | Chưa rõ, phải thử |
| Giới hạn | Oracle Free: 2 luồng CPU, 2 GB RAM | Phân trang tối đa 10.000; TIMEOUT mặc định trả thiếu |

### Thí nghiệm dự kiến

| # | Câu hỏi | Oracle | Redis |
|---|---|---|---|
| E1 | Tìm SKU | Full Scan → B-tree unique | `SCAN` → TAG |
| E2 | Tìm từ trong tên | `LIKE '%từ%'` → CONTEXT (+ CTXCAT) | `SCAN` → TEXT |
| E3 | Lọc + sắp xếp + phân trang | Full Scan → index đơn → index nhiều cột | `SCAN` → `FT.SEARCH` |
| E4 | Chi phí ghi | Chèn 10.000 dòng với 0/1/3 index, + Oracle Text | `JSON.SET` 10.000 document khi chưa/đã có index |
| E5 | Dung lượng | `USER_SEGMENTS` | `FT.INFO`, `INFO memory` |
| E6 | Thời điểm cập nhật | CONTEXT chưa đồng bộ vs `SYNC (ON COMMIT)` | RediSearch thấy ngay; index tự làm có thể lệch |

### Nguyên tắc đo

1. Cùng dữ liệu hai bên (cùng 200.000 sản phẩm, cùng quy tắc sinh).
2. Cùng câu hỏi, kiểm tra cùng kết quả trước khi so thời gian.
3. Bỏ lần chạy đầu (warm-up: đọc từ đĩa lên cache, phân tích câu lệnh).
4. Chạy 5 lần, báo trung bình ± độ lệch chuẩn.
5. Phân biệt đo trong DB (cơ chế index: thời gian, Buffers, `FT.PROFILE`) với đo từ ứng dụng (trải nghiệm người dùng, có mạng); không trộn.
6. Ghi môi trường (CPU, RAM, phiên bản, Docker) và giới hạn (Oracle Free 2 luồng CPU, 2 GB RAM); không chạy việc nặng khác khi đo.

### Từ số đo đến quyết định

Cân theo bối cảnh ShopFlash: chênh lệch tốc độ người dùng có cảm nhận được không; ShopFlash đọc nhiều hơn ghi rất nhiều; độ trễ dữ liệu có chấp nhận được không; chi phí RAM và độ phức tạp vận hành. Kết luận có thể là "mỗi bên làm một việc", miễn có số đo làm bằng chứng.
