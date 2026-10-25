# Ghi nhận bàn giao F10 cho TV4

Ngày 25/10/2026, người yêu cầu xác nhận đã có sự đồng ý để thực hiện task 1.2 của OpenSpec change `transaction-checkout-orders-study`. Theo xác nhận đó, TV4 đã cập nhật quyền sở hữu F10 và chuyển route `/cart` từ TV5 sang module `transaction`. Tài liệu này ghi lại phạm vi đã chuyển; việc gắn nút vào Product Listing/Detail vẫn cần phối hợp với TV2 ở task 2.6.

## Quyền sở hữu

| Phần việc | Người phụ trách đề xuất |
|---|---|
| F10 Cart API, bảng `CARTS`/`CART_ITEMS`, giao diện `/cart`, thành phần thêm vào giỏ | TV4, module `transaction` |
| F7 Checkout, F8 xem/hủy đơn | TV4, module `transaction` |
| F9 Flash Sale và phần giữ suất | TV5, module `concurrency` |
| Gắn thành phần thêm vào giỏ vào Product Listing/Detail | TV2, module `indexing`, sau khi nhận hợp đồng của TV4 |
| F0 thật | Người phụ trách `common/security` và `shared/auth`; TV4 chỉ tích hợp danh tính đã xác thực |

## File đã cập nhật trong task 1.2

1. `AGENTS.md` gốc và `frontend/AGENTS.md`: F10 và route `/cart` thuộc TV4; TV5 chỉ giữ F9.
2. `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md` và `frontend/src/modules/concurrency/AGENTS.md`: bỏ F10 và mô tả màn giỏ; giữ Flash Sale và điểm phối hợp tồn kho.
3. `backend/src/main/java/com/shopflash/modules/transaction/AGENTS.md` và `frontend/src/modules/transaction/AGENTS.md`: ghi F10 cùng F7/F8 và dẫn hợp đồng trong `CONTRACT.md`.
4. `frontend/src/modules/concurrency/routes.jsx`: bỏ khai báo `/cart`. `frontend/src/modules/transaction/routes.jsx` khai báo `/cart` một lần, lazy-load placeholder mới trong module TV4. `frontend/src/modules/concurrency/pages/CartPage.jsx` là placeholder cũ không còn được router tham chiếu; giữ nguyên file để không xóa lịch sử phần việc của TV5.
5. Placeholder `CheckoutPage` của TV4 đã đổi mô tả `GET /api/cart` sang module Transaction. Cart API và giao diện thật chưa được triển khai trong task này.

## Điểm tích hợp tiếp theo

TV2 cần thay mô tả/điểm gọi nút thêm vào giỏ hiện trỏ TV5 trong module `indexing` bằng thành phần/API TV4 cung cấp ở task 2.6; TV4 không tự sửa module `indexing` trong task bàn giao. TV5 và TV4 cần thống nhất thứ tự lấy khóa Inventory khi F9 được triển khai; F10 không giữ suất Flash Sale. F0 thật vẫn là phụ thuộc riêng.
