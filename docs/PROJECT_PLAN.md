# PROJECT PLAN – DATABASE MANAGEMENT SYSTEMS

## 1. Tổng quan đề tài

| Nội dung | Mô tả |
|---|---|
| **Tên đề tài** | **ShopFlash – Hệ thống bán hàng trực tuyến có Flash Sale** |
| **Application domain** | Retail / E-commerce |
| **DBMS nghiên cứu** | Oracle và Redis |
| **Số thành viên** | 5 |
| **Mục tiêu** | Xây dựng một ứng dụng bán hàng làm ngữ cảnh thực tế để nghiên cứu, demo và so sánh các chức năng chính của Oracle và Redis |
| **Application** | Web application |
| **Trọng tâm nghiệp vụ** | Quản lý sản phẩm, tìm kiếm, thống kê, giỏ hàng, đặt hàng và Flash Sale |

### Phân chia topic

| Thành viên | Topic phụ trách |
|---|---|
| **TV1** | Data Storage & Management + Data Backup & Recovery |
| **TV2** | Indexing |
| **TV3** | Query Processing |
| **TV4** | Transaction |
| **TV5** | Concurrency Control |

> Việc phân chia trên là theo **topic nghiên cứu**. Feature trong tài liệu là phạm vi tham chiếu; khi hiện thực có thể bổ sung hoặc điều chỉnh các chức năng hỗ trợ để hoàn chỉnh business flow và thể hiện topic tốt hơn.

Ứng dụng ShopFlash vừa là sản phẩm cuối cùng, vừa là môi trường để xây dựng các ví dụ, thử nghiệm và demonstration cho từng topic trên Oracle và Redis.

---

## 2. Mô tả nghiệp vụ ShopFlash

ShopFlash là hệ thống bán hàng trực tuyến, trong đó Customer có thể xem và tìm kiếm sản phẩm, quản lý giỏ hàng, đặt hàng, quản lý đơn hàng và tham gia các chương trình Flash Sale. Admin quản lý dữ liệu bán hàng, cấu hình Flash Sale và theo dõi báo cáo/thống kê.

### Actors

| Actor | Nghiệp vụ chính |
|---|---|
| **Customer** | Xem/tìm kiếm sản phẩm, lọc sản phẩm, quản lý giỏ hàng, đặt hàng, xem và quản lý đơn hàng, tham gia Flash Sale |
| **Admin** | Quản lý danh mục, sản phẩm và tồn kho; tạo/cấu hình Flash Sale; xem báo cáo và thống kê |

### Business flow tổng quát

```text
Customer
   │
   ├── Xem / tìm kiếm / lọc sản phẩm
   ├── Xem chi tiết sản phẩm
   ├── Thêm / cập nhật sản phẩm trong giỏ hàng
   ├── Đặt hàng
   ├── Xem / quản lý đơn hàng
   └── Mua sản phẩm trong Flash Sale

Admin
   │
   ├── Quản lý danh mục
   ├── Quản lý sản phẩm / tồn kho
   ├── Quản lý Flash Sale
   └── Xem báo cáo / thống kê
```

### Business rules chính

- Sản phẩm chỉ được mua khi đang ở trạng thái cho phép bán và còn số lượng khả dụng.
- Số lượng dành cho Flash Sale được tách khỏi phần tồn kho có thể bán qua checkout thông thường để tránh bán trùng cùng một lượng hàng.
- Giỏ hàng của Customer được lưu bền vững; giá và tình trạng sản phẩm phải được kiểm tra lại tại thời điểm checkout.
- Một checkout phải được xử lý nhất quán: nếu một bước quan trọng thất bại thì các thay đổi liên quan không được để lại trạng thái dữ liệu dở dang.
- Order lưu lại thông tin cần thiết tại thời điểm mua để lịch sử đơn hàng không bị thay đổi khi thông tin Product thay đổi sau này.
- Chỉ những Order đáp ứng điều kiện nghiệp vụ mới có thể bị hủy hoặc thay đổi trạng thái.
- Mỗi Flash Sale có thời gian hoạt động, chính sách chung và danh sách sản phẩm/quota riêng.
- Flash Sale có thể giới hạn số lượng mua trên mỗi Customer và sử dụng reservation trong khoảng thời gian ngắn trước khi hoàn tất Order.
- Khi nhiều request mua Flash Sale đồng thời, hệ thống phải tránh overselling, race condition và mất quota khi quá trình tạo Order thất bại.

---

## 3. Các nhóm chức năng dự kiến

Với nhóm 5 thành viên, ứng dụng cần có tối thiểu **10 functional requirements** không tính login/logout theo yêu cầu assignment.

Các feature dưới đây mô tả phạm vi chức năng chính, không phải danh sách đóng.

| ID | Nhóm chức năng | Actor | Mô tả |
|---|---|---|---|
| **F1** | Quản lý danh mục | Admin | Tạo, xem, cập nhật và quản lý trạng thái danh mục sản phẩm |
| **F2** | Quản lý sản phẩm và tồn kho | Admin | Tạo, xem, chỉnh sửa sản phẩm và cập nhật thông tin tồn kho phục vụ bán hàng |
| **F3** | Tìm kiếm sản phẩm | Customer | Tìm sản phẩm theo tên, SKU, danh mục hoặc các thông tin phù hợp |
| **F4** | Lọc và sắp xếp sản phẩm | Customer | Lọc theo nhiều điều kiện, sắp xếp và phân trang kết quả |
| **F5** | Báo cáo bán hàng | Admin | Tổng hợp doanh thu, số đơn và các chỉ số kinh doanh theo điều kiện/thời gian |
| **F6** | Thống kê dữ liệu kinh doanh | Admin | Thống kê sản phẩm, khách hàng, danh mục hoặc các nhóm dữ liệu phục vụ phân tích |
| **F7** | Đặt hàng | Customer | Kiểm tra Cart, giá, tình trạng sản phẩm và tồn kho trước khi tạo Order |
| **F8** | Quản lý đơn hàng | Customer | Xem danh sách, chi tiết và thực hiện các thao tác hợp lệ trên Order |
| **F9** | Mua hàng Flash Sale | Customer | Mua sản phẩm theo quota và policy của Flash Sale, xử lý cạnh tranh giữa nhiều request |
| **F10** | Quản lý giỏ hàng | Customer | Thêm, cập nhật, xóa và xem các sản phẩm dự kiến mua |
| **F11** | Quản lý Flash Sale | Admin | Tạo/cập nhật campaign, cấu hình policy, chọn sản phẩm, giá Flash Sale và quota |

> **Lưu ý:** F1–F11 chỉ là phạm vi tham chiếu. Một business flow có thể được tách thành nhiều feature nhỏ hơn hoặc bổ sung thêm functionality khi hiện thực.

---

## 4. Các màn hình chính

### 4.1. Customer

| Màn hình | Chức năng chính | Feature liên quan |
|---|---|---|
| **Home / Product Listing** | Hiển thị danh sách sản phẩm, khu vực Flash Sale và các thông tin bán hàng chính | F3, F4, F9 |
| **Product Detail** | Hiển thị thông tin chi tiết, giá, tình trạng bán và thao tác thêm vào Cart | F2, F10 |
| **Search / Filter Results** | Hiển thị kết quả tìm kiếm, lọc, sắp xếp và phân trang | F3, F4 |
| **Flash Sale** | Hiển thị campaign đang hoạt động, sản phẩm, giá sale, quota và thao tác mua | F9 |
| **Cart** | Xem, thêm, xóa và cập nhật số lượng sản phẩm trước khi checkout | F10 |
| **Checkout / Order Confirmation** | Kiểm tra lại giá, sản phẩm, số lượng và thông tin nhận hàng trước khi tạo Order | F7 |
| **My Orders** | Xem danh sách các Order của Customer | F8 |
| **Order Detail** | Xem snapshot chi tiết Order và thực hiện thao tác hợp lệ như hủy Order khi được phép | F8 |

### 4.2. Admin

| Màn hình | Chức năng chính | Feature liên quan |
|---|---|---|
| **Admin Dashboard** | Hiển thị tổng quan dữ liệu bán hàng và truy cập nhanh các khu vực quản trị | F5, F6 |
| **Category Management** | Xem, tạo, cập nhật và quản lý trạng thái danh mục | F1 |
| **Product Management** | Xem, tạo, chỉnh sửa sản phẩm và cập nhật tồn kho | F2 |
| **Flash Sale Management** | Tạo/cập nhật campaign, cấu hình thời gian/policy, chọn Product, giá sale và quota | F11 |
| **Reports / Statistics** | Hiển thị báo cáo doanh thu, số đơn, sản phẩm bán chạy và các thống kê phù hợp | F5, F6 |

### Điều hướng tổng quát

```text
Customer
Home
 ├── Product Detail
 ├── Search / Filter Results
 ├── Flash Sale
 ├── Cart
 │    └── Checkout / Order Confirmation
 └── My Orders
      └── Order Detail

Admin
Admin Dashboard
 ├── Category Management
 ├── Product Management
 ├── Flash Sale Management
 └── Reports / Statistics
```

---

## 5. Đối chiếu topic với nghiệp vụ

Mỗi topic được gắn với các business scenario phù hợp để tạo bối cảnh nghiên cứu và demo. Một feature có thể liên quan đến nhiều topic.

| Thành viên | Topic | Feature/nghiệp vụ tham chiếu | Trọng tâm nghiên cứu |
|---|---|---|---|
| **TV1** | **Data Storage & Management** | F1, F2 và dữ liệu nghiệp vụ cốt lõi | Cách Oracle và Redis tổ chức, lưu trữ và quản lý dữ liệu; relational model, Redis data model, persistence và constraint |
| **TV1** | **Data Backup & Recovery** | Dữ liệu nghiệp vụ của ShopFlash | Backup, restore/recovery và khả năng khôi phục dữ liệu trên Oracle và Redis |
| **TV2** | **Indexing** | F3 – Tìm kiếm, F4 – Lọc/sắp xếp | Oracle index và Redis search/index; ảnh hưởng tới read performance, write/storage cost và khả năng search/filter |
| **TV3** | **Query Processing** | F5 – Báo cáo, F6 – Thống kê | Query plan/optimizer, join, subquery, aggregate trên dữ liệu quan hệ và read model denormalized |
| **TV4** | **Transaction** | F7 – Checkout, F8 – Order, F10 – Cart | ACID, commit/rollback, consistency và xử lý business transaction nhiều bước |
| **TV5** | **Concurrency Control** | F9 – Mua Flash Sale, F11 – Quản lý/config Flash Sale | Race condition, overselling, atomic operation, reservation, concurrent update và xử lý nhiều request đồng thời |

Mỗi topic được nghiên cứu và demo trên **cả Oracle và Redis**, sau đó so sánh mức độ phù hợp của từng DBMS với workload tương ứng.

---

## 6. Đối chiếu yêu cầu thao tác dữ liệu của Assignment

| Yêu cầu Assignment | Feature có thể thể hiện | Ví dụ nghiệp vụ |
|---|---|---|
| **Insert** | F1, F2, F7, F11 | Tạo Category, Product, Order hoặc Flash Sale |
| **Delete** | F1, F2, F10 | Xóa dữ liệu khi business rule cho phép hoặc xóa Cart Item |
| **Update** | F1, F2, F8, F10, F11 | Cập nhật Category, Product, Inventory, Order, Cart hoặc Flash Sale |
| **Query – single condition** | F3, F8 | Tìm Product hoặc Order theo một điều kiện |
| **Query – composite condition** | F4 | Lọc Product theo nhiều tiêu chí |
| **Join** | F5, F6, F8 | Kết hợp User, Order, Order Item, Product và Category |
| **Subquery** | F5, F6 | Tìm hoặc thống kê dữ liệu dựa trên kết quả của truy vấn khác |
| **Aggregate** | F5, F6 | Doanh thu, số Order, số lượng bán, thống kê Product/Customer/Category |

Mapping trên dùng để đảm bảo phạm vi nghiệp vụ có khả năng đáp ứng assignment; query cụ thể sẽ được xác định theo schema và implementation thực tế.

---

## 7. Định hướng dữ liệu và phạm vi nghiên cứu

### Vai trò của hai DBMS

| DBMS | Vai trò chính trong application |
|---|---|
| **Oracle** | Source of truth cho dữ liệu nghiệp vụ bền vững: User, Category, Product, Inventory, Cart, Order và cấu hình Flash Sale |
| **Redis** | Hỗ trợ các workload phù hợp: product search/read projection, Flash Sale runtime state/reservation và analytics read model |

Chi tiết schema, field, constraint và Redis key/data structure được thiết kế ở phần database riêng.

### Phạm vi nghiên cứu

| Topic | Nội dung chính dự kiến |
|---|---|
| **Data Storage & Management** | Relational model và Redis data model, kiểu dữ liệu, persistence, constraint/validation và đặc điểm lưu trữ |
| **Data Backup & Recovery** | Backup, restore, recovery, durability và phạm vi dữ liệu có thể khôi phục |
| **Indexing** | Index/search mechanism, truy vấn có/không index, read performance, write/storage cost |
| **Query Processing** | Query execution, query plan, optimizer, join, subquery, aggregate và denormalized query model |
| **Transaction** | ACID, commit/rollback, transaction failure, idempotency và consistency của checkout/order |
| **Concurrency Control** | Concurrent access, race condition, overselling, atomic operation, reservation và synchronization giữa runtime state với durable data |

Hướng nghiên cứu chung:

```text
Lý thuyết
   ↓
Oracle
   ↓
Redis
   ↓
Demo / thử nghiệm
   ↓
So sánh
   ↓
Kết luận trong bối cảnh ShopFlash
```

> Thay đổi cấu trúc database trong quá trình phát triển được lưu theo migration version. Khi business feature thay đổi đáng kể, phần mô tả chung và phần thiết kế liên quan được cập nhật tương ứng.

---

## 8. Dữ liệu và kết quả mong đợi

Schema sẽ được thiết kế ở phần riêng dựa trên business requirements và nhu cầu thực tế của các module. Các thay đổi cấu trúc database trong quá trình phát triển được lưu theo **migration version**.

```text
Initial schema
   ↓
Migration 1
   ↓
Migration 2
   ↓
...
```

Khi feature hoặc business logic thay đổi đáng kể, phần mô tả liên quan trong tài liệu chung và tài liệu module tương ứng cần được cập nhật lại.

### Kết quả cuối cùng

| Kết quả | Nội dung |
|---|---|
| **DBMS Demonstration** | Demo và so sánh các topic trên Oracle và Redis |
| **Application** | ShopFlash chạy được ổn định trên ít nhất một DBMS và hỗ trợ các business flow chính |
| **Comparison** | Đánh giá ưu, nhược điểm và mức độ phù hợp của Oracle/Redis đối với từng workload |

Mục tiêu cuối cùng không phải chứng minh một DBMS luôn tốt hơn DBMS còn lại, mà là xác định **Oracle và Redis phù hợp với những dạng workload nào trong ShopFlash và vì sao**.

