# Design

## Context

Xem [proposal.md](proposal.md) để biết lý do và phạm vi thay đổi. `V1__schema.sql` đã tạo `USERS`, `CARTS`, `CART_ITEMS`, `ORDERS`, `ORDER_ITEMS`, `PRODUCT_INVENTORY` và các ràng buộc cần thiết; migration V2 đã thêm `CARTS.version` và `ORDERS.checkout_request_hash`. `R__00_core_seed.sql` có tài khoản và dữ liệu mẫu. F0/JWT, Cart API và giao diện `/cart` đã có; Checkout/Order còn chờ triển khai. TV3 đã có chức năng dựng lại dữ liệu báo cáo Redis từ Oracle.

`db/DATABASE_DESIGN.md` §7–10 và §14–16 mô tả các bảng và luồng nghiệp vụ dự kiến. Quyền sở hữu F10 và route `/cart` đã chuyển sang TV4. Ngày 26/10/2026, người yêu cầu xác nhận TV5 đã đồng ý bàn giao F0 trong `common/security` và `shared/auth` cho TV4. Không tìm thấy `docs/local/PROJECT_ONBOARDING.md` và `../assignment/KE_HOACH_BTL.md` trong môi trường khảo sát trước đó.

## Goals / Non-Goals

**Goals:** Hoàn thiện F0 bằng JWT trước khi nối F10 → F7 → F8 trên Oracle; kiểm tra được trạng thái dữ liệu sau thành công, lỗi và tranh chấp; xây dựng thí nghiệm Oracle–Redis có thể chạy lại.

**Non-Goals:** Không tạo giao dịch phân tán Oracle–Redis; không triển khai F9, khôi phục mật khẩu qua email, đăng nhập mạng xã hội, thanh toán hoặc vận chuyển; không cập nhật mô hình báo cáo Redis của TV3 trong cùng giao dịch đặt/hủy đơn.

## Decisions

### 1. Phân chia trách nhiệm và điểm tích hợp

| Thành phần | Hiện trạng | Phần việc của Transaction |
|---|---|---|
| Cấu trúc dữ liệu Oracle | Migration V1, ràng buộc, chỉ mục và dữ liệu mẫu | Dùng lại; thêm `CARTS.version` và `ORDERS.checkout_request_hash` bằng migration mới |
| F10 | Cart API, trang `/cart` và route trong Transaction đã có | Tiếp tục kiểm chứng giỏ qua JWT rồi nối Checkout/Order |
| F0 | API Oracle/JWT, form đăng nhập/đăng ký và client chung đã có | Kiểm thử chức năng qua frontend → backend → Oracle và xử lý lỗi tìm thấy |
| F7/F8 | Các đường dẫn giao diện đã có, nội dung là trang chờ | Xây dựng controller, service, repository và ba trang trong module Transaction |
| Tồn kho F2 | `StorageRepository.updateInventory` dùng `version` | Dùng chung bảng tồn kho; viết câu SQL riêng cho nghiệp vụ trừ/hoàn hàng và tăng `version` |
| Báo cáo TV3 | `AnalyticsProjectionService.rebuild()` đã có | Ghi đơn vào Oracle; dùng công cụ của TV3 để kiểm tra dữ liệu báo cáo khi cần |

Việc chuyển F10 không có nghĩa TV4 sửa toàn bộ module `concurrency`. TV4 đã đặt backend, `CartPage` và route `/cart` trong module `transaction`; route cũ của TV5 đã bỏ khỏi router. Để thử F10 phục vụ Transaction, CartPage nhận ID sản phẩm và số lượng, gọi Cart API Oracle thật. Profile `dev` đã có `CORE-001` (ID 1), tồn kho và giỏ mẫu; các bài test tự động tiếp tục dùng fixture độc lập. Không dùng giỏ mock chỉ lưu trong trình duyệt vì không thể đối chiếu số lượng với Oracle. Điểm nối Product Listing/Detail của TV2 nằm ngoài đường găng. TV5 tiếp tục sở hữu F9. Theo bàn giao F0, TV4 được cập nhật `common/security`, `shared/auth` và `shared/api/client.js` mà không thay đổi nghiệp vụ các module khác.

Mỗi khách hàng chỉ có một giỏ theo `uk_carts_user`. Trang giỏ và checkout dùng cùng repository và hợp đồng API của Transaction; không tạo một bản Cart thứ hai trong Redis. `PriceTag` và client API dùng chung được tái sử dụng. `OrderStatusTag` cần hiển thị đúng miền trạng thái Oracle trước khi sử dụng.

### 2. API và dữ liệu yêu cầu

- `GET /api/cart`: trả `{version, items, totalAmount}`; giá và tổng tiền chỉ để tham khảo. Giỏ chưa tồn tại được biểu diễn bằng `version: 0`, `items: []`, `totalAmount: 0` mà không ghi Oracle.
- `POST /api/cart/items {productId, quantity}`: tự tạo giỏ nếu cần; nếu mặt hàng đã có thì cộng số lượng vào dòng hiện tại. `PATCH /api/cart/items/{productId} {quantity}` đặt số lượng tuyệt đối; `DELETE /api/cart/items/{productId}` xóa một mặt hàng; `DELETE /api/cart/items` xóa mọi mặt hàng nhưng giữ dòng `CARTS` để duy trì phiên bản.
- Mọi API sửa giỏ nhận `X-Cart-Version` bằng phiên bản từ lần đọc gần nhất và trả phiên bản mới. Phiên bản sai trả `409 CART_CHANGED`, không tự ghi đè. Số lượng hợp lệ từ 1 đến 999; `productId` phải tồn tại và sản phẩm còn `ACTIVE` tại lúc thêm. Không giữ hàng, không lưu giá trong `CART_ITEMS`; frontend không tự cộng dồn giá sau khi API trả kết quả mới.
- `GET /api/checkout/preview`: đọc giỏ, sản phẩm và tồn kho từ Oracle; trả `cartVersion`, `lines[{productId, quantity, unitPrice, lineTotal, normalAvailable, ...}]`, `totalAmount` và cảnh báo đối với sản phẩm không còn hợp lệ. API này không giữ hàng hoặc sửa dữ liệu; kết quả có thể thay đổi trước khi khách hàng xác nhận.
- `POST /api/orders`: nhận header `Idempotency-Key` (UUID dạng chuẩn do trình duyệt tạo, dài 36 ký tự) và body `{cartVersion, recipientName, recipientPhone, shippingAddress, expectedLines:[{productId,quantity,unitPrice}]}`. `cartVersion` và `expectedLines` ghi lại đúng nội dung đã xác nhận; server vẫn kiểm tra giỏ, giá và tồn kho Oracle trong giao dịch. Các mặt hàng được sắp theo `productId` trước khi so sánh.
- Khi đặt hàng thành công, API trả `orderId`, `orderCode`, `status`, `totalAmount`. Gửi lại cùng khóa và cùng nội dung sẽ nhận lại cùng đơn. Các API `GET /api/orders?page=1&size=20&status=...`, `GET /api/orders/{orderId}` và `POST /api/orders/{orderId}/cancel` lấy `userId` từ principal JWT đã được backend xác thực.
- API dùng định dạng lỗi chung: `400` khi dữ liệu đầu vào sai, `401` khi chưa xác thực, `404` khi mặt hàng/đơn không tồn tại hoặc đơn không thuộc người dùng, `409` khi giỏ, giá, tồn kho, trạng thái đơn hoặc khóa gửi lại bị xung đột. Hết thời gian chờ khóa hoặc deadlock được đổi thành mã lỗi riêng có thể gửi lại. Không trả dữ liệu đơn hàng của người khác.

`cartVersion` và `expectedLines` giúp phát hiện giỏ hoặc giá thay đổi, đồng thời cho phép tính mã băm nội dung yêu cầu sau khi giỏ đã được xóa. Trình duyệt giữ nguyên khóa khi gửi lại cùng nội dung; nếu mặt hàng hoặc thông tin nhận hàng thay đổi sau khi yêu cầu cũ đã được giải quyết, trình duyệt tạo khóa mới. Không tự gửi lại thao tác **cộng** hàng vào giỏ sau khi hết thời gian chờ: trước tiên tải lại giỏ và kiểm tra phiên bản, tránh cộng hai lần.

### 2.1. F0 và danh tính cho Transaction

TV4 triển khai F0 trong `common/security` và `shared/auth` theo bàn giao của TV5. Dùng `spring-boot-starter-security` và `spring-boot-starter-oauth2-resource-server` (bao gồm thư viện JOSE do Spring quản lý), `BCryptPasswordEncoder` cho `USERS.password_hash` và `NamedParameterJdbcTemplate` với bind variable. Tài khoản đăng ký luôn là `CUSTOMER/ACTIVE`; email được trim và chuẩn hóa chữ thường trước khi kiểm tra duy nhất. Tên dài 1–150 ký tự, email tối đa 255, số điện thoại tùy chọn tối đa 30, mật khẩu tối thiểu 10 ký tự và tối đa 72 byte UTF-8 để không bị bcrypt cắt ngầm. Không có API đăng ký ADMIN; từ chối trường `role`/`userId` tự khai. `POST /api/auth/register` trả `201` cùng hồ sơ công khai rồi giao diện chuyển tới đăng nhập; xung đột email kể cả hai request đồng thời trả `409`. `POST /api/auth/login` kiểm tra mật khẩu/trạng thái rồi trả `{accessToken, tokenType, expiresAt, user}`; `GET /api/auth/me` trả hồ sơ từ Oracle, không trả mật khẩu/mã băm. Lỗi đăng nhập dùng cùng thông điệp `401` cho email không có, mật khẩu sai và tài khoản bị vô hiệu hóa.

JWT ký bằng HS256 với secret Base64 tối thiểu 32 byte từ `JWT_SECRET_BASE64` ngoài repo; backend từ chối khởi động khi thiếu hoặc yếu. Token có `sub` là ID người dùng, `iss`, `aud`, `iat`, `exp` và `jti` ngẫu nhiên; hạn dùng ngắn, mặc định 30 phút, không ghi thông tin nhạy cảm vào claim. Decoder chỉ nhận thuật toán đã chốt, xác minh chữ ký/issuer/audience/hạn dùng. Mỗi yêu cầu được bảo vệ tra lại `USERS` theo `sub` để kiểm tra `ACTIVE` và vai trò hiện tại; không coi vai trò trong token hoặc `userId` trong body là nguồn phân quyền. `SecurityFilterChain` không tạo HTTP session, công khai chỉ `POST /api/auth/register`, `POST /api/auth/login` và `/api/health`; `/api/auth/me` cần xác thực, `/api/admin/**` cần ADMIN, Cart/Checkout/Order cần CUSTOMER; các `/api/**` không khai báo bị từ chối. API dùng Bearer header, không dùng cookie xác thực nên vô hiệu CSRF cho API; không mở CORS. Lỗi `401/403` theo JSON lỗi chung. Service Transaction nhận `userId` đã được xác minh từ Spring Security principal. Adapter token CSV và endpoint đối chiếu danh tính thử nghiệm đã bị gỡ.

Frontend thay `loginAs(role)` bằng `login({email,password})`, trang đăng ký thật, và giữ hình dạng `{user,isAdmin,logout}` cho layout. JWT được lưu tự động trong `sessionStorage` của tab sau đăng nhập và gắn vào `Authorization: Bearer ...` bởi interceptor của `shared/api/client.js` cho đường dẫn `/api` cùng origin; không lưu trong `localStorage`, URL, biến `VITE_*` hoặc log. Khi tải lại, `AuthProvider` gọi `/api/auth/me` trước khi xác định quyền; `RequireAdmin` chờ trạng thái khôi phục để không chuyển hướng sai. `401` xóa phiên rồi yêu cầu đăng nhập lại; `403` giữ phiên nhưng báo thiếu quyền. Đăng xuất xóa JWT ở tab, không có refresh token hay bảng phiên: JWT đã cấp vẫn có hiệu lực đến `exp` (tối đa 30 phút), nên không hứa thu hồi tức thời trên server. JWT trong `sessionStorage` có thể bị đọc nếu origin có XSS, nên phần triển khai phải tránh HTML không tin cậy, không đưa token vào lỗi/telemetry và ghi rõ giới hạn này trong tài liệu. Checkout giữ bản ghi idempotency đang chờ theo ID/email từ `/me`, không dựa vào `localStorage` giả lập.

**Môi trường local:** Oracle và Redis chạy bằng `docker compose` với cổng bind `127.0.0.1`; backend và Vite chạy trên host. `.env` gốc cung cấp `ORACLE_*`, `REDIS_*` và `JWT_SECRET_BASE64`; tạo secret bằng `openssl rand -base64 32`, không commit. Chạy backend với profile `dev` để có tài khoản mẫu. Vite proxy `/api` đến backend nên không cần bật CORS; k6 gọi backend trên host và đăng nhập khách fixture trước khi đo, loại thời gian đăng nhập khỏi latency nghiệp vụ. Không lưu JWT trong kết quả thô.

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# ở terminal khác, từ gốc repo:
cd frontend
npm run dev
```

Trong môi trường local, `iss` dùng URL cố định `https://shopflash.local` để Spring Security đọc và kiểm tra claim đúng kiểu; đây là định danh bên phát hành, không phải endpoint mạng. `aud` là `shopflash-api`.

### 2.2. Giao dịch sửa giỏ

Migration mới thêm `CARTS.version NUMBER(19) DEFAULT 0 NOT NULL`, không thay bảng hoặc dữ liệu mẫu V1. Lần đọc giỏ chưa tồn tại trả phiên bản 0. Khi thêm hàng lần đầu, transaction tạo dòng `CARTS`, lấy khóa dòng đó, kiểm tra phiên bản 0 rồi ghi `CART_ITEMS` và tăng phiên bản lên 1. Nếu hai yêu cầu cùng tạo giỏ, ràng buộc `uk_carts_user` phân định yêu cầu thắng; yêu cầu còn lại rollback và trả lỗi để tải lại giỏ. Không bắt lỗi ràng buộc rồi tiếp tục trên cùng transaction. Các lần sửa tiếp theo khóa dòng `CARTS FOR UPDATE`, đối chiếu `X-Cart-Version`, thay đổi `CART_ITEMS`, tăng `version` và `updated_at` đúng một lần. Xóa giỏ rỗng là thao tác không đổi phiên bản. Cộng số lượng của sản phẩm đã có sau khi giữ khóa; không tạo dòng thứ hai.

Không lấy khóa `PRODUCTS` hoặc `PRODUCT_INVENTORY` khi sửa giỏ. Khi thêm hàng, chỉ đọc trạng thái sản phẩm để báo lỗi sớm; không giảm hoặc giữ tồn kho. Giá/tồn kho hiển thị trong giỏ là thông tin tham khảo và phải được kiểm tra lại tại checkout. Khi đọc giỏ bằng nhiều câu SQL, đọc `CARTS.version` trước và sau danh sách mặt hàng; nếu phiên bản đổi thì đọc lại, tránh ghép phiên bản cũ với mặt hàng mới. Giá và tồn kho có thể đổi độc lập với phiên bản giỏ nên vẫn chỉ là thông tin tham khảo. Cách kết hợp khóa dòng giỏ với phiên bản tránh mất cập nhật từ hai tab; chỉ dùng khóa dòng sẽ tuần tự hóa câu SQL nhưng một `PATCH` từ tab cũ vẫn có thể ghi đè giá trị mới.

Trên `/cart`, đổi số lượng hợp lệ cập nhật ngay thành tiền tham khảo ở giao diện và tự gửi `PATCH` sau khoảng chờ ngắn. Các lệnh `PATCH` của cùng tab chạy tuần tự theo `CARTS.version`; nếu lỗi hoặc không rõ kết quả, tải lại Oracle và yêu cầu khách kiểm tra. Chặn đặt hàng và các thao tác sửa giỏ khác khi số lượng đang lưu. Tập `productId` khách chọn để đặt đơn lưu trong `sessionStorage` theo danh tính, mặc định rỗng và được loại bỏ khi mặt hàng biến mất khỏi giỏ; lựa chọn này chỉ phục vụ giao diện, backend luôn kiểm tra lại giỏ.

### 3. Giao dịch đặt hàng trên Oracle

Service dùng `@Transactional` cho toàn bộ bước sau; mọi câu SQL đều dùng biến bind.

```text
Xác thực khách hàng
  -> chuẩn hóa nội dung và tìm đơn đã lưu theo Idempotency-Key
  -> khóa CARTS(user_id) FOR UPDATE, tìm lại đơn theo khóa
  -> đọc CART_ITEMS, đối chiếu cartVersion và các expectedLines đã chọn
  -> khóa PRODUCTS theo product_id tăng dần, đọc giá và trạng thái
  -> khóa PRODUCT_INVENTORY theo product_id tăng dần
  -> kiểm tra trạng thái ACTIVE, giá và tồn kho có thể bán
  -> INSERT ORDERS(PENDING, NORMAL, order_code, mã băm yêu cầu)
  -> INSERT ORDER_ITEMS(thông tin tại thời điểm mua)
  -> UPDATE PRODUCT_INVENTORY có điều kiện, tăng version
  -> DELETE các CART_ITEMS đã mua; tăng CARTS.version
  -> COMMIT
```

Thứ tự lấy khóa do TV4 kiểm soát là **`CARTS` → `PRODUCTS` theo `product_id` tăng dần → `PRODUCT_INVENTORY` theo `product_id` tăng dần**. Các thao tác sửa giỏ chỉ giữ khóa `CARTS`. F2 sửa thông tin sản phẩm lấy khóa `PRODUCTS`; F2 sửa tồn kho cập nhật `PRODUCT_INVENTORY` với điều kiện `version`, hiện không giữ cả hai khóa cùng lúc. Nếu F2 đã ghi trước, checkout đọc lại giá/tồn; nếu checkout ghi trước, F2 thấy `version` đổi và báo xung đột. TV5 cần đối chiếu thứ tự khóa của F9 trước khi thử đồng thời với Flash Sale; không sửa F9 trong change này. Hủy đơn khóa `ORDERS` rồi mới đến Inventory, không khóa Cart/Product, nên không tạo vòng khóa với checkout theo luồng hiện tại.

Giữ khóa `PRODUCTS` đến khi giao dịch hoàn tất để giá và trạng thái không đổi sau khi kiểm tra. Khóa Inventory và giảm hàng bằng câu `UPDATE` có điều kiện `on_hand_quantity - reserved_flash_quantity >= :quantity`, đồng thời tăng `version`; không sửa `reserved_flash_quantity`. Các dòng được đọc và cập nhật theo cùng thứ tự ID. Nếu giỏ chưa tồn tại hoặc trống, trả lỗi nghiệp vụ trước khi tạo đơn. Nếu giỏ đã đổi phiên bản, trả `CART_CHANGED`; nếu giá hoặc trạng thái thay đổi, trả lỗi tương ứng và yêu cầu khách xem lại. Sau khi xóa các dòng đã mua, kiểm tra số dòng bị xóa và tăng phiên bản giỏ để tab cũ không thể ghi bằng dữ liệu trước lúc đặt hàng.

`GET /api/checkout/preview` tiếp tục trả toàn bộ giỏ để khách xem dữ liệu Oracle hiện tại. CheckoutPage lọc theo các ID đã chọn, tính tổng và cảnh báo của đúng các dòng này; nếu chưa chọn hoặc dòng đã chọn không còn trong giỏ thì không cho xác nhận. `POST /api/orders` nhận `expectedLines` là tập con không rỗng của giỏ, đã sắp theo ID. Trong khóa `CARTS`, server kiểm tra từng dòng đã chọn có cùng số lượng; các dòng không chọn không bị khóa Product/Inventory, không bị trừ tồn và vẫn nằm trong giỏ sau `COMMIT`. `cartVersion` vẫn phát hiện mọi thay đổi giỏ kể cả ở dòng không chọn. Giá và tồn của dòng được chọn luôn được kiểm tra lại trong giao dịch ghi.

Tiền dùng `BigDecimal`: giá từ `PRODUCTS.base_price`, số lượng nguyên dương, `line_total = unit_price × quantity`, `subtotal = tổng line_total`, `discount_amount = 0`, `total_amount = subtotal`; chuẩn hóa về hai chữ số thập phân, kiểm tra giới hạn `NUMBER(15,2)` và không làm tròn âm thầm. Dùng tên, SKU và giá Oracle để lưu vào `ORDER_ITEMS`. Tạo `order_code` dạng `SF-` cộng UUID bỏ dấu gạch ngang (35 ký tự, vừa `VARCHAR2(40)`); ràng buộc `uk_orders_code` là bảo vệ cuối cùng. Nếu hiếm khi trùng mã đơn, rollback toàn bộ rồi server tạo mã mới khi thực hiện lại yêu cầu với cùng `Idempotency-Key`; phân biệt lỗi này với va chạm `uk_orders_idempotency`. Luồng này chỉ tạo đơn `NORMAL`.

Thời gian chờ khóa Oracle được giới hạn (mặc định ba giây, ghi rõ trong cấu hình test); deadlock hoặc hết thời gian chờ được phân loại thành lỗi có thể thử lại sau khi giao dịch đã rollback. Không tự lặp lại một phần các lệnh ghi trong cùng transaction. Với checkout, client giữ cùng khóa và nội dung khi thử lại; với sửa giỏ, client tải giỏ mới trước khi quyết định gửi thao tác khác.

Không dùng Redis làm nguồn tồn kho cho đơn thông thường: Oracle đã giữ dữ liệu gốc, các ràng buộc quan hệ và khả năng hoàn tác nhiều bảng. Dự án không dùng giao dịch phân tán XA giữa Oracle và Redis. Nếu sau này F9 cần tạo đơn Flash Sale, TV5 sẽ quyết định cách giữ suất và bù trừ khi thất bại; hai bên cần thống nhất điểm gọi nghiệp vụ tạo đơn riêng.

### 4. Nhận diện yêu cầu gửi lại và kết quả giao dịch chưa rõ

Migration mới thêm `ORDERS.checkout_request_hash VARCHAR2(64)`, cho phép `NULL` đối với đơn đã có từ V1 hoặc dữ liệu mẫu. Trước khi băm, server kiểm tra `Idempotency-Key` là UUID dạng chuẩn, chuyển về chữ thường; chuẩn hóa chuỗi nhận hàng bằng Unicode NFC và bỏ khoảng trắng hai đầu, giữ nguyên khoảng trắng bên trong; kiểm tra độ dài theo cột Oracle. `expectedLines` phải có `productId` duy nhất, `quantity` dương; sắp theo `productId` và chuyển `unitPrice` thành chuỗi thập phân đúng hai chữ số, từ chối giá có phần lẻ không biểu diễn được bằng `NUMBER(15,2)`. Băm SHA-256 của bản mã hóa có thứ tự trường cố định gồm phiên bản hợp đồng, `userId`, `cartVersion`, thông tin nhận hàng và các dòng đã chuẩn hóa. Cùng nội dung nhưng thứ tự dòng khác vẫn có cùng mã băm; nội dung khác không được dùng lại khóa cũ.

Hệ thống tìm đơn theo khóa trước và sau khi khóa giỏ. Nếu đã có đơn của cùng `userId` với cùng mã băm, trả lại `orderId`/`orderCode` dù giỏ nay đã trống. Nếu khóa thuộc người khác, nội dung khác hoặc đơn cũ không có hash, trả `409` chung mà không lộ thông tin đơn. `uk_orders_idempotency` của V1 bảo vệ trường hợp hai request đồng thời: nếu insert trùng khóa, transaction hiện tại rollback; một giao dịch đọc mới kiểm tra lại đơn đã commit rồi trả kết quả hoặc báo thử lại bằng chính khóa cũ. Không tiếp tục sau lỗi unique trong transaction bị đánh dấu rollback. Nếu kết nối mất ngay lúc `COMMIT`, **không khẳng định đã rollback**; client gửi lại cùng khóa và nội dung để xác định kết quả. Lưu hash trên order giữ bằng chứng cùng giao dịch Oracle, không phụ thuộc Redis.

`CheckoutPage` tạo một UUID mới khi khách bấm xác nhận, đóng băng chính body đã xác nhận và ghi `{principalUserId, idempotencyKey, body}` vào `sessionStorage` **trước khi** gửi `POST /api/orders`. Khi hết thời gian chờ, mất mạng, nhận lỗi mà chưa xác định được trạng thái `COMMIT` hoặc tải lại trang trong cùng tab, trang khôi phục bản ghi này, không tạo khóa mới, không dựng lại body từ giỏ/giá vừa tải và không cho tạo yêu cầu đặt hàng khác. Giao diện cho khách gửi lại **đúng khóa và body cũ** để backend trả đơn đã lưu hoặc thực hiện lại sau rollback. Chỉ xóa bản ghi khi đã nhận kết quả đơn có `orderId` hoặc lỗi nghiệp vụ xác định yêu cầu không được commit; lúc đó, nếu muốn sửa thông tin, khách xem trước lại rồi tạo khóa mới. Nếu `/api/auth/me` trả danh tính khác bản ghi đang chờ hoặc JWT đã hết hạn, trang chặn gửi lại cho đến khi đăng nhập đúng tài khoản; không xóa bản ghi chưa rõ kết quả. `sessionStorage` duy trì qua lần tải lại cùng tab; đóng tab trước khi giải quyết thì phải kiểm tra lịch sử đơn trước khi bắt đầu yêu cầu mới. Không tự retry thao tác cộng hàng vào giỏ theo cơ chế này.

### 5. Xem lịch sử và hủy đơn hàng

Truy vấn danh sách và chi tiết luôn lọc `user_id` ngay trong SQL. `page` bắt đầu từ 1, `size` mặc định 20 và tối đa 100; trạng thái lọc nằm trong bốn giá trị của V1. Danh sách sắp `created_at DESC, order_id DESC`, dùng bind variable cho `OFFSET`/`FETCH` và câu đếm áp dụng cùng bộ lọc. Trang vượt cuối trả danh sách rỗng. Chi tiết đọc tên, SKU và giá đã lưu trong `ORDER_ITEMS`, không lấy giá hiện tại từ `PRODUCTS`. Khi hủy, hệ thống khóa đơn theo `order_id` và `user_id`, kiểm tra loại `NORMAL` và trạng thái `PENDING`, rồi khóa Inventory theo `product_id` tăng dần. Tăng `on_hand_quantity`, tăng `version`, đổi trạng thái thành `CANCELLED` và ghi `cancelled_at` trong cùng `@Transactional`. Khóa đơn ngăn hai yêu cầu hoàn tồn kho hai lần; lỗi giữa chừng rollback toàn bộ. Không đưa sản phẩm về giỏ. API này từ chối đơn `FLASH_SALE`; việc hoàn suất Flash Sale thuộc TV5.

`OrderStatusTag` hiện dùng `PAID/SHIPPED`, không khớp các trạng thái mà Oracle cho phép. Giao diện Transaction phải hiển thị đúng `PENDING/CONFIRMED/COMPLETED/CANCELLED`. Muốn sửa component dùng chung trong `shared/design-system` cần trao đổi với người phụ trách trước; có thể hiển thị nhãn trạng thái ngay trong module Transaction. Giao diện dùng `apiClient` và `PriceTag`; `useAuth` lấy tài khoản từ `/api/auth/me` và backend vẫn quyết định quyền theo JWT cùng trạng thái Oracle hiện tại.

### 6. Thí nghiệm và đo hiệu năng

**Kiểm thử cơ chế: tính đúng đắn là trọng tâm.** JUnit 5 trên Oracle/Redis thật kiểm tra `COMMIT` thành công, `ROLLBACK` sau lỗi cố ý đặt giữa các lệnh ghi, trạng thái sau khi client mất kết nối, tranh chấp và tính cô lập. Với Oracle `READ COMMITTED`, dùng hai kết nối để quan sát phiên B trước/sau `COMMIT` của phiên A, đồng thời thử hai phiên cùng sửa giỏ hoặc cùng mua lượng hàng cuối, hết thời gian chờ khóa và deadlock có kiểm soát. Với Redis dùng các khóa `txlab:*` để thử `MULTI/EXEC` thành công, `DISCARD` trước `EXEC`, lỗi lúc `EXEC`, `WATCH` xung đột và Lua lỗi sau lệnh ghi. `DISCARD` không hoàn tác lệnh đã chạy; Lua không bị lệnh khác chen ngang nhưng lỗi runtime không tự hoàn tác ghi trước đó. Mỗi trường hợp lưu mã kịch bản, lệnh/bước gây lỗi, trạng thái dữ liệu **trước**, trạng thái **mong đợi**, trạng thái **thực tế sau** và kết quả assertion kèm tham chiếu truy vấn/lệnh hoặc log kiểm chứng; ghi riêng quan sát ở phiên B trước và sau `COMMIT` khi thử isolation. Oracle được hậu kiểm trên **kết nối độc lập sau khi giao dịch kết thúc**; Redis được đọc lại từ client riêng. Không đặt cả bài test trong một transaction tự rollback khi test kết thúc. Các bài thử tranh chấp dùng `CountDownLatch`/`CyclicBarrier` và điểm dừng chỉ bật trong test để xác nhận phiên A đã lấy khóa hoặc đã `WATCH` trước khi cho phiên B tiếp tục; kiểm tra thời hạn chờ, giải phóng khóa trong `finally` và chờ các luồng kết thúc. Không dùng `Thread.sleep` để đoán thứ tự thực thi. Với F2, bài test có thể giữ khóa bằng kết nối JDBC riêng rồi gọi API F2 hiện có, không cần sửa module Storage.

**Công cụ đo.** Java runner nhỏ dùng Oracle JDBC và Jedis đã có trong `backend/pom.xml`, đo bằng `System.nanoTime()` và xuất CSV/JSON. Một lớp JUnit 5 gọi runner để dùng classpath của Maven mà không thêm framework benchmark; chỉ chạy khi gọi tên test benchmark. k6 CLI đo riêng API HTTP của Cart, Checkout, Order và cả chuỗi thao tác qua API. Repo chưa có k6 trong dependency và máy khảo sát chưa thấy lệnh `k6`; README thí nghiệm phải nêu `k6 version` là bước kiểm tra trước khi chạy. Chỉ công bố số liệu đã đo thật.

**Mặc định và cách tính.** Mỗi kịch bản dùng giỏ 1 hoặc 5 sản phẩm, mức đồng thời 1 hoặc 10; 50 thao tác làm nóng rồi đặt lại dữ liệu trước năm lượt đo, mỗi lượt 200 thao tác. Đặt lại cùng tập đầu vào trước từng lượt; cố định số sản phẩm/khách/giỏ, giá, tồn kho ban đầu, nội dung yêu cầu, số kết nối/pool, bản image, cấu hình Oracle/Redis (kể cả AOF), phần cứng và tải nền, rồi lưu cùng kết quả. Mỗi mẫu sau giai đoạn làm nóng được phân loại `success`, `rejected` (từ chối nghiệp vụ dự kiến), `expected_failure` (lỗi được tiêm có chủ đích), `error` (lỗi ngoài dự kiến) hoặc `invalid` (thiếu mốc thời gian); giữ mọi nhóm trong dữ liệu thô. **Chỉ `success` có thời gian đo hợp lệ** tham gia p50/p95 (ms) gộp từ năm lượt và thông lượng chính. Thông lượng thành công = số giao dịch/yêu cầu thành công chia cho thời gian thực của **toàn lượt đo**, kể cả thời gian dành cho yêu cầu bị từ chối hoặc lỗi; DB dùng transactions/second, HTTP dùng requests/second. Thống kê độ trễ và số lượng `rejected`/`expected_failure`/`error` riêng, không gộp vào phân vị thành công; đếm `invalid` riêng. Mẫu hoàn tất = `success` + `rejected` + `expected_failure` + `error`; error rate = `error` / mẫu hoàn tất × 100%, rejection rate = `rejected` / mẫu hoàn tất × 100%; `409` được tạo có chủ đích thuộc `rejected`, còn `expected_failure` có số lượng riêng. Nếu hậu kiểm phát hiện vi phạm bất biến, mẫu liên quan không được coi là thành công và số vi phạm được báo cáo. Báo cáo trung bình và độ lệch chuẩn giữa năm lượt của p50/p95 theo lượt, thông lượng và các chỉ số có đủ mẫu; nêu lượt không có mẫu thành công thay vì tính phân vị giả. Ghi số thành công/từ chối/lỗi, trạng thái sau `COMMIT`/`ROLLBACK` và số vi phạm bất biến.

Với kịch bản HTTP nối nhiều API, một thao tác đo là một lượt chạy trọn luồng và có thể gồm nhiều yêu cầu HTTP; ghi cả số lượt luồng lẫn số yêu cầu thực tế. k6 gắn nhãn riêng cho từng API Cart/Checkout/Order và `outcome`, đồng thời đo thời gian toàn luồng bằng metric riêng. Báo cáo p50/p95 và thông lượng thành công **theo từng API** (requests/second) và **theo toàn luồng** (flows/second) ở các cột riêng. Một luồng chỉ là `success` khi mọi bước bắt buộc thành công; nếu có bước từ chối hoặc lỗi, thời gian và kết quả toàn luồng nằm trong nhóm tương ứng. Không dùng `http_req_duration` tổng hợp mọi API để thay thế độ trễ luồng hoặc độ trễ Checkout.

CSV/JSON mẫu đo đã chuẩn hóa có ít nhất: `run_id`, `scenario`, `dbms_or_http`, `api_or_flow`, `cart_size`, `concurrency`, `iteration`, `started_at`, `duration_ms`, `outcome`, `error_code` và mã fixture. Giữ riêng JSON thô do k6 xuất; chương trình tổng hợp chuyển dữ liệu đó sang cùng định dạng mẫu đo. Tệp tổng hợp có số mẫu theo từng nhóm kết quả, p50/p95 và thông lượng **thành công**, error rate, rejection rate, độ trễ các nhóm còn lại và số vi phạm bất biến. Bảng báo cáo đặt hai nhóm cơ sở dữ liệu và HTTP ở hai phần riêng, với cột kịch bản, hệ hoặc API/luồng, số mặt hàng, mức đồng thời, p50, p95, thông lượng thành công, tỷ lệ lỗi/từ chối, lỗi tiêm chủ đích và vi phạm bất biến. Không so sánh trực tiếp độ trễ/thông lượng giữa hai lớp đo; chỉ điền số từ kết quả đã đo.

**Công bằng và cô lập.** Runner Oracle/Redis dùng cùng thao tác logic của một yêu cầu đặt hàng mô phỏng: đọc/kiểm tra giỏ và tồn có thể bán, ghi đầu đơn và 1 hoặc 5 dòng, giảm tồn, dọn giỏ; cùng dữ liệu đầu vào, giá/số lượng, số khóa hoặc hàng tương ứng và mức đồng thời. Đo từ lúc runner bắt đầu thao tác logic đến khi nhận kết quả và hoàn tất thao tác tương ứng, bao gồm các bước kiểm tra và mã mô phỏng cần thiết ở mỗi hệ; công bố số lệnh, lượng dữ liệu, các ràng buộc Oracle và kiểm tra mà Redis phải mô phỏng bằng mã ứng dụng. Không chuẩn hóa được hoàn toàn ràng buộc quan hệ, ngữ nghĩa nguyên tử/hoàn tác, độ bền theo cấu hình AOF/Oracle và chi phí client/network; ghi những khác biệt này cạnh số đo, không suy diễn thành phép so sánh ACID tương đương. Dữ liệu dùng cho từng yêu cầu đo phải độc lập, trừ kịch bản cố ý tranh chấp; không cho 10 khách đồng thời vô tình mua cùng một giỏ. Script `setup`, `reset`, `cleanup` chỉ tác động đến user/order/cart/inventory được đánh dấu cho thí nghiệm và khóa `txlab:*`. Reset trước mỗi lượt và sau warm-up, không sửa seed nền hoặc khóa `flash:*`, `product:*`, `order:analytics:*`, `order:line:*`. Redis là đối chứng cơ chế, **không phải** cách triển khai đặt hàng của ShopFlash. Bảng kết quả DB và HTTP phải tách riêng; số đo không chứng minh hai hệ có cùng ACID hay cùng nghiệp vụ.

**Lệnh cần ghi trong `report/transaction/README.md` khi triển khai:**

```bash
docker compose up -d --wait
cd backend
./mvnw -version
./mvnw test -Dtest='CartOracleIT,CheckoutOracleIT,OrderOracleIT,TransactionSemanticsIT,RedisTransactionIT'
./mvnw test -Dtest='TransactionDbBenchmarkIT'
cd ..
k6 version
k6 run --vus 1 --iterations 200 --out json=report/transaction/results/http-raw.json report/transaction/k6/cart-checkout-order.js
```

`AuthOracleIT`, `CartOracleIT` và fixture Transaction đã có; các lớp Checkout/Order/semantics/benchmark, script k6 và tệp kết quả ở ví dụ trên là **đầu ra còn phải tạo**. README sẽ có lệnh cho toàn bộ bốn tổ hợp kích thước giỏ/mức đồng thời, thao tác warm-up 50 lần và năm lượt đo, không chỉ lệnh minh họa một lượt. Tệp JSON của k6 chứa mẫu đo thô; chương trình tổng hợp của thí nghiệm tạo bảng CSV/JSON. Lưu cùng dữ liệu thô các script, cấu hình môi trường/kết nối, phiên bản image và bảng chứng cứ trước/mong đợi/sau của từng thí nghiệm; không đưa mật khẩu/JWT vào artifact. `backend/pom.xml` có `spring-boot-starter-test` (JUnit 5), Oracle JDBC và Jedis; chưa cấu hình Failsafe, nên gọi `*IT` bằng `-Dtest` với Maven Surefire. Flyway chạy `db/migration` mặc định, profile `dev` mới thêm `db/dev-seed`; benchmark dùng fixture riêng, không phụ thuộc `dev` seed. Phần báo cáo Transaction theo đúng thứ tự của `report/AGENTS.md`: Lý thuyết → Oracle (demo và số đo) → Redis (demo và số đo) → Bảng so sánh → Kết luận theo ShopFlash; công bố trung bình/độ lệch qua năm lượt và dẫn nguồn cho nhận định kỹ thuật. Cú pháp `--vus`, `--iterations` và `--out json` được đối chiếu với [tài liệu k6](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/) và [JSON output](https://grafana.com/docs/k6/latest/results-output/real-time/json/).

### 7. Nghiệm thu chức năng theo mốc

Sau F0/F10, F7 và F8, TV4 chạy kiểm thử backend trên Oracle thật và kiểm thử end-to-end bằng frontend trên host qua proxy Vite đến backend, dùng JWT của tài khoản fixture. Bài kiểm tra đi từ thao tác giao diện đến phản hồi API và trạng thái Oracle sau `COMMIT` trên kết nối độc lập; không coi `npm run build` hoặc chỉ gọi API là bằng chứng end-to-end. Thử các đường thành công, lỗi nghiệp vụ và đăng nhập/đăng xuất liên quan đến mốc đó. Người yêu cầu kiểm thử chức năng trên giao diện sau mỗi mốc lớn; khi luồng hoạt động đúng mới chuyển mốc tiếp theo. Nếu không có trình duyệt điều khiển được để chạy tự động, cung cấp checklist chức năng cụ thể và ghi rõ phần chưa được tự động xác minh.

Bố cục, màu sắc, khoảng cách và kiểm tra hình thức ở 375px được gom vào lần rà soát cuối theo phản hồi của người yêu cầu. Lỗi giao diện khiến luồng không thể thao tác hoặc kết quả không rõ ràng vẫn thuộc nghiệm thu chức năng của mốc hiện tại.

Tài liệu cơ chế: [Oracle Database Concepts 26ai](https://docs.oracle.com/en/database/oracle/oracle-database/26/lnpcb/database-concepts.html), [Redis Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/), [Redis Lua scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/). Thiết kế F0 tham chiếu [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) và [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Risks / Trade-offs

- [Thêm F0 làm thay đổi phần dùng chung] -> TV5 đã bàn giao `shared/auth`; giới hạn chỉnh sửa vào `common/security`, `shared/auth`, `shared/api/client.js` và phần tích hợp Transaction. Test toàn bộ API `/api/admin/**` cùng Cart/Checkout/Order để phát hiện route bị chặn nhầm; không thay đổi controller nghiệp vụ của thành viên khác.
- [JWT bị lộ hoặc hết hạn] -> Secret ngoài repo; token có hạn dùng ngắn, chỉ lưu trong `sessionStorage` của tab và không ghi log. Nếu hết hạn khi Checkout chưa rõ kết quả, giữ idempotency request và yêu cầu đăng nhập lại đúng tài khoản trước khi gửi lại.
- [Điểm nối sản phẩm TV2 chưa có ID Oracle thật] -> Thử F10 bằng sản phẩm Oracle trên `/cart`; chỉ nối vào Listing/Detail sau khi TV2 có dữ liệu phù hợp, không sửa module TV2 trong change này.
- [Hai tab hoặc checkout cùng sửa giỏ] -> Khóa `CARTS` và kiểm tra `CARTS.version`; yêu cầu dùng phiên bản cũ nhận `409`, không tự ghi đè.
- [Mức cô lập `READ COMMITTED` cho phép dữ liệu thay đổi giữa các câu SQL] -> Khóa `PRODUCTS`/`PRODUCT_INVENTORY` theo thứ tự đã chốt và kiểm tra lại giá, trạng thái, tồn kho khi đã giữ khóa.
- [Deadlock hoặc hết thời gian chờ khóa khi F2/F9 cùng cập nhật hàng] -> Giữ transaction ngắn, giới hạn thời gian chờ, không retry mù bên trong transaction; kiểm thử xung đột F2 hiện có và phối hợp TV5 để đối chiếu thứ tự khóa F9 khi sẵn sàng.
- [Dữ liệu báo cáo hoặc tìm kiếm Redis có thể chậm cập nhật sau đặt/hủy đơn] -> Dùng Oracle để xác nhận kết quả nghiệp vụ; dùng chức năng dựng lại dữ liệu của TV3 khi cần đối chiếu và phối hợp với chủ module nếu cần cập nhật thường xuyên hơn.
- [Đơn cũ không có mã băm nội dung yêu cầu] -> Vẫn cho xem lịch sử nhưng không coi khóa của đơn cũ là bằng chứng để trả lại kết quả đặt hàng.
- [Cấu hình lưu bền của Redis khác cơ chế commit của Oracle] -> Ghi nhận và kiểm tra cấu hình AOF thực tế; không suy luận độ bền dữ liệu từ phép đo tốc độ.

## Migration Plan

1. Đã giữ nguyên `V1__schema.sql`, thêm `CARTS.version` và `ORDERS.checkout_request_hash` bằng V2, rồi kiểm tra Flyway trên schema riêng. Không sửa migration đã được Flyway ghi nhận hoặc tự xóa cột có dữ liệu.
2. Đã bàn giao F0/F10 cho TV4, triển khai JWT, Cart API và trang `/cart`. Hoàn tất kiểm thử chức năng từ frontend qua backend đến Oracle và để người yêu cầu thử luồng F0/F10 trước khi chuyển sang F7. Điểm nối trang TV2 được thực hiện khi TV2 có ID sản phẩm Oracle thật.
3. Triển khai Checkout rồi Order service/repository/controller và giao diện tương ứng; kiểm thử Oracle và end-to-end sau từng mốc, chờ người yêu cầu xác nhận luồng hoạt động trước mốc kế tiếp.
4. Chạy thí nghiệm bằng dữ liệu riêng, dọn đúng phạm vi và dùng chức năng dựng lại dữ liệu báo cáo của TV3 nếu demo cần số liệu mới. Cuối change mới rà soát hình thức giao diện theo phản hồi người yêu cầu.
5. Nếu quay lại phiên bản ứng dụng trước, mã cũ vẫn chạy với hai cột mới. Mọi chỉnh sửa schema tiếp theo dùng migration mới sau khi kiểm tra dữ liệu đã phát sinh.
