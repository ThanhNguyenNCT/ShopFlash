# Báo cáo Oracle vs Redis

Đây là bộ báo cáo mới trong `Report/` (R hoa), được bố trí theo mẫu
`local_docs/Report/`: `main.tex`, `Sections/preamp.tex`, các file
`Sections/Tasks/task*.tex` và hình trong `Pictures/`. Thư mục `report/` cũ
vẫn giữ bản trước và các script/minh chứng thí nghiệm để đối chiếu.

## Cấu trúc Markdown hiện hành

Mục lục ghép và trạng thái từng phần: **[REPORT.md](REPORT.md)**.

1. **Giới thiệu đề tài và tổng quan hệ thống** (1.1–1.4): nội dung trong
   `Sections/Introduction/noi-dung.tex` và `Sections/Introduction/database-design.tex`.
2. **Nghiên cứu và so sánh Oracle với Redis** (2.1–2.6): trọng tâm báo cáo.
   [Transaction](Sections/Tasks/Transaction/noi-dung.md#transaction-2-4) dùng
   mục **2.4.1** từ bản đầy đủ `Bao_cao_Transaction_Muc_3_1-3_3_MD`, còn
   **2.4.2** dùng bản 3.2 rút gọn mới nhất và **2.4.3** giữ nội dung bản rút gọn trước đó; các nguồn của thành viên
   khác được dẫn tại mục tương ứng trong `REPORT.md`.
3. **Hiện thực ứng dụng và kết luận** (3.1–3.3): nội dung trong
   `Sections/Implementation/noi-dung.tex`, gồm bảng chức năng, sáu ảnh Client và kết luận;
   minh họa Flash Sale/Admin còn chờ bổ sung.

Transaction hiện có 22 bảng, 15 đoạn mã, 10 khối kết quả và ba biểu đồ PDF vector
theo các nguồn hiện hành; Markdown liên kết trực tiếp tới biểu đồ, không vẽ lại hoặc tự tạo số đo.
Các bản trong `Bao_cao*/` vẫn chỉ để local. Hai bản Markdown Transaction cũ
trong `report/transaction/` được giữ để đối chiếu, không ghép thêm vào bản mới.

## Báo cáo LaTeX/PDF ba phần

`main.tex` đã ghép đúng ba phần. Phần 1 nằm ở `Sections/Introduction/noi-dung.tex`
(1.1–1.3) và `database-design.tex` (1.4, Hình 1.2–1.3, Bảng 1.2–1.3); Hình 1.1 dùng ảnh
`Pictures/image.png` do người dùng cung cấp. Phần 2 có sáu topic 2.1–2.6; hiện đã ghép đầy đủ
Transaction 2.4. Phần 3 nằm ở
`Sections/Implementation/noi-dung.tex`, có Bảng 3.1 và Hình 3.1–3.6. Các topic khác chưa được ghép vào
PDF vẫn giữ tiêu đề, dù nguồn Markdown nghiên cứu có thể đã tồn tại.

```text
Report/
├── REPORT.md                        khung ghép Markdown ba phần hiện hành
├── main.tex                         ghép bìa, mục lục, phân công và ba phần lớn
├── Sections/
│   ├── preamp.tex                    font, lề, header/footer, khung bìa và thông tin nhóm
│   ├── styles.tex                    lưới bảng, màu và định dạng code/log
│   ├── transaction-format.tex        kiểu kết quả, ngôn ngữ Lua/JavaScript
│   ├── workload.tex                  thành viên và nhiệm vụ đã được cung cấp
│   ├── Introduction/
│   │   ├── noi-dung.tex              Phần 1, gồm 1.1–1.3
│   │   ├── database-design.tex       1.4 Thiết kế cơ sở dữ liệu
│   │   └── architecture.tex          bản sơ đồ TikZ trước, giữ để đối chiếu
│   ├── Implementation/
│   │   └── noi-dung.tex              Phần 3: 3.1–3.3 và sáu ảnh Client
│   └── Tasks/
│       ├── task1.tex                 2.1 Data Storage & Management
│       ├── task2.tex                 2.2 Indexing
│       ├── task3.tex                 2.3 Query Processing
│       ├── task4.tex                 2.4 Transaction, ghép ba tiểu mục bên dưới
│       ├── task5.tex                 2.5 Concurrency Control, ghép bốn file bên dưới
│       ├── task6.tex                 2.6 Data Backup & Recovery
│       ├── Concurrency/
│       │   ├── 01-co-so-ly-thuyet.tex   2.5.1–2.5.3 Lý thuyết, cơ chế Oracle và Redis
│       │   ├── 02-thiet-ke-flash-sale.tex 2.5.4 Flash Sale trên hai DBMS
│       │   ├── 03-thuc-nghiem.tex       2.5.5–2.5.6 Thiết kế thí nghiệm, kết quả E1–E5
│       │   └── 04-so-sanh-ket-luan.tex  2.5.7 So sánh và lựa chọn DBMS
│       └── Transaction/
│           ├── noi-dung.md           Markdown hiện hành: 2.4.1–2.4.3
│           ├── 01-tong-quan.tex       2.4.1 Tổng quan và phương pháp nghiên cứu
│           ├── 02-thuc-nghiem-acid.tex 2.4.2 Cơ sở lý thuyết và thực nghiệm ACID
│           └── 03-hieu-nang.tex       2.4.3 Đánh giá hiệu năng và lựa chọn DBMS
├── Pictures/
│   ├── hcmut.png                     logo từ template
│   ├── image.png                     sơ đồ kiến trúc Hình 1.1
│   ├── Section_3/                    ảnh Client (client-*.png) và Admin (admin-*.png)
│   ├── Task_4/                       ba biểu đồ benchmark dạng PDF vector
│   └── Task_5/                       biểu đồ E1, E2 và ảnh Concurrency Lab, đối chiếu runtime
├── references.bib                    tài liệu tham khảo khi có trích dẫn
├── build.sh, .latexmkrc              cấu hình biên dịch XeLaTeX + biber
└── build/main.pdf                    kết quả build, không commit
```

Trang bìa dùng đề tài **Oracle vs Redis**, lớp **L01**, nhóm **L01_02**, học kỳ **261**,
GVHD **Lê Thị Bảo Thu**, sinh viên **Đặng Hải Sơn — 2312955**, tháng **11/2026**.
Mẫu bìa hai nét, header có logo và footer có số trang được giữ theo template.
Logo dùng kích thước gốc; thông tin căn theo bảng, tên sinh viên và MSSV cùng
một dòng. Ngày tháng đặt sau bảng thông tin theo khoảng cách của mẫu.

Font Latin Modern có cùng kiểu chữ Computer Modern/VNR của mẫu và hỗ trợ
Unicode tiếng Việt trong XeLaTeX. Nội dung dùng cỡ 13pt, baseline 18pt và
giãn dòng 1.3; Phần 1 dùng 12pt/16pt, giãn dòng 1.15 để giữ trong ba trang.
Tiêu đề, header và footer theo cách trình bày của template.

Transaction dùng tiểu mục 2.4.1–2.4.3; heading cấp tiếp theo là 2.4.x.y.
Caption bảng/hình/mã/kết quả dùng 2.4.n. Hình và bảng của Phần 1 là 1.1.
Trạng thái nguồn nghiên cứu của các topic được ghi trong `REPORT.md`.

## Chỉnh sửa và biên dịch

Các lệnh dưới đây biên dịch **báo cáo ba phần hiện hành**.
Mở `Report/main.tex` bằng TeXstudio hoặc editor LaTeX bất kỳ. Chỉnh thông tin
bìa ở `Sections/preamp.tex`; chỉnh phần Transaction trong
`Sections/Tasks/Transaction/`. Các file LaTeX và hình nằm hoàn toàn trong
`Report/`; build không cần `local_docs/`, `Bao_cao*/` hay file từ `report/` cũ.
Phần 1 và Phần 3 sửa trực tiếp trên LaTeX (`Sections/Introduction/`,
`Sections/Implementation/noi-dung.tex`); bản Markdown nháp đã gỡ khỏi repo.

Ảnh Client do người dùng cung cấp được giữ nguyên dữ liệu ảnh, chỉ đổi tên và
chèn vào đúng vị trí trong LaTeX:

| Hình | Tên cũ | Ảnh trong `Pictures/Section_3/` |
|---|---|---|
| 3.1 | `image.png` | [client-home.png](Pictures/Section_3/client-home.png) |
| 3.2 | `image copy.png` | [client-product-search.png](Pictures/Section_3/client-product-search.png) |
| 3.3 | `image copy 2.png` | [client-cart-selection.png](Pictures/Section_3/client-cart-selection.png) |
| 3.4 | `image copy 3.png` | [client-checkout.png](Pictures/Section_3/client-checkout.png) |
| 3.5 | `image copy 4.png` | [client-orders.png](Pictures/Section_3/client-orders.png) |
| 3.6 | `image copy 5.png` | [client-order-detail.png](Pictures/Section_3/client-order-detail.png) |

Phần 3 dùng cỡ chữ 12pt/16pt và giãn dòng 1.15 như Phần 1; hạn chế dòng lẻ đầu/cuối
trang. Bảng có lưới đầy đủ và nằm nguyên khối; caption và tham chiếu hình trong
PDF được đánh số tự động. Hình 3.7 mới là chỗ chờ ảnh Flash Sale, không phải một
minh chứng đã có; nội dung/ảnh Admin giữ chỗ bổ sung của tác giả.

Với TeX Live/MiKTeX có XeLaTeX, latexmk và biber:

```bash
cd Report
latexmk -outdir=build main.tex
```

`Sections/preamp.tex` là file cấu hình được nạp từ `main.tex`, không phải tài
liệu độc lập. File có chỉ dẫn `% !TeX root = ../main.tex` và XeLaTeX để editor
nhận đúng tài liệu gốc khi đang chỉnh preamble.

Nếu lỗi dừng ở `preamp.tex` với `File 'biblatex.sty' not found`, bản TeX trên
máy đang thiếu gói bibliography; đây không phải lỗi cú pháp ở dòng
`\addbibresource`. Trên Debian/Ubuntu có thể bổ sung bằng
`sudo apt install texlive-bibtex-extra biber` (cần quyền quản trị). Bộ Docker
bên dưới đã có `biblatex`, style IEEE và `biber`, nên dùng được khi TeX local
chưa cài đủ gói. Luôn biên dịch `main.tex` bằng XeLaTeX.

Hoặc từ gốc repository, dùng image Docker LaTeX đã có của dự án:

```bash
bash Report/build.sh
```

PDF nằm ở `Report/build/main.pdf`. Không commit file build và các bộ Markdown
local. Bảng dùng lưới đầy đủ, nền trắng và hàng đầu xám; mỗi bảng và caption
nằm liền khối, có thể chuyển vị trí để đoạn văn lấp phần trống. Code dùng nền
xám ấm theo `mystyle` của mẫu, phân màu từ khóa/comment/chuỗi; log dùng nền
xám theo `RResultStyle`, giãn dòng đơn. Giữ nguyên dòng lệnh và kết quả.

Nội dung Transaction dùng bản đầy đủ cho tổng quan 2.4.1 và bản rút gọn đã chốt
cho thực nghiệm/hiệu năng 2.4.2–2.4.3 (2.4.2 đã thay bằng bản 3.2 cập nhật), gồm 14 tiêu đề con,
22 bảng, 15 đoạn mã, 10 khối kết quả và 3 biểu đồ. Số đo vẫn là số đo đã có;
không chạy lại benchmark hoặc tự điền số mới trong lần chuyển cấu trúc này.

Kiểm thử cấu trúc ba phần ban đầu ngày 17/11/2026: build XeLaTeX trong Docker, PDF 39
trang; không có tràn nội dung, thiếu glyph, cảnh báo font hoặc tham chiếu chưa
xác định. Đã đối chiếu nội dung ba phần Transaction và ba hình vector, kiểm tra thông
tin bìa, mục lục, số trang, lưới bảng và khối log. Phần 1 dài ba trang, giữ
nguyên nội dung Markdown, có sơ đồ và bảng dữ liệu. Build chỉ gắn thư mục
`Report/` vào container, nên không phụ thuộc file từ bản cũ hoặc template local.

Cập nhật Phần 3 ngày 17/11/2026: PDF **45 trang**, Phần 3 nằm ở trang PDF
39–45 (số in 38–44). Đã đối chiếu sáu ảnh, Bảng 3.1, Hình 3.1–3.6, đường dẫn
Markdown và tham chiếu LaTeX; giữ đủ 28 đoạn nội dung cùng các heading/bảng của
nguồn, không đổi dữ liệu ảnh. Bảng nằm nguyên khối, phần kết luận ở cùng trang
cuối, không có trang trắng, tràn nội dung hoặc cảnh báo LaTeX trong lần build cuối.

Cập nhật mục 2.4.1: thay phần tổng quan/phương pháp bằng toàn văn file 3.1 của
`local_docs/Bao_cao_Transaction_Muc_3_1-3_3_MD/`, giữ hai bảng và chuẩn hóa tham
chiếu sang 2.4. Các mục 2.4.2–2.4.3 không đổi. Build Docker sau lần thay này
xuất **46 trang**, không có cảnh báo LaTeX hoặc trang trắng.

Cập nhật mục 2.4.2: nhập toàn văn bản 3.2 mới nhất từ
`local_docs/Bao_cao_Transaction_3_1-3_3_Rut_gon/`, gồm 14 bảng, 15 mã nguồn và
10 khối kết quả. Giữ nguyên mục 2.4.1; nội dung benchmark 2.4.3 chỉ thay số bảng
và nhãn tham chiếu thành 2.4.17–2.4.22 để nối tiếp. Đã đối chiếu nội dung Markdown,
mã nguồn và các dòng kết quả với nguồn; toàn báo cáo hiện có **48 trang**, không
có cảnh báo LaTeX hoặc trang trắng. Bảng trình bày nguyên khối với lưới và header
xám; code/log giữ kiểu và nền của mẫu.

Cập nhật mục 2.4.3.4: thay nội dung đối chiếu/lựa chọn DBMS theo bản 3.3.4 mới
trong nguồn rút gọn. Giữ nguyên bảng tổng hợp và toàn bộ benchmark phía trước;
chuẩn hóa số bảng nguồn 3.32 thành 2.4.22. PDF sau cập nhật có **49 trang**,
không có cảnh báo LaTeX hoặc trang trắng.

Cập nhật mục 2.5 ngày 19/11/2026: ghép Concurrency Control từ bản đã viết của
người phụ trách (`report/concurrency/`), gồm 2.5.1–2.5.7, 9 bảng, 7 mã nguồn và
4 hình; dẫn lại B1–B5 của 2.4.2.3 thay vì làm lại. Số đo E1–E5 giữ nguyên từ
`report/concurrency/results/`, không đo lại. Phần 3 thêm Hình 3.7, 3.8 (Flash
Sale phía khách) và Hình 3.12 (quản lý Flash Sale). Build Docker: PDF **90 trang**,
không có lỗi, tham chiếu chưa xác định hoặc dòng tràn lề.
