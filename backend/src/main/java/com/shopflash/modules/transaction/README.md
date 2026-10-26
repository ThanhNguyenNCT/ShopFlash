# Chạy và kiểm thử module Transaction

Hợp đồng F10/F7/F8 nằm trong [CONTRACT.md](CONTRACT.md). Tài liệu này mô tả môi trường local và các lệnh kiểm thử đã đối chiếu với `compose.yaml`, `backend/pom.xml`, `application*.properties` và `frontend/vite.config.js`.

Chi tiết năm Cart API và ví dụ: xem [CART.md](CART.md).

## Môi trường local

Oracle và Redis chạy trong Docker; backend Spring Boot và frontend Vite chạy trên host. Các cổng database trong `compose.yaml` chỉ bind `127.0.0.1`. Tại gốc repo, xem `db/README.md` để pull hai image đã ghim digest, tạo `.env` từ `.env.example`, đặt mật khẩu riêng và chạy:

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
```

Backend chạy từ thư mục `backend/` và đã nhập `../.env`. Profile `dev` nạp dữ liệu mẫu, gồm khách `customer@shopflash.local` và Admin `admin@shopflash.local`. Tạo secret JWT ngẫu nhiên rồi thêm vào `.env` gốc, không commit hoặc đưa vào `VITE_*`:

```bash
openssl rand -base64 32
```

Đặt `JWT_SECRET_BASE64=<kết quả lệnh trên>` trong `.env`; backend từ chối khởi động nếu thiếu hoặc ngắn hơn 32 byte giải mã. Giữ `SERVER_ADDRESS=127.0.0.1` cho máy local. JWT tồn tại tối đa 30 phút; đăng xuất xóa JWT ở tab, không thu hồi token đã cấp ở nơi khác trước `exp` trừ khi tài khoản bị vô hiệu hóa.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Ở terminal khác, từ gốc repo:

```bash
cd frontend
npm run dev
```

Vite proxy `/api` sang `http://127.0.0.1:8080`, nên frontend dùng đường dẫn tương đối qua `apiClient` và không cần cấu hình CORS. Trình duyệt mở Vite tại `http://127.0.0.1:5173`, đăng nhập ở `/login` bằng tài khoản dev seed hoặc đăng ký tài khoản mới. Client HTTP trên host như k6 có thể gọi backend `127.0.0.1:8080` trực tiếp. Frontend giữ JWT trong `sessionStorage` của tab, khôi phục tài khoản qua `/api/auth/me`; backend xác minh JWT và tra lại trạng thái/vai trò Oracle trên mỗi request được bảo vệ.

Nếu Flyway báo schema `SHOP` đã có bảng nhưng thiếu `flyway_schema_history`, **dừng lại để xác định nguồn gốc schema và dữ liệu**. Không tự bật `baselineOnMigrate`, sửa V1 hoặc xóa schema; migration V2 chỉ được Flyway áp sau khi lịch sử được xử lý đúng.

Kiểm tra đăng nhập và danh tính qua proxy sau khi hai tiến trình đã chạy. Mật khẩu dev seed là `ShopFlash123!`; lệnh dưới giữ JWT trong biến shell và không in token:

```bash
tx_token=$(curl -sS --fail-with-body -H 'Content-Type: application/json' \
  -d '{"email":"customer@shopflash.local","password":"ShopFlash123!"}' \
  http://127.0.0.1:5173/api/auth/login | jq -r '.accessToken')
curl -sS -H "Authorization: Bearer $tx_token" http://127.0.0.1:5173/api/auth/me
unset tx_token
```

Lệnh trên phải trả email khách hàng trong Oracle. Gọi API qua proxy giúp trình duyệt giữ cùng origin; không dùng gọi thẳng từ trang `:5173` sang backend `:8080`.

## Lệnh kiểm thử

`backend/pom.xml` đã có JUnit 5 qua `spring-boot-starter-test`, Oracle JDBC và Jedis. Maven Wrapper chạy Java 21. Flyway mặc định chỉ đọc `classpath:db/migration`; profile `dev` mới thêm `classpath:db/dev-seed`. Maven Surefire không tự chọn `*IT`, vì vậy gọi tên lớp integration test bằng `-Dtest`:

```bash
cd backend
./mvnw -version
./mvnw test -Dtest='*CoreTest'
./mvnw test -Dtest=JwtConfigCoreTest,SecurityErrorWriterCoreTest,CartCoreTest
./mvnw test -Dtest=AuthOracleIT
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

Kịch bản benchmark HTTP ở task 5.4 phải đăng nhập tài khoản fixture trước mỗi lượt đo, giữ JWT trong bộ nhớ k6 và bắt đầu đồng hồ nghiệp vụ sau khi nhận token. Latency đăng nhập không được gộp vào Cart/Checkout/Order; không xuất mật khẩu hoặc JWT vào CSV/JSON thô. Reset fixture giữa các lượt như quy định của từng kịch bản.

Sau khi có k6 CLI, có thể kiểm tra đăng nhập và một yêu cầu từ host mà không chạy benchmark. Mật khẩu/JWT chỉ nằm trong bộ nhớ k6, không ghi vào kết quả thô:

```bash
cat > /tmp/shopflash-tx-jwt-smoke.js <<'EOF'
import http from 'k6/http';
import { check } from 'k6';
export default function () {
  const login = http.post('http://127.0.0.1:8080/api/auth/login', JSON.stringify({
    email: 'customer@shopflash.local', password: __ENV.TX_SMOKE_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  const token = login.json('accessToken');
  const r = http.get('http://127.0.0.1:8080/api/cart', {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(r, { 'đọc giỏ thành công': (response) => response.status === 200 });
}
EOF
TX_SMOKE_PASSWORD='ShopFlash123!' k6 run --vus 1 --iterations 1 /tmp/shopflash-tx-jwt-smoke.js
rm /tmp/shopflash-tx-jwt-smoke.js
```
