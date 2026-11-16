# Report

Báo cáo môn học, nộp dạng PDF. Mỗi người viết phần module và chủ đề của mình, sau đó ghép lại.

## Dàn ý (tối đa 7 mục)

1. Giới thiệu
2. Phân tích yêu cầu
3. So sánh Oracle và Redis: 6 tiểu mục 3.1–3.6 theo 6 chủ đề
4. Thiết kế và hiện thực ứng dụng
5. Đánh giá và lựa chọn DBMS
6. Kết luận và tự đánh giá

Sau đó là Tài liệu tham khảo (trích dẫn theo IEEE) và Phụ lục: A. Đóng góp, B. Biên bản họp, C. Toàn bộ câu SQL và lệnh Redis, D. Hướng dẫn chạy demo.

## Khung cho mỗi tiểu mục chủ đề (3.x)

Lý thuyết → cơ chế trên Oracle (có demo và số đo) → cơ chế trên Redis (có demo và số đo) → bảng so sánh → kết luận trong bối cảnh ShopFlash.

IMPORTANT: Viết phần chủ đề theo `KHUNG_CHU_DE.md`: 5 phần bắt buộc, mỗi demo theo 4 bước, quy ước đo chung, cách để file trong `report/<chủ-đề>/`. Demo và công cụ đo do người phụ trách chủ đề tự chọn.

Số đo phải chạy lại được. Mỗi phép đo chạy 5 lần, báo cáo trung bình và độ lệch, ghi rõ cấu hình máy.

## Viết bằng LaTeX

Báo cáo chung là `main.tex` (XeLaTeX và biber), dựa trên [hcmut-report-template](https://github.com/davidnguyen1802/hcmut-report-template) (MIT, xem `TEMPLATE_LICENSE`). `main.tex` ghép phần của từng người; mỗi người chỉ sửa thư mục chủ đề của mình. File nào chưa có thì PDF hiện dòng chữ xám "[Chưa viết]".

Thư mục chủ đề: `storage` (3.1), `indexing` (3.2), `query` (3.3), `transaction` (3.4), `concurrency` (3.5), `backup-recovery` (3.6). Phần chung (mục 1, 2, 4.1, 5, 6, phụ lục A, B, D) nằm trong `chung/`.

| File trong thư mục chủ đề | Được ghép vào | Bắt đầu bằng |
|---|---|---|
| `noi-dung.tex` | Mục 3.x | `\subsection{...}` |
| `thiet-ke.tex` | Mục 4 (thiết kế module) | `\subsection{...}` |
| `lenh.tex` | Phụ lục C | `\subsubsection{...}` |
| `<chủ đề>.bib` | Tài liệu tham khảo | |
| `images/` | Ảnh | |

Quy ước:

- **Nhãn** (`\label`) và **khóa tài liệu** (`.bib`) có tiền tố của chủ đề: `st-`, `ix-`, `q-`, `tx-`, `cc-`, `bk-`. Tài liệu nhiều người dùng chung để trong `references.bib`.
- **Tên ảnh** bắt đầu bằng số mục, ví dụ `3.5-01-lab-ket-qua.png`, vì mọi thư mục `images/` nằm chung một `\graphicspath`.
- **Không `\usepackage`** trong file nội dung. Gói có sẵn trong `main.tex`: `booktabs`, `tabularx`, `multirow`, `float`, `caption`, `subcaption`, `graphicx`, `tikz`, `pgfplots`, `listings`, `fvextra`, `enumitem`, `amsmath`, `xcolor`, `hyperref`. Cần thêm gói thì báo nhóm để sửa `main.tex` và `docker/Dockerfile`.
- **Bảng:** báo cáo không ngắt từ (mẫu ngắt từ tiếng Anh cắt sai tiếng Việt), nên cột chữ hẹp dùng kiểu căn trái `L` (co giãn như `X`) hoặc `P{độ rộng}` thay cho `X`, `p{...}` để chữ không bị giãn.
- **Code:** `lstlisting` với `language=OracleSQL`, `Redis`, `Lua`, `Java`, `Python` hoặc `bash` (kiểu ở `styles.tex`); đoạn chỉ là văn bản thì dùng `Verbatim`.
- **Trích dẫn:** `\cite{khóa}`. Chỉ tài liệu được trích dẫn mới in ra.

### Biên dịch

- **Docker** (không cần cài TeX lên máy): `powershell -ExecutionPolicy Bypass -File report\build.ps1` (Windows) hoặc `report/build.sh` (macOS, Linux, Git Bash). Lần đầu dựng image `shopflash-latex:1` (tải khoảng 625 MB, chiếm khoảng 2,3 GB trên đĩa). PDF ra `report/build/main.pdf`. Thêm `-c` để xóa file trung gian. Xóa image: `docker rmi shopflash-latex:1`. Trên Windows, script gắn font Times New Roman, Consolas của máy vào container; không có thì dùng TeX Gyre Termes.
- **Overleaf:** nén thư mục `report/` rồi Upload Project; Menu → Compiler: **XeLaTeX**, Main document: `main.tex`.
- **TeX cài sẵn** (TeX Live, MiKTeX): `latexmk -outdir=build main.tex` trong `report/`.
