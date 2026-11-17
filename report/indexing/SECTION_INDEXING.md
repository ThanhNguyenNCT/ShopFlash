<a id="indexing-2-2"></a>

## 2.2. Indexing

Người thực hiện: TV2. Chức năng liên quan: F3 Tìm kiếm sản phẩm, F4 Lọc nâng cao.
Số liệu thô, script chạy lại và ghi chú chi tiết: `report/indexing/` (`README.md`, `ket-qua.md`, `ket-luan.md`).

### 2.2.1. Bối cảnh và câu hỏi nghiên cứu

Trang tìm kiếm của ShopFlash nhận bốn kiểu câu hỏi: tìm đúng một mã SKU; tìm sản phẩm có **từ khóa** trong tên ("tai nghe sony"); duyệt theo **danh mục** kèm **khoảng giá**, xếp theo giá và phân trang; và lọc theo tình trạng còn hàng. Khách hỏi liên tục, còn quản trị viên chỉ thêm hoặc sửa sản phẩm vài lần mỗi ngày, nên đây là tải **đọc nhiều, ghi ít**.

Phần này trả lời ba câu hỏi:

1. Với từng kiểu câu hỏi, index trên Oracle và trên Redis giúp truy vấn **nhanh hơn bao nhiêu**?
2. Cái giá của index là gì: **ghi chậm hơn** bao nhiêu, **tốn thêm** bao nhiêu dung lượng, và index **được cập nhật lúc nào**?
3. Từ đó, F3/F4 của ShopFlash nên dùng DBMS nào, loại index nào?

### 2.2.2. Lý thuyết

#### Index và chi phí truy vấn

Oracle lưu bảng thành các block (mặc định 8 KB). Không có index phù hợp, tìm một dòng phải đọc mọi block (**full table scan**), chi phí tăng tuyến tính theo kích thước bảng. Index là một cấu trúc phụ, **đã sắp xếp** theo cột được đánh index, mỗi mục trỏ về vị trí dòng (ROWID); tìm qua index chỉ đọc vài block và chi phí tăng theo log n [1], [2].

Index chỉ có lợi khi điều kiện lọc ra **ít dòng** (độ chọn lọc cao). Lấy phần lớn bảng thì đọc tuần tự toàn bảng rẻ hơn nhảy qua index đọc từng block lẻ, và optimizer tự chọn full scan [2]. Đổi lại, index tốn dung lượng và mỗi `INSERT`/`DELETE` phải cập nhật mọi index, mỗi `UPDATE` phải cập nhật các index chứa cột bị sửa.

#### Oracle

| Loại index | Ý tưởng | Dùng cho F3/F4 |
|---|---|---|
| B-tree unique | Cây cân bằng, lá nối chuỗi; tìm bằng, khoảng, sắp xếp | SKU |
| B-tree nhiều cột | Xếp theo cột 1, trong cột 1 xếp theo cột 2. Chỉ dùng hiệu quả khi câu hỏi có cột đầu; cột so sánh bằng đặt trước, cột khoảng/sắp xếp đặt sau | Danh mục + giá |
| B-tree trên hàm | Index trên biểu thức, vd. `UPPER(name)` | Tìm tên theo tiền tố |
| Bitmap | Mỗi giá trị một chuỗi bit; giỏi với cột ít giá trị, nhưng khi ghi khóa cả đoạn bitmap chứa nhiều dòng [1] | Bị loại: tồn kho thay đổi liên tục, nhiều người mua cùng lúc |
| Oracle Text CONTEXT | **Inverted index** (từ → danh sách tài liệu), toán tử `CONTAINS`, có điểm liên quan `SCORE` [4] | Từ khóa trong tên |
| Oracle Text CTXCAT | Inverted index cho văn bản ngắn, cập nhật ngay trong transaction, toán tử `CATSEARCH`; **deprecated từ Oracle AI Database 26ai** [4] | Bị loại (2.2.6) |

Oracle dùng **optimizer dựa trên chi phí** (CBO): ước lượng chi phí từng cách truy cập từ **thống kê** (số dòng, số giá trị khác nhau, histogram, clustering factor) rồi chọn cách rẻ nhất [2]. Kế hoạch thực thi xem được bằng `DBMS_XPLAN.DISPLAY_CURSOR`, gồm số dòng ước lượng (E-Rows), thực tế (A-Rows) và số block đã đọc (Buffers) [3]. B-tree được cập nhật **trong cùng transaction** với bảng: rollback thì cả hai cùng quay về, và mọi thay đổi trên index đều được ghi redo. Index CONTEXT thì cập nhật bằng **đồng bộ** (sync) riêng; từ Oracle AI Database 26ai, mặc định Oracle tự đồng bộ ở chế độ nền, kiểm tra mỗi 3 giây [5].

#### Redis

Redis là kho key–value trên RAM, tra theo **tên key** bằng bảng băm. Redis gốc không có `WHERE` và không có index phụ: muốn tìm theo giá trị bên trong document, ứng dụng phải tự dựng index bằng các kiểu dữ liệu (Set cho so sánh bằng, Sorted Set cho khoảng và sắp xếp, key riêng cho tra duy nhất) và **tự cập nhật** chúng mỗi lần ghi [8].

**RediSearch** (có trong Redis Stack) cho phép khai báo index trên document JSON bằng `FT.CREATE`. Kiểu trường: `TEXT` (inverted index, tách từ, có stemming theo ngôn ngữ, mặc định tiếng Anh), `TAG` (khớp nguyên giá trị, mặc định chuyển về chữ thường), `NUMERIC` (khoảng số), tùy chọn `SORTABLE` (sắp xếp nhanh, tốn thêm bộ nhớ) [6]. Truy vấn bằng `FT.SEARCH`, trả kèm tổng số kết quả trong cùng lệnh [7]. Index được cập nhật bên trong chính lệnh ghi. RediSearch không có optimizer dựa trên thống kê: truy vấn chạy theo cây giao/hợp các điều kiện đã khai báo.

ShopFlash đã có sẵn index `idx:products` trên bản sao sản phẩm `product:{id}` mà module storage chép từ Oracle sang Redis.

### 2.2.3. Phương pháp thí nghiệm

| Nội dung | Cách làm |
|---|---|
| Dữ liệu | 200.000 sản phẩm sinh theo **cùng công thức** ở hai bên: bảng `IDXLAB_PRODUCTS` (Oracle, 14 MB, 1.792 block) và document `idxlab:product:{n}` (Redis, 68,3 MB). Đối tượng riêng, không đụng dữ liệu thật của nhóm; index thí nghiệm tạo rồi xóa trong từng script |
| Đo ở đâu | **Bên trong DB**: vòng lặp PL/SQL (Oracle) và script Lua (Redis). Thời gian Redis lấy từ hiệu `INFO commandstats` (`cmdstat_eval`), vì trong một lần chạy script Lua, `TIME` trả cùng một giá trị (phát hiện khi đo, xem `ket-qua.md`) |
| Lặp | 1 lần khởi động bỏ qua + 5 lần đo; báo trung bình ± độ lệch chuẩn |
| Kiểm tra trước khi so | Mọi cách tìm phải trả **cùng số kết quả**, khớp số tính trước từ công thức sinh dữ liệu |
| Thước đo | Thời gian; số block đọc (Buffers) và kế hoạch thực thi (Oracle); `FT.EXPLAIN`, `FT.INFO` (Redis); dung lượng (`USER_SEGMENTS`, `MEMORY USAGE`, `FT.INFO`); lượng nhật ký (redo, AOF) |

**Môi trường:** Intel Core i7-14650HX, RAM 15,6 GB, Windows, Docker 29.9.2. Oracle AI Database 26ai Free 23.26.3.0.0 (giới hạn 2 luồng CPU, 2 GB RAM). Redis 7.4.7, RediSearch 2.10.20, RedisJSON 2.8.9, AOF `appendfsync everysec`. Image hai DBMS ghim theo digest trong `compose.yaml`. Ngày đo 30/10/2026.

**Giới hạn của phép so sánh:** Oracle Free bị giới hạn CPU và RAM còn Redis thì không; hai bên đo bằng hai cơ chế khác nhau (PL/SQL và Lua), nên ở mức vài micro giây chỉ nên so **bậc độ lớn**. Redis bật AOF `everysec` (sập máy có thể mất khoảng 1 giây dữ liệu) trong khi Oracle ghi redo xuống đĩa khi commit.

Sáu thí nghiệm:

| # | Câu hỏi | Oracle | Redis |
|---|---|---|---|
| E1 | Tìm theo SKU | Full scan → B-tree unique | `SCAN` → key tự làm → RediSearch `TAG` |
| E2 | Tìm từ trong tên (F3) | `LIKE '%từ%'` → CONTEXT → CTXCAT | `SCAN` → RediSearch `TEXT` |
| E3 | Lọc + sắp xếp + phân trang (F4) | Không index → 2 index đơn → index ghép đúng thứ tự → ghép sai thứ tự | `SCAN` → Sorted Set tự làm → RediSearch |
| E4 | Chi phí ghi | Thêm/sửa giá 10.000 dòng với số index tăng dần | Như Oracle, với index tự làm và RediSearch |
| E5 | Dung lượng | Đo kèm E1–E3 | Đo kèm E1–E3 |
| E6 | Index cập nhật lúc nào | B-tree, CONTEXT mặc định, `SYNC (ON COMMIT)` | RediSearch khi mới tạo và khi ghi; index tự làm khi ứng dụng dừng giữa chừng |

### 2.2.4. Kết quả trên Oracle

#### E1. Tìm theo SKU

| | Không index | B-tree unique |
|---|---|---|
| Kế hoạch | `TABLE ACCESS FULL`, điều kiện là `filter` | `INDEX UNIQUE SCAN` → `TABLE ACCESS BY INDEX ROWID`, điều kiện là `access` |
| Buffers / lần tìm | 1.754 | 4 (3 trong index + 1 bảng) |
| 200 lần tìm | 642,79 ± 13,43 ms | 1,21 ± 0,23 ms |
| 1 lần tìm | 3,214 ms | 0,006 ms |

Số block đọc giảm khoảng 438 lần, thời gian khoảng 530 lần. Cây index có `BLEVEL = 2` (3 tầng), khớp đúng 3 buffer trong index. Cái giá: index 5 MB (khoảng 36% bảng), tạo trong 0,20 s. Cùng một câu SQL, Oracle tự đổi kế hoạch khi có index mà không phải sửa câu truy vấn. `CLUSTERING_FACTOR = 192.848` (gần bằng số dòng) cho thấy các dòng xếp theo SKU nằm rải rác trong bảng; không ảnh hưởng tìm một SKU nhưng ảnh hưởng tìm theo khoảng (E3).

#### E2. Tìm từ trong tên

Mỗi lần tìm gồm câu lấy trang đầu 24 sản phẩm và câu đếm tổng số. Đơn vị: ms cho 1 lần tìm.

| Từ khóa (số kết quả) | `LIKE '%từ%'` | CONTEXT | CTXCAT |
|---|---|---|---|
| `615` (200) | 37,02 | 1,00 | 0,40 |
| `sony pro` (1.679) | 33,69 | 1,20 | 1,66 |
| `pro` (20.159) | 34,19 | 6,36 | 5,64 |

- `LIKE` không phụ thuộc từ khóa vì luôn quét toàn bảng (1.753 buffer). Optimizer ước lượng sai (E-Rows 500, A-Rows 1.679) vì không biết nội dung chuỗi.
- Inverted index có lợi nhiều nhất với từ hiếm (CONTEXT nhanh hơn `LIKE` 37 lần) và ít dần với từ phổ biến (5,4 lần): posting list càng dài càng nhiều việc.
- Kế hoạch CONTEXT: `DOMAIN INDEX` trả 1.679 ROWID, phải đọc cả 1.679 dòng để xếp theo `SCORE` rồi mới lấy trang đầu. Câu CTXCAT (không xếp theo điểm) dừng ngay khi đủ dòng (`COUNT STOPKEY`, 37 buffer).
- Từ điển của CONTEXT (`DR$...$I`) cho thấy "Tai nghe" bị tách thành hai token `TAI`, `NGHE`.
- Dung lượng và thời gian tạo: CONTEXT 21,8 MB, 2,56 s; CTXCAT 73 MB (5,2 lần bảng), 3,41 s.

#### E3. Lọc + sắp xếp + phân trang

A: danh mục 5 và giá 1–5 triệu (321 kết quả); B: chỉ giá 1–5 triệu (15.218); C: chỉ danh mục 5 (4.000). Optimizer tự chọn, không ép hint. Đơn vị: ms cho 1 lần tìm (trang 24 + đếm).

| Cấu hình | A | B | C |
|---|---|---|---|
| Không index | 4,941 | 6,712 | 4,585 |
| 2 index đơn `(category_id)`, `(price)` | 0,582 | 2,952 | 0,679 |
| Ghép đúng thứ tự `(category_id, status, price)` | **0,024** | 6,878 | **0,101** |
| Ghép sai thứ tự `(price, category_id, status)` | 0,484 | **0,508** | 2,837 |

- Ghép đúng thứ tự, câu A: cả bốn điều kiện là `access`, kết quả đã xếp sẵn theo giá nên dừng ở dòng 24 (10 buffer), câu đếm chỉ đọc index (4 buffer). Nhanh hơn không index khoảng 200 lần.
- Cùng index đó, câu B **thiếu cột đầu** nên optimizer bỏ index, quét toàn bảng (6,88 ms).
- Ghép sai thứ tự: `category_id`, `status` chỉ còn là `filter` trong index, câu A chậm hơn ghép đúng 20 lần, nhưng lại tốt nhất cho câu B. **Không có index nào tốt nhất cho mọi câu hỏi.**
- Với 2 index đơn, câu B optimizer chọn index trên giá dù `CLUSTERING_FACTOR` xấu nhất (200.000), vì index đã đúng thứ tự giá nên chỉ đọc 25 dòng.
- Ước lượng sai vì thiếu histogram: E-Rows 160 so với A-Rows 321, do optimizer coi `status = 'ACTIVE'` lọc 50% (2 giá trị) trong khi thực tế 95%.
- Mỗi index ghép 3 cột: 6 MB, tạo trong khoảng 0,17 s.

#### E4. Chi phí khi ghi (µs mỗi dòng; redo cho 10.000 dòng)

| Cấu hình | Thêm | redo | Sửa giá | redo |
|---|---|---|---|---|
| C0: chỉ khóa chính | 9,8 | 5,5 MB | 6,6 | 2,7 MB |
| C1: + SKU | 11,1 | 7,9 MB | 6,1 | 2,7 MB |
| C2: + 2 index có cột giá | 19,3 | 12,7 MB | 17,3 | 12,0 MB |
| C3: C2 + CTXCAT | 1.014,8 | 33,1 MB | 635,8 | 12,0 MB |
| C4: C2 + CONTEXT `SYNC (ON COMMIT)` | 48,1 | 21,7 MB | 43,3 * | 12,0 MB |

\* Một lượt bất thường (độ lệch lớn); giá trị đáng tin gần mức C2.

- Mỗi index làm thêm dòng chậm thêm và sinh thêm redo; 3 B-tree làm thêm chậm gần 2 lần.
- **Sửa giá không đụng index không chứa cột giá:** C1 sửa giá nhanh như C0, redo y hệt.
- CTXCAT đắt nhất: thêm chậm 53 lần so với C2; sửa giá (không đổi tên) vẫn chậm 37 lần dù redo bằng C2. Giả thuyết: Oracle vẫn gọi thủ tục cập nhật của domain index cho mỗi dòng; chưa kiểm chứng thêm.

#### E6. Index cập nhật lúc nào

| Bước | Kết quả |
|---|---|
| B-tree: INSERT chưa commit, phiên mình / phiên khác tìm SKU | 1 / 0 |
| B-tree: sau ROLLBACK; sau INSERT lại + COMMIT (phiên khác) | 0; 1 |
| CONTEXT mặc định: đã COMMIT, tìm ngay bằng `CONTAINS` / bằng `LIKE` | 0 / 1 |
| CONTEXT mặc định: tự tìm thấy sau | khoảng 3,0 giây (`idx_sync_type = MANUAL`, Oracle tự đồng bộ ở chế độ nền [5]) |
| CONTEXT `SYNC (ON COMMIT)`: chưa commit, phiên mình tìm bằng B-tree / bằng `CONTAINS` | 1 / 0 |
| CONTEXT `SYNC (ON COMMIT)`: sau COMMIT | 1 |

B-tree luôn khớp bảng theo đúng transaction. Oracle Text không theo transaction như B-tree: kể cả chính phiên vừa thêm cũng không tìm thấy trước khi index được đồng bộ.

### 2.2.5. Kết quả trên Redis

#### E1. Tìm theo SKU

| | `SCAN` (không index) | Key tự làm `idxlab:sku:{sku}` | RediSearch `TAG` |
|---|---|---|---|
| Thời gian mỗi lượt | 1.910,13 ± 83,00 ms (5 lần tìm) | 0,69 ± 0,03 ms (200 lần) | 1,95 ± 0,25 ms (200 lần) |
| 1 lần tìm | 382,03 ms | 0,0035 ms | 0,0097 ms |
| Tạo index | — | 368 ms | 4.289 ms |
| RAM thêm | — | 12,2 MB | 49,5 MB |

`FT.INFO` của index chỉ có một trường `TAG`: inverted index 16,4 MB, nhưng bảng document 20,9 MB và bảng key 6,0 MB; hai bảng này là **chi phí cố định** cho mỗi document RediSearch quản lý. `FT.EXPLAIN` cho thấy giá trị TAG được lưu chữ thường, đúng mặc định của `TAG` [6].

#### E2. Tìm từ trong tên (ms cho 1 lần tìm; 1 lệnh `FT.SEARCH ... LIMIT 0 24` trả cả trang lẫn tổng số)

| Từ khóa (số kết quả) | `SCAN` | RediSearch `TEXT` |
|---|---|---|
| `615` (200) | 424,33 | 0,056 |
| `sony pro` (1.679) | 435,48 | 0,908 |
| `pro` (20.159) | 430,67 | 0,709 |

Index `TEXT` tốn 34,9 MB (trong đó 27 MB là phần cố định như E1), tạo trong 4,70 s. `FT.EXPLAIN` cho thấy stemming tiếng Anh mặc định mở rộng `sony` thành `soni` [6]; không làm sai kết quả ở đây nhưng có thể sinh khớp lạ với tên tiếng Việt.

#### E3. Lọc + sắp xếp + phân trang (ms cho 1 lần tìm)

| Cách | A (321) | B (15.218) | C (4.000) |
|---|---|---|---|
| `SCAN` + giải mã JSON | 652,11 | 701,23 | 672,56 |
| Sorted Set tự làm (mỗi danh mục một ZSET, score = giá) | 0,006 † | 0,005 | 0,004 |
| RediSearch (`categoryId` NUMERIC, `price` NUMERIC SORTABLE, `status` TAG) | 1,041 | 2,990 | 1,023 |

† Trung vị; một lượt đo nhiễu (1,62 ms) làm trung bình thành 0,021 ms.

RediSearch không có index nhiều cột: `FT.EXPLAIN` cho thấy truy vấn là phép **giao** ba danh sách, trong đó `status:{active}` khớp 190.000 document, rồi còn phải sắp xếp và đếm toàn bộ kết quả trước khi trả trang đầu. Sorted Set tự làm nhanh nhất vì được dựng riêng cho đúng các câu hỏi này (điều kiện `status` "nướng sẵn" vào index), đổi lại tốn 31,7 MB và ứng dụng phải tự cập nhật.

#### E4. Chi phí khi ghi (µs mỗi document; AOF cho 10.000 document)

| Cấu hình | Thêm | AOF | Sửa giá | AOF |
|---|---|---|---|---|
| R0: không index phụ | 5,6 | 1,8 MB | 2,6 | 0,7 MB |
| R1: key SKU tự làm | 6,5 | 2,3 MB | 2,5 | 0,7 MB |
| R2: SKU + Sorted Set giá | 11,7 | 3,5 MB | 6,8 | 1,9 MB |
| R3: RediSearch chỉ `sku` | 8,2 | 1,8 MB | 6,3 | 0,7 MB |
| R4: RediSearch đầy đủ như `idx:products` | 14,3 | 1,8 MB | 15,4 | 0,7 MB |

- **RediSearch index lại cả document dù chỉ sửa một trường:** R3 chỉ index `sku` nhưng sửa giá vẫn chậm 2,4 lần so với R0; R4 (có `name TEXT`) sửa giá chậm 6 lần, còn chậm hơn thêm mới. Ngược với Oracle (C1).
- **AOF giống hệt nhau ở R0, R3, R4:** số đo cho thấy RediSearch không ghi index vào nhật ký; khi khởi động lại, Redis phải index lại toàn bộ document (vài giây với 200.000 document). Index tự làm là dữ liệu bình thường nên làm tăng AOF.

#### E6. Index cập nhật lúc nào

| Bước | Kết quả |
|---|---|
| Tìm `*` ngay sau `FT.CREATE`, 4 lần cách nhau khoảng 0,3 s | 78 → 9.982 → 21.148 → 32.736 / 200.000 document |
| Sau khi index xong | 200.000 / 200.000 |
| Thêm, đổi giá, xóa một document; client khác tìm ngay sau mỗi lệnh | Đúng ngay lập tức ở cả 6 bước |
| Index tự làm: đã `JSON.SET` giá mới, "sập" trước `ZADD` | Dữ liệu 9.999.000, index 7.142.000; lọc đúng giá **không thấy** sản phẩm, lọc giá cũ **lại thấy** |
| `MULTI` + `JSON.SET` giá + `ZADD` score sai + `EXEC` | `JSON.SET` đã chạy, `ZADD` lỗi; lệnh đầu **không được hoàn tác**, dữ liệu và index lại lệch |

RediSearch thấy dữ liệu ngay sau lệnh ghi, nhưng trong lúc dựng index, tìm kiếm chỉ thấy **một phần** dữ liệu mà không báo lỗi. Index tự làm có thể lệch âm thầm, và `MULTI/EXEC` không cứu được.

### 2.2.6. So sánh

| Tiêu chí | Oracle | Redis + RediSearch | Bằng chứng |
|---|---|---|---|
| Tìm chính xác, có index | 0,006 ms | 0,010 ms (TAG); 0,0035 ms (tự làm) | E1 |
| Tìm chính xác, không index | 3,2 ms | 382 ms | E1 |
| Tìm từ trong tên | Oracle Text 0,4–6,4 ms | **0,06–0,9 ms** | E2 |
| Tìm từ, không index | 34–37 ms | 424–435 ms | E2 |
| Lọc danh mục + giá + sắp xếp + phân trang | **0,02–0,1 ms** (index ghép đúng thứ tự) | 1,0 ms | E3 |
| Chỉ lọc giá toàn cửa hàng | 0,5–3,0 ms | 3,0 ms | E3 |
| Ai chọn cách chạy | Optimizer theo chi phí, tự bỏ index khi không có lợi | Theo index đã khai báo | E1–E3 |
| Chi phí ghi | Vài µs mỗi index; sửa cột không có trong index không tốn thêm | Sửa trường bất kỳ là index lại cả document | E4 |
| Nhật ký, khôi phục | Index ghi redo, khôi phục cùng bảng | Index không vào AOF, phải dựng lại khi khởi động | E4 |
| Dung lượng index | B-tree 4–6 MB; CONTEXT 22 MB (đĩa) | 33–50 MB (RAM), khoảng 27 MB cố định | E1–E3 |
| Nhất quán | B-tree theo transaction; Oracle Text 26ai trễ khoảng 3 s | Thấy ngay sau lệnh ghi; thấy một phần khi đang dựng index; index tự làm có thể lệch | E6 |
| Vai trò dữ liệu trong ShopFlash | Nguồn gốc | Bản sao của Oracle, có thể cũ | Thiết kế nhóm |

**Đánh giá:**

- **Không bên nào thắng mọi loại câu hỏi.** Tìm theo từ khóa: RediSearch nhanh hơn Oracle Text 1,3–18 lần. Lọc có cấu trúc kèm sắp xếp và phân trang: index ghép của Oracle nhanh hơn RediSearch 10–43 lần.
- **Không có index, Redis chậm hơn Oracle 12–140 lần** dù dữ liệu trên RAM: Redis không có cơ chế quét bảng, phải gọi một lệnh cho mỗi document. "Nằm trên RAM" không tự động có nghĩa là nhanh hơn.
- **Mọi cách có index đều dưới 10 ms** cho một lần tìm; một khách hàng không cảm nhận được khác biệt. Khác biệt có ý nghĩa khi nhiều người tìm cùng lúc.
- **Oracle mạnh về tính đúng và tự động:** index luôn khớp dữ liệu, optimizer tự chọn, dữ liệu là bản gốc. **Redis mạnh về tìm toàn văn trên RAM**, đổi lại tốn RAM, phải đồng bộ bản sao, và có khoảng thời gian kết quả chưa đầy đủ.
- **Loại bỏ:** CTXCAT (dung lượng gấp 5 lần bảng, ghi chậm 37–53 lần, và đã deprecated từ 26ai [4]); bitmap (khóa nhiều dòng khi ghi đồng thời); Redis index tự làm (có thể lệch âm thầm).

### 2.2.7. Kết luận trong bối cảnh ShopFlash

ShopFlash dùng **mỗi DBMS cho việc nó giỏi nhất**; mỗi yêu cầu tìm kiếm đi tới đúng một nguồn, không trộn kết quả:

| Yêu cầu của khách | Nguồn | Index | Bằng chứng |
|---|---|---|---|
| Có từ khóa (kèm hoặc không kèm bộ lọc) | RediSearch `idx:products` | `name TEXT` + các trường lọc | E2 |
| Không có từ khóa: duyệt danh mục, lọc giá | Oracle | Mới: `(category_id, status, base_price, product_id)` | E3 |
| Chi tiết sản phẩm, tồn kho | Oracle | Khóa chính | Dữ liệu gốc |
| Có từ khóa nhưng Redis không truy cập được | Oracle Text CONTEXT | Mới: `ix_products_name_text` | E2, E6 |

Hiện thực trong ứng dụng (module `indexing`):

- Migration `V3__indexing_product_search.sql` thêm hai index trên `PRODUCTS`. Index ghép có thêm `product_id` ở cuối để thứ tự ổn định khi trùng giá (phân trang không lặp) mà Oracle vẫn không phải sắp xếp lại.
- API công khai `GET /api/products/search` định tuyến theo bảng trên; mỗi response có `meta` ghi nguồn, đường đi (`KEYWORD`/`BROWSE`), câu SQL hoặc lệnh Redis đã chạy, số lượt gọi DB và thời gian. `GET /api/products/{id}`, `GET /api/products/categories` đọc Oracle.
- Từ khóa được tách giống nhau cho hai nguồn: chỉ giữ chữ và số (kể cả chữ có dấu), bỏ toán tử của Oracle Text và stopword của RediSearch, nên không thể chèn cú pháp truy vấn; mọi giá trị vào SQL đều là bind variable.
- Trang **Indexing Lab** (admin) chạy cùng một yêu cầu trên hai nguồn, có và không có index, hiện kế hoạch thực thi và dung lượng index, phục vụ demo.

**Giới hạn đã biết:**

- Tìm theo từ khóa đọc bản sao Redis, nên tình trạng còn hàng có thể cũ; trang chi tiết và đặt hàng luôn đọc Oracle. Khi `idx:products` được dựng lại hoặc Redis khởi động lại, vài giây đầu chỉ tìm thấy một phần sản phẩm.
- Khi trùng giá, `SORTBY` của RediSearch chỉ theo một trường nên thứ tự giữa các trang có thể không ổn định.
- Chưa hỗ trợ tìm không dấu ("dien thoai" ra "điện thoại") và chưa tắt stemming tiếng Anh của `idx:products`.
- Trường hợp từ khóa **kèm** bộ lọc trên RediSearch chưa được đo trên 200.000 sản phẩm (E2 chỉ đo từ khóa, E3 chỉ đo bộ lọc); trang Indexing Lab có thẻ so sánh cho trường hợp này.

### Tài liệu tham khảo

[1] Oracle, "Indexes and Index-Organized Tables," in *Oracle Database Concepts*. [Online]. Available: https://docs.oracle.com/database/122/CNCPT/indexes-and-index-organized-tables.htm (accessed Oct. 30, 2026).

[2] Oracle, "Optimizer Access Paths," in *Oracle Database SQL Tuning Guide*, 19c. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/19/tgsql/optimizer-access-paths.html (accessed Oct. 30, 2026).

[3] Oracle, "Explaining and Displaying Execution Plans," in *Oracle Database SQL Tuning Guide*, 23ai. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/23/tgsql/generating-and-displaying-execution-plans.html (accessed Oct. 30, 2026).

[4] Oracle, "About Oracle Text Indexes," in *Oracle Text Application Developer's Guide*, 23ai. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/23/ccapp/oracle-text-indexes.html (accessed Oct. 30, 2026).

[5] Oracle, "Using Automatic Maintenance for an Index," in *Oracle Text Application Developer's Guide*, Oracle AI Database 26ai. [Online]. Available: https://docs.oracle.com/en/database/oracle/oracle-database/26/ccapp/using-automatic-maintenance-index.html (accessed Oct. 30, 2026).

[6] Redis, "FT.CREATE," *Redis Documentation*. [Online]. Available: https://redis.io/docs/latest/commands/FT.CREATE/ (accessed Oct. 30, 2026).

[7] Redis, "FT.SEARCH," *Redis Documentation*. [Online]. Available: https://redis.io/docs/latest/commands/ft.search/ (accessed Oct. 30, 2026).

[8] Redis, "Secondary indexing," *Redis Documentation*. [Online]. Available: https://redis.io/docs/latest/develop/clients/patterns/indexes/ (accessed Oct. 30, 2026).
