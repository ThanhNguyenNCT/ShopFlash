# 2.3. Query Processing: chạy lại demo và số đo

Module `query` (F5 báo cáo doanh thu, F6 top bán chạy / khách VIP / sản phẩm chưa bán) chạy cùng một báo cáo trên Oracle và Redis để so cách mỗi DBMS xử lý truy vấn. Contract API: `backend/src/main/java/com/shopflash/modules/query/AGENTS.md`.

## Cấu trúc

```text
report/query/
├── bench/run_benchmarks.py   đo và lưu số liệu, kế hoạch thực thi
├── tests/contract_test.py    kiểm tra API theo contract (165 kiểm tra, có kịch bản D6 và cache)
├── demo/d6-order.sh      chèn/xóa đơn demo cho bước Nhất quán khi thuyết trình
├── results/              số liệu thô .csv, bảng tóm tắt, môi trường đo, kế hoạch thực thi (plans/), cache-summary.md
└── images/               ảnh chụp giao diện (3.3-xx-*.png)
```

Script sinh 100k đơn nằm ở `backend/src/main/resources/db/history-seed/`, chạy tay, không phải migration.

## Chạy từ đầu

Từ thư mục gốc repo, sau khi làm theo `db/README.md` (pull image, `.env`, `docker compose up -d --wait`, `init.lua`).
`.env` phải có `JWT_SECRET_BASE64` (ít nhất 32 byte ngẫu nhiên, mã hóa base64), nếu thiếu backend dừng ngay khi khởi động.

1. **Chạy backend** (JDK 21, profile `dev` để có tài khoản `admin@shopflash.local` / `ShopFlash123!`). Lần chạy đầu Flyway tạo bảng:

   ```bash
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   Trên Windows có thể thay bước này và bước 4 bằng `powershell -ExecutionPolicy Bypass -File scripts\dev.ps1`.

2. **Nạp dữ liệu Oracle** (khoảng 1,5 phút, chạy trong Git Bash): xem `../../backend/src/main/resources/db/history-seed/README.md`.
3. **Dựng read model Redis** (khoảng 20–35 giây cho 100k đơn). API quản trị cần token của tài khoản ADMIN, nên làm một trong hai cách:
   - Trên giao diện: đăng nhập admin, vào **Query Lab**, tab **Nhất quán**, bấm **Dựng lại read model Redis**.
   - Bằng lệnh:

     ```bash
     TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
       -d '{"email":"admin@shopflash.local","password":"ShopFlash123!"}' | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
     curl -X POST http://localhost:8080/api/admin/reports/analytics/rebuild \
       -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
     ```

4. **Chạy giao diện:**

   ```bash
   cd frontend
   npm install
   npm run dev          # gọi backend thật qua proxy /api -> :8080
   npm run dev:mock     # không cần backend, dùng dữ liệu mẫu
   ```

   Đăng nhập `admin@shopflash.local` / `ShopFlash123!`, rồi mở http://localhost:5173/admin/reports (trang Báo cáo) và http://localhost:5173/admin/reports/lab (Query Lab).

5. **Kiểm tra API** (khoảng 2 phút, có chèn rồi xóa 1 đơn tạm trong Oracle):

   ```bash
   python report/query/tests/contract_test.py
   ```

6. **Đo lại số liệu** (khoảng 2,5 phút; đừng chạy việc khác trên máy trong lúc đo):

   ```bash
   python report/query/bench/run_benchmarks.py --runs 5 --warmup 1
   ```

Hai script ở bước 5 và 6 tự đăng nhập bằng tài khoản admin ở trên; đổi bằng biến môi trường `SHOPFLASH_ADMIN_EMAIL` / `SHOPFLASH_ADMIN_PASSWORD`.

Test của backend:
- Unit test không cần DB: `./mvnw test -Dtest='*CoreTest'`.
- Test tích hợp cần stack đang chạy: `./mvnw test -Dtest='Oracle*,Redis*'`.

## Kết quả chính

Đo ngày 19/11/2026 trên AMD Ryzen 5 7535HS, 15,3 GB RAM, Docker 29.5.2. Oracle AI Database 26ai Free 23.26.3, Redis Stack 7.4.7. Dữ liệu: 87.139 đơn được tính, 215.805 dòng hàng (nạp lại bằng `simulate.py reset` rồi `history`). Khoảng ngày cố định 01/11/2025–18/10/2026 (script đo tự truyền `from`/`to`). Khởi động 1 lần không tính, đo 5 lần. Chi tiết ở `results/benchmark-summary.md`, môi trường ở `results/environment.md`, lượt đo lặp lại ngay trước đó ở `results/benchmark-lan-lap-19-11.md`.

| Báo cáo | Oracle AUTO | Oracle NL (ép) | Redis AGGREGATE | Redis CLIENT |
|---|---|---|---|---|
| Doanh thu theo tháng | 50 ± 3 ms | – | 63 ± 1 ms | 625 ± 59 ms (9 round-trip) |
| Doanh thu theo danh mục | 95 ± 8 ms | 459 ± 10 ms | 56 ± 2 ms | 1.686 ± 122 ms (22) |
| Top sản phẩm | 101 ± 5 ms | 528 ± 28 ms | 92 ± 8 ms | 1.962 ± 77 ms (22) |
| Khách VIP (subquery) | 127 ± 11 ms (2 câu) | 160 ± 13 ms | 193 ± 9 ms (2 lượt) | 674 ± 44 ms (9) |
| Sản phẩm chưa bán (NOT EXISTS) | 32 ± 3 ms | – | không hỗ trợ | không hỗ trợ |

Lần đo đầu ngày 18/10/2026 (cùng máy, cùng code) cho Oracle nhanh hơn Redis AGGREGATE ở cả 4 báo cáo, 1,8–5,5 lần (79/209/155/194 ms so với 200/373/851/875 ms). Ngày 19/11, hai lượt đo liên tiếp đều cho Redis nhanh hơn ở theo danh mục và top sản phẩm, Oracle nhanh hơn ở theo tháng và VIP. Không có thay đổi code Redis hay script đo giữa hai ngày; chưa rõ nguyên nhân (giả thuyết: tải máy). Vì vậy chênh lệch dưới 2 lần giữa Oracle AUTO và Redis AGGREGATE không dùng làm căn cứ chọn DBMS. Các chênh lệch lớn (NL/AUTO, CLIENT/AGGREGATE, cache HIT/Oracle) giữ cùng chiều ở mọi lần đo.

### Sáu khác biệt và bằng chứng

| # | Khác biệt | Bằng chứng |
|---|---|---|
| D1 | Cùng báo cáo, cùng kết quả trên hai nguồn | `/compare` khớp ở cả 4 báo cáo Redis hỗ trợ (`tests/contract_test.py`); ảnh `3.3-03` |
| D2 | Oracle có optimizer theo chi phí: AUTO chọn HASH JOIN; ép NL chậm hơn 4,9–5,2 lần ở hai báo cáo join 4 bảng (VIP chỉ join 2 bảng nên chỉ chậm 1,3 lần) | `results/plans/*-oracle-{AUTO,NL,HASH,MERGE}.txt`; bảng trên; ảnh `3.3-04` |
| D3 | Thống kê quyết định kế hoạch. Xóa thống kê thì top-products chuyển từ plan `2314863829` (HASH JOIN, ước lượng 9.001 dòng, thực tế 9.000) sang `56634007` (MERGE JOIN, ước lượng 285K dòng, dynamic sampling, đọc 91.035 block thay vì 5.161); gather lại thì quay về như cũ | `results/plans/stats-*.txt` |
| D4 | Redis không có optimizer; tự tính trong ứng dụng (CLIENT) cần 9–22 round-trip và chậm gấp 3,5–30 lần so với để FT.AGGREGATE làm | bảng trên; `results/plans/*-redis-*.txt` (`FT.PROFILE`); ảnh `3.3-06` |
| D5 | Subquery: Oracle viết `HAVING … > (SELECT AVG …)` và `NOT EXISTS` trong SQL; Redis phải tách 2 lượt cho VIP và không làm được "chưa từng bán" | ảnh `3.3-07`; `meta.supported=false` |
| D6 | Read model Redis chỉ nhất quán sau rebuild: chèn 1 đơn vào Oracle thì compare lệch, rebuild xong mới khớp | `tests/contract_test.py` phần D6; ảnh `3.3-08` |

### Những điều phát hiện khi đo (nên đưa vào báo cáo)

- **Index RediSearch phải khai báo SORTABLE** cho mọi trường được `GROUPBY` / `REDUCE` / trả về. Nếu không, FT.AGGREGATE phải đọc lại JSON của từng document: doanh thu theo danh mục mất khoảng 1,5 s, có SORTABLE còn khoảng 0,15–0,33 s (đo phía server). Đây là việc lập trình viên phải tự thiết kế; Oracle tự chọn cách truy cập dựa trên thống kê.
- **Redis xử lý từng lệnh một:** trang Báo cáo gọi 5 báo cáo cùng lúc thì các lệnh FT.AGGREGATE xếp hàng chờ nhau, mỗi báo cáo bị đội lên vài giây. Vì vậy thời gian hiện trên trang Báo cáo không dùng để so sánh; số liệu so sánh lấy từ tab "Đo hiệu năng" hoặc `bench/run_benchmarks.py`, nơi các lần đo chạy lần lượt.
- **`DBMS_STATS` cần `no_invalidate => FALSE`:** để mặc định thì Oracle dùng lại kế hoạch cũ đã parse, xóa thống kê xong kế hoạch vẫn y nguyên.
- **`FT.AGGREGATE` phải kèm `TIMEOUT 0`:** Redis đang đặt `TIMEOUT 500` với `ON_TIMEOUT return`, quá 500 ms là trả về một phần kết quả mà không báo lỗi.
- **Giới hạn số của RediSearch:** SUM/AVG được in ra với 12 chữ số có nghĩa. Dữ liệu hiện tại là VND tròn và mỗi tổng dưới 10^12 nên vẫn khớp Oracle; tổng lớn hơn sẽ mất độ chính xác.

## Kết hợp: Oracle tính, Redis giữ kết quả (cache-aside)

Redis nhanh nhất khi đọc theo khóa, nên cách kết hợp là để Oracle tính rồi giữ kết quả trong Redis:

- `GET /api/admin/reports/{report}?source=cache`: tra key `report:cache:<báo cáo>:<tham số>`. Có thì trả luôn (`HIT`); không có thì chạy Oracle, `SET … PX <TTL>` rồi trả (`MISS`). TTL mặc định 5 phút (`shopflash.query.cache-ttl`).
- Cái giá: không xóa cache khi có đơn mới, nên trong thời gian TTL kết quả có thể cũ. `tests/contract_test.py` kiểm điều này: chèn một đơn vào Oracle, cache vẫn trả số cũ; xóa cache (như hết TTL) thì lần sau khớp Oracle mới.
- Đo lại: `python report/query/bench/run_benchmarks.py --only-cache` (khoảng 1 phút), ghi `results/cache-summary.md` và `results/benchmark-cache.csv`.

Đo ngày 19/11/2026, cùng lần chạy với bảng "Kết quả chính" (cột Oracle AUTO là một lượt đo riêng xen giữa các lần đo cache, nên lệch tới 40% so với bảng trên):

| Báo cáo | Oracle AUTO | Cache MISS (trống) | Cache HIT | Oracle / HIT |
|---|---|---|---|---|
| Doanh thu theo tháng | 45 ± 1 ms | 49 ± 4 ms | 1,05 ± 0,09 ms | 43× |
| Doanh thu theo danh mục | 118 ± 19 ms | 124 ± 49 ms | 1,49 ± 0,18 ms | 79× |
| Top sản phẩm | 142 ± 40 ms | 161 ± 62 ms | 1,90 ± 0,10 ms | 75× |
| Khách VIP | 134 ± 10 ms | 154 ± 13 ms | 1,40 ± 0,11 ms | 96× |
| Sản phẩm chưa bán | 33 ± 2 ms | 37 ± 5 ms | 1,34 ± 0,10 ms | 24× |

Lần đo ngày 11/11/2026 cũng cho HIT 2–4 ms, nhanh hơn Oracle 18–65 lần.

Lần MISS tốn hơn Oracle một chút (thêm `GET` và `SET`, 3–4 round-trip); từ lần thứ hai chỉ còn 1 `GET`. Cách này hợp với báo cáo admin: xem lại cùng khoảng ngày nhiều lần, chậm vài phút vẫn chấp nhận được. Báo cáo cần số mới tuyệt đối thì đọc thẳng Oracle.

## Kịch bản demo 3 phút

Bốn ý, mỗi ý một lần bấm. Số trong ngoặc là số đo ngày 11/11/2026 trên 87.141 đơn, chỉ để biết trước sẽ thấy gì.

**Chuẩn bị** (trước giờ demo):
- Stack chạy, `analytics/status` báo `inSync: true`, thống kê Oracle đã thu thập (bấm **Thu thập thống kê** một lần nếu lần trước dừng giữa chừng).
- Mở sẵn http://localhost:5173/admin/reports/lab, khoảng ngày mặc định 12 tháng.
- Mở sẵn một cửa sổ Git Bash ở thư mục gốc repo cho bước 4.

| # | Thời gian | Bấm | Chỉ vào | Nói |
|---|---|---|---|---|
| 1 | 0:00–0:30 | Tab **Kết quả hai nguồn**, top sản phẩm → **So sánh hai nguồn** | Thẻ "Khớp"; SQL 15 dòng cạnh 1 lệnh FT.AGGREGATE | Cùng báo cáo, cùng kết quả. Oracle join 4 bảng lúc chạy; Redis đọc read model đã denormalize sẵn, nên 1 lệnh trên 1 index |
| 2 | 0:30–1:30 | Tab **Optimizer** → **Hint join** (AUTO, NL) → **Chạy và xem kế hoạch**; rồi **Thống kê** → **Xem kế hoạch** → **Xóa thống kê** → **Thu thập thống kê** | Thẻ "Chênh lệch" (NL chậm ~6×); ba thẻ thống kê: HASH JOIN, ước lượng 9.004 → MERGE JOIN, ước lượng 285.000 (lệch ~32×), plan hash đổi → HASH JOIN trở lại | Oracle có optimizer theo chi phí: tự chọn HASH JOIN, ép NESTED LOOPS thì trả giá. Optimizer dựa vào thống kê: mất thống kê là đoán sai số dòng và đổi kế hoạch |
| 3 | 1:30–2:20 | Tab **Round-trip và subquery** → **Round-trip**, doanh thu theo danh mục → **Chạy 3 cách**; rồi **Subquery** → **Chạy trên hai nguồn** (cả hai khối) | CLIENT 20 round-trip, chậm ~14× so với AGGREGATE; VIP: 1 câu SQL so với 2 lượt Redis; "chưa từng bán": Redis không hỗ trợ | Redis không có optimizer: người viết lệnh tự quyết tính ở đâu. Không có subquery hay NOT EXISTS: phải tách lượt hoặc không làm được trên read model này |
| 4 | 2:20–3:00 | Terminal: `bash report/query/demo/d6-order.sh insert` → tab **Nhất quán** → **Làm mới** → **Dựng lại read model Redis** | Oracle 87.142 / Redis 87.141, tag "Lệch" → "Đồng bộ" sau khoảng 25 giây | Oracle là nguồn gốc, đọc luôn thấy dữ liệu mới. Redis là bản sao dựng theo lô: nhất quán sau, phải dựng lại. Vì vậy báo cáo cần đúng tuyệt đối đọc Oracle, dashboard nhanh đọc Redis |

Sau demo: `bash report/query/demo/d6-order.sh delete`, rồi **Dựng lại read model Redis** để hai bên khớp lại.

Nói khi hỏi về tốc độ: thời gian trên màn hình là một lần chạy. Số dùng trong báo cáo là 5 lần đo ở bảng "Kết quả chính" phía trên (tab **Đo hiệu năng** hoặc `bench/run_benchmarks.py`).

Dự phòng: quay video trước; `npm run dev:mock` chạy được giao diện mà không cần DB.
