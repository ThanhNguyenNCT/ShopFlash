# Ghi chú thuyết trình: Oracle vs Redis, Nhóm 11

File này để tự đọc trước khi lên trình bày. Trên slide không có ghi chú người nói.
Số thứ tự cảnh khớp với bộ đếm ở góc trái dưới (cảnh 1 là bìa). Tổng thời gian 30 phút.

Phím tắt: mũi tên hoặc Space để chuyển cảnh, F để toàn màn hình, phím số 1 đến 7 để nhảy tới đầu phần. Mọi nội dung đã hiện sẵn trên slide, không cần bấm chuột.
Các phần lấy từ Canva (2, 3, 5, 6, 7) giữ nguyên câu chữ trên slide. Ghi chú bên dưới chỉ gợi ý cách nói.

## Mở đầu (khoảng 1 phút)

**Cảnh 1 · Bìa.** Chào thầy cô và các bạn. Nhóm 11 so sánh Oracle và Redis, rồi áp vào ShopFlash, một ứng dụng bán hàng có Flash Sale.
Bên phải là tên giảng viên hướng dẫn và năm thành viên.

**Cảnh 2 · Nội dung chính.** Đọc nhanh bảy phần. Slide không ghi thời lượng; tổng 30 phút, gồm 1 phút tổng kết.
Câu hỏi chung của cả bài: với từng loại dữ liệu, ShopFlash nên để ở DBMS nào.

## Phần 1 · Giới thiệu và kiến trúc (3 phút)

**Cảnh 3 · Mở phần 1.** Oracle là DBMS quan hệ, ghi xuống đĩa. Redis là kho key-value chạy trên RAM.
ShopFlash có hai luồng: mua hàng thường và Flash Sale, nơi nhiều người tranh một số suất có hạn.
Nhóm không đi tìm DBMS thắng cuộc. Nhóm chọn DBMS cho từng loại dữ liệu.

**Cảnh 4 · Bài toán và mục tiêu.** Đọc bốn thẻ theo thứ tự. Đề tài so sánh Oracle và Redis trên sáu chủ đề của môn học.
Mục tiêu là hiểu cơ chế, có kết quả đo, rồi chọn DBMS hợp với từng loại dữ liệu. Mọi thứ chạy trên máy local, không phải hệ thống production.

**Cảnh 5 · Kiến trúc tổng thể.** Sơ đồ tự sáng lần lượt từng luồng, mỗi luồng 3 giây, thẻ bên phải sáng theo. Nói theo luồng đang sáng.
Checkout: tạo Order, ghi Order Items, trừ Inventory, xóa Cart trong một transaction Oracle.
Tìm kiếm và báo cáo: Redis giữ bản chiếu dựng từ Oracle, có thể trễ hơn Oracle.
Flash Sale: Redis giữ suất bằng Lua, Oracle ghi đơn. Hai bên không có transaction chung nên dùng bù trừ (chi tiết ở phần 6).

**Cảnh 6 · Phân chia dữ liệu giữa Oracle và Redis.** Đọc bảng theo hàng. Một câu tóm tắt: Oracle giữ dữ liệu gốc và mọi thao tác ghi quan trọng. Redis giữ thứ dựng lại được từ Oracle, cộng trạng thái Flash Sale cần nhanh.
Nếu bị hỏi Redis mất dữ liệu thì sao: số suất còn lại tính lại bằng số suất cấp trừ số đã bán trong Oracle, số đã mua của từng khách đếm lại từ đơn hàng. Suất đang giữ dở thì mất. Khách đặt đơn bằng suất đó nhận lỗi RESERVATION_EXPIRED và phải giữ lại. Oracle vẫn không bán vượt. Câu UPDATE có điều kiện chặn ở suất cuối (thí nghiệm E4, phần 6).
Sáu phần sau kiểm chứng cách chia này bằng số đo.

## Phần 2 · Lưu trữ (3 phút)

**Cảnh 7 · Mở phần 2.** Câu hỏi: ghi xuống đĩa theo block hay giữ trên RAM?

**Cảnh 8 · Lưu trữ logic của Oracle.** Chỉ vào hình, đi từ lớn tới nhỏ: tablespace chứa segment, segment gồm extent, extent gồm các data block. Block 8 KB là đơn vị I/O nhỏ nhất.
Bên phải hình là phía vật lý: data file và OS block.

**Cảnh 9 · Lưu trữ của Redis.** Bốn ý theo bốn ô. Nhấn ô cuối: Redis không có ràng buộc schema, không có foreign key hay CHECK.

**Cảnh 10 · So sánh và thiết kế ShopFlash.** Bảng bên phải so bốn tiêu chí. Khối "Hybrid" bên trái là cách ShopFlash làm: commit Oracle xong mới cập nhật Redis. Redis lỗi thì không rollback Oracle; bản chiếu được dựng lại.

**Cảnh 11 · Kết luận.** Đọc ba ý. Ý 2 là điều nhóm đã thử: ghi thẳng giá âm hoặc Category không tồn tại vào Redis, Redis vẫn trả OK.

## Phần 3 · Chỉ mục (5 phút)

**Cảnh 12 · Mở phần 3.** Câu hỏi: index nào cho câu hỏi tìm kiếm nào?

**Cảnh 13 · Cấu trúc index và optimizer trong Oracle.** Ba loại index bên trái. Bên phải là CBO: Oracle tự ước lượng chi phí dựa trên thống kê rồi chọn dùng index hay quét bảng.

**Cảnh 14 · Index trong Redis.** Mặc định Redis chỉ tra được theo key. Muốn tìm theo giá trị thì dùng RediSearch hoặc tự dựng Set, Sorted Set. Không có optimizer: đường truy vấn nào cũng do lập trình viên thiết kế.

**Cảnh 15 · So sánh cơ chế indexing.** Đọc nhanh bốn hàng, dừng ở hàng "Quyết định truy vấn": Oracle tự chọn, Redis do người viết lệnh chọn.

**Cảnh 16 · Tìm chính xác theo mã SKU.** Có index thì cả hai đều dưới 0,01 ms. Không có index thì Oracle quét 3,2 ms, Redis SCAN mất 382 ms.
ShopFlash tra SKU bằng B-tree unique của Oracle vì đã đủ nhanh và đọc thẳng dữ liệu gốc.

**Cảnh 17 · Tìm từ khóa trong tên sản phẩm.** Cột cuối là RediSearch, nhanh nhất ở cả ba từ khóa. LIKE và SCAN chậm nhất.
ShopFlash dùng RediSearch làm chính, Oracle Text CONTEXT làm dự phòng khi Redis lỗi.

**Cảnh 18 · Lọc, sắp xếp giá và phân trang.** Ba câu hỏi A, B, C ghi ở trên bảng. Nhìn hàng tô màu: index ghép đúng thứ tự trả Câu A trong 0,024 ms. Ghép sai thứ tự thì chậm 20 lần.

**Cảnh 19 · Chi phí cập nhật index khi ghi.** Index không miễn phí. CTXCAT làm mỗi lần thêm dòng chậm 53 lần nên bị loại. B-tree và CONTEXT chỉ tốn thêm vài chục µs.

**Cảnh 20 · Dung lượng lưu trữ index.** CTXCAT chiếm 73 MB, gấp 5,2 lần bảng. RediSearch tốn RAM, khoảng 27 MB cố định cho 200.000 document.

**Cảnh 21 · Thời điểm index cập nhật.** B-tree khớp ngay sau COMMIT. CONTEXT trễ khoảng 3 giây. Index tự làm trên Redis có thể lệch mà không báo lỗi.
Vì vậy tìm kiếm chấp nhận trễ, còn đặt hàng và tồn kho luôn đọc Oracle.

**Cảnh 22 · Lựa chọn cho ShopFlash.** Đọc bảng tác vụ và nguồn phục vụ. Ba thẻ bên phải tóm lại phần 3.

## Phần 4 · Xử lý truy vấn

Thứ tự: nói 6 cảnh slide (cảnh 23 đến 28), demo trên hai DBMS trong terminal, rồi demo app. Đây là bản đầy đủ, đọc hết sẽ dài hơn khung 5 phút; khi lên trình bày thì lược bớt phần giải thích nếu bị giục giờ. Lời nói ghi trong ngoặc kép; chỉ dẫn trong ngoặc vuông là việc cần làm.

Chuẩn bị trước giờ trình bày:
- Chạy `scripts/dev.sh` trên máy trình bày, chờ tới dòng "Đang chạy". Lúc này app chỉ có dữ liệu mẫu, các phần 2, 3 demo trên dữ liệu này.
- Mở sẵn một terminal thứ hai ở thư mục gốc repo, chữ cỡ lớn (Ctrl + phím cộng vài lần) để người ngồi sau đọc được.
- Mở sẵn hai tab trình duyệt, đã đăng nhập admin: Query Lab (http://localhost:5173/admin/reports/lab) và Báo cáo (http://localhost:5173/admin/reports).
- Số trên màn hình là một lần chạy, lệch vài phần trăm so với slide. Nói "khoảng", không đọc số lẻ.

Khi nào nạp 100.000 đơn:
- Chạy `scripts/seed-history.sh` **ngay khi phần 3 demo xong**, lúc người phần 3 quay lại slide kết luận. Script mất khoảng 2 phút (nạp đơn 1,5 phút, dựng read model Redis 30 giây) và chạy xong trong lúc nói các cảnh slide của phần 4.
- Không chạy sớm hơn: script thêm 10.000 sản phẩm và 50 danh mục sinh tự động, sẽ hiện ra trong trang tìm kiếm của phần 3.
- Chờ dòng xanh "Read model Redis đã khớp Oracle" rồi mới chuyển sang demo.
- Sau khi nạp, chạy `bash report/query/demo/dbms-demo.sh thu-thap` một lần (bước 4 của demo DBMS cũng làm việc này) để thống kê khớp dữ liệu mới.
- Demo phần 4 xong, chạy `scripts/seed-history.sh --xoa` (khoảng 1 phút) để trả app về dữ liệu mẫu cho phần 5, 6, 7.

### Vì sao Oracle bỏ qua index (đọc để hiểu, không cần nói)

- Bảng `ORDERS` có index ghép `IX_ORDERS_STATUS_CREATED` trên `(status, created_at)`, dùng để lọc đơn hợp lệ theo khoảng ngày. `ORDER_ITEMS`, `PRODUCTS`, `CATEGORIES` có index của khóa chính và khóa ngoại.
- Index B-tree chỉ lưu giá trị các cột được đánh index kèm `ROWID`, tức địa chỉ của dòng trên bảng. Các cột khác của dòng (như `order_id`) không nằm trong index.
- Lá index xếp liền nhau theo `(status, created_at)`, nhưng các dòng thật của bảng nằm rải rác ở nhiều block. Đi qua index thì Oracle đọc lá index (`INDEX RANGE SCAN`), rồi với **mỗi `ROWID`** lại nhảy tới block chứa dòng đó (`TABLE ACCESS BY INDEX ROWID`), mỗi lần đọc một block lẻ (*single-block I/O*).
- `TABLE ACCESS FULL` đọc thẳng mọi block của bảng theo từng cụm liên tiếp (*multiblock I/O*). Báo cáo cần **87.139 trên 100.000 đơn**, tức 87%. Ở tỉ lệ đó, đọc cả bảng một lượt rẻ hơn đọc index rồi nhảy 87.139 lần.
- Kế hoạch khi mất thống kê (cảnh 25) cho thấy đúng cái giá này: đi qua index rồi đọc từng dòng `ORDERS` tốn **88.010 block** chỉ riêng bước đó.

### Slide

**Cảnh 23 · Mở phần 4.**
"Phần 4 là xử lý truy vấn. Em dùng các báo cáo doanh thu của trang quản trị ShopFlash: theo tháng, theo danh mục, top sản phẩm, khách VIP, và sản phẩm chưa từng bán. Dữ liệu là 100.000 đơn sinh tự động trong 12 tháng; báo cáo tính **87.139 đơn** hợp lệ với **215.805 dòng hàng**."

**Cảnh 24 · Hai cách chạy báo cáo doanh thu.**
"Ở cảnh này, em đặt hai hệ quản trị cạnh nhau, mỗi bên chạy một báo cáo doanh thu trên cùng khoảng ngày: bên trái là top sản phẩm trên Oracle, bên phải là doanh thu theo danh mục trên Redis. Điều em muốn so là **cách mỗi DBMS đi tới kết quả**."

**[Chỉ sang cột bên trái – Oracle]**

"Ở Oracle, em chỉ viết một câu SQL khai báo: join **4 bảng** chuẩn hóa `ORDERS`, `ORDER_ITEMS`, `PRODUCTS`, `CATEGORIES`, lọc đơn đã xác nhận hoặc hoàn tất, rồi gom nhóm theo sản phẩm. Em không chỉ cách chạy. Bộ tối ưu dựa trên chi phí, **CBO** (*Cost-Based Optimizer*), tự quyết định qua ba bước:"

- "**Bước 1, chọn đường đọc** (*access path*): báo cáo lấy tới **87% số đơn**, nên CBO bỏ qua index và **quét toàn bảng** `ORDERS` và `ORDER_ITEMS`, thay vì đọc index rồi nhảy theo từng `ROWID`."
- "**Bước 2, tự viết lại câu lệnh** (*query transformation*): câu SQL gốc viết join trước, gom sau. CBO đổi lại, gom **215 nghìn dòng hàng** theo sản phẩm trước, còn **9.000 nhóm**, rồi mới join. Phép biến đổi này tên là *group-by placement*; trong kế hoạch nó hiện thành view `VW_GBC_17`."
- "**Bước 3, chọn thuật toán join** (*join method*): CBO dùng **hash join** để nối 9.000 nhóm đó với `PRODUCTS` và `CATEGORIES`. CBO ước lượng **9.001 dòng**, thực tế **9.000**, gần như đúng tuyệt đối."

"Cả kế hoạch đọc **5.161 block** từ bộ đệm. Em lấy con số này làm mốc cho cảnh sau."

**[Chỉ sang cột bên phải – Redis]**

"Ở Redis thì ngược lại. Redis không có phép join và cũng không có CBO. Muốn có báo cáo theo danh mục, nhóm phải tự join và làm phẳng dữ liệu từ trước, lúc dựng **read model**: mỗi dòng hàng được lưu kèm sẵn tên sản phẩm, danh mục, trạng thái và ngày của đơn. Vì vậy lúc chạy báo cáo, số phép join là **0**."

"Ứng dụng gửi đúng **1 lệnh** `FT.AGGREGATE`. Lệnh chạy theo đúng thứ tự em viết, từ trái qua phải: lọc qua index theo trạng thái và khoảng ngày ra **215.805 dòng**, gom thành **50 nhóm** theo danh mục, rồi sắp xếp."

"Khác biệt chính: **với Oracle, DBMS quyết định cách chạy; với Redis, người viết lệnh quyết định.** Cái giá của Redis nằm ở chỗ khác: dựng lại read model cho 100.000 đơn mất **14 đến 39 giây**, và read model chỉ đúng tới lần dựng gần nhất."

**Cảnh 25 · Oracle chọn kế hoạch dựa trên thống kê.**
"CBO chọn giỏi đến đâu, và nó dựa vào gì? Nhóm làm hai thí nghiệm."

**[Chỉ bảng bên trái]**

"**Thí nghiệm 1, ép thuật toán join bằng hint.** Em giữ nguyên thứ tự join và ép lần lượt nested loops, hash, merge. Kế hoạch CBO tự chọn nhanh nhất ở cả ba báo cáo. Ép **nested loops** làm hai báo cáo join 4 bảng chậm đi **4,9 và 5,2 lần**. Lý do: với mỗi đơn trong 87.139 đơn, Oracle dò index `ORDER_ITEMS` một lần; với mỗi dòng trong 215 nghìn dòng hàng, nó lại dò sang `PRODUCTS` và `CATEGORIES`. Kế hoạch đó đọc khoảng **681 nghìn block**, gấp **132 lần** kế hoạch tự chọn. Khách VIP chỉ join 2 bảng nên chỉ chậm **1,3 lần**."

**[Chỉ bảng bên phải]**

"**Thí nghiệm 2, xóa thống kê** bằng `DELETE_TABLE_STATS`. Mất thống kê, CBO đoán sai nặng: sau gom nhóm nó ước lượng **285.000 dòng** trong khi thực tế **9.000**. Nó còn tin index lọc đơn chỉ trả về 2 dòng, thực tế 87.139. Vì tin điều kiện rất chọn lọc, CBO đi qua index rồi đọc từng dòng `ORDERS`, đổi hash join sang **merge join**, và số block đọc tăng từ 5.161 lên **91.035**, gấp **17,6 lần**."

"Thu thập lại bằng `GATHER_TABLE_STATS` thì hash join quay về, đúng plan hash cũ. Một điểm em gặp khi làm: phải truyền `no_invalidate => FALSE`. Để mặc định thì Oracle vẫn dùng kế hoạch cũ đang lưu trong *shared pool*, xóa thống kê xong kế hoạch vẫn y nguyên. Bài học vận hành cho ShopFlash: nạp nhiều dữ liệu xong phải thu thập lại thống kê."

**Cảnh 26 · Hai quyết định thiết kế trên Redis.**
"Redis không có CBO, nên tốc độ báo cáo phụ thuộc hoàn toàn vào **hai quyết định** của người thiết kế."

**[Chỉ khung bên trái]**

"**Quyết định 1, khai báo `SORTABLE` khi tạo index.** Các trường dùng trong `GROUPBY` và `REDUCE` phải khai báo `SORTABLE`, để RediSearch giữ sẵn giá trị trong index. Thiếu nó, Redis phải đọc lại JSON của từng document trong 215.805 document. Báo cáo theo danh mục mất khoảng **1,5 giây** phía server; khai báo xong còn **0,15 đến 0,33 giây**, nhanh hơn **4,5 đến 10 lần**. Hai con số này đo lúc nhóm dựng index, không cùng lần đo với bảng bên phải."

**[Chỉ bảng bên phải]**

"**Quyết định 2, để Redis tự gom nhóm hay kéo dữ liệu về ứng dụng.** Cách AGGREGATE để Redis gom trong một lệnh. Cách CLIENT coi Redis như kho key-value: lấy document thô theo trang, mỗi trang 10.000 document, rồi Java tự gom. Cách CLIENT phải gọi Redis **9 đến 22 lần** và kéo tới **215.805 dòng** qua mạng, nên chậm hơn **3,5 đến 30 lần**. Báo cáo theo danh mục chênh nhiều nhất: **55,8 ms** so với khoảng **1,7 giây**, vì kết quả cuối chỉ có 50 dòng mà phải kéo về cả 215 nghìn dòng."

**Cảnh 27 · Kết quả đo bốn báo cáo.**
"Cảnh này tổng hợp thời gian chạy, mỗi cách đo 5 lần, lấy trung bình."

- "**Join 4 bảng rồi gom nhóm**, tức theo danh mục và top sản phẩm: Redis AGGREGATE nhanh hơn nhẹ, **1,7 lần** và **1,1 lần**, vì phép join đã làm sẵn lúc dựng read model."
- "**Gom nhóm trên một bảng** (theo tháng) và **truy vấn có subquery** (khách VIP): Oracle nhanh hơn, **1,3 lần** và **1,5 lần**. Ở khách VIP, Oracle tính subquery mức chi trung bình đúng một lần rồi dùng để lọc. `FT.AGGREGATE` không có subquery, nên Redis phải chạy **2 lượt**: lượt một tính mức trung bình, ứng dụng đọc con số rồi chèn vào lượt hai."
- "**Sản phẩm chưa từng bán**, viết bằng `NOT EXISTS`: Oracle tự đổi thành **hash join anti**, chỉ quét index trên `order_items.product_id`, mất **31,7 ms** và tìm ra **1.147 sản phẩm**. Redis **không làm được**, vì read model chỉ chứa dòng hàng đã bán; sản phẩm chưa bán không có mặt để mà tìm."
- "Hai chênh lệch lớn thì lần đo nào cũng giống: ép nested loops chậm khoảng 5 lần, kéo dữ liệu về ứng dụng chậm nhiều lần."

**[Chỉ khung lần đo trước]**

"Lưu ý: lần đo trước, ngày 18/10, kết quả ngược lại, Oracle nhanh hơn ở cả bốn báo cáo. Nhóm chưa tìm ra nguyên nhân. Vì chênh lệch giữa hai bên dưới 2 lần và không ổn định, nhóm **không chọn DBMS dựa trên tốc độ**."

**Cảnh 28 · Lựa chọn cho báo cáo của ShopFlash.**
"Từ các thí nghiệm, nhóm chốt cách chạy báo cáo cho ShopFlash."

**[Chỉ cột Oracle]**

"**Oracle là mặc định cho báo cáo quản trị.** Nó luôn đọc dữ liệu đã commit mới nhất, thấy cả đơn vừa đặt. Join, subquery, anti-join viết được trong một câu SQL; thêm báo cáo mới chỉ cần thêm một câu SQL. Không có bản sao nào phải dựng lại."

**[Chỉ cột Redis cache]**

"**Redis dùng làm cache theo kiểu cache-aside.** Oracle tính, Redis giữ kết quả 5 phút. Xem lại cùng khoảng ngày thì chỉ mất **1,05 đến 1,90 ms**, nhanh hơn gọi Oracle **24 đến 96 lần**. Cái giá là số liệu có thể trễ tối đa 5 phút, vì đơn mới không xóa cache."

**[Chỉ cột read model]**

"**Read model trên Redis** mất **14 đến 39 giây** mỗi lần dựng lại, và nếu quên dựng thì báo cáo trả số cũ mà không báo lỗi. Nhóm không dùng nó cho báo cáo vận hành, chỉ giữ trong Query Lab để so sánh."

"Giờ em demo trực tiếp trên hai DBMS, rồi trên ứng dụng."
[Chuyển sang terminal.]

### Demo trên hai DBMS (terminal)

1. **Oracle tự chọn kế hoạch.** [Gõ `bash report/query/demo/dbms-demo.sh oracle`]
"Đây là đúng câu SQL top sản phẩm của backend, chạy thẳng trong SQL*Plus. Dòng `Elapsed` là thời gian chạy. Bên dưới là kế hoạch thực thi thật, lấy bằng `DBMS_XPLAN.DISPLAY_CURSOR`."
[Chỉ các dòng `HASH JOIN` và `TABLE ACCESS FULL`.]
"Oracle chọn **hash join** và quét toàn bảng, đúng như cảnh 24. Cột **E-Rows** là số dòng CBO ước lượng, **A-Rows** là số dòng thực tế, **Buffers** là số block đã đọc. Ở dòng gom nhóm, E-Rows và A-Rows gần bằng nhau, khoảng 9 nghìn."

2. **Ép nested loops.** [Gõ `bash report/query/demo/dbms-demo.sh oracle-nl`]
"Cùng câu đó, em thêm hint ép **nested loops**. Kế hoạch đổi sang `NESTED LOOPS`."
[Chỉ cột `Starts` và `Buffers`.]
"Cột `Starts` cho thấy index bị dò hàng chục nghìn lần, mỗi đơn một lần. Buffers tăng vọt, thời gian lâu hơn khoảng **5 lần**. CBO tự chọn đúng mà em không cần chỉ."

3. **Xóa thống kê.** [Gõ `bash report/query/demo/dbms-demo.sh xoa-thong-ke`]
"Giờ em xóa thống kê của 5 bảng rồi chạy lại cùng câu."
[Chỉ dòng `MERGE JOIN` và cột E-Rows.]
"Không còn thống kê, E-Rows ở dòng gom nhóm vọt lên khoảng **285 nghìn** trong khi thực tế **9 nghìn**. Oracle đổi sang **merge join**, đi qua index `IX_ORDERS_STATUS_CREATED` rồi đọc từng dòng đơn, số block đọc gấp khoảng **17 lần**."

4. **Thu thập lại.** [Gõ `bash report/query/demo/dbms-demo.sh thu-thap`]
"Thu thập lại thống kê với `no_invalidate => FALSE` thì **hash join** quay về, số block về lại khoảng 5 nghìn. Nạp nhiều dữ liệu xong phải thu thập lại thống kê."

5. **Redis.** [Gõ `bash report/query/demo/dbms-demo.sh redis`]
"Bên Redis là một lệnh `FT.AGGREGATE` trên read model, ra doanh thu theo danh mục."
[Chỉ phần `FT.PROFILE`.]
"`FT.PROFILE` cho thấy Redis chạy đúng các bước em viết: phần iterator là bước lọc theo trạng thái và ngày, phần result processor là đọc dòng, gom nhóm, sắp xếp. Không có bước nào do DBMS tự chọn."
"Giờ em demo ứng dụng."
[Chuyển sang trình duyệt.]

### Demo app

1. **Trang Báo cáo.** [Tab Báo cáo.]
"Đây là chức năng F5, F6 của ShopFlash: doanh thu theo tháng, theo danh mục, top bán chạy, khách VIP và sản phẩm chưa bán."
[Đổi khoảng ngày sang "6 tháng gần nhất", rồi đổi "Nguồn dữ liệu" từ Oracle sang Redis.]
"Đổi nguồn sang Redis thì số liệu giống nhau, vì read model vừa được dựng lại. Nhưng Redis chỉ đúng tới lần dựng gần nhất, còn Oracle luôn đọc dữ liệu mới."

2. **Query Lab, gom nhóm ở đâu.** [Tab Query Lab, mục "Round-trip và subquery", phần "Round-trip", chọn doanh thu theo danh mục, bấm "Chạy 3 cách".]
"Với Redis, ứng dụng phải tự chọn cách gọi. Cách AGGREGATE để Redis gom, **một lần gọi**. Cách CLIENT kéo hết dữ liệu về ứng dụng rồi tự gom: **hơn 20 lần gọi**, chậm hơn khoảng **30 lần**. Đây là quyết định thứ hai ở cảnh 26."

3. **Query Lab, subquery.** [Phần "Subquery", bấm "Chạy trên hai nguồn".]
"Báo cáo sản phẩm chưa từng bán cần `NOT EXISTS`. Oracle chạy được, ra danh sách sản phẩm; Redis báo **không hỗ trợ**, vì read model không có sản phẩm chưa bán. Em xin hết phần 4."

[Sau khi xong: ở terminal thứ hai chạy `scripts/seed-history.sh --xoa` để trả app về dữ liệu mẫu cho phần 5.]

Nếu demo lỗi (máy hoặc app không lên): bỏ qua demo, nói thêm theo cảnh 25 và 26, vì hai cảnh này có đủ số của phần demo.

## Phần 5 · Giao dịch (5 phút)

**Cảnh 29 · Mở phần 5.** Câu hỏi: lỗi ở bước cuối thì các bước trước đi đâu?

**Cảnh 30 · Transaction trong ShopFlash.** Một lần checkout ghi bốn bảng, phải coi là một đơn vị. Đọc nhanh bốn chữ ACID.

**Cảnh 31 · Bắt đầu và thực thi transaction.** Ví dụ tồn kho P1 = 100, giảm 1.
Oracle thực thi UPDATE ngay, session hiện tại đã thấy 99 dù chưa COMMIT. Redis sau MULTI chỉ xếp lệnh vào hàng đợi, trả QUEUED, chưa có kết quả.

**Cảnh 32 · Kết thúc transaction.** Hàng 03A: COMMIT và EXEC đều cho 99. Hàng 03B: ROLLBACK và DISCARD đều về 100.
Khác biệt nằm ở hàng cuối: Oracle có SAVEPOINT để hoàn tác một phần. Redis đã EXEC thì không có cách quay lại.

**Cảnh 33 · Lỗi sau khi đã ghi dữ liệu.** Oracle: lỗi CHECK ở bước cuối, ứng dụng rollback, bảng quay về như trước.
Redis: lệnh LPUSH lỗi WRONGTYPE nhưng các lệnh trước vẫn được ghi. Stock còn 99, Order vẫn tồn tại.

**Cảnh 34 · Ai chịu trách nhiệm bảo vệ dữ liệu?** Oracle từ chối câu SQL vi phạm UNIQUE, FOREIGN KEY, CHECK.
Redis không có constraint, nên kiểm tra trước khi ghi trong một Lua script. Vượt tồn thì trả OUT_OF_STOCK và chưa ghi gì.

**Cảnh 35 · Cô lập và lưu bền.** Hàng trên: Oracle READ COMMITTED không đọc dữ liệu chưa commit. Redis WATCH phát hiện key bị đổi thì hủy, ứng dụng thử lại.
Hàng dưới: Oracle ghi redo log khi COMMIT. Redis ghi đĩa qua RDB hoặc AOF; môi trường đo dùng AOF everysec.

**Cảnh 36 · Hiệu năng trong workload đã khảo sát.** Redis có P95 thấp hơn và throughput cao hơn ở cả bốn cấu hình. Nhắc lại: đây là workload mô phỏng, giỏ 1 hoặc 5 sản phẩm, 1 hoặc 10 worker.

**Cảnh 37 · Lựa chọn DBMS cho ShopFlash.** Oracle giữ Cart, Order, Inventory vì cần rollback, constraint, khóa dòng. Redis giữ quota và suất đang giữ của Flash Sale.

**Cảnh 38 · Demo giao dịch (video).** Vào cảnh là video tự phát, có tiếng; bấm mũi tên phải là dừng video và sang phần 6. Video dài 6 phút 46 giây. Cần mạng nếu máy không có sẵn file video.

## Phần 6 · Kiểm soát đồng thời (5 phút)

**Cảnh 39 · Mở phần 6.** Câu hỏi: 1000 khách tranh 100 suất, ai giữ được con số?

**Cảnh 40 · Bài toán và bốn cơ chế.** Đọc banner lost update: hai khách cùng đọc 99, cùng ghi 100, bộ đếm chỉ tăng 1.
Đi qua bốn thẻ: khóa bi quan, khóa lạc quan, MVCC, thao tác nguyên tử.

**Cảnh 41 · So sánh Oracle và Redis.** Khối trái: Oracle khóa từng dòng; Redis chạy từng lệnh hoặc script một mình nên không deadlock, nhưng cũng không rollback.
Câu cuối bảng: cả hai đều chặn được bán vượt, khác nhau ở chi phí khi tranh chấp cao.

**Cảnh 42 · Thiết kế Flash Sale ShopFlash.** Bốn bước: giữ suất bằng Lua trên Redis, đặt đơn trong transaction Oracle, xác nhận hoặc trả suất, job dọn suất hết hạn mỗi 5 giây.
Thanh màu dưới: Oracle giữ nguồn gốc, Redis giữ trạng thái chạy, Redis dựng lại được từ Oracle. Oracle là chốt chặn cuối: điều kiện sold + q <= allocated.

**Cảnh 43 · Kết luận.** Viết kiểu "đọc rồi ghi" thì bán vượt 900 suất trên 100 suất. Lua đạt 8.951 lượt/giây, gấp 4,6 lần UPDATE có điều kiện trên Oracle, ở 64 lượt đồng thời.
WATCH chỉ bán được 797 trên 2000 vì xung đột liên tục. Chốt: Redis + Lua giữ suất, Oracle + UPDATE có điều kiện chốt đơn.

## Phần 7 · Sao lưu và khôi phục (3 phút)

**Cảnh 44 · Mở phần 7.** Câu hỏi: mất dữ liệu thì bao lâu chạy lại, mất bao nhiêu?

**Cảnh 45 · Sao lưu của Oracle: Data Pump.** Theo mũi tên: expdp xuất schema SHOP ra file .dmp, impdp tạo lại bảng, index, ràng buộc và nạp dữ liệu.

**Cảnh 46 · Persistence của Redis.** RDB chụp nhanh, có thể mất dữ liệu giữa hai lần chụp. AOF ghi từng lệnh nên bền hơn.
Banner dưới là đánh đổi chính: fsync càng thường xuyên thì càng an toàn, ghi càng chậm.

**Cảnh 47 · Thiết kế sao lưu ShopFlash.** Một gói backup có ba phần: file Data Pump, file RDB, manifest SHA-256. Kiểm tra mã băm trước khi khôi phục.

**Cảnh 48 · Kết luận.** Khôi phục thành công mọi lượt thử. AOF always làm p95 ghi tăng khoảng 40 lần so với everysec.
ShopFlash chọn everysec: ghi gần như khi tắt AOF, mất tối đa khoảng 1 giây.

## Tổng kết (1 phút)

**Cảnh 49 · Tổng kết.** Đọc theo cột "ShopFlash dùng". Dữ liệu gốc và thao tác ghi quan trọng ở Oracle. Redis giữ bản chiếu để tìm kiếm, kết quả báo cáo đã tính, suất Flash Sale đang giữ. Mất Redis thì dựng lại từ Oracle, trừ suất đang giữ dở.
Mỗi hàng có một số đo lấy từ phần tương ứng.

**Cảnh 50 · Cảm ơn.** Cảm ơn thầy cô và các bạn, mời đặt câu hỏi. Cần quay lại phần nào thì gõ phím số 1 đến 7 để nhảy tới đầu phần đó.

## Cảnh dự phòng (không tính giờ)

Sau cảnh Cảm ơn có hai cảnh chỉ mở khi được hỏi. Từ cảnh 50 bấm mũi tên phải để tới cảnh 51 và 52.

**Cảnh 51 · Kiến trúc của Oracle và Redis.** Mở khi được hỏi về kiến trúc. Đọc theo hàng mà câu hỏi nhắm tới.
Điểm hay bị hỏi: Redis chạy lệnh trên một luồng chính, nên một script Lua dài sẽ chặn mọi client khác trong lúc chạy.

**Cảnh 52 · ACID trên Oracle và Redis.** Dùng khi được hỏi Redis có đủ ACID không. Mã trong ngoặc là thí nghiệm của phần Transaction.
Câu trả lời ngắn: không đủ. Redis không có rollback khi lỗi runtime, không có ràng buộc trong schema, và mất khoảng 1 giây khi sập máy nếu dùng AOF everysec. Đơn hàng vì thế nằm ở Oracle.

## Câu hỏi có thể gặp

Máy đo khác nhau: các phần đo trên hai máy (i7-14650HX và i7-12700H). Chỉ so số trong cùng một phần.
Số phần 4 đổi chiều giữa 18/10 và 19/11: nhóm chưa tìm ra nguyên nhân, và không dùng các chênh lệch dưới 2 lần để chọn DBMS.
Nếu bị hỏi giữa hai lần đo có đổi gì không: có vài commit. Script đo được ghim khoảng ngày 01/11/2025 đến 18/10/2026; lần đầu nó lấy "12 tháng gần nhất", vẫn phủ hết dữ liệu. Code thêm phần cache, đăng nhập JWT, và hai index của nhóm tìm kiếm trên bảng products. Câu SQL, index Redis và lệnh FT.AGGREGATE giữ nguyên. Nhóm chưa đo riêng từng thay đổi, nên không nói chắc thay đổi nào gây ra chênh lệch.
Vì sao không dùng XA: Redis không hỗ trợ XA. ShopFlash bù trừ bằng cách trả suất về Redis, còn Oracle chặn bán vượt ở bước cuối.
