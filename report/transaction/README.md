# Transaction: thí nghiệm và benchmark

Thư mục này chứa thí nghiệm tính đúng đắn, phép đo cơ chế database và phép đo HTTP của F10 → F7 → F8. Oracle là nguồn dữ liệu nghiệp vụ của ShopFlash. Redis trong phép đo database chỉ là mô phỏng cùng thao tác logic; không thay thế giao dịch đặt hàng thật.

## 1. Môi trường

- Oracle Database Free và Redis Stack chạy bằng `docker compose` trên `127.0.0.1` theo [`compose.yaml`](../../compose.yaml). Redis bật AOF (`--appendonly yes`). Ghi lại digest image, phiên bản Docker, CPU/RAM và tải nền trong lần chạy báo cáo.
- Backend Spring Boot và frontend Vite chạy trên host. Vite chuyển `/api` tới backend `127.0.0.1:8080`; không cần mở CORS. Backend đọc `../.env` từ thư mục `backend`; cần `ORACLE_*`, `REDIS_*` và `JWT_SECRET_BASE64` đủ mạnh. Không ghi giá trị secret vào báo cáo.
- Java 21, Maven Wrapper, Python 3 và k6 CLI v0.55.2 trong image Docker ghim digest. Chạy các lệnh sau trước khi đo:

```bash
docker compose up -d --wait
cd backend && ./mvnw -version && cd ..
docker pull grafana/k6:0.55.2
docker run --rm grafana/k6@sha256:3cd0ae83b47a29b273193eb998740c3b0e219fc34cdf579b90a8fc1698f5c025 version
```

Trên hai terminal riêng, khởi chạy host nếu chưa có tiến trình tương ứng:

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm run dev
```

Cho backend khởi động thành công ít nhất một lần để Flyway áp dụng V1/V2 trước khi gọi các lớp `*IT` (các lớp này tắt Flyway để không đưa migration vào thời gian đo). Profile `dev` chỉ cần khi xem dữ liệu mẫu trên giao diện; thí nghiệm tạo fixture `txlab` riêng, không phụ thuộc seed dev. Không chạy đồng thời hai lần benchmark trên một schema vì chúng sẽ tranh tài nguyên và làm sai số đo.

## 2. Kiểm chứng cơ chế trước khi đo tốc độ

JUnit 5 dùng Oracle/Redis thật; không có `@Transactional` bọc toàn test. Oracle được đọc lại từ kết nối độc lập sau `COMMIT`/`ROLLBACK`, Redis từ client riêng. Bảng chứng cứ gồm trạng thái trước, mong đợi, thực tế và lệnh kiểm chứng.

```bash
cd backend
./mvnw test -Dtest='TransactionSemanticsIT,RedisTransactionIT'
cd ..
```

Đầu ra: `backend/target/transaction-evidence/oracle.csv` và `redis.csv`. Các case Oracle gồm commit, rollback sau khi ghi đơn/dòng/tồn/giỏ, `READ COMMITTED`, lock timeout của giỏ/tồn kho và deadlock có `CyclicBarrier`. Các case Redis gồm `MULTI/EXEC`, `DISCARD`, lỗi runtime lúc `EXEC`, xung đột `WATCH` có `CountDownLatch`, và Lua lỗi sau ghi. `DISCARD` bỏ lệnh chưa thực thi; lỗi ở `EXEC` hoặc Lua không tự hoàn tác ghi đã chạy.

## 3. Database-level: Java runner

Runner là lớp JUnit gọi đích danh, dùng Oracle JDBC và Jedis đã có trong project, đo bằng `System.nanoTime()`; không thêm framework benchmark. Mỗi yêu cầu có fixture riêng: một khách, một giỏ, năm sản phẩm và tồn đầu kỳ 100/sản phẩm. Hai bên dùng giỏ 1 hoặc 5 sản phẩm, số lượng 1, đơn giá 100. Từng thao tác đọc/kiểm tra giỏ và tồn, ghi đầu đơn và dòng, giảm tồn rồi dọn giỏ. Oracle dùng bảng/ràng buộc SQL và `COMMIT`; Redis dùng `WATCH` + `MULTI/EXEC`, các key `txlab:*` và kiểm tra được viết trong Java.

```bash
cd backend
./mvnw test -Dtest='TransactionDbBenchmarkIT'
cd ..
```

Mặc định runner chạy từng tổ hợp **1/5 sản phẩm × đồng thời 1/10**, 50 thao tác làm nóng, sau đó **5 lượt × 200 thao tác/lượt**. Dữ liệu fixture được đặt lại trước warm-up và từng lượt; 200 yêu cầu có giỏ/sản phẩm tách biệt. Khoảng đo latency bắt đầu khi đọc/khóa dữ liệu nghiệp vụ và kết thúc khi nhận kết quả `COMMIT`/`EXEC`; không gồm reset, nhưng gồm lệnh SQL/Redis cần cho giao dịch. Thông lượng chia số thành công cho thời gian thực từ lúc thả các worker đến khi toàn lượt đã trả kết quả và hậu kiểm xong. Cấu hình được lưu trong `db-*-config.json`; kết quả từng mẫu ở `db-*.csv` và `db-*.jsonl`, thời gian thực từng lượt ở `db-*-runs.jsonl`.

Chỉ giảm số mẫu cho lượt thử công cụ, kèm lý do công khai:

```bash
cd backend
./mvnw test -Dtest='TransactionDbBenchmarkIT' -Dtx.bench.warmup=2 -Dtx.bench.rounds=1 -Dtx.bench.samples=10 -Dtx.bench.override.reason=smoke
cd ..
```

Số đo Oracle–Redis không chuẩn hóa hoàn toàn: Oracle kiểm tra khóa ngoại, CHECK, unique, lưu redo và cập nhật nhiều bảng trong giao dịch; Redis dùng key độc lập, điều kiện ứng dụng/`WATCH` và AOF của cấu hình này. Số lệnh và chi phí client/network khác nhau; số liệu Redis không phải hiệu năng đặt đơn của ứng dụng ShopFlash.

## 4. Application-level: k6 qua Vite proxy

Script [`k6/cart-checkout-order.js`](k6/cart-checkout-order.js) đo riêng từng API Cart, Checkout, Order và toàn luồng. Một luồng chuẩn gồm `GET cart`, thêm 1 hoặc 5 sản phẩm, preview, tạo đơn, xem danh sách/chi tiết và hủy đơn để hoàn tồn kho. Mỗi VU dùng khách/giỏ/sản phẩm fixture riêng. Lệnh đăng nhập trong `setup()` của k6 lấy JWT trước lượt đo, có nhãn `phase=setup` và **không** đi vào metric nghiệp vụ. Không ghi JWT hoặc mật khẩu trong JSON thô; không truyền token bằng URL.

Mật khẩu fixture hiện được định nghĩa trong `TransactionFixtures` (chỉ dùng local/test). Đưa vào biến môi trường của phiên shell, tránh ghi vào script và báo cáo:

```bash
read -r -s -p 'Mật khẩu fixture: ' TX_BENCH_PASSWORD
export TX_BENCH_PASSWORD
bash report/transaction/scripts/run-http.sh
unset TX_BENCH_PASSWORD
```

Script kiểm tra k6, tạo mười khách fixture, đặt lại dữ liệu trước mỗi lượt, chạy đủ 4 tổ hợp × (50 warm-up + 5 × 200 lượt), hậu kiểm Oracle sau từng lượt bằng `TransactionHttpAuditIT`, rồi cleanup bằng trap. File `*-audit.json` ghi số đơn/dòng, trạng thái hủy, giỏ và tồn kho trước reset. Có thể thử script ngắn bằng `TX_HTTP_SMOKE=1 bash report/transaction/scripts/run-http.sh`; kết quả ghi `override_reason=smoke`. Nếu phiên bị `SIGKILL`, chạy cleanup thủ công bằng ID fixture trong `backend/target/transaction-http-fixtures.json`:

```bash
cd backend
./mvnw test -Dtest='TransactionHttpFixtureCommandIT' \
  -Dtx.fixture.action=cleanup -Dtx.fixture.ids='<10 ID phân cách bằng dấu phẩy>'
cd ..
```

Có thể gọi `-Dtx.fixture.action=setup` hoặc `reset` với cùng danh sách ID; `target/transaction-http-fixtures.json` chứa email/ID sản phẩm, không chứa mật khẩu/JWT. Mọi thao tác chỉ chạm user, giỏ, đơn, tồn của fixture `txlab-*` và khóa Redis `txlab:*`; không xóa seed nền hay `flash:*`, `product:*`, `order:analytics:*`, `order:line:*`.

JSON thô k6 được nén nguyên vẹn tại `report/transaction/results/http-<thời điểm>/cart<1|5>-c<1|10>-r<0..5>.json.gz`. Round 0 là warm-up. `tx_api_duration_ms` gắn nhãn API/kết quả, `tx_flow_duration_ms` gắn nhãn cả luồng/kết quả/số request; `http_req_duration` tổng hợp không được dùng thay metric Checkout hoặc toàn luồng.

## 5. Tổng hợp và đọc kết quả

```bash
python3 report/transaction/scripts/summarize.py \
  --db-csv report/transaction/results/db-<run_id>.csv \
  --http-dir report/transaction/results/http-<run_id> \
  --output-dir report/transaction/results/summary-<run_id>
```

Chương trình xuất CSV/JSON mẫu chuẩn hóa và hai bảng **database**/**HTTP** tách biệt, thêm `comparison.md`. p50/p95 dùng toàn bộ mẫu `success` hợp lệ của năm lượt. Thông lượng thành công = số `success` / tổng thời gian thực của các lượt (DB: transactions/s; HTTP: requests/s theo API hoặc flows/s toàn luồng). Error rate = `error` / mẫu hoàn tất; rejection rate = `rejected` / mẫu hoàn tất; `expected_failure` và `invalid` đếm riêng. File JSON có số mẫu, p50/p95, trung bình và độ lệch chuẩn giữa các lượt, cùng số vi phạm bất biến. Lượt không có mẫu thành công để trống phân vị. HTTP tính thời gian thực từ dấu thời gian đầu/cuối của metric toàn luồng, không cộng thời gian đăng nhập.

Đối chiếu correctness trước khi kết luận performance: đọc bảng chứng cứ Oracle/Redis, số thành công/từ chối/lỗi, trạng thái sau commit/rollback và hậu kiểm tồn kho/đơn. Không so trực tiếp các trị số latency/throughput của database-level với HTTP. Phần trình bày báo cáo theo `report/AGENTS.md`: Lý thuyết → Oracle (demo và số đo) → Redis (demo và số đo) → Bảng so sánh → Kết luận theo ShopFlash.

## 6. Lần đo dùng trong báo cáo

Ngày 27/10/2026, lần chạy mặc định dùng file database `results/db-2026-10-27T00-57-08-100088724Z.csv` và thư mục HTTP `results/http-20261027-stable/`. Mỗi nhánh có đủ 5 lượt × 200 mẫu đo; HTTP còn có một lượt làm nóng cho mỗi tổ hợp. Có thể dựng lại bảng tổng hợp từ dữ liệu thô đã lưu:

```bash
python3 -B report/transaction/scripts/summarize.py \
  --db-csv report/transaction/results/db-2026-10-27T00-57-08-100088724Z.csv \
  --http-dir report/transaction/results/http-20261027-stable \
  --output-dir report/transaction/results/summary-20261027
```

Đọc [tiểu mục báo cáo](SECTION_TRANSACTION.md), `results/evidence/oracle.csv`, `results/evidence/redis.csv`, `results/summary-20261027/{database,http}-summary.csv`, `results/summary-20261027/comparison.md` và 24 file `*-audit.json` để kiểm tra từng kết luận. File `results/environment-20261027.json` ghi phiên bản, image digest và hash của runner/script; các mật khẩu và JWT không được đưa vào kết quả. Lần đo này ghi nhận 8.000 giao dịch database và 4.000 luồng HTTP đo chính thức thành công, 0 vi phạm bất biến. Đây là số quan sát tại local, không phải ngưỡng bảo đảm hiệu năng.
