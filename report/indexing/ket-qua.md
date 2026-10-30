# Kết quả thí nghiệm Indexing (TV2)

Số liệu thô nằm trong `results/oracle/` và `results/redis/`. File này tóm tắt và ghi nhận xét, làm nguyên liệu cho mục 3.2. Cấu trúc thư mục và cách chạy: `README.md`.

## Môi trường

- Oracle AI Database 26ai Free 23.26.3.0.0 (Docker, image ghim theo digest trong `compose.yaml`). Oracle Free giới hạn 2 luồng CPU, 2 GB RAM.
- Redis 7.4.7 (Redis Stack: RediSearch 2.10.20, RedisJSON 2.8.9), bật AOF `everysec`.
- Máy: Intel Core i7-14650HX, RAM 15,6 GB, Windows, Docker 29.9.2.
- Dữ liệu Oracle: bảng thí nghiệm `IDXLAB_PRODUCTS`, 200.000 dòng, 1.792 block, 14 MB trên đĩa (`oracle/00_setup.sql`). Tạo bằng CTAS trong 0,63 s.
- Dữ liệu Redis: 200.000 document JSON `idxlab:product:{n}`, **cùng công thức sinh** với Oracle (`redis/00_setup.sh`). Nạp trong 1,1 s; `MEMORY USAGE` tổng 68,3 MB (358 byte/document), `used_memory` tăng 85,7 MB. Redis lúc đo có tổng 200.005 key.
- Cách đo chung: đo **bên trong DB** (Oracle: vòng lặp PL/SQL; Redis: script Lua, thời gian lấy từ hiệu `INFO commandstats` của `cmdstat_eval`, vì `TIME` trong Lua bị "đóng băng" suốt một lần chạy script từ Redis 7). 1 lần khởi động bỏ qua + 5 lần đo; báo trung bình ± độ lệch chuẩn.

## E1. Tìm theo SKU

### Oracle (30/10/2026, `oracle/e1_sku.sql`, thô: `results/oracle/e1-sku.txt`)

Đo trong DB bằng PL/SQL: mỗi lần đo tìm 200 SKU khác nhau; 1 lần khởi động bỏ qua + 5 lần đo.

| | Không index | B-tree unique `ix_idxlab_sku` |
|---|---|---|
| Kế hoạch | `TABLE ACCESS FULL`, predicate `filter` | `INDEX UNIQUE SCAN` → `TABLE ACCESS BY INDEX ROWID`, predicate `access` |
| Buffers (1 lần tìm) | 1.754 | 4 (3 index + 1 bảng) |
| 200 lần tìm | 642,79 ± 13,43 ms | 1,21 ± 0,23 ms |
| 1 lần tìm | 3,214 ms | 0,006 ms |

- Số block đọc giảm khoảng **438 lần** (1.754 → 4); thời gian giảm khoảng **530 lần**.
- Cây index: `BLEVEL = 2` (3 tầng: gốc, nhánh, lá) → đúng 3 buffer trong index, cộng 1 block bảng.
- Cái giá: index 5 MB (640 block) so với bảng 14 MB, tức khoảng **36%** dung lượng bảng chỉ cho một cột. Tạo index mất 0,20 s.
- Cùng một câu SQL (`SQL_ID` giống nhau), khi có index Oracle tự đổi kế hoạch (`Plan hash value` 717870639 → 3019723367), không phải sửa câu truy vấn.
- `CLUSTERING_FACTOR = 192.848`, gần bằng số dòng (200.000) → các dòng xếp theo SKU nằm rải rác trong bảng (CTAS có JOIN nên thứ tự vật lý không theo SKU). Không ảnh hưởng tìm 1 SKU (luôn chỉ 1 block bảng), nhưng sẽ ảnh hưởng tìm theo khoảng (E3).
- Độ lệch khi có index tương đối lớn (0,23 trên 1,21 ms ≈ 19%) vì con số quá nhỏ, dễ nhiễu; nên tăng số lần tìm khi đo chính thức.

### Redis (30/10/2026, `redis/e1_sku.sh`, thô: `results/redis/e1-sku.txt`)

| | Không index (`SCAN`) | Index tự làm (`idxlab:sku:{sku}` → id) | RediSearch TAG (`idx:idxlab-e1`) |
|---|---|---|---|
| Cách tìm | `SCAN` mọi key + `JSON.GET $.sku` từng document | `GET` + `JSON.GET` | `FT.SEARCH "@sku:{LAB\-...}" LIMIT 0 1` |
| Số lần tìm mỗi lượt | 5 (vì rất chậm) | 200 | 200 |
| Thời gian mỗi lượt | 1.910,13 ± 83,00 ms | 0,69 ± 0,03 ms | 1,95 ± 0,25 ms |
| 1 lần tìm | 382,03 ms | 0,0035 ms | 0,0097 ms |
| Thời gian tạo index | — | 368 ms | 4.289 ms (index nền tới khi xong 200.000 document) |
| RAM thêm | — | 12,2 MB (`MEMORY USAGE`), `used_memory` +12,7 MB | 49,5 MB (`total_index_memory_sz_mb`), `used_memory` +50,5 MB |

- `FT.INFO` của index chỉ có một trường TAG: `inverted_sz_mb` 16,4; `doc_table_size_mb` 20,9; `key_table_size_mb` 6,0; `tag_overhead_sz_mb` 6,1. Bảng document và bảng key là chi phí cố định cho mỗi document RediSearch quản lý, không phụ thuộc số trường.
- `FT.EXPLAIN` cho thấy TAG lưu giá trị chữ thường (`lab-0123456`): TAG mặc định không phân biệt hoa thường (`CASESENSITIVE` để đổi).
- `FT.PROFILE` báo mọi bước 0 ms: quá nhanh so với độ phân giải mà RediSearch in ra.

### So sánh E1

| 1 lần tìm theo SKU | Oracle | Redis |
|---|---|---|
| Không index | 3,214 ms (`TABLE ACCESS FULL`, 1.754 block) | 382,03 ms (`SCAN` + `JSON.GET` 200.000 lần) |
| Có index | 0,0060 ms (B-tree unique) | 0,0035 ms (tự làm) / 0,0097 ms (RediSearch TAG) |
| Index nhanh hơn không index | khoảng 530 lần | khoảng 109.000 lần (tự làm) / 39.000 lần (RediSearch) |

| Cái giá của index trên một cột SKU | Oracle B-tree | Redis tự làm | RediSearch TAG |
|---|---|---|---|
| Dung lượng | 5 MB (đĩa), khoảng 36% bảng 14 MB | 12,2 MB (RAM), khoảng 18% dữ liệu 68 MB | 49,5 MB (RAM), khoảng 72% dữ liệu 68 MB |
| Thời gian tạo | 0,20 s | 0,37 s | 4,29 s |
| Ai duy trì khi ghi | Oracle, cùng transaction | **Ứng dụng tự làm** | RediSearch, trong cùng lệnh ghi |

Nhận xét:

1. **Không có index, Redis chậm hơn Oracle khoảng 119 lần** (382 ms so với 3,2 ms), dù dữ liệu Redis nằm trên RAM. Oracle quét toàn bảng bằng mã C, đọc tuần tự từng block chứa nhiều dòng ở định dạng gọn. Redis phải gọi một lệnh cho mỗi document (`JSON.GET` 200.000 lần, mỗi lần phân tích JSON) và duyệt key bằng `SCAN`. Redis không được thiết kế để tìm theo giá trị nếu không có index. "Nằm trên RAM" không tự động có nghĩa là nhanh hơn.
2. **Có index, cả ba cách đều chỉ vài micro giây** (3,5 – 9,7 µs). Chênh lệch giữa chúng người dùng không thể cảm nhận. Hơn nữa, hai bên đo bằng hai cơ chế khác nhau (vòng PL/SQL và script Lua, mỗi cái có chi phí gọi riêng), nên ở mức micro giây chỉ nên kết luận **cùng bậc độ lớn**, không nên kết luận bên nào nhanh hơn.
3. **Cái giá về bộ nhớ khác nhau rất nhiều:** cho cùng một cột SKU, RediSearch tốn gần **10 lần** dung lượng so với B-tree của Oracle, và là RAM chứ không phải đĩa. Index tự làm gọn hơn RediSearch khoảng 4 lần, nhưng đổi lại ứng dụng phải tự duy trì, có nguy cơ lệch (bài 6).
4. **Tạo index:** RediSearch chậm nhất (4,3 s), vì phải đọc và phân tích JSON của 200.000 document, và việc index diễn ra ở chế độ nền. Trong lúc đó các truy vấn chỉ thấy một phần kết quả.

## E2. Tìm từ trong tên (F3)

Mỗi "lần tìm" = lấy trang đầu 24 sản phẩm + đếm tổng số kết quả, như một trang kết quả thật. Oracle cần 2 câu SQL; Redis 1 lệnh `FT.SEARCH` (trả kèm tổng số). Ba từ khóa với độ chọn lọc khác nhau. **Cả 5 cách trả cùng số kết quả** (khớp số tính trước bằng công thức sinh dữ liệu), nên so sánh thời gian là hợp lệ.

Thô: `results/oracle/e2-name.txt` (`oracle/e2_name.sql`), `results/redis/e2-name.txt` (`redis/e2_name.sh`), 30/10/2026.

### Thời gian 1 lần tìm (ms, trung bình 5 lần đo)

| Từ khóa (số kết quả) | Oracle `LIKE` | Oracle Text CONTEXT | Oracle Text CTXCAT | Redis `SCAN` | RediSearch TEXT |
|---|---|---|---|---|---|
| `615` (200) | 37,02 | 1,00 | 0,40 | 424,33 | 0,056 |
| `sony pro` (1.679) | 33,69 | 1,20 | 1,66 | 435,48 | 0,908 |
| `pro` (20.159) | 34,19 | 6,36 | 5,64 | 430,67 | 0,709 |

Độ lệch chuẩn mọi ô dưới 5% giá trị (chi tiết trong file thô). Oracle đo 20 lần tìm mỗi lượt; Redis `SCAN` 2 lần/lượt, RediSearch 20 lần/lượt.

### Cái giá của index

| | Oracle Text CONTEXT | Oracle Text CTXCAT | RediSearch TEXT |
|---|---|---|---|
| Thời gian tạo | 2,56 s | 3,41 s | 4,70 s |
| Dung lượng | 21,8 MB (đĩa), khoảng 1,6 lần bảng 14 MB | 73 MB (đĩa), khoảng 5,2 lần bảng | 34,9 MB (RAM, `total_index_memory_sz_mb`), khoảng 51% dữ liệu 68 MB; `used_memory` +49,4 MB |
| Thành phần chính | `$K` + 2 index (17 MB), `$I` từ điển + posting list (4 MB), cùng nhiều bảng phụ nhỏ | `$I` (26 MB) + index `$R` (24 MB) + `$X` (23 MB) | `doc_table` 20,9 + `key_table` 6,0 (cố định, giống E1) + `inverted` 6,9 + `offset_vectors` 1,0 |

### Nhận xét

1. **`LIKE '%từ%'` không phụ thuộc từ khóa** (34–37 ms ở cả 3 từ): luôn quét toàn bảng (`TABLE ACCESS FULL`, 1.753 buffer), còn phải chạy `UPPER` và so chuỗi trên 200.000 tên. Chậm hơn tìm SKU không index ở E1 (3,2 ms) khoảng 10 lần vì phần tính toán chuỗi này, dù số block đọc như nhau. Thời gian tăng tuyến tính theo số sản phẩm.
2. **Inverted index có lợi nhiều nhất khi từ khóa hiếm, lợi ít dần khi từ phổ biến** (đúng bài 1 về độ chọn lọc): CONTEXT nhanh hơn `LIKE` 37 lần với `615`, 28 lần với `sony pro`, chỉ 5,4 lần với `pro`. Posting list càng dài thì càng nhiều việc (đếm, tính điểm, đọc dòng).
3. **Kế hoạch thực thi giải thích khác biệt CONTEXT và CTXCAT:**
   - CONTEXT có `ORDER BY SCORE`: `DOMAIN INDEX` trả 1.679 ROWID, rồi `TABLE ACCESS BY INDEX ROWID` đọc **cả 1.679 dòng** (907 buffer) để sắp xếp, mới lấy 5 dòng đầu.
   - Câu CTXCAT trong thí nghiệm không xếp theo điểm nên không phải sắp xếp: lấy đủ 5 dòng là dừng (`COUNT STOPKEY`, 37 buffer). Vì thế CTXCAT nhanh hơn với từ hiếm, nhưng tốn dung lượng gấp hơn 3 lần CONTEXT. Ngoài ra CTXCAT đã bị deprecated từ Oracle AI Database 26ai.
4. **Ước lượng của optimizer với `LIKE` sai:** `E-Rows = 500`, `A-Rows = 1.679`. Optimizer không biết nội dung chuỗi nên đoán mỗi `LIKE '%..%'` lọc 5% (200.000 × 5% × 5% = 500). Minh họa bài 3: không có thống kê phù hợp thì ước lượng sai.
5. **Từ điển Oracle Text** (`DR$IX_IDXLAB_NAME_TXT$I`): `PRO` 20.159, `SONY` 16.679, `TAI` 13.333, `NGHE` 13.333, `615` 200 sản phẩm. Khớp với dữ liệu sinh ra. "Tai nghe" bị tách thành 2 token, đúng như bài 4 nói về tiếng Việt.
6. **Không index, Redis chậm hơn Oracle khoảng 12 lần** (430 ms so với 34 ms). Khoảng cách nhỏ hơn E1 (119 lần) vì ở E2 Oracle cũng phải làm việc nặng với chuỗi; nhưng Redis vẫn phải gọi `JSON.GET` 200.000 lần cho mỗi lần tìm.
7. **Có index, RediSearch nhanh hơn Oracle Text** trong cả 3 trường hợp: khoảng 18 lần với từ hiếm (0,056 so với 1,00 ms), khoảng 1,3 lần với 2 từ, khoảng 9 lần với từ phổ biến (0,71 so với 6,36 ms). Một phần do Redis trả trang + tổng số trong 1 lệnh trên RAM, còn Oracle chạy 2 câu SQL và với CONTEXT phải đọc các dòng từ bảng để sắp theo điểm. Mọi trường hợp đều dưới 10 ms, người dùng khó nhận ra khi ít truy cập; khác biệt có ý nghĩa khi nhiều người tìm cùng lúc.
8. **RediSearch mặc định dùng stemming tiếng Anh:** `FT.EXPLAIN` cho thấy `sony` được mở rộng thành `soni`, `+soni`. Không ảnh hưởng kết quả ở đây, nhưng với tên tiếng Việt có thể sinh khớp lạ; cân nhắc `NOSTEM` hoặc `LANGUAGE` khi thiết kế index thật. Oracle Text mặc định không stemming.
9. Thứ tự 5 kết quả đầu của "sony pro" khác nhau giữa Oracle và Redis vì mọi sản phẩm khớp có cùng điểm (mỗi từ xuất hiện 1 lần); khi bằng điểm, mỗi hệ thống tự chọn thứ tự. Tập kết quả vẫn giống nhau.

## E3. Lọc + sắp xếp + phân trang (F4)

Mỗi "lần tìm" = trang đầu 24 sản phẩm xếp giá tăng (chỉ lấy id) + đếm tổng số kết quả. Ba câu hỏi:
**A** danh mục 5 và giá 1–5 triệu, đang bán (321 kết quả); **B** chỉ giá 1–5 triệu, đang bán (15.218); **C** chỉ danh mục 5, đang bán (4.000). Mọi cấu hình, mọi cách đều trả đúng các số này.

Thô: `results/oracle/e3-filter.txt` (`oracle/e3_filter.sql`), `results/redis/e3-filter.txt` (`redis/e3_filter.sh`), 30/10/2026. Phía Oracle không ép hint: optimizer tự chọn cách chạy.

### Thời gian 1 lần tìm (ms)

| Cách | A (321) | B (15.218) | C (4.000) |
|---|---|---|---|
| Oracle, không index | 4,941 | 6,712 | 4,585 |
| Oracle, 2 index đơn `(category_id)`, `(price)` | 0,582 | 2,952 | 0,679 |
| Oracle, ghép đúng thứ tự `(category_id, status, price)` | **0,024** | 6,878 | **0,101** |
| Oracle, ghép sai thứ tự `(price, category_id, status)` | 0,484 | **0,508** | 2,837 |
| Redis, không index (`SCAN` + giải mã JSON) | 652,11 | 701,23 | 672,56 |
| Redis, Sorted Set tự làm | 0,021 * | 0,005 | 0,004 |
| RediSearch (NUMERIC, NUMERIC SORTABLE, TAG) | 1,041 | 2,990 | 1,023 |

\* Lượt đo thứ 4 của ô này là 1,62 ms so với 0,11–0,15 ms ở 4 lượt còn lại (nhiễu); trung vị khoảng 0,006 ms/lần. Các ô khác độ lệch dưới 10%.

### Kế hoạch Oracle chọn (trang đầu; Buffers)

| Cấu hình | A | B | C |
|---|---|---|---|
| Không index | `TABLE ACCESS FULL` + `SORT ORDER BY STOPKEY` (1.753) | như A (1.753) | như A (1.753) |
| 2 index đơn | `IX_E3_CAT` range scan 4.000 dòng, lọc giá trên bảng, sắp xếp (82) | `IX_E3_PRICE` range scan theo thứ tự giá, dừng ở 25 dòng, **không sắp xếp** (29) | `IX_E3_CAT` 4.000 dòng + sắp xếp (82) |
| Ghép đúng | range scan với **cả 4 điều kiện là `access`**, dừng ở 24, không sắp xếp (10); câu đếm **chỉ đọc index** (4) | **Bỏ index**, `TABLE ACCESS FULL` + sắp xếp (1.753): thiếu cột đầu `category_id` | range scan `(category_id, status)`, giá đã xếp sẵn, dừng ở 24 (11) |
| Ghép sai | range scan theo giá; `category_id`, `status` thành `filter` trong index; trang dừng sớm (14), câu đếm đọc 61 buffer thay vì 4 | range scan theo giá, dừng ở 24 (29); câu đếm chỉ đọc index | `INDEX FULL SCAN` theo thứ tự giá, lọc danh mục trong index (15); câu đếm phải đọc cả index |

### Cái giá của index

| | Dung lượng | Thời gian tạo |
|---|---|---|
| Oracle `(category_id)` / `(price)` | 4 MB + 4 MB (đĩa) | 0,14 s + 0,11 s |
| Oracle ghép 3 cột (mỗi thứ tự) | 6 MB (đĩa) | 0,16–0,17 s |
| Redis Sorted Set tự làm (51 key, 380.000 phần tử) | 31,7 MB (RAM) | 1,44 s |
| RediSearch 3 trường | 33,6 MB (RAM): cố định 26,9 (doc + key table) + `sortable_values` 4,6 + `inverted` 2,1 | 4,49 s |

`CLUSTERING_FACTOR`: `IX_E3_CAT` 3.534 (gần số block 1.792, tốt); `IX_E3_PRICE` 200.000 (bằng số dòng, xấu nhất); ghép đúng 37.232; ghép sai 200.000.

### Nhận xét

1. **Thứ tự cột quyết định tất cả (bài 2 được kiểm chứng trọn vẹn).** Cùng 3 cột, cùng 6 MB:
   - Ghép đúng `(category_id, status, price)`: A nhanh hơn không index **khoảng 200 lần** (0,024 so với 4,94 ms), C khoảng 45 lần. Mọi điều kiện của A đều là `access` (đi thẳng tới đúng đoạn trong index), kết quả đã xếp sẵn theo giá nên dừng ở dòng 24, và câu đếm **chỉ đọc index** (index "đủ cột", 4 buffer).
   - Nhưng với B (không lọc danh mục), index này **vô dụng**: optimizer tự bỏ nó và quét toàn bảng (6,88 ms, như không index). Đúng ví dụ "tìm Tên mà không biết Họ".
   - Ghép sai `(price, ...)`: A chậm hơn ghép đúng 20 lần (`category_id`, `status` chỉ là `filter`: phải đọc mọi mục trong khoảng giá rồi loại), nhưng lại là **tốt nhất cho B**.
2. **Không có index nào tốt nhất cho mọi câu hỏi.** Thiết kế index phải dựa vào **câu hỏi người dùng thường hỏi**. Với ShopFlash, khách thường duyệt theo danh mục rồi lọc giá (A, C) → `(category_id, status, base_price)`. Nếu cần lọc giá trên toàn cửa hàng (B) thì giữ thêm index trên giá. Hiện `V1__schema.sql` có `(category_id, status)` và `(base_price)`, gần với cấu hình "2 index đơn"; mở rộng thành `(category_id, status, base_price)` sẽ nhanh hơn khoảng 24 lần cho A theo số đo này.
3. **Optimizer tự chọn đúng** trong mọi cấu hình: dùng index khi có lợi, bỏ khi không (B với ghép đúng), chọn index cho phép dừng sớm thay vì phải sắp xếp (B với index đơn chọn `IX_E3_PRICE` dù clustering factor xấu nhất, vì chỉ cần 25 dòng).
4. **Ước lượng sai vì thiếu histogram (bài 3):** với A, `E-Rows = 160`, `A-Rows = 321`. Không có histogram, optimizer coi `status = 'ACTIVE'` lọc 50% (2 giá trị) trong khi thực tế 95%: 200.000 × 1/50 × 8% × 50% = 160. Đúng ví dụ ở bài 3.
5. **RediSearch kém hẳn index ghép của Oracle cho bài toán này:** A chậm hơn khoảng 43 lần (1,04 so với 0,024 ms), C khoảng 10 lần. RediSearch không có index nhiều cột: mỗi trường một cấu trúc riêng, rồi **giao (INTERSECT)** các danh sách như `FT.EXPLAIN` cho thấy. Điều kiện `status:{active}` khớp 190.000 document (95%), cộng với phải sắp xếp và đếm toàn bộ kết quả trước khi trả trang đầu. Oracle thì đi thẳng vào một đoạn liên tục của cây đã xếp sẵn và dừng ở 24. Với B, RediSearch ngang Oracle 2 index đơn (2,99 so với 2,95 ms).
6. **Sorted Set tự làm nhanh nhất (4–6 µs)** vì được dựng **riêng cho đúng các câu hỏi này**: mỗi danh mục một Sorted Set, chỉ chứa sản phẩm đang bán (điều kiện `status` "nướng sẵn" vào index). Cái giá: 31,7 MB RAM, mỗi tổ hợp bộ lọc mới cần thêm key, và ứng dụng phải tự cập nhật khi đổi giá, đổi danh mục, ngừng bán (bài 6).
7. **E3 ngược với E2:** tìm toàn văn thì RediSearch nhanh hơn Oracle Text; lọc có cấu trúc + sắp xếp + phân trang thì B-tree ghép của Oracle nhanh hơn RediSearch. Mỗi bên mạnh ở loại câu hỏi khác nhau.
8. Không index: Redis chậm hơn Oracle khoảng 100–140 lần (650–700 ms so với 4,6–6,7 ms), vì phải giải mã JSON 200.000 document mỗi lần tìm.

## E4. Chi phí khi ghi

Cùng một lượng ghi trên các cấu hình có số index tăng dần: **THÊM** 10.000 sản phẩm mới (mỗi lần 1 dòng, rồi 1 commit) và **SỬA GIÁ** 10.000 sản phẩm (mỗi lần 1 dòng, rồi 1 commit). Sau mỗi lần đo, dữ liệu được trả về như cũ (không tính giờ). Oracle ghi vào bảng riêng `IDXLAB_WRITE` (bản sao 200.000 dòng, có khóa chính, tạo lại cho mỗi cấu hình). Kèm lượng nhật ký sinh ra: **redo** (Oracle, `v$mystat`) và **AOF** (Redis, `aof_current_size`, `appendfsync everysec`).

Thô: `results/oracle/e4-write.txt` (`oracle/e4_write.sql`), `results/redis/e4-write.txt` (`redis/e4_write.sh`), 30/10/2026. Lần chạy này nhãn dòng SỬA GIÁ phía Redis bị in sai thành `THEM / SUA GIA` do lỗi biến trong script; đã sửa script và sửa nhãn trong file thô, số liệu giữ nguyên.

### Oracle (µs mỗi dòng; redo cho 10.000 dòng)

| Cấu hình | THÊM | redo THÊM | SỬA GIÁ | redo SỬA GIÁ |
|---|---|---|---|---|
| C0: chỉ khóa chính | 9,8 | 5,5 MB | 6,6 | 2,7 MB |
| C1: + SKU | 11,1 | 7,9 MB | 6,1 | 2,7 MB |
| C2: + `(category_id, status, price)` + `(price)` | 19,3 | 12,7 MB | 17,3 | 12,0 MB |
| C3: C2 + Oracle Text CTXCAT | **1.014,8** | 33,1 MB | **635,8** | 12,0 MB |
| C4: C2 + Oracle Text CONTEXT `SYNC (ON COMMIT)` | 48,1 | 21,7 MB | 43,3 * | 12,0 MB |

\* Độ lệch của ô này (± 589 ms trên trung bình 433 ms cho 10.000 dòng) cho thấy có một lượt bất thường; giá trị đáng tin hơn là gần mức C2. Script đã được bổ sung in từng lượt để kiểm tra khi chạy lại. Các ô khác độ lệch dưới 16%.

### Redis (µs mỗi document; AOF cho 10.000 document)

| Cấu hình | THÊM | AOF THÊM | SỬA GIÁ | AOF SỬA GIÁ |
|---|---|---|---|---|
| R0: không index phụ | 5,6 | 1,8 MB | 2,6 | 0,7 MB |
| R1: tự làm `idxlab:sku:*` | 6,5 | 2,3 MB | 2,5 | 0,7 MB |
| R2: tự làm SKU + Sorted Set giá | 11,7 | 3,5 MB | 6,8 | 1,9 MB |
| R3: RediSearch chỉ `sku` TAG | 8,2 | 1,8 MB | 6,3 | 0,7 MB |
| R4: RediSearch đầy đủ như `idx:products` | 14,3 | 1,8 MB | 15,4 ** | 0,7 MB |

\*\* Độ lệch ± 64,6 ms trên 154,3 ms: có một lượt chậm bất thường. Các ô khác độ lệch dưới 18%.

### Nhận xét

1. **Mỗi index làm thêm dòng chậm đi và sinh thêm nhật ký (bài 5 được kiểm chứng).** Oracle: thêm 1 B-tree (C1) làm THÊM chậm khoảng 13% và tăng redo 2,4 MB/10.000 dòng; 3 B-tree (C2) làm THÊM chậm gần **2 lần** so với chỉ có khóa chính, redo tăng 2,3 lần. Redis tự làm: 1 index (R1) +16%, 3 cấu trúc (R2) chậm khoảng 2,1 lần.
2. **SỬA GIÁ chỉ tốn thêm khi index có chứa cột giá — với Oracle.** C1 (chỉ thêm index SKU) sửa giá **nhanh như C0**, redo y hệt (2,7 MB): Oracle không đụng index không chứa cột bị sửa. C2 có 2 index chứa giá → sửa giá chậm 2,6 lần, redo **4,4 lần** (mỗi lần sửa = xóa mục cũ + chèn mục mới ở vị trí khác trong 2 index).
3. **RediSearch thì index lại cả document dù chỉ sửa một trường.** R3 chỉ index `sku`, nhưng sửa giá vẫn chậm **2,4 lần** so với R0 (6,3 so với 2,6 µs). R4 (có `name TEXT`) sửa giá chậm **6 lần**, còn chậm hơn cả THÊM: mỗi lần đổi giá, RediSearch tách từ lại tên sản phẩm. Ngược với Oracle (nhận xét 2). Index tự làm (R1) thì không bị: sửa giá không đụng `idxlab:sku:*`.
4. **Oracle Text CTXCAT là cái giá đắt nhất:** THÊM chậm khoảng **53 lần** so với C2 (1.015 so với 19 µs/dòng), redo 33 MB. Bất ngờ hơn: **SỬA GIÁ (không đổi tên) vẫn chậm khoảng 37 lần** so với C2, trong khi redo bằng hệt C2 (12,0 MB), tức là không ghi gì thêm vào index CTXCAT. Giả thuyết: Oracle vẫn gọi thủ tục cập nhật của domain index cho mỗi dòng bị UPDATE, và thủ tục của CTXCAT tốn nhiều CPU dù cuối cùng không phải thay đổi gì. Cần kiểm chứng thêm nếu muốn đưa vào báo cáo như một kết luận. Kết hợp với E2 (73 MB), CTXCAT không hợp với bảng sản phẩm có ghi thường xuyên.
5. **CONTEXT với `SYNC (ON COMMIT)` rẻ hơn nhiều:** THÊM 48 µs/dòng (2,5 lần C2), vì sản phẩm mới được gom lại và đồng bộ một lần lúc commit. Đây là cách hợp lý cho ShopFlash nếu dùng Oracle Text.
6. **Nhật ký khác nhau về bản chất:** redo của Oracle **tăng theo từng index** (5,5 → 7,9 → 12,7 → 21,7 → 33,1 MB khi THÊM), vì Oracle ghi nhật ký cho cả index để khôi phục được sau sự cố. AOF của Redis **giống hệt nhau** ở R0, R3, R4 (1,8 MB): RediSearch **không ghi index vào AOF**, khi khởi động lại Redis phải **index lại toàn bộ document** (với 200.000 document là khoảng 4–5 giây, như thời gian tạo index ở E1–E3). Index tự làm (R1, R2) là dữ liệu bình thường nên có làm tăng AOF. Liên quan chủ đề Backup & Recovery của TV1.
7. **So hai bên ở cấu hình tương đương:** index tìm kiếm đầy đủ: Oracle C4 48 µs/dòng so với Redis R4 14 µs; 3 B-tree so với 3 cấu trúc tự làm: 19 so với 12 µs. Redis ghi nhanh hơn, nhưng lưu ý hai bên bảo đảm độ bền khác nhau: Oracle ghi redo xuống đĩa khi commit, Redis AOF `everysec` ghi xuống đĩa mỗi giây (sập máy có thể mất tới khoảng 1 giây dữ liệu). Mọi giá trị đều chỉ vài chục µs, trừ CTXCAT.
8. **Bối cảnh ShopFlash:** admin thêm hoặc sửa sản phẩm vài lần mỗi ngày, khách tìm kiếm liên tục. Chậm thêm vài chục µs mỗi lần ghi là không đáng kể so với lợi ích đọc ở E1–E3. Ngoại lệ đáng lưu ý: CTXCAT, và việc RediSearch index lại cả document mỗi lần đổi giá (tồn kho `inStock` cũng nằm trong document, nên mỗi lần đồng bộ tồn kho cũng gây index lại).

## E6. Index cập nhật lúc nào

Demo hành vi, không đo tốc độ. Thô: `results/oracle/e6-freshness.txt` (`oracle/e6_freshness.sql`), `results/redis/e6-freshness.txt` (`redis/e6_freshness.sh`), 30/10/2026. "Phiên khác" phía Oracle được giả lập bằng hàm `AUTONOMOUS_TRANSACTION` (transaction riêng, chỉ thấy dữ liệu đã commit).

### Oracle

| Bước | Kết quả |
|---|---|
| **B-tree** (`ix_idxlab_sku`, kế hoạch `INDEX UNIQUE SCAN`) | |
| 1a. INSERT, chưa COMMIT, phiên mình tìm SKU | 1 sản phẩm |
| 1b. INSERT, chưa COMMIT, phiên khác tìm SKU | 0 |
| 1c. Sau ROLLBACK, phiên mình | 0 |
| 1d. INSERT lại + COMMIT, phiên khác | 1 |
| **Oracle Text CONTEXT, cấu hình mặc định** | |
| 2a. Đã COMMIT, tìm bằng `LIKE` | 1 |
| 2b. Đã COMMIT, tìm ngay bằng `CONTAINS` | **0** |
| 2c. `CTX_USER_INDEXES.idx_sync_type` | `MANUAL` |
| 2e. Không gọi đồng bộ tay (user `SHOP` không có quyền `CTX_DDL`), tìm lại mỗi giây | Thấy sau khoảng **3,0 giây** |
| **Oracle Text CONTEXT `SYNC (ON COMMIT)`** | |
| 3a. Chưa COMMIT, phiên mình tìm SKU (B-tree) | 1 |
| 3b. Chưa COMMIT, phiên mình `CONTAINS` | **0** |
| 3c. Sau COMMIT, `CONTAINS` | 1 |

### Redis

| Bước | Kết quả |
|---|---|
| **RediSearch vừa `FT.CREATE`** (index chạy nền trên 200.000 document) | |
| Tìm `*` ngay sau khi tạo, 4 lần cách nhau khoảng 0,3 s | 78 → 9.982 → 21.148 → 32.736 / 200.000 |
| Sau khi index xong | 200.000 / 200.000 |
| **RediSearch khi ghi** (client khác tìm ngay sau mỗi lệnh) | |
| Thêm document "Zeta", tìm `@name:zeta` | 0 → **1** ngay sau `JSON.SET` |
| Tăng giá 7.990.000 → 8.990.000 | tìm giá cũ 0, giá mới 1 ngay lập tức |
| Xóa key | 0 |
| **Index tự làm** (Sorted Set giá), "sập" sau `JSON.SET` trước `ZADD` | |
| Giá trong dữ liệu / trong index | 9.999.000 / **7.142.000** |
| Lọc giá 9,99–10,01 triệu có sản phẩm không | **0** (đúng phải là 1) |
| Lọc giá 7,1–7,2 triệu có sản phẩm không | **1** (đúng phải là 0) |
| **`MULTI` + `JSON.SET` giá + `ZADD` score sai + `EXEC`** | `JSON.SET` OK, `ZADD` lỗi `value is not a valid float` |
| Giá trong dữ liệu / trong index sau `EXEC` | 5.555.000 / **9.999.000**: lệnh đầu không được hoàn tác |

### Nhận xét

1. **B-tree của Oracle theo đúng transaction:** phiên mình thấy thay đổi chưa commit, phiên khác thì không; rollback thì bảng và index cùng quay về; commit thì mọi phiên cùng thấy. Không bao giờ có lúc index lệch bảng.
2. **Oracle Text không theo transaction như B-tree:** kể cả với `SYNC (ON COMMIT)`, chính phiên vừa thêm cũng **không** tìm thấy bằng `CONTAINS` trước khi commit (3b), trong khi B-tree thì thấy (3a).
3. **Oracle AI Database 26ai mặc định tự bảo trì index Oracle Text ở chế độ nền:** index tạo không tham số có `idx_sync_type = MANUAL`, nhưng sau khi commit khoảng **3 giây** thì tự tìm thấy. Theo tài liệu Oracle, từ 26ai index CONTEXT mới tạo mặc định dùng *automatic maintenance* kết hợp `SYNC (MANUAL)`: `CTX_DDL.SYNC_INDEX` được tự gọi ở chế độ nền, tiến trình lập lịch kiểm tra việc cần làm **mỗi 3 giây** theo mặc định, và không thể đổi khoảng này; `SYNC (ON COMMIT)` thuộc chế độ bảo trì thủ công [Oracle Text Application Developer's Guide 26ai, §5.8 "Using Automatic Maintenance for an Index"]. Ở các bản trước 26ai, `SYNC (MANUAL)` nghĩa là phải tự gọi đồng bộ. Số đo 3,0 giây khớp với mô tả này. (`CTX_USER_INDEXES` có thêm cột `IDX_MAINTENANCE_TYPE` để xác nhận `AUTO`; script hiện chưa in cột này.)
4. User `SHOP` không có quyền `CTX_DDL` (cần role `CTXAPP`), nên ứng dụng không tự gọi đồng bộ được; với 26ai thì không cần nhờ chế độ tự bảo trì. Cách dự phòng cũ `ALTER INDEX ... REBUILD PARAMETERS ('SYNC')` báo `DRG-11000: invalid keyword SYNC`.
5. **RediSearch: ghi xong là mọi client tìm thấy ngay**, cả thêm, sửa, xóa. Redis không có khái niệm "chưa commit": mỗi lệnh có hiệu lực ngay với mọi client.
6. **Nhưng ngay sau khi tạo index (hoặc khi Redis khởi động lại và phải index lại, xem E4), tìm kiếm chỉ thấy một phần dữ liệu mà không báo lỗi.** Oracle không có trạng thái này: `CREATE INDEX` xong mới có hiệu lực.
7. **Index tự làm có thể lệch dữ liệu âm thầm** khi ứng dụng dừng giữa hai lệnh, và `MULTI/EXEC` không cứu được: lệnh lỗi giữa chừng không làm hoàn tác lệnh trước. Kết quả lọc giá sai (thiếu sản phẩm đúng, thừa sản phẩm sai) mà không ai được báo. Đây là rủi ro lớn nhất của cách "tự làm index" ở bài 6, đối lập với nhận xét 1.
8. **Ba mức "thấy ngay" trong ShopFlash:** (a) B-tree Oracle: ngay trong transaction; (b) RediSearch: ngay sau lệnh ghi vào Redis, nhưng document Redis chỉ là bản sao của Oracle, nên còn phụ thuộc lúc projection được cập nhật (module storage); (c) Oracle Text 26ai: khoảng 3 giây sau commit. Với tìm kiếm sản phẩm, trễ vài giây là chấp nhận được; với tồn kho khi đặt hàng thì phải đọc Oracle.

Tài liệu: Oracle, "5.8 Using Automatic Maintenance for an Index", *Oracle Text Application Developer's Guide*, Oracle AI Database 26ai. https://docs.oracle.com/en/database/oracle/oracle-database/26/ccapp/using-automatic-maintenance-index.html
