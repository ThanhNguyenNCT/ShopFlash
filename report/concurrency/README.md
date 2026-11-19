# 2.5. Concurrency Control: thí nghiệm và cách chạy lại

Nội dung báo cáo nằm ở `Report/Sections/Tasks/Concurrency/` (ghép bằng `Report/Sections/Tasks/task5.tex`), tài liệu tham khảo là các khóa `cc-*` trong `Report/references.bib`. Thư mục này chỉ giữ script, số liệu thô, nhật ký và ảnh gốc để chạy lại các phép đo. Thiết kế module: `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

Trước khi báo cáo đổi sang cấu trúc ba phần, mục này là **3.5**, nên tên ảnh và docstring của script vẫn mang số 3.5; không đổi tên để không gãy đường dẫn.

Giả thuyết ghi trong báo cáo **trước khi đo**. Đo xong thì ghi kết quả thật, kể cả khi trái giả thuyết; không điền số chưa đo. Mỗi phép đo: 1 lần khởi động không tính, 5 lần đo, báo cáo trung bình ± độ lệch chuẩn mẫu, không chạy việc khác trên máy trong lúc đo.

## Cấu trúc

```text
report/concurrency/
├── README.md        tài liệu này
├── oracle/          script SQL chạy bằng hai phiên (E3)
├── bench/           script gọi Lab API (E1, E2), sự cố (E4), k6 (E5)
├── results/         số liệu thô .csv, nhật ký, environment.md
└── images/          ảnh chụp và biểu đồ gốc (3.5-xx-*.png)
```

Ảnh dùng trong PDF là bản chép trong `Report/Pictures/`: `Task_5/` giữ nguyên tên (biểu đồ E1, E2, Concurrency Lab, đối chiếu runtime); `Section_3/` đổi tên theo quy ước Phần 3 (`3.5-04` thành `client-flash-sale.png`, `3.5-05` thành `client-flash-checkout.png`, `3.5-06` thành `admin-flash-sales.png`). Vẽ lại biểu đồ thì chép lại sang `Report/Pictures/Task_5/`.

## Thí nghiệm

| | Nội dung | Cách chạy | Trạng thái |
|---|---|---|---|
| E1 | Bán vượt suất: 10 chiến lược, N = 1000, M = 100, C = 64 | Concurrency Lab hoặc `bench/run_lab.py e1` | Đã đo 2026-11-16 (`results/e1-*.csv`) |
| E2 | Throughput, độ trễ theo C ∈ {1, 8, 32, 64, 128} cho 5 chiến lược đúng, N = M = 2000 | `bench/run_lab.py e2` | Đã đo 2026-11-16 (`results/e2-*.csv`) |
| E3 | SERIALIZABLE trên Oracle: ORA-08177 | `sh report/concurrency/oracle/e3-run.sh` (hai phiên `sqlplus`: `e3-session-1.sql`, `e3-session-2.sql`), hoặc dán tay vào hai cửa sổ | Đã chạy 2026-11-16 (`results/e3-session-*.txt`) |
| E4 | Sự cố giữa Redis và Oracle: backend sập sau khi giữ suất; mất key runtime rồi dựng lại | `bench/e4_incident.py a1`, tắt và bật lại backend, `a2 <mã suất>`, rồi `b` | Đã chạy 2026-11-16 (`results/e4-log.txt`; lần chạy đầu của bước b lộ lỗi suất mồ côi, đã sửa, log giữ cả hai lần) |
| E5 | 500 khách giữ 50 suất qua HTTP (kế hoạch ban đầu là 100 suất; đổi sang sản phẩm 9102 có 50 suất vì sản phẩm 100 suất đã bán 3 suất ở E4) | k6 trong Docker, lệnh ở đầu `bench/e5-flash-reserve.js`; backend chạy với `SERVER_ADDRESS=0.0.0.0` | Đã chạy 2026-11-16, một lần (`results/e5-summary.json`) |

Đọc dữ liệu đã commit, quyết định dựa trên giá trị đọc trước khi ghi, chờ khóa quá hạn, deadlock, `WATCH` và Lua giữ suất với vài client đã có ở mục 2.4.2.3 (thí nghiệm B1–B5); mục 2.5 dẫn lại, không làm lại.

## Chạy lại

1. Làm theo `db/README.md`, chạy backend với profile `dev` (có seed chương trình 9101 và khách 9001–9500).
2. Đăng nhập tài khoản admin để gọi `/api/admin/concurrency-lab/*`.
3. E1, E2: `python report/concurrency/bench/run_lab.py e1` rồi `... e2` (mặc định 1 lần khởi động, 5 lần đo; cần `requests`, `matplotlib`). Script tự đăng nhập admin của seed dev, ghi CSV vào `results/` và vẽ lại ảnh trong `images/`; `... plot` chỉ vẽ lại từ CSV. Môi trường và kích thước pool lúc đo: `results/environment.md`.
4. E3: `sh report/concurrency/oracle/e3-run.sh` từ thư mục gốc repo (đọc mật khẩu user `SHOP` trong `.env`). E4: xem docstring của `bench/e4_incident.py`; bước `b` tạo đơn thật trên dữ liệu dev, mỗi khách chỉ mua được 1 suất nên chạy lại thì đổi khách (`b 494 493`).
5. Biên dịch báo cáo: xem `Report/README.md`.
