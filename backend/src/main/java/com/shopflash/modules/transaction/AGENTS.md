# Module transaction

Chủ đề: Transaction.

## Chức năng

- F10 Giỏ hàng
- F7 Đặt hàng
- F8 Lịch sử và hủy đơn

## Thiết kế

Hợp đồng API, lỗi, danh tính và thứ tự khóa: xem [CONTRACT.md](CONTRACT.md). Hướng dẫn local/test: xem [README.md](README.md). Việc bàn giao F10 từ TV5: xem [F10_HANDOFF.md](F10_HANDOFF.md).

Oracle quản lý `CARTS`, `CART_ITEMS`, `ORDERS`, `ORDER_ITEMS` và tồn kho nghiệp vụ. F10 không giữ hàng trong giỏ; F7 kiểm tra lại giá/trạng thái/tồn kho tại Checkout. TV5 giữ F9, TV2 gắn nút thêm vào giỏ trên trang sản phẩm sau khi nhận thành phần/API từ TV4.
