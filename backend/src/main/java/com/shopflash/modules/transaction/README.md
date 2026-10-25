# Chạy và kiểm thử module Transaction

Hợp đồng F10/F7/F8 nằm trong [CONTRACT.md](CONTRACT.md). Tài liệu này mô tả môi trường local và các lệnh kiểm thử đã đối chiếu với `compose.yaml`, `backend/pom.xml`, `application*.properties` và `frontend/vite.config.js`.

## Môi trường local

Oracle và Redis chạy trong Docker; backend Spring Boot và frontend Vite chạy trên host. Các cổng database trong `compose.yaml` chỉ bind `127.0.0.1`. Tại gốc repo, xem `db/README.md` để pull hai image đã ghim digest, tạo `.env` từ `.env.example`, đặt mật khẩu riêng và chạy:

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
```

Backend chạy từ thư mục `backend/` và đã nhập `../.env`. Profile `dev` nạp dữ liệu mẫu, gồm khách `customer@shopflash.local`; profile `tx-local` chỉ dùng cho HTTP local. Tạo một tệp CSV **ngoài repo** có quyền đọc giới hạn cho người chạy backend, bắt đầu bằng `token,email`. Mỗi token là 32 byte ngẫu nhiên ở dạng hex chữ thường. Ví dụ từ gốc repo:

```bash
umask 077
mkdir -p "$HOME/.config/shopflash"
{ printf 'token,email\n'; openssl rand -hex 32 | tr -d '\n'; printf ',customer@shopflash.local\n'; } > "$HOME/.config/shopflash/tx-local-tokens.csv"
```

Tệp có cấu trúc:

```text
token,email
<64 ký tự hex ngẫu nhiên>,customer@shopflash.local
```

Thêm `TX_LOCAL_TOKEN_FILE=/đường/dẫn/tuyệt/đối/tới/tx-local-tokens.csv` vào `.env` (thay `$HOME` bằng đường dẫn tuyệt đối thật). Không commit tệp token, đưa token vào biến `VITE_*`, URL, log hoặc kết quả benchmark. Giữ `SERVER_ADDRESS=127.0.0.1` (hoặc dùng giá trị mặc định). Profile từ chối khởi động nếu tệp thiếu hoặc backend bind ngoài loopback.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,tx-local
```

Ở terminal khác, từ gốc repo:

```bash
cd frontend
npm run dev
```

Vite hiện proxy `/api` sang `http://127.0.0.1:8080`, nên frontend dùng đường dẫn tương đối qua `apiClient` và không cần cấu hình CORS. Không gọi thẳng `:8080` từ trang `:5173`. Client HTTP trên host như k6 có thể gọi `127.0.0.1:8080` trực tiếp; CORS chỉ là quy tắc của trình duyệt. Khi có giao diện F10/F7/F8, chọn khách giả lập `customer@shopflash.local` trong `useAuth()`, nhập token local vào tab và kiểm tra `GET /api/transaction/local-identity` khớp email/vai trò trước khi thao tác. Backend luôn quyết định danh tính theo token, không theo giá trị `userId` ở trình duyệt.

Nếu Flyway báo schema `SHOP` đã có bảng nhưng thiếu `flyway_schema_history`, **dừng lại để xác định nguồn gốc schema và dữ liệu**. Không tự bật `baselineOnMigrate`, sửa V1 hoặc xóa schema; migration V2 chỉ được Flyway áp sau khi lịch sử được xử lý đúng.

Kiểm tra danh tính qua proxy sau khi hai tiến trình đã chạy (lệnh không in token):

```bash
tx_token=$(sed -n '2s/,.*//p' "$HOME/.config/shopflash/tx-local-tokens.csv")
curl -sS -H "Authorization: Bearer $tx_token" http://127.0.0.1:5173/api/transaction/local-identity
```

Lệnh trên phải trả email khách hàng trong Oracle. Gọi API qua proxy giúp trình duyệt giữ cùng origin; không dùng gọi thẳng từ trang `:5173` sang backend `:8080`.

## Lệnh kiểm thử

`backend/pom.xml` đã có JUnit 5 qua `spring-boot-starter-test`, Oracle JDBC và Jedis. Maven Wrapper chạy Java 21. Flyway mặc định chỉ đọc `classpath:db/migration`; profile `dev` mới thêm `classpath:db/dev-seed`. Maven Surefire không tự chọn `*IT`, vì vậy gọi tên lớp integration test bằng `-Dtest`:

```bash
cd backend
./mvnw -version
./mvnw test -Dtest='*CoreTest'
./mvnw test -Dtest=LocalTransactionIdentityCoreTest
./mvnw test -Dtest=TransactionFixtureIT
# Khi các lớp nghiệp vụ được triển khai và Flyway hoạt động:
./mvnw test -Dtest='CartOracleIT,CheckoutOracleIT,OrderOracleIT,TransactionSemanticsIT,RedisTransactionIT'
```

`TransactionFixtureIT` dùng Oracle/Redis thật, tạo dữ liệu tách biệt, kiểm tra commit trên kết nối độc lập, rồi tự dọn; nó tắt Flyway **chỉ trong bài test fixture** để không nhầm phép thử setup/reset với phép thử migration. Các bài test nghiệp vụ về sau phải chạy trên schema đã được Flyway quản lý. Không đặt `@Transactional` bao ngoài bài test cần quan sát `COMMIT`/`ROLLBACK`.

Fixture có ID chỉ gồm chữ thường và số (tối đa 20 ký tự). Mỗi ID có user, category, năm product/inventory và cart riêng trong Oracle, các khóa Redis chỉ bắt đầu bằng `txlab:<id>:`. Dùng ID khác nhau cho các test/lượt đo; setup và reset cùng ID sẽ trả về cùng trạng thái đầu vào. Từ thư mục `backend/`:

```bash
./mvnw test -Dtest=TransactionFixtureCommandIT -Dtx.fixture.action=setup   -Dtx.fixture.id=m1demo
./mvnw test -Dtest=TransactionFixtureCommandIT -Dtx.fixture.action=reset   -Dtx.fixture.id=m1demo
./mvnw test -Dtest=TransactionFixtureCommandIT -Dtx.fixture.action=cleanup -Dtx.fixture.id=m1demo
```

Lệnh cleanup chỉ xóa dữ liệu được đánh dấu bằng ID này và các khóa `txlab:<id>:`; không đụng dữ liệu mẫu nền hay khóa của module khác. `TransactionFixtureIT` đã thử setup/reset/cleanup hai lần và xác nhận fixture thứ hai không bị ảnh hưởng.

Migration V2 đã được thử trên schema Oracle tạm: Flyway tạo V1 trên schema rỗng, thêm một giỏ và một đơn, áp V2, xác nhận `CARTS.version=0` và `ORDERS.checkout_request_hash IS NULL`, rồi khởi động lại để xác nhận checksum; schema tạm đã được dọn. Phép thử này không giải quyết lịch sử Flyway còn thiếu của schema `SHOP` hiện tại.

Benchmark HTTP cần k6 CLI ở giai đoạn 5.x. Kiểm tra trước bằng:

```bash
k6 version
```

Nếu lệnh chưa có, các unit/integration test backend vẫn chạy; hướng dẫn đo k6 và CSV/JSON kết quả sẽ được bổ sung ở milestone benchmark. Không điền số đo giả.

Sau khi có k6 CLI, có thể kiểm tra một yêu cầu từ host mà không chạy benchmark:

```bash
cat > /tmp/shopflash-tx-local-smoke.js <<'EOF'
import http from 'k6/http';
import { check } from 'k6';
export default function () {
  const r = http.get('http://127.0.0.1:8080/api/transaction/local-identity', {
    headers: { Authorization: `Bearer ${__ENV.TX_SMOKE_TOKEN}` },
  });
  check(r, { 'khách hàng đúng': (response) => response.status === 200 && response.json('email') === 'customer@shopflash.local' });
}
EOF
TX_SMOKE_TOKEN=$(sed -n '2s/,.*//p' "$HOME/.config/shopflash/tx-local-tokens.csv") k6 run --vus 1 --iterations 1 /tmp/shopflash-tx-local-smoke.js
rm /tmp/shopflash-tx-local-smoke.js
```
