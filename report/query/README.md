# 3.3 Query processing: chạy lại demo và số đo

Module `query` (F5 báo cáo doanh thu, F6 top bán chạy / khách VIP / sản phẩm chưa bán) chạy cùng một báo cáo trên Oracle và Redis để so cách mỗi DBMS xử lý truy vấn. Contract API: `backend/src/main/java/com/shopflash/modules/query/AGENTS.md`.

## Cấu trúc

```text
report/query/
├── oracle/seed/          script sinh dữ liệu benchmark (100k đơn), chạy tay, không phải migration
├── bench/run_benchmarks.py   đo và lưu số liệu, kế hoạch thực thi
├── tests/contract_test.py    kiểm tra API theo contract (146 kiểm tra, có kịch bản D6)
├── results/              số liệu thô .csv, bảng tóm tắt, môi trường đo, kế hoạch thực thi (plans/)
└── images/               ảnh chụp giao diện (3.3-xx-*.png)
```

## Chạy từ đầu

Từ thư mục gốc repo, sau khi làm theo `db/README.md` (pull image, `.env`, `docker compose up -d --wait`, `init.lua`).

1. **Nạp dữ liệu Oracle** (khoảng 1,5 phút): xem `oracle/seed/README.md`.
2. **Chạy backend** (JDK 21):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

3. **Dựng read model Redis** (khoảng 20–35 giây cho 100k đơn):

   ```bash
   curl -X POST http://localhost:8080/api/admin/reports/analytics/rebuild
   ```

4. **Chạy giao diện:**

   ```bash
   cd frontend
   npm install
   npm run dev          # gọi backend thật qua proxy /api -> :8080
   npm run dev:mock     # không cần backend, dùng dữ liệu mẫu
   ```

   Mở http://localhost:5173/admin/reports (trang Báo cáo) và http://localhost:5173/admin/reports/lab (trang So sánh Oracle–Redis).

5. **Kiểm tra API** (khoảng 2 phút, có chèn rồi xóa 1 đơn tạm trong Oracle):

   ```bash
   python report/query/tests/contract_test.py
   ```

6. **Đo lại số liệu** (khoảng 2,5 phút; đừng chạy việc khác trên máy trong lúc đo):

   ```bash
   python report/query/bench/run_benchmarks.py --runs 5 --warmup 1
   ```

Test của backend:
- Unit test không cần DB: `./mvnw test -Dtest='*CoreTest'`.
- Test tích hợp cần stack đang chạy: `./mvnw test -Dtest='Oracle*,Redis*'`.

## Kết quả chính

Đo ngày 18/10/2026 trên AMD Ryzen 5 7535HS, 15,3 GB RAM, Docker 29.5.2. Oracle AI Database 26ai Free 23.26.3, Redis Stack 7.4.7. Dữ liệu: 87.141 đơn được tính, 215.807 dòng hàng. Khởi động 1 lần không tính, đo 5 lần. Chi tiết ở `results/benchmark-summary.md`, môi trường ở `results/environment.md`.

| Báo cáo | Oracle AUTO | Oracle NL (ép) | Redis AGGREGATE | Redis CLIENT |
|---|---|---|---|---|
| Doanh thu theo tháng | 79 ± 17 ms | – | 200 ± 22 ms | 820 ± 55 ms (9 round-trip) |
| Doanh thu theo danh mục | 209 ± 41 ms | 1.181 ± 228 ms | 373 ± 49 ms | 2.597 ± 382 ms (22) |
| Top sản phẩm | 155 ± 24 ms | 853 ± 117 ms | 851 ± 32 ms | 4.851 ± 1.463 ms (22) |
| Khách VIP (subquery) | 194 ± 24 ms (2 câu) | 272 ± 17 ms | 875 ± 77 ms (2 lượt) | 2.363 ± 517 ms (9) |
| Sản phẩm chưa bán (NOT EXISTS) | 139 ± 23 ms | – | không hỗ trợ | không hỗ trợ |

### Sáu khác biệt và bằng chứng

| # | Khác biệt | Bằng chứng |
|---|---|---|
| D1 | Cùng báo cáo, cùng kết quả trên hai nguồn | `/compare` khớp ở cả 4 báo cáo Redis hỗ trợ (`tests/contract_test.py`); ảnh `3.3-03` |
| D2 | Oracle có optimizer theo chi phí: AUTO chọn HASH JOIN; ép NL chậm hơn 5,5–5,7 lần ở hai báo cáo join 4 bảng (VIP chỉ join 2 bảng nên chỉ chậm 1,4 lần) | `results/plans/*-oracle-{AUTO,NL,HASH,MERGE}.txt`; bảng trên; ảnh `3.3-04` |
| D3 | Thống kê quyết định kế hoạch. Xóa thống kê thì top-products chuyển từ plan `2314863829` (HASH JOIN, ước lượng 9.004 dòng, thực tế 9.001) sang `56634007` (MERGE JOIN, ước lượng 285K dòng, dynamic sampling); gather lại thì quay về như cũ | `results/plans/stats-*.txt` |
| D4 | Redis không có optimizer; tự tính trong ứng dụng (CLIENT) cần 9–22 round-trip và chậm gấp 2,7–7 lần so với để FT.AGGREGATE làm | bảng trên; `results/plans/*-redis-*.txt` (`FT.PROFILE`); ảnh `3.3-06` |
| D5 | Subquery: Oracle viết `HAVING … > (SELECT AVG …)` và `NOT EXISTS` trong SQL; Redis phải tách 2 lượt cho VIP và không làm được "chưa từng bán" | ảnh `3.3-07`; `meta.supported=false` |
| D6 | Read model Redis chỉ nhất quán sau rebuild: chèn 1 đơn vào Oracle thì compare lệch, rebuild xong mới khớp | `tests/contract_test.py` phần D6; ảnh `3.3-08` |

### Những điều phát hiện khi đo (nên đưa vào báo cáo)

- **Index RediSearch phải khai báo SORTABLE** cho mọi trường được `GROUPBY` / `REDUCE` / trả về. Nếu không, FT.AGGREGATE phải đọc lại JSON của từng document: doanh thu theo danh mục mất khoảng 1,5 s, có SORTABLE còn khoảng 0,15–0,33 s (đo phía server). Đây là việc lập trình viên phải tự thiết kế; Oracle tự chọn cách truy cập dựa trên thống kê.
- **Redis xử lý từng lệnh một:** trang Báo cáo gọi 5 báo cáo cùng lúc thì các lệnh FT.AGGREGATE xếp hàng chờ nhau, mỗi báo cáo bị đội lên vài giây. Vì vậy thời gian hiện trên trang Báo cáo không dùng để so sánh; số liệu so sánh lấy từ tab "Đo hiệu năng" hoặc `bench/run_benchmarks.py`, nơi các lần đo chạy lần lượt.
- **`DBMS_STATS` cần `no_invalidate => FALSE`:** để mặc định thì Oracle dùng lại kế hoạch cũ đã parse, xóa thống kê xong kế hoạch vẫn y nguyên.
- **`FT.AGGREGATE` phải kèm `TIMEOUT 0`:** Redis đang đặt `TIMEOUT 500` với `ON_TIMEOUT return`, quá 500 ms là trả về một phần kết quả mà không báo lỗi.
- **Giới hạn số của RediSearch:** SUM/AVG được in ra với 12 chữ số có nghĩa. Dữ liệu hiện tại là VND tròn và mỗi tổng dưới 10^12 nên vẫn khớp Oracle; tổng lớn hơn sẽ mất độ chính xác.

## Kịch bản demo 4 phút

Chuẩn bị: stack chạy, đã rebuild, mở sẵn trang So sánh, khoảng ngày mặc định.

| Thời gian | Màn hình | Nói gì |
|---|---|---|
| 0:00–0:30 | Báo cáo (Oracle → Redis) | F5/F6 chạy trên cả hai nguồn; mỗi khối hiện thời gian, round-trip, câu lệnh |
| 0:30–1:00 | Tab 1 Kết quả giống nhau, top-products | Hai bên khớp từng dòng; Oracle join 4 bảng, Redis đọc read model đã denormalize |
| 1:00–1:45 | Tab 2 Optimizer, AUTO vs NL | CBO chọn HASH JOIN; ép NESTED LOOPS chậm hơn 5 lần; Redis FT.PROFILE chỉ chạy đúng như lệnh viết |
| 1:45–2:30 | Tab 3 Thống kê: Xóa → Xem lại kế hoạch → Thu thập | Ước lượng từ 9K thành 285K dòng, kế hoạch đổi sang MERGE JOIN; gather xong quay lại |
| 2:30–3:00 | Tab 4 Round-trip, doanh thu theo danh mục | AGGREGATE 1 lượt so với CLIENT 22 lượt |
| 3:00–3:30 | Tab 5 Subquery | VIP: 1 câu SQL so với 2 lượt Redis; "chưa từng bán": Redis không làm được |
| 3:30–4:00 | Tab 6 Nhất quán | Chèn 1 đơn trong SQL Developer → Làm mới thấy lệch → Dựng lại → khớp |

Dự phòng: quay video trước; `npm run dev:mock` chạy được mà không cần DB.
