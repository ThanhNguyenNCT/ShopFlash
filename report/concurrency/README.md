# 2.5. Concurrency Control: thí nghiệm và cách chạy lại

Nguồn LaTeX trước đây nằm ở `noi-dung.tex`, tài liệu tham khảo ở `concurrency.bib`; bộ báo cáo hiện hành dùng [Report/main.tex](../../Report/main.tex), chưa ghép nội dung topic này. Thư mục này lưu nội dung và kế hoạch thí nghiệm để đối chiếu. Thiết kế module: `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

Mục hiện hành là **2.5** theo [khung ba phần](../../Report/REPORT.md).
File LaTeX vẫn thuộc bản xuất cũ ở mục 5. Các trạng thái “Chưa có Lab/API”
bên dưới là ghi chú kế hoạch cũ: code Lab và API F9 hiện đã có (xem
`backend/src/main/java/com/shopflash/modules/concurrency/`); chưa coi đó là
bằng chứng các phép đo dự kiến đã hoàn tất.

Giả thuyết ghi trong `noi-dung.tex` **trước khi đo**. Đo xong thì ghi kết quả thật, kể cả khi trái giả thuyết; không điền số chưa đo. Mỗi phép đo: 1 lần khởi động không tính, 5 lần đo, báo cáo trung bình ± độ lệch chuẩn mẫu, không chạy việc khác trên máy trong lúc đo.

## Cấu trúc

```text
report/concurrency/
├── README.md        tài liệu này
├── noi-dung.tex     mục 5 của báo cáo
├── concurrency.bib  tài liệu tham khảo (khóa cc-)
├── oracle/          script SQL chạy tay bằng hai phiên (E3)
├── bench/           script gọi Lab API (E1, E2), k6 (E5)
├── results/         số liệu thô .csv, bảng tóm tắt, environment.md
└── images/          ảnh chụp và biểu đồ (5-xx-*.png)
```

## Thí nghiệm

| | Nội dung | Cách chạy | Trạng thái |
|---|---|---|---|
| E1 | Bán vượt suất: 10 chiến lược, N = 1000, M = 100, C = 64 | Concurrency Lab hoặc `bench/run_lab.py` | Chưa có Lab |
| E2 | Throughput, độ trễ theo C ∈ {1, 8, 32, 64, 128} cho 5 chiến lược đúng | `bench/run_lab.py` | Chưa có Lab |
| E3 | SERIALIZABLE trên Oracle: ORA-08177 | Hai phiên SQL theo `oracle/e3-serializable.sql` | Chưa viết |
| E4 | Sự cố giữa Redis và Oracle: backend sập sau khi giữ suất; mất key runtime rồi dựng lại | API admin `.../runtime`, `.../runtime/rebuild` và view `v_inventory_invariant_check` | Chưa có API |
| E5 | 500 khách giữ 100 suất qua HTTP (nếu còn thời gian) | `k6 run bench/e5-flash-reserve.js` (đăng nhập trong `setup()`) | Chưa viết |

Lost update hai phiên, lock timeout, deadlock, `MULTI`/`EXEC` không rollback và `WATCH` đã có ở [mục 2.4 Transaction](../../Report/Sections/Tasks/Transaction/noi-dung.md#tx-section-2-4-2) (thí nghiệm A4–A5, B1–B5); mục 2.5 dẫn lại, không làm lại.

## Chạy lại

1. Làm theo `db/README.md`, chạy backend với profile `dev` (có seed chương trình 9101 và khách 9001–9500).
2. Đăng nhập tài khoản admin để gọi `/api/admin/concurrency-lab/*`.
3. E1, E2: `python report/concurrency/bench/run_lab.py --runs 5 --warmup 1` (sẽ viết cùng Lab).
4. E3: chạy tay theo `oracle/`.
5. Biên dịch bộ báo cáo hiện hành: xem [Report/README.md](../../Report/README.md); nguồn topic này vẫn cần được người phụ trách chốt và ghép vào 2.5.
