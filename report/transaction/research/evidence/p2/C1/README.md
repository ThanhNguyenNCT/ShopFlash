# C1 — Ranh giới ràng buộc Oracle

- Trạng thái: **PASS**, run `native01`, 2026-10-28; chỉ đọc metadata bằng `oracle/experiments/C1.sql` trong schema `SHOP`.
- Setup/execute/observe: fixture P1; lệnh `oracle/run-sql.sh native01 oracle/experiments/C1.sql`; output nguyên gốc `native01-C1-metadata.txt` gồm 29 constraint trên bốn bảng. Tất cả constraint được nêu dưới đây `ENABLED`, `VALIDATED`, `NOT DEFERRABLE`, `IMMEDIATE`.

| Quan hệ/quy tắc | DBMS tự bảo vệ theo metadata thực | Cần logic nghiệp vụ/conditional UPDATE |
|---|---|---|
| Order | `PK_ORDERS`; `FK_ORDERS_USER`; `UK_ORDERS_CODE`, `UK_ORDERS_IDEMPOTENCY`; `CK_ORDERS_TYPE/STATUS/SUBTOTAL/DISCOUNT/TOTAL/CANCELLED` | Xác nhận Cart thuộc user, request idempotency có cùng payload, giá hiện tại, tổng nhiều dòng khớp Order; constraint tổng chỉ kiểm tra các cột cùng Order. |
| Order Item | `PK_ORDER_ITEMS`; `FK_ORDER_ITEMS_ORDER/PRODUCT/FS_ITEM`; `CK_ORDER_ITEMS_PRICE/QUANTITY/TOTAL/LINE` | Chọn Product `ACTIVE`, snapshot giá và SKU đúng thời điểm, tổng các dòng khớp Order, chọn đúng dòng Cart được mua. |
| Product/Inventory | `PK_PRODUCTS`, `PK_PRODUCT_INVENTORY`, `FK_INVENTORY_PRODUCT`, `CK_PRODUCTS_PRICE/STATUS`, `CK_INVENTORY_ON_HAND/RESERVED/RES_LE_ON` | Không oversell tồn có thể bán thường: `UPDATE ... WHERE on_hand_quantity - reserved_flash_quantity >= :quantity` và kiểm tra row count; khóa hoặc điều kiện cập nhật phải áp dụng khi hai checkout cạnh tranh. |

- Expected/actual: tên, loại, cột và biểu thức CHECK trong bảng khớp `native01-C1-metadata.txt` và audit P0; không lặp lại bộ lỗi A2.
- Verify/cleanup: truy vấn chỉ đọc, snapshot/fixture không đổi; không có row/key cần dọn riêng.
- Kết luận: FK/CHECK bảo vệ cấu trúc và giá trị từng row; trạng thái sản phẩm, tính khớp Cart–Order và tồn khả dụng cần logic giao dịch có khóa/điều kiện.

## Giải thích kết quả

[Metadata Oracle](native01-C1-metadata.txt) cho biết DBMS tự giữ tính hợp lệ của những quan hệ đã khai báo. Ví dụ `FK_ORDER_ITEMS_ORDER` buộc mỗi Order Item trỏ tới một Order có thật; `CK_ORDER_ITEMS_QUANTITY` buộc số lượng dương; `CK_INVENTORY_RES_LE_ON` không cho reserved lớn hơn tồn vật lý. Bảng ma trận phía trên chỉ ra tên constraint và phần việc tương ứng, không lặp lại ba lỗi của A2.

Một constraint trên từng row không thể tự xác nhận khách đã chọn đúng dòng Cart, giá đặt đơn là giá mới nhất, hoặc tổng các dòng bằng tổng Order. Tồn có thể bán thường cũng là điều kiện theo thời điểm: cần `UPDATE ... WHERE on_hand_quantity - reserved_flash_quantity >= :quantity` trong transaction và kiểm tra số row được cập nhật. Đây là phần trách nhiệm của logic Checkout, dù schema đã có nhiều constraint.
