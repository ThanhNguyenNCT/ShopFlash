# So sánh và lựa chọn cho ShopFlash (Indexing, F3/F4)

Tổng hợp từ 6 thí nghiệm ở `ket-qua.md`. Số liệu là thời gian **1 lần tìm**, đo **bên trong DB**, 200.000 sản phẩm, trung bình 5 lần đo. Phần cuối là quyết định cho ứng dụng.

## 1. Bảng so sánh theo tiêu chí

| Tiêu chí | Oracle | Redis + RediSearch | Bằng chứng |
|---|---|---|---|
| Tìm chính xác (SKU), có index | 0,006 ms (B-tree unique) | 0,010 ms (TAG); 0,0035 ms (key tự làm) | E1 |
| Tìm chính xác, **không** index | 3,2 ms (quét toàn bảng) | 382 ms (`SCAN` + `JSON.GET`) | E1 |
| Tìm từ trong tên | Oracle Text: 0,4–6,4 ms (CTXCAT / CONTEXT) | **RediSearch TEXT: 0,06–0,9 ms** | E2 |
| Tìm từ trong tên, không index | `LIKE '%từ%'`: 34–37 ms | `SCAN`: 424–435 ms | E2 |
| Lọc danh mục + giá + sắp xếp + phân trang | **Index ghép đúng thứ tự: 0,02–0,1 ms** | RediSearch: 1,0 ms (không có index nhiều cột, phải giao các danh sách) | E3 |
| Chỉ lọc giá trên toàn cửa hàng | 0,5–3,0 ms (tùy index) | 3,0 ms | E3 |
| Ai chọn cách chạy | Optimizer theo chi phí và thống kê; tự bỏ index khi không có lợi | Chạy theo index đã khai báo, không có optimizer dựa trên thống kê | E1–E3 |
| Chi phí ghi | Mỗi index thêm vài µs/dòng; sửa cột không có trong index thì không tốn thêm | Mỗi lần sửa bất kỳ trường nào, RediSearch index lại **cả document** | E4 |
| Nhật ký và khôi phục | Index được ghi redo, khôi phục cùng bảng | RediSearch không ghi index vào AOF; khởi động lại phải index lại toàn bộ (vài giây) | E4 |
| Dung lượng index | B-tree 4–6 MB; Oracle Text CONTEXT 22 MB (đĩa) | RediSearch 33–50 MB (RAM), trong đó khoảng 27 MB cố định cho 200.000 document | E1–E3 |
| Nhất quán | B-tree theo transaction, không bao giờ lệch; Oracle Text 26ai trễ khoảng 3 s sau commit | Thấy ngay sau lệnh ghi; chỉ thấy **một phần** khi index đang dựng; index tự làm có thể **lệch âm thầm** | E6 |
| Vai trò dữ liệu | Nguồn gốc | Bản sao (projection) của Oracle, có thể cũ | Thiết kế nhóm |

## 2. Đánh giá

- **Không có bên nào thắng mọi loại câu hỏi.** Tìm theo từ khóa trong tên: RediSearch nhanh hơn Oracle Text từ 1,3 đến 18 lần. Lọc có cấu trúc (danh mục, giá) kèm sắp xếp và phân trang: index ghép của Oracle nhanh hơn RediSearch từ 10 đến 43 lần.
- **Mọi cách có index đều dưới 10 ms** cho một lần tìm. Một khách hàng sẽ không cảm nhận được khác biệt; khác biệt có ý nghĩa khi nhiều người tìm cùng lúc (số lần tìm mỗi giây mà DB phục vụ được).
- **Không có index thì Redis rất chậm** (vài trăm ms), chậm hơn Oracle không index 12–140 lần: Redis chỉ nhanh khi biết tên key hoặc đã có index.
- **Oracle mạnh về tính đúng:** index luôn khớp dữ liệu, optimizer tự chọn cách chạy, dữ liệu là bản gốc. **Redis mạnh về tìm toàn văn trên RAM**, đổi lại tốn RAM, phải đồng bộ bản sao, và có những khoảng thời gian kết quả chưa đầy đủ.
- **Loại bỏ:** CTXCAT (gấp 5 lần dung lượng bảng, ghi chậm 37–53 lần, E2/E4; Oracle đã deprecated loại index này từ 26ai và khuyên dùng CONTEXT); bitmap index (khóa nhiều dòng khi ghi đồng thời, không hợp bảng sản phẩm có tồn kho thay đổi liên tục, bài 4); Redis index tự làm bằng Sorted Set (nhanh nhất nhưng có thể lệch âm thầm, `MULTI/EXEC` không cứu được, E6).

## 3. Quyết định cho F3/F4: mỗi DBMS làm việc nó giỏi nhất

Mỗi yêu cầu tìm kiếm được gửi tới **một** nguồn, chọn theo loại câu hỏi (không trộn kết quả hai nguồn):

| Yêu cầu của khách | Nguồn | Index dùng | Lý do |
|---|---|---|---|
| **Có từ khóa** (`q`), có hoặc không kèm lọc danh mục, giá, còn hàng | **RediSearch** `idx:products` | `name TEXT` + `categoryId`, `price`, `status`, `inStock` | E2: nhanh nhất cho tìm từ, có điểm liên quan; trả trang và tổng số trong 1 lệnh |
| **Không có từ khóa**, duyệt theo danh mục (có thể kèm khoảng giá) | **Oracle** | **Mới:** `(category_id, status, base_price)` | E3: nhanh nhất (0,02–0,1 ms), dữ liệu gốc, đã xếp sẵn theo giá |
| Không có từ khóa, chỉ lọc giá trên toàn cửa hàng | Oracle | `ix_products_price` (có sẵn) | E3: index đơn trên giá ngang RediSearch; giữ một nguồn cho mọi yêu cầu không có từ khóa |
| Tìm đúng mã SKU | Oracle | `uk_products_sku` (có sẵn) | E1: cả hai đều vài µs; Oracle là nguồn gốc |
| Trang chi tiết sản phẩm, tồn kho | Oracle | Khóa chính | Dữ liệu mới nhất; tồn kho trong Redis có thể cũ |
| Redis không truy cập được | Oracle Text CONTEXT trên `products(name)` | **Mới** | E2: 1–6 ms vẫn chấp nhận được; Oracle 26ai tự đồng bộ sau khoảng 3 s (E6) |

Sắp xếp hỗ trợ: độ liên quan (khi có từ khóa), giá tăng, giá giảm.

## 4. Thay đổi so với hiện trạng và rủi ro cần nêu

**Thay đổi trên Oracle (migration của module indexing):**
- Thêm index `(category_id, status, base_price)` trên `PRODUCTS`. Index này bắt đầu bằng `category_id` nên vẫn thỏa quy tắc "khóa ngoại phải có index". Index cũ `ix_products_category_status (category_id, status)` trở nên thừa (là phần đầu của index mới); việc bỏ nó là thay đổi schema chung, cần nhóm đồng ý.
- Thêm index Oracle Text CONTEXT trên `products(name)` cho đường dự phòng.
- Hai index này làm **thay đổi số đo footprint của TV1** (B-tree mới được tính; các bảng phụ `DR$...` của Oracle Text thì không). Theo thỏa thuận, TV2 đo lại và cập nhật báo cáo của TV1.

**Rủi ro của phần dùng Redis (phải nêu trong báo cáo):**
- Document `product:{id}` là bản sao, do module storage cập nhật sau khi Oracle commit; `inStock` hiện **không** được cập nhật sau checkout. Kết quả tìm kiếm có thể hiện tình trạng còn hàng cũ; trang chi tiết và đặt hàng luôn đọc Oracle.
- Khi `idx:products` được tạo lại hoặc Redis khởi động lại, trong vài giây tìm kiếm chỉ thấy một phần sản phẩm (E6).
- Mỗi lần storage cập nhật tồn kho hoặc giá, RediSearch index lại cả document, kể cả tách từ lại tên (E4).
- `idx:products` dùng stemming tiếng Anh mặc định (`sony` → `soni`, E2); chưa gây sai với dữ liệu thí nghiệm, nhưng với tên tiếng Việt nên cân nhắc `NOSTEM` (thay đổi `init.lua`, file chung).
- Chưa đo trường hợp **từ khóa kèm bộ lọc** trên RediSearch (E2 chỉ đo từ khóa, E3 chỉ đo bộ lọc). Đây là một hạn chế của báo cáo, chưa được kiểm tra riêng.
