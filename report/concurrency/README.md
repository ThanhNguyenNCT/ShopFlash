# 3.5 Concurrency control: thí nghiệm và cách chạy lại

Nội dung báo cáo nằm ở `noi-dung.tex` (ghép vào `report/main.tex`), tài liệu tham khảo ở `concurrency.bib`. Thư mục này chứa script và số liệu để chạy lại các phép đo. Thiết kế module: `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

Giả thuyết ghi trong `noi-dung.tex` **trước khi đo**. Đo xong thì ghi kết quả thật, kể cả khi trái giả thuyết; không điền số chưa đo. Mỗi phép đo: 1 lần khởi động không tính, 5 lần đo, báo cáo trung bình ± độ lệch chuẩn mẫu, không chạy việc khác trên máy trong lúc đo.

## Cấu trúc

```text
report/concurrency/
├── README.md        tài liệu này
├── noi-dung.tex     mục 3.5 của báo cáo
├── concurrency.bib  tài liệu tham khảo (khóa cc-)
├── oracle/          script SQL chạy tay bằng hai phiên (E3)
├── bench/           script gọi Lab API (E1, E2), k6 (E5)
├── results/         số liệu thô .csv, bảng tóm tắt, environment.md
└── images/          ảnh chụp và biểu đồ (3.5-xx-*.png)
```

## Thí nghiệm

| | Nội dung | Cách chạy | Trạng thái |
|---|---|---|---|
| E1 | Bán vượt suất: 10 chiến lược, N = 1000, M = 100, C = 64 | Concurrency Lab hoặc `bench/run_lab.py` | Chưa có Lab |
| E2 | Throughput, độ trễ theo C ∈ {1, 8, 32, 64, 128} cho 5 chiến lược đúng | `bench/run_lab.py` | Chưa có Lab |
| E3 | SERIALIZABLE trên Oracle: ORA-08177 | Hai phiên SQL theo `oracle/e3-serializable.sql` | Chưa viết |
| E4 | Sự cố giữa Redis và Oracle: backend sập sau khi giữ suất; mất key runtime rồi dựng lại | API admin `.../runtime`, `.../runtime/rebuild` và view `v_inventory_invariant_check` | Chưa có API |
| E5 | 500 khách giữ 100 suất qua HTTP (nếu còn thời gian) | `k6 run bench/e5-flash-reserve.js` (đăng nhập trong `setup()`) | Chưa viết |

Lost update hai phiên, lock timeout, deadlock, `MULTI`/`EXEC` không rollback và `WATCH` đã có ở báo cáo 3.4 (`report/transaction/REPORT.md`, thí nghiệm A4–A5, B1–B5); mục 3.5 dẫn lại, không làm lại.

## Chạy lại

1. Làm theo `db/README.md`, chạy backend với profile `dev` (có seed chương trình 9101 và khách 9001–9500).
2. Đăng nhập tài khoản admin để gọi `/api/admin/concurrency-lab/*`.
3. E1, E2: `python report/concurrency/bench/run_lab.py --runs 5 --warmup 1` (sẽ viết cùng Lab).
4. E3: chạy tay theo `oracle/`.
5. Biên dịch báo cáo: xem `report/AGENTS.md`.
