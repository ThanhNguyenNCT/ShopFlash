# Module concurrency

Chủ đề: Concurrency control.

## Chức năng

- F9 Flash sale

F10 Giỏ hàng đã được bàn giao cho TV4 (`transaction`). F9 giữ trách nhiệm trạng thái và suất Flash Sale; khi tích hợp với Checkout, hai module cần thống nhất thứ tự khóa `PRODUCT_INVENTORY`.

## Thiết kế

Người phụ trách module tự chốt và ghi vào đây khi đã quyết định:

- Dữ liệu: bảng Oracle, key Redis.
- API công khai cho module khác (nếu có).
- Phụ thuộc vào module khác (nếu có).
