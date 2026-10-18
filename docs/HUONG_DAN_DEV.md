# Hướng dẫn phát triển ShopFlash

Tài liệu cho cả nhóm: cài một lần, chạy hằng ngày, và các bước để làm module của mình. Module `query` (TV3) đã làm xong, dùng làm ví dụ mẫu xuyên suốt.

Cách dùng app (cho người không code): [`HUONG_DAN_SU_DUNG.md`](HUONG_DAN_SU_DUNG.md).

## 1. Cài một lần

| Cần | Ghi chú |
|---|---|
| Docker Desktop | Cấp khoảng 5–6 GB RAM là đủ (Settings → Resources). Máy 16 GB nên tắt bớt ứng dụng khác khi chạy |
| JDK 21 | Ví dụ Temurin 21. Máy có cả JDK 17 cũng được: `dev.ps1` tự tìm JDK 21 trong `Program Files`; `dev.sh` dùng `JAVA_HOME` (macOS tự tìm bằng `java_home`) |
| Node.js 20 trở lên | Kèm npm |
| Git | Windows: bật `core.autocrlf` cũng được, `.gitattributes` đã giữ đúng kiểu xuống dòng cho script |

Kéo 2 image database (một lần; Oracle tải khoảng 3,7 GB, chiếm khoảng 10 GB ổ đĩa). Digest phải khớp `compose.yaml`; nếu nhóm đổi phiên bản thì lấy lệnh mới trong [`db/README.md`](../db/README.md#setup):

```bash
docker pull container-registry.oracle.com/database/free@sha256:f988b0c04c4c386cd306a2a914c0d7a9702d83acc31b064a28ad8eb6278a8fba
docker pull redis/redis-stack-server@sha256:798ab84d9f266936b034ab11c4d04a2b8e4b441884c5aa7d17ac951eefdf742a
```

## 2. Chạy hằng ngày

### Một lệnh

| Máy | Lệnh (chạy ở thư mục gốc repo) |
|---|---|
| Windows | `powershell -ExecutionPolicy Bypass -File scripts\dev.ps1` |
| macOS / Linux | `scripts/dev.sh` |

Script làm lần lượt các bước:
1. Kiểm tra Docker và image.
2. Tạo `.env` với mật khẩu ngẫu nhiên nếu chưa có.
3. Bật Oracle và Redis, tạo index Redis.
4. Kiểm tra cổng 8080 còn trống, rồi chạy backend (profile `dev`, có dữ liệu mẫu).
5. Chờ `GET /api/health` báo sẵn sàng.
6. Dựng read model Redis nếu đang trống.
7. Kiểm tra cổng 5173 còn trống, chạy frontend, mở http://localhost:5173.

Trên Windows, backend và frontend chạy ở hai cửa sổ riêng; đóng cửa sổ là dừng.

| Tùy chọn | Ý nghĩa |
|---|---|
| `-NoBackend` / `--no-backend` | Không chạy backend, ví dụ để chạy backend trong IntelliJ |
| `-NoFrontend` / `--no-frontend` | Không chạy frontend |
| `-Mock` / `--mock` | Chỉ chạy frontend với dữ liệu mẫu, không cần Docker hay backend |
| `-Stop` / `--stop` | Dừng Oracle và Redis để giải phóng RAM (dữ liệu vẫn giữ) |

### Chạy tay từng phần

```bash
docker compose up -d --wait
docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # cần JAVA_HOME trỏ tới JDK 21
cd frontend && npm install && npm run dev
```

### Địa chỉ

| Địa chỉ | Là gì |
|---|---|
| http://localhost:5173 | Cửa hàng (khách hàng) |
| http://localhost:5173/admin | Quản trị. Đăng nhập tạm: bấm "Vào với vai trò Admin" |
| http://127.0.0.1:8080/api/health | Backend còn sống không, kết nối được Oracle và Redis không |
| `localhost:1521/FREEPDB1`, user `SHOP` | Oracle (mở bằng SQL Developer / DBeaver, mật khẩu `ORACLE_APP_PASSWORD` trong `.env`) |
| `localhost:6379` | Redis (RedisInsight, mật khẩu `REDIS_PASSWORD` trong `.env`) |

Backend và database chỉ mở trên máy mình (`127.0.0.1`), máy khác cùng wifi không vào được. Lý do: chưa có đăng nhập thật (F0).

### Dữ liệu

| Dữ liệu | Cách có |
|---|---|
| Dữ liệu mẫu nhỏ: 2 user, 1 sản phẩm, 2 đơn | Tự nạp khi chạy profile `dev` (`dev-seed/R__00_core_seed.sql`) |
| Dữ liệu lớn: 100k đơn, 10k sản phẩm, 20k khách | Chạy tay, khoảng 1,5 phút: [`report/query/oracle/seed/README.md`](../report/query/oracle/seed/README.md) |
| Read model Redis cho báo cáo | Script tự dựng khi đang trống; hoặc trang "So sánh Oracle–Redis", tab 6, nút "Dựng lại" |

## 3. Cấu trúc repo

```text
shopflash/
├── scripts/dev.ps1, dev.sh        chạy toàn bộ một lệnh
├── compose.yaml                   Oracle + Redis (image ghim theo digest)
├── backend/
│   ├── src/main/java/com/shopflash/
│   │   ├── common/                dùng chung: RedisClient, format lỗi, /api/health
│   │   └── modules/<module>/      mỗi người một module (AGENTS.md = thiết kế đã chốt)
│   └── src/main/resources/db/
│       ├── oracle-init/           tạo user SHOP (container tự chạy)
│       ├── migration/             V1__schema.sql + migration của từng module
│       ├── dev-seed/              dữ liệu mẫu (profile dev)
│       └── redis/init.lua         index RediSearch
├── frontend/src/
│   ├── app/                       router (tự gom routes.jsx), providers
│   ├── shared/                    api client, auth (F0 tạm), layouts, design-system, ComingSoon
│   └── modules/<module>/          pages/, components/, api.js, routes.jsx
├── db/DATABASE_DESIGN.md          thiết kế dữ liệu chung
├── docs/                          tài liệu nhóm (file này)
└── report/<chủ-đề>/               báo cáo, demo, số đo của từng chủ đề
```

| Module | Người | Chức năng | Màn hình |
|---|---|---|---|
| `storage` | TV1 | F1 danh mục, F2 sản phẩm | `/admin/categories`, `/admin/products` |
| `indexing` | TV2 | F3 tìm kiếm, F4 lọc | `/`, `/search`, `/products/:productId` |
| `query` | TV3 | F5 doanh thu, F6 top/VIP | `/admin`, `/admin/reports`, `/admin/reports/lab` (đã xong) |
| `transaction` | TV4 | F7 đặt hàng, F8 đơn hàng | `/checkout`, `/orders`, `/orders/:orderId` |
| `concurrency` | TV5 | F9 flash sale, F10 giỏ hàng | `/flash-sale`, `/cart`, `/admin/flash-sales` |
| `backup` | TV1 | F11 sao lưu | `/admin/backups` |
| `shared/auth`, `common/security` | TV5 | F0 đăng nhập | `/login`, `/register` (đang là bản tạm) |

## 4. Làm module của bạn

Mọi màn hình đã có route, menu, layout và phân quyền. Màn hình chưa làm hiện trang "Đang làm" (component `ComingSoon`), ghi rõ người phụ trách, file cần sửa, việc cần làm và API gợi ý. Bạn chỉ cần làm theo các bước sau.

### Bước 1: chốt thiết kế

Ghi vào `backend/src/main/java/com/shopflash/modules/<module>/AGENTS.md`:
- bảng Oracle và key Redis sẽ dùng;
- API: đường dẫn, tham số, dạng response, mã lỗi;
- phụ thuộc vào module khác.

Ví dụ đầy đủ: `modules/query/AGENTS.md`. API gợi ý trên trang "Đang làm" chỉ là gợi ý; bạn chốt rồi ghi vào đây.

### Bước 2: database

| Việc | Ở đâu | Quy ước |
|---|---|---|
| Đổi schema Oracle | `backend/src/main/resources/db/migration/V<n>__<module>_<mô tả>.sql` | Lấy số kế tiếp lúc mở PR. Không sửa file đã có trên `main`. Khóa ngoại nào cũng có index. Ràng buộc trên một dòng thì dùng `CHECK` |
| Dữ liệu mẫu của module | `db/dev-seed/R__10_<module>_seed.sql` | Viết bằng `MERGE` để chạy lại được; dùng ID dưới 10000 |
| Index RediSearch mới | `db/redis/init.lua` + `db/DATABASE_DESIGN.md` | Trường dùng để `GROUPBY`/`SORTBY` phải `SORTABLE` |
| Key Redis | Ghi vào `AGENTS.md` của module | Prefix riêng, không trùng prefix của index khác |

### Bước 3: backend

Tạo package trong `modules/<module>/`:

```text
controller/   nhận request, gọi service, trả response (không có logic)
dto/request/  payload vào, kiểm tra bằng @Valid
dto/response/ payload ra (Java record)
service/      logic nghiệp vụ, transaction
repository/   truy cập Oracle (NamedParameterJdbcTemplate) và Redis (RedisClient)
```

- **Lỗi:** trong service chỉ cần `throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm")` hoặc `ApiException.badRequest("price", "Giá phải lớn hơn hoặc bằng 0")` (lớp `common/web/ApiException`). `GlobalExceptionHandler` đổi sang `{ "error": { code, message, details } }`; lỗi `@Valid`, body sai và lỗi Oracle đã được xử lý sẵn.
- **Redis:** inject `RedisClient redis` (bean ở `common/redis/RedisConfig`), không tự tạo kết nối. Lệnh `FT.SEARCH` / `FT.AGGREGATE` luôn kèm `TIMEOUT 0`.
- **SQL:** luôn dùng bind variable, không bao giờ nối chuỗi.

### Bước 4: frontend

1. **`src/modules/<module>/api.js`:** hàm gọi API, dùng `apiClient` từ `@/shared/api/client`. Lỗi đã được chuẩn hóa thành `{ status, code, message, details }`. Ví dụ: `src/modules/query/api.js`.
2. **Sửa đúng file trang** đang hiện "Đang làm", ví dụ `src/modules/storage/pages/AdminProductsPage.jsx`: thay `<ComingSoon .../>` bằng màn hình thật, giữ `export default`.
3. **Trong `routes.jsx` của module:** bỏ `status: 'todo'` để menu và trang Tổng quan đổi sang "Đã có".
4. **Cần biết ai đang đăng nhập** thì dùng `useAuth()` từ `@/shared/auth`.
5. **Giao diện:** dùng Ant Design, lấy màu và khoảng cách từ `@/shared/design-system` (xem `frontend/design-system.html`), không viết mã màu. Có trạng thái loading và lỗi, hỏi xác nhận trước khi xóa, danh sách phải phân trang.
6. **Tùy chọn:** viết `mocks/` để chạy `npm run dev:mock` khi backend chưa xong (ví dụ: `src/modules/query/mocks`).

Chi tiết route metadata và layout: `frontend/AGENTS.md`.

### Bước 5: test

| Loại | Lệnh |
|---|---|
| Unit test backend, không cần DB (đặt tên `*CoreTest`) | `cd backend && ./mvnw test -Dtest='*CoreTest'` |
| Test với DB thật (đặt tên khác, ví dụ `*IT`) | `./mvnw test -Dtest='<Tên>*'` |
| Build frontend | `cd frontend && npm run build` |
| Thử bằng tay | Chạy app, bấm qua màn hình của mình ở 1280 px và 375 px, mở DevTools xem không có lỗi đỏ |

Ví dụ test hợp đồng API chạy trên stack thật: `report/query/tests/contract_test.py`.

### Bước 6: đưa lên GitHub

1. Tạo nhánh từ `main`, ví dụ `feat/storage-products`, `fix/transaction-cancel`.
2. Chỉ sửa module của mình. Cần đổi `common/`, `shared/`, `init.lua` hay module khác thì báo người phụ trách trước và ghi rõ trong PR.
3. Commit theo kiểu `feat(storage): ...`, `fix(query): ...`, nội dung tiếng Việt.
4. Mở PR vào `main`, mô tả đã làm gì và test thế nào. Cần ít nhất 1 người review.

## 5. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Máy chậm, backend/Vite tự tắt | Thiếu RAM. Đóng bớt ứng dụng; `scripts\dev.ps1 -Stop` khi không dùng; giảm RAM của Docker xuống 5–6 GB |
| `Docker chưa chạy` | Mở Docker Desktop, chờ biểu tượng chuyển xanh |
| `Thiếu image ...` | Chạy đúng lệnh `docker pull` mà script in ra |
| `docker compose up` treo lâu | `docker compose logs oracle` rồi tìm dòng `shopflash`: mật khẩu trong `.env` phải bắt đầu bằng chữ cái, chỉ gồm chữ, số và `_` |
| `init.lua` báo `Incompatible Redis index` | Chạy lệnh `FT.DROPINDEX` có trong thông báo (document vẫn giữ), rồi chạy lại script |
| Backend báo `ORA-01017` | Mật khẩu `SHOP` trong `.env` vừa đổi: chạy `docker compose up -d --wait` để container cập nhật; đừng `export` biến môi trường trong shell vì nó đè lên `.env` |
| `release version 21 not supported` | Đang dùng JDK 17. Dùng `scripts/dev.ps1` (tự tìm JDK 21) hoặc đặt `JAVA_HOME` |
| `Cổng 8080/5173 đang bị chiếm` | Còn backend hoặc Vite cũ đang chạy (cửa sổ cũ, IntelliJ). Đóng nó, hoặc chạy lệnh `Stop-Process -Id <PID>` mà script in ra. Muốn dùng backend đang chạy trong IDE thì thêm `-NoBackend` |
| Git Bash: `mvnw` lỗi `ClassNotFoundException ... Launcher` | Đừng `export MSYS_NO_PATHCONV=1` khi chạy `mvnw` |
| Trang báo cáo Redis báo `REDIS_NOT_BUILT` hay số lệch | Dựng lại read model: trang So sánh, tab 6, hoặc `POST /api/admin/reports/analytics/rebuild` với body `{}` |
