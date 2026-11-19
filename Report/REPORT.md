# Khung ghép báo cáo DBMS — ShopFlash

Nhóm: **L01_02**.

Đây là mục lục ghép **Markdown hiện hành**, theo cấu trúc ba phần đã thống nhất ngày 17/11/2026. Nội dung nghiên cứu nằm tại các file được liên kết; không chép lại các bản cũ song song. Ghi chú trạng thái dưới đây phục vụ biên tập, không đưa vào bản nộp.

# Phần 1. GIỚI THIỆU ĐỀ TÀI VÀ TỔNG QUAN HỆ THỐNG

Nguồn chính: [section_1.md](../section_1.md). Đã có đủ ba tiểu mục, sơ đồ kiến trúc và bảng phân chia dữ liệu; giữ nội dung chính. Phần này dự kiến 2–3 trang A4, cung cấp bối cảnh cho nghiên cứu ở Phần 2.

## 1.1. Bối cảnh, mục tiêu và phạm vi đề tài

[Nội dung 1.1](../section_1.md#section-1-1).

## 1.2. Tổng quan ứng dụng ShopFlash

[Nội dung 1.2](../section_1.md#section-1-2).

## 1.3. Kiến trúc hệ thống và phân chia dữ liệu Oracle–Redis

[Nội dung 1.3](../section_1.md#section-1-3), gồm Hình 1.1 và Bảng 1.1. Từ bối cảnh và cách phân vai dữ liệu này, Phần 2 nghiên cứu các cơ chế trên từng DBMS và đối chiếu bằng thực nghiệm.

# Phần 2. NGHIÊN CỨU VÀ SO SÁNH ORACLE VỚI REDIS

Đây là trọng tâm báo cáo: cơ chế DBMS → code/thí nghiệm thực tế → kết quả → so sánh → kết luận lựa chọn trong ShopFlash. Quy ước chung: [AGENTS.md](AGENTS.md). Việc có file kết quả không đồng nghĩa phép đo đã được chạy lại trong lần biên tập này.

## 2.1. Data Storage & Management

**Đã có bản viết và minh chứng:** [Storage](../report/storage/README.md), [số đo footprint](../report/storage/results/storage-footprint.md) và hình/script trong thư mục đó. Người phụ trách tiếp tục chốt bản chương, đánh số tiểu mục/bảng/hình theo 2.1; không thay thế nội dung đã có bằng khung trống.

## 2.2. Indexing

**Đã có bản chương:** [Indexing](../report/indexing/SECTION_INDEXING.md#indexing-2-2), đã chuẩn hóa thành 2.2.1–2.2.7; giữ nguyên nghiên cứu, code, số đo, kết luận và tài liệu tham khảo. [Runbook và trạng thái thí nghiệm](../report/indexing/README.md).

## 2.3. Query Processing

**Đã ghép vào LaTeX:** [Sections/Tasks/task3.tex](Sections/Tasks/task3.tex), người thực hiện Nguyễn Công Thành (2313119); gồm 2.3.1 Câu hỏi nghiên cứu và phương pháp, 2.3.2 Cơ chế và thực nghiệm trên Oracle, 2.3.3 Cơ chế và thực nghiệm trên Redis, 2.3.4 Đối chiếu kết quả và lựa chọn DBMS. Có 10 bảng, 5 mã nguồn, 4 khối kết quả, 5 hình (4 ảnh Query Lab chụp ngày 19/11/2026 ở `Pictures/Task_3/`, 1 biểu đồ pgfplots). Số đo chính ngày 19/11/2026: [benchmark](../report/query/results/benchmark-summary.md), [cache-aside](../report/query/results/cache-summary.md), [lượt đo lặp lại](../report/query/results/benchmark-lan-lap-19-11.md); kế hoạch thực thi ở `report/query/results/plans/`. Lần đo 18/10/2026 cho thứ tự Oracle–Redis khác ở hai báo cáo, được trình bày ở 2.3.4.2. [Runbook chạy lại](../report/query/README.md).

## 2.4. Transaction

**Bản Markdown dùng để ghép:** [Transaction](Sections/Tasks/Transaction/noi-dung.md#transaction-2-4). Mục 2.4.1 dùng nội dung 3.1 của bản đầy đủ `Bao_cao_Transaction_Muc_3_1-3_3_MD`; 2.4.2 dùng bản 3.2 mới nhất trong `Bao_cao_Transaction_3_1-3_3_Rut_gon`; 2.4.3 giữ benchmark bản rút gọn, riêng kết luận 2.4.3.4 đã cập nhật theo bản 3.3.4 mới:

- [2.4.1. Tổng quan và phương pháp nghiên cứu](Sections/Tasks/Transaction/noi-dung.md#tx-section-2-4-1).
- [2.4.2. Cơ sở lý thuyết và thực nghiệm các tính chất ACID](Sections/Tasks/Transaction/noi-dung.md#tx-section-2-4-2).
- [2.4.3. Đánh giá hiệu năng và lựa chọn DBMS](Sections/Tasks/Transaction/noi-dung.md#tx-section-2-4-3).

Có **22 bảng, 15 đoạn mã, 10 khối kết quả và 3 biểu đồ** theo nguồn cập nhật. Bảng/hình/mã/kết quả mang tiền tố **2.4**, không đổi mã thí nghiệm A1–D2. Hai bảng tổng quan là 2.4.1–2.4.2; 14 bảng thực nghiệm là 2.4.3–2.4.16; sáu bảng hiệu năng nối tiếp ở 2.4.17–2.4.22. [Runbook, nguồn gốc và các bản cũ](../report/transaction/README.md).

## 2.5. Concurrency Control

**Đã ghép vào LaTeX (19/11/2026):** `Sections/Tasks/task5.tex` ghép bốn file trong `Sections/Tasks/Concurrency/`; số đo, script, nhật ký và cách chạy lại ở [report/concurrency](../report/concurrency/README.md).

- 2.5.1. Cơ sở lý thuyết (hiện tượng bất thường, mức cô lập, Saga/TCC).
- 2.5.2. Cơ chế trên Oracle; 2.5.3. Cơ chế trên Redis.
- 2.5.4. Thiết kế Flash Sale trên hai DBMS (chia dữ liệu, vòng đời, Try–Confirm–Cancel).
- 2.5.5. Thiết kế thí nghiệm (Concurrency Lab, 10 chiến lược); 2.5.6. Kết quả E1–E5.
- 2.5.7. So sánh và lựa chọn DBMS trong ShopFlash.

Có **9 bảng, 7 mã nguồn và 4 hình**, mang tiền tố 2.5. Số đo E1–E5 đo ngày 16/11/2026, giữ nguyên.

## 2.6. Data Backup & Recovery

**Đã có bản viết và dữ liệu thí nghiệm:** [Backup & Recovery](../report/backup-recovery/README.md), [REPORT_DATA.md](../report/backup-recovery/REPORT_DATA.md), [runbook phục hồi](../report/backup-recovery/scripts/README.md). Giữ nguyên kết quả đã lưu; người phụ trách chuẩn hóa thành chương 2.6 và ghi đúng phạm vi dữ liệu đã phục hồi.

**Ranh giới giữa các topic:** 2.4 giữ các case isolation/locking/WATCH và phần Durability đã viết để giải thích bảo đảm transaction. 2.5 dẫn lại các case đó, tập trung vào chiến lược dưới tải, reservation và phối hợp Flash Sale; không chép lại transcript B1–B5. 2.6 tập trung vào backup/restore và kiểm chứng phục hồi; không lặp phần giải thích Durability/AOF của 2.4 như một kết quả crash-recovery mới.

# Phần 3. HIỆN THỰC ỨNG DỤNG VÀ KẾT LUẬN

Nguồn chính: [section_3.md](../section_3.md), đã ghép vào `Sections/Implementation/noi-dung.tex`. Có bảng tổng hợp chức năng, nội dung Client/đánh giá/kết luận và sáu ảnh gốc tại `Pictures/Section_3/` (Hình 3.1–3.6). Flash Sale đã có ảnh trong LaTeX (Hình 3.7, 3.8 phía khách; Hình 3.12 quản lý chương trình, 19/11/2026); minh họa báo cáo/thống kê phía Admin vẫn chờ bổ sung. [Source Audit](../SECTION_1_HANDOFF.md) là nguồn đối chiếu chức năng và giới hạn tại thời điểm khảo sát; phải phân biệt ảnh giao diện với kiểm chứng giao dịch DBMS ở Phần 2.

## 3.1. Các chức năng đã hiện thực

[Bảng tổng hợp bốn nhóm chức năng](../section_3.md#31-các-chức-năng-đã-hiện-thực), xuất thành Bảng 3.1; không liệt kê toàn bộ API/module.

## 3.2. Minh họa và đánh giá ứng dụng

[Minh họa và đánh giá](../section_3.md#32-minh-họa-và-đánh-giá-ứng-dụng): trang chủ, tìm kiếm/lọc, giỏ hàng, Checkout, danh sách đơn và chi tiết đơn. Ảnh Client phản ánh luồng đơn 188.000 đồng đã có; không tạo mới ảnh hoặc kết quả. Flash Sale đã bổ sung trong LaTeX; giữ chỗ cho báo cáo/thống kê phía Admin.

## 3.3. Kết luận, hạn chế và hướng phát triển

[Kết luận, hạn chế và hướng phát triển](../section_3.md#33-kết-luận-hạn-chế-và-hướng-phát-triển): giữ nội dung người dùng đã biên soạn; kết luận từng topic cần tiếp tục được nhóm đối chiếu khi hoàn thiện Phần 2.

---

**Ghi chú ghép và xuất bản — không phải nội dung báo cáo:**

- Nội dung chính Phần 1 giữ ở `section_1.md`; Transaction dùng đúng bản 2.4 được liên kết ở trên. `report/transaction/REPORT.md` và `SECTION_TRANSACTION.md` là bản trước, không ghép thêm để tránh lặp.
- Tiểu mục nghiên cứu dùng `2.<topic>.<tiểu mục>`. Bảng/hình/mã/kết quả Transaction dùng `2.4.<số thứ tự>`; các topic khác dùng cùng quy tắc khi được người phụ trách chuẩn hóa. Hình 1.1 và Bảng 1.1 thuộc Phần 1.
- Giữ nguyên tên file ảnh, dữ liệu thô và bằng chứng có số cũ để không gãy đường dẫn. Số hiển thị của báo cáo nằm trong heading/caption; số trong tên artifact cũ không phải số mục hiện hành.
- Các mục chỉ có runbook/nguyên liệu ở trên chưa được coi là chương đã biên tập hoàn chỉnh. Khung chú thích không phải nội dung nghiên cứu mới.
- **LaTeX/PDF trong `Report/` đã dùng ba phần**, có Phần 1 từ `section_1.md`, Transaction 2.4 và Phần 3 từ `section_3.md` kèm sáu ảnh Client. Concurrency 2.5 đã ghép (19/11/2026). Các topic chưa ghép vẫn giữ khung; báo cáo/thống kê phía Admin ở Phần 3 còn chỗ bổ sung. PDF hiện hành là `Report/build/main.pdf`. Nguồn Markdown khác được liệt kê ở trên vẫn cần người phụ trách chốt và chuyển sang LaTeX; không suy ra chúng đã được ghép chỉ từ việc có file nguồn.
