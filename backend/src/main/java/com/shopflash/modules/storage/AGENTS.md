# Module storage

Chủ đề: Data storage & management.

## Chức năng

- F1 Quản lý danh mục
- F2 Quản lý sản phẩm

## Thiết kế

## Quyết định đã chốt

- **Oracle là nguồn dữ liệu chính.** CRUD F1/F2 dùng các bảng nền đã có: `CATEGORIES`, `PRODUCTS`, `PRODUCT_INVENTORY`. Không tạo lại các bảng này bằng migration của module.
- **Truy cập Oracle:** Storage dùng Spring Data JPA cho luồng CRUD; Flyway vẫn là nơi duy nhất sở hữu DDL. `CategoryEntity`, `ProductEntity`, `ProductInventoryEntity` ánh xạ ba bảng trên. `ProductInventoryEntity.version` dùng optimistic locking.
- **Redis là product search projection**, không phải nơi ghi nghiệp vụ. Key chuẩn là `product:{productId}` (RedisJSON), index dùng `idx:products` do foundation tạo. Document có `id`, `sku`, `name`, `categoryId`, `categoryName`, `price`, `status`, `inStock`, `thumbnailUrl`.
- Sau khi transaction Oracle thành công, Storage cập nhật product projection Redis. Thay đổi tên/trạng thái Category phải cập nhật projection của toàn bộ Product thuộc Category đó. Có API kiểm tra và dựng lại projection từ Oracle.
- Không đưa schema `EXP_STORAGE_*`, key `demo:storage:*`, hay thao tác Redis ghi dữ liệu lỗi từ thí nghiệm report vào application database.

## Quy tắc nghiệp vụ

- Category chỉ được xóa cứng khi không còn Product; ngược lại trả `409 CATEGORY_HAS_PRODUCTS`. Có thể chuyển Category sang `INACTIVE`.
- Không tạo hoặc chuyển Product sang `ACTIVE` nếu Category đang `INACTIVE`.
- Tạo Product đồng thời tạo `PRODUCT_INVENTORY` trong cùng transaction. SKU và slug là duy nhất; giá không âm.
- Storage chỉ thay `on_hand_quantity`; không thay `reserved_flash_quantity` do module `concurrency` quản lý. Không cho `on_hand_quantity < reserved_flash_quantity`.
- Product không hard-delete; chuyển `status = INACTIVE` để bảo toàn lịch sử Order. Projection của Product inactive giữ `status = INACTIVE`, `inStock = false`; tính năng search lọc Product active.

## API (v1)

Base CRUD: `/api/admin`.

```text
GET    /categories?page=&size=&q=&status=
POST   /categories
PUT    /categories/{id}
DELETE /categories/{id}

GET    /products?page=&size=&q=&categoryId=&status=
POST   /products
PUT    /products/{id}
PATCH  /products/{id}/inventory
```

Base kỹ thuật: `/api/admin/catalog`.

```text
GET  /projection/status
POST /projection/rebuild
GET  /storage-footprint
```

- `projection/status` so số Product Oracle và số document Redis.
- `projection/rebuild` dựng lại `product:*` từ Oracle; endpoint này chỉ dành cho admin.
- `storage-footprint` là phép đo chỉ đọc cho Storage Lab: trả riêng `USER_SEGMENTS` allocated bytes của Oracle và tổng `MEMORY USAGE` các product document Redis. Hai số được gắn nhãn rõ ràng, không được diễn giải là so sánh tổng dung lượng toàn DBMS.

## Giao diện

- `/admin/categories`: F1 CRUD Category.
- `/admin/products`: F2 CRUD Product và Inventory.
- `/admin/products/storage-lab`: Storage Lab, thể hiện E1 (constraint/validation) và E2 (storage footprint) dựa trên dữ liệu thật của ShopFlash. Không sửa `/admin/reports` hay `/admin/reports/lab` của module `query`.

## Phụ thuộc

- Cần thêm `spring-boot-starter-data-jpa` vào `backend/pom.xml`; đây là thay đổi dùng chung nên phải báo nhóm trước khi thực hiện.
- Module `indexing` dùng `idx:products` để search. Storage sở hữu dữ liệu ghi và product projection; mọi thay đổi schema/index Redis phải thống nhất với người phụ trách indexing.
