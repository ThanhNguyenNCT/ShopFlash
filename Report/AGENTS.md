# Report — bộ báo cáo theo template

Thư mục này có chữ **R hoa**, là bộ báo cáo mới theo cấu trúc
`local_docs/Report/`. Không tự sao chép nội dung dự án mẫu vào báo cáo Oracle vs Redis.

- Cấu trúc Markdown hiện hành nằm ở `REPORT.md`: Phần 1 giới thiệu (1.1–1.3),
  Phần 2 nghiên cứu Oracle–Redis (2.1–2.6), Phần 3 ứng dụng/kết luận (3.1–3.3).
- Phần 1 dùng `../section_1.md`; Transaction dùng `Sections/Tasks/Transaction/noi-dung.md`
  ở mục 2.4, gồm 2.4.1–2.4.3. Không ghép thêm hai bản Markdown Transaction cũ.
  Riêng 2.4.1 lấy nguyên nội dung 3.1 từ bản đầy đủ
  `local_docs/Bao_cao_Transaction_Muc_3_1-3_3_MD/`; 2.4.3 giữ nội dung bản rút gọn.
  Mục 2.4.2 dùng bản 3.2 mới nhất trong `Bao_cao_Transaction_3_1-3_3_Rut_gon/`:
  14 bảng, 15 mã nguồn, 10 kết quả; bảng của 2.4.3 nối số 17–22.
  Riêng 2.4.3.4 lấy nội dung 3.3.4 cập nhật trong bản rút gọn; bảng nguồn 3.32
  được chuẩn hóa thành Bảng 2.4.22, giữ nhãn và các phần benchmark phía trước.
- Giữ nguyên code, log, bảng số liệu và kết luận đã có; chỉ sửa số hiển thị,
  liên kết/caption khi chuẩn hóa. Không đổi tên artifact chỉ vì có số cũ.
- Topic chưa viết chỉ tạo khung; nguồn có sẵn của thành viên khác phải được giữ
  và dẫn lại. Phần 1/3 ngắn gọn; Phần 2 là trọng tâm. Ranh giới 2.4/2.5/2.6
  được ghi trong `REPORT.md`, không sao chép transcript/kết quả giữa các topic.
- `main.tex` chỉ ghép tài liệu; cấu hình và thông tin bìa ở `Sections/preamp.tex`.
  LaTeX/PDF dùng ba `\section` ứng với ba phần; sáu task dùng `\subsection`
  ở 2.1–2.6. `txsection` là `\subsubsection` (2.4.1–2.4.3), `txsubsection`
  là `\paragraph` dạng block (2.4.x.y); không chèn số viết cứng vào nội dung.
- Phần 1 nằm ở `Sections/Introduction/noi-dung.tex`, chuyển từ `../section_1.md`;
  giữ nguyên văn; Hình 1.1 dùng ảnh `Pictures/image.png` do người dùng cung cấp.
  Phần 3 chuyển từ `../section_3.md` sang `Sections/Implementation/noi-dung.tex`;
  sáu ảnh Client tại `Pictures/Section_3/client-*.png` ứng với Hình 3.1–3.6.
  Chỗ bổ sung minh họa Flash Sale và Admin giữ rõ là chưa hoàn thiện.
- Phần chưa viết chỉ có tiêu đề, không tạo số liệu hoặc ghi tên thành viên chưa cung cấp.
- Hình nghiên cứu đặt ở `Pictures/Task_<số>/`, nhãn Transaction có tiền tố `tx-`;
  ảnh giao diện Phần 3 đặt ở `Pictures/Section_3/`, nhãn có tiền tố `impl-`.
- Không thêm `\usepackage` trong file nội dung. Dùng các gói có trong preamble;
  cấu hình build cần chạy với XeLaTeX và biber khi có trích dẫn.
- Font Latin Modern giữ kiểu Computer Modern của mẫu và hỗ trợ Unicode tiếng Việt;
  nội dung nghiên cứu 13pt/18pt, giãn dòng 1.3; Phần 1/3 dùng 12pt/16pt,
  giãn dòng 1.15 (Phần 1 giữ trong 2–3 trang). Bìa theo bố cục `main.tex` của mẫu:
  logo kích thước gốc, hai đường kẻ, bảng thông tin và tên/MSSV trên cùng dòng.
- Bảng dùng `\reporttablesetup`, lưới `|`/`\hline`, header nền
  `ReportTableHead` và `\reportheader{...}`. Code/log dùng kiểu trong `Sections/styles.tex`.
- Nền code xám ấm theo `mystyle`, log xám theo `RResultStyle` của mẫu; bảng trắng,
  header xám, chữ đen. Không dùng nền xanh dương cho code, log hoặc bảng.
- Bảng phải nằm trọn trong một float `table` với caption; ưu tiên `[!htbp]` để
  đoạn văn lấp khoảng trống. Không dùng `longtable` để tách bảng giữa hai trang.
  Code/log dài có thể ngắt; `\reportblockspace{72pt}` giữ caption với đầu khối
  bằng khoảng co giãn/penalty, không ép `\newpage` dựa trên `\pagegoal`.
- Giữ nguyên mã nguồn, số đo và dòng kết quả. Không chạy seed/benchmark chỉ để build PDF.
- Các thư mục `Bao_cao*/` và template `local_docs/` chỉ để local. PDF phải build
  được chỉ với file trong `Report/`, không `\input` sang `report/` cũ hay thư mục local.
- Build: `latexmk -outdir=build main.tex` trong `Report/` hoặc
  `bash Report/build.sh` từ gốc repo. Kết quả: `Report/build/main.pdf`.
