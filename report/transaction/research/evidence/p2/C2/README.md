# C2 — Redis cần validation tường minh

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:53:21 +07.
- Mục tiêu: đối chiếu CLI ghi dữ liệu sai với Lua validation trên cùng mô hình key.
- Setup: năm key fixture; `order` đã có trong manifest nhưng ban đầu vắng mặt.
- Execute: `redis/experiments/C2.sh invalid native01 12941` ghi `order.product_id=999999` (không có Inventory tương ứng) và `inventory:12941.on_hand=-5`. Gọi cùng script với `reference` rồi `negative` để chạy `lua/C2-validate.lua`.
- Expected/actual: CLI trả `2`, `0` và `native01-C2-after-cli.txt` thấy tham chiếu giả cùng tồn âm được lưu; Lua trả `ERR INVALID_PRODUCT_REFERENCE` và `ERR INVALID_STOCK` trong hai transcript riêng. `native01-C2-after-lua.txt` giống byte trạng thái sau CLI: validation không ghi thêm.
- Cleanup/verify: preview/reset sáu key trong `native01-C2-reset-preview.txt`, `native01-C2-reset.txt`; `native01-C2-after-reset.txt` giống đầu kỳ.
- Kết luận: Redis Hash không tự có FK/CHECK quan hệ; quy tắc nghiệp vụ phải được mã hóa và kiểm tra trước khi ghi.

## Giải thích kết quả

[CLI trả thành công](native01-C2-invalid.txt) khi ghi `product_id=999999` dù không có Inventory tương ứng, và khi đặt `on_hand=-5`. [Snapshot sau CLI](native01-C2-after-cli.txt) xác nhận cả hai giá trị sai nằm trong Redis. Redis Hash lưu field/value theo lệnh; nó không tự biết quan hệ FK giữa hai key hoặc quy tắc tồn không âm của ShopFlash.

Khi chạy [Lua kiểm tra tham chiếu](native01-C2-reference.txt) và [Lua kiểm tra tồn](native01-C2-negative.txt), script tự trả lỗi rồi dừng trước ghi. [Snapshot sau Lua](native01-C2-after-lua.txt) vẫn giống sau CLI: validation ngăn thao tác mới chứ không tự sửa dữ liệu sai đã tồn tại. Vì vậy ứng dụng phải đặt validation đúng chỗ trong mọi đường ghi Redis.
