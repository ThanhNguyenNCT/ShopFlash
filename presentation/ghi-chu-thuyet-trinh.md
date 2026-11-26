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

## Phần 4 · Xử lý truy vấn (5 phút)

**Cảnh 23 · Mở phần 4.** Bốn báo cáo doanh thu: theo tháng, theo danh mục, top sản phẩm, khách VIP. Dữ liệu: 87.139 đơn được tính, 215.805 dòng hàng.
Câu hỏi: ai chọn cách chạy, và mỗi cách tốn gì.

**Cảnh 24 · Hai cách chạy báo cáo doanh thu.** Trái là SQL top sản phẩm, join 4 bảng. CBO chọn hash join, quét toàn bảng, và tự viết lại câu: gom 215 nghìn dòng hàng còn 9.000 rồi mới join.
Phải là lệnh Redis. Không có join vì join đã làm sẵn khi dựng read model. Redis chạy đúng các bước đã viết.
Ý chính: với Oracle, DBMS quyết định cách chạy. Với Redis, lập trình viên quyết định.

**Cảnh 25 · Oracle chọn kế hoạch dựa trên thống kê.** Bảng trái: ép thuật toán join bằng hint. CBO tự chọn nhanh nhất ở cả ba báo cáo. Ép nested loops chậm 4,9 đến 5,2 lần ở hai báo cáo join 4 bảng; khách VIP chỉ join 2 bảng nên chậm 1,3 lần.
Bảng phải, đọc theo hàng: có thống kê thì CBO ước lượng 9.001 dòng (thực tế 9.000), chọn hash join, đọc 5.161 block. Xóa thống kê thì ước lượng 285.000 dòng, đổi sang merge join, đọc 91.035 block. Thu thập lại thì về như cũ.
GATHER_TABLE_STATS phải có no_invalidate => FALSE, nếu không Oracle giữ kế hoạch cũ.

**Cảnh 26 · Hai quyết định thiết kế trên Redis.** Hai quyết định của lập trình viên.
Một: khai báo SORTABLE cho trường dùng trong GROUPBY. Thiếu thì Redis đọc lại JSON từng document, khoảng 1,5 giây thay vì 0,15 đến 0,33 giây.
Hai: để Redis tự gom (AGGREGATE) thay vì kéo dữ liệu thô về ứng dụng (CLIENT). Bảng bên phải: CLIENT chậm 3,5 đến 30 lần.
Số SORTABLE đo phía server lúc dựng index, khác lần đo với bảng bên phải, nên không so trực tiếp hai con số.

**Cảnh 27 · Kết quả đo bốn báo cáo.** Bảng có đủ năm báo cáo, bốn cách chạy. Oracle AUTO và Redis AGGREGATE không bên nào thắng mọi báo cáo, và kết quả đổi chiều giữa hai lần đo (18/10 và 19/11). Chênh dưới 2 lần nên nhóm không dùng số này để chọn DBMS.
Các chênh lệch lớn thì ổn định: ép nested loops chậm khoảng 5 lần, CLIENT chậm nhiều lần. Báo cáo "sản phẩm chưa từng bán" cần anti-join, Redis không trả lời được.

**Cảnh 28 · Lựa chọn cho báo cáo của ShopFlash.** Báo cáo quản trị chạy trên Oracle. Lý do: dữ liệu luôn mới, viết được mọi câu hỏi bằng SQL, không có bản sao phải dựng lại.
Redis chỉ giữ kết quả đã tính trong 5 phút (cache-aside): mỗi lần cache HIT mất 1,05 đến 1,90 ms. Read model trên Redis chỉ còn trong Query Lab để so sánh.

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

## Phần 6 · Kiểm soát đồng thời (5 phút)

**Cảnh 38 · Mở phần 6.** Câu hỏi: 1000 khách tranh 100 suất, ai giữ được con số?

**Cảnh 39 · Bài toán và bốn cơ chế.** Đọc banner lost update: hai khách cùng đọc 99, cùng ghi 100, bộ đếm chỉ tăng 1.
Đi qua bốn thẻ: khóa bi quan, khóa lạc quan, MVCC, thao tác nguyên tử.

**Cảnh 40 · So sánh Oracle và Redis.** Khối trái: Oracle khóa từng dòng; Redis chạy từng lệnh hoặc script một mình nên không deadlock, nhưng cũng không rollback.
Câu cuối bảng: cả hai đều chặn được bán vượt, khác nhau ở chi phí khi tranh chấp cao.

**Cảnh 41 · Thiết kế Flash Sale ShopFlash.** Bốn bước: giữ suất bằng Lua trên Redis, đặt đơn trong transaction Oracle, xác nhận hoặc trả suất, job dọn suất hết hạn mỗi 5 giây.
Thanh màu dưới: Oracle giữ nguồn gốc, Redis giữ trạng thái chạy, Redis dựng lại được từ Oracle. Oracle là chốt chặn cuối: điều kiện sold + q <= allocated.

**Cảnh 42 · Kết luận.** Viết kiểu "đọc rồi ghi" thì bán vượt 900 suất trên 100 suất. Lua đạt 8.951 lượt/giây, gấp 4,6 lần UPDATE có điều kiện trên Oracle, ở 64 lượt đồng thời.
WATCH chỉ bán được 797 trên 2000 vì xung đột liên tục. Chốt: Redis + Lua giữ suất, Oracle + UPDATE có điều kiện chốt đơn.

## Phần 7 · Sao lưu và khôi phục (3 phút)

**Cảnh 43 · Mở phần 7.** Câu hỏi: mất dữ liệu thì bao lâu chạy lại, mất bao nhiêu?

**Cảnh 44 · Sao lưu của Oracle: Data Pump.** Theo mũi tên: expdp xuất schema SHOP ra file .dmp, impdp tạo lại bảng, index, ràng buộc và nạp dữ liệu.

**Cảnh 45 · Persistence của Redis.** RDB chụp nhanh, có thể mất dữ liệu giữa hai lần chụp. AOF ghi từng lệnh nên bền hơn.
Banner dưới là đánh đổi chính: fsync càng thường xuyên thì càng an toàn, ghi càng chậm.

**Cảnh 46 · Thiết kế sao lưu ShopFlash.** Một gói backup có ba phần: file Data Pump, file RDB, manifest SHA-256. Kiểm tra mã băm trước khi khôi phục.

**Cảnh 47 · Kết luận.** Khôi phục thành công mọi lượt thử, tổng khoảng 33 giây cho cả hai DBMS. AOF always làm p95 ghi tăng khoảng 40 lần so với everysec.
ShopFlash chọn everysec: ghi gần như khi tắt AOF, mất tối đa khoảng 1 giây.

## Tổng kết (1 phút)

**Cảnh 48 · Tổng kết.** Đọc theo cột "ShopFlash dùng". Dữ liệu gốc và thao tác ghi quan trọng ở Oracle. Redis giữ bản chiếu để tìm kiếm, kết quả báo cáo đã tính, suất Flash Sale đang giữ. Mất Redis thì dựng lại từ Oracle, trừ suất đang giữ dở.
Mỗi hàng có một số đo lấy từ phần tương ứng.

**Cảnh 49 · Cảm ơn.** Cảm ơn thầy cô và các bạn, mời đặt câu hỏi. Cần quay lại phần nào thì gõ phím số 1 đến 7 để nhảy tới đầu phần đó.

## Cảnh dự phòng (không tính giờ)

Sau cảnh Cảm ơn có hai cảnh chỉ mở khi được hỏi. Từ cảnh 49 bấm mũi tên phải một lần là tới cảnh 50, hai lần là cảnh 51.

**Cảnh 50 · Kiến trúc của Oracle và Redis.** Mở khi được hỏi về kiến trúc. Đọc theo hàng mà câu hỏi nhắm tới.
Điểm hay bị hỏi: Redis chạy lệnh trên một luồng chính, nên một script Lua dài sẽ chặn mọi client khác trong lúc chạy.

**Cảnh 51 · ACID trên Oracle và Redis.** Dùng khi được hỏi Redis có đủ ACID không. Mã trong ngoặc là thí nghiệm của phần Transaction.
Câu trả lời ngắn: không đủ. Redis không có rollback khi lỗi runtime, không có ràng buộc trong schema, và mất khoảng 1 giây khi sập máy nếu dùng AOF everysec. Đơn hàng vì thế nằm ở Oracle.

## Câu hỏi có thể gặp

Máy đo khác nhau: các phần đo trên hai máy (i7-14650HX và i7-12700H). Chỉ so số trong cùng một phần.
Số phần 4 đổi chiều giữa 18/10 và 19/11: nhóm chưa tìm ra nguyên nhân, và không dùng các chênh lệch dưới 2 lần để chọn DBMS.
Nếu bị hỏi giữa hai lần đo có đổi gì không: có vài commit. Script đo được ghim khoảng ngày 01/11/2025 đến 18/10/2026; lần đầu nó lấy "12 tháng gần nhất", vẫn phủ hết dữ liệu. Code thêm phần cache, đăng nhập JWT, và hai index của nhóm tìm kiếm trên bảng products. Câu SQL, index Redis và lệnh FT.AGGREGATE giữ nguyên. Nhóm chưa đo riêng từng thay đổi, nên không nói chắc thay đổi nào gây ra chênh lệch.
Vì sao không dùng XA: Redis không hỗ trợ XA. ShopFlash bù trừ bằng cách trả suất về Redis, còn Oracle chặn bán vượt ở bước cuối.
