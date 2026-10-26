# F10 — Hợp đồng và cách dùng Cart API

Nguồn danh tính và lỗi chung: xem [CONTRACT.md](CONTRACT.md). Oracle lưu một giỏ trên mỗi khách (`uk_carts_user`); Redis không giữ giỏ. Mọi ví dụ dưới đây dùng JWT từ F0 trong `Authorization: Bearer <token>` và **không gửi `userId`**.

## Phản hồi và phiên bản

`GET /api/cart` trả `version`, `items` và `totalAmount`. Mỗi item có `productId`, `name`, `sku`, `quantity`, `unitPrice`, `lineTotal`. Giá là `PRODUCTS.base_price` hiện tại; `lineTotal = unitPrice × quantity`, `totalAmount` là tổng các dòng. Tiền dùng số thập phân Oracle/Java; giao diện hiển thị bằng `PriceTag`. Đây là **giá tham khảo**: Checkout sẽ kiểm tra lại giá, trạng thái và tồn kho. Thêm hoặc sửa giỏ không giảm `PRODUCT_INVENTORY` và không giữ suất Flash Sale.

Khi chưa có dòng `CARTS`, `GET` không ghi Oracle và trả:

```json
{"version":0,"items":[],"totalAmount":0.00}
```

Mọi API sửa giỏ yêu cầu header `X-Cart-Version` bằng `version` vừa đọc. Nếu giỏ đã đổi, API trả `409 CART_CHANGED`; tải lại giỏ rồi để người dùng quyết định thao tác tiếp. `quantity` là số nguyên từ **1 đến 999**; tổng sau khi cộng một mặt hàng cũng không được vượt 999.

## Năm API

| API | Nội dung gửi | Kết quả |
|---|---|---|
| `GET /api/cart` | Bearer token | Giỏ hiện tại; giỏ chưa có trả rỗng, `version=0` |
| `POST /api/cart/items` | `X-Cart-Version`, `{"productId":10001,"quantity":2}` | Tự tạo giỏ khi cần, cộng vào dòng đã có; trả giỏ với phiên bản tăng một lần |
| `PATCH /api/cart/items/10001` | `X-Cart-Version`, `{"quantity":3}` | Đặt số lượng tuyệt đối; trả phiên bản mới |
| `DELETE /api/cart/items/10001` | `X-Cart-Version` | Xóa một mặt hàng; trả phiên bản mới |
| `DELETE /api/cart/items` | `X-Cart-Version` | Xóa mọi mặt hàng nhưng giữ dòng `CARTS`; nếu giỏ đã rỗng thì phiên bản không đổi |

Ví dụ một giỏ rỗng, sản phẩm `10001` đang `ACTIVE` với giá `100.00`:

```http
POST /api/cart/items
Authorization: Bearer <token>
X-Cart-Version: 0
Content-Type: application/json

{"productId":10001,"quantity":2}
```

```json
{"version":1,"items":[{"productId":10001,"name":"Sản phẩm mẫu","sku":"SKU-10001","quantity":2,"unitPrice":100.00,"lineTotal":200.00}],"totalAmount":200.00}
```

Gửi lại `POST` với `X-Cart-Version: 1` và `quantity: 1` sẽ **cộng** thành 3, trả `version: 2`. `PATCH` cùng mặt hàng với `X-Cart-Version: 2` và `{"quantity":4}` sẽ **đặt** thành 4, trả `version: 3`. `DELETE /api/cart/items/10001` với phiên bản 3 trả giỏ rỗng, phiên bản 4. `DELETE /api/cart/items` trên giỏ rỗng với phiên bản 4 trả phiên bản 4. Ví dụ ID và giá chỉ minh họa hình dạng dữ liệu; `CartOracleIT` tạo ID fixture riêng khi chạy.

## Lỗi và gửi lại

| Tình huống | HTTP/mã lỗi | Hành động phía client |
|---|---|---|
| Token thiếu/sai, tài khoản không phải khách hợp lệ | `401 AUTH_REQUIRED` hoặc `403 ACCESS_DENIED` | Đăng nhập lại đúng tài khoản |
| `productId` không tồn tại, mặt hàng sửa/xóa không có trong giỏ | `404 PRODUCT_NOT_FOUND` hoặc `404 CART_ITEM_NOT_FOUND` | Tải lại giỏ hoặc chọn sản phẩm khác |
| Sản phẩm ngừng bán | `409 PRODUCT_UNAVAILABLE` | Chọn sản phẩm khác |
| Phiên bản cũ hoặc hai yêu cầu cùng tạo giỏ | `409 CART_CHANGED` | Tải lại giỏ; không tự lặp lệnh cộng |
| Hết thời gian chờ khóa hoặc deadlock | `409 LOCK_TIMEOUT` hoặc `409 DEADLOCK` | Tải lại giỏ trước khi quyết định gửi thao tác mới |
| Số lượng ngoài giới hạn, mã sản phẩm sai | `400 VALIDATION_ERROR` | Sửa dữ liệu đầu vào |

**Timeout hoặc mất phản hồi của `POST` cộng hàng không chứng minh giao dịch đã rollback.** Client tải lại giỏ và hiển thị kết quả mới nhất trước khi cho người dùng bấm thêm lần nữa. Nếu gửi lại nguyên lệnh cộng ngay, số lượng có thể tăng hai lần. `PATCH` là đặt giá trị tuyệt đối nhưng vẫn phải dùng phiên bản vừa đọc. Frontend giữ JWT trong `sessionStorage`, khôi phục hồ sơ qua `/api/auth/me` và tự gắn Bearer khi gọi Cart API.

## Kiểm thử

`CartCoreTest` kiểm tra ranh giới danh tính và phản hồi controller. `CartOracleIT` chạy với Oracle/Redis thật trên schema đã được Flyway áp V1/V2; test tạo fixture riêng, kiểm tra dữ liệu đã commit trên kết nối khác và tự dọn. Các case gồm giỏ chưa có, hai khách, giá hiện tại, thêm trùng, validate, sửa/xóa, hai lần tạo giỏ đồng thời, hai yêu cầu cùng phiên bản, lock timeout và đọc trong khi giao dịch khác chưa commit. Chạy đích danh:

```bash
cd backend
./mvnw test -Dtest=CartCoreTest
./mvnw test -Dtest=CartOracleIT
```

Schema `SHOP` local có bảng nhưng thiếu `flyway_schema_history` sẽ không chạy được bài integration test mặc định cho tới khi lịch sử schema được xử lý; không tự baseline hoặc xóa dữ liệu. Có thể dùng schema thử nghiệm riêng đã cấp quyền tương đương `SHOP` để Flyway tạo V1/V2 rồi chạy `CartOracleIT`.

Để thử thủ công trên `/cart`, dùng profile `dev` và đăng nhập `customer@shopflash.local`. Giỏ mẫu ban đầu có năm sản phẩm: `CORE-001` (ID 1), `TX-101` đến `TX-104` (ID 101–104). Có thể chọn nhanh ID trên CartPage hoặc nhập ID sản phẩm Oracle bất kỳ. `TX-105` (ID 105) còn bán nhưng tồn kho bằng 0 để thử Checkout từ chối; `TX-106` (ID 106) ngừng bán để thử Cart từ chối. `POST` làm tăng `CART_ITEMS.quantity`; `PATCH` đặt số lượng tuyệt đối; xóa giảm hoặc bỏ dòng giỏ. Các thao tác F10 này không đổi `PRODUCT_INVENTORY.on_hand_quantity`. Khi đặt đơn ở F7, Checkout mới trừ tồn kho theo số lượng được xác nhận; hủy đơn hợp lệ ở F8 mới hoàn tồn kho. Seed Transaction chỉ thêm dòng còn thiếu nên không tự đặt lại giá, tồn kho hoặc số lượng đã thay đổi; Flyway chỉ chạy lại seed khi file này đổi. Xem truy vấn đối chiếu và các bước thử trong [CART_UI_CHECKLIST.md](../../../../../../../../frontend/src/modules/transaction/CART_UI_CHECKLIST.md).
