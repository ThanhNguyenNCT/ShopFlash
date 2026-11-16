# C2 — Consistency bằng guarded Checkout EVAL

- Bản hiện hành: revision-20261109, run `rev1109b`, năm lượt correctness. [Tổng hợp và cleanup](../../revision-20261109/README.md).
- [Lượt 1: input/output, before/after và assertion](../../revision-20261109/1-C2.txt); các lượt 2–5 tương ứng. Chạy `bash report/transaction/research/redis/experiments/C2.sh <run> <p1> <p2> <p3>`.
- Fixture chung được reset theo manifest trước từng case; Product Hash ACTIVE là phần bổ sung riêng C2, từ ID Product Oracle fixture, không dùng Inventory thay cho kiểm tra Product. KEYS trong EVAL phải khớp prefix/Product ID và đúng vai trò.
- 14 case lỗi độc lập: thiếu Product; Inventory không khớp Product; không đủ tồn; quantity 0, âm, lẻ; stock âm; version không phải số; reserved vượt stock; Cart mismatch; Inventory/Cart/Product sai kiểu; Order đã tồn tại. Từng mã từ chối được assertion và snapshot tất cả key của run (type/TTL/value) không đổi.
- Case hợp lệ: Order product_id=P1/quantity=1/PENDING, stock 99, version 1, reserved 0; Cart chỉ xóa P1, giữ P2/P3; quota và các key khác không đổi. Assertion so toàn snapshot với trạng thái mong đợi.
- Lua kiểm tra kiểu trước HGET, canonical integer trong [0,1e9], quantity dương, version còn tăng được, key không TTL, tham chiếu Product ACTIVE và Cart/Inventory trước ghi đầu tiên. Validation và mutation Checkout trong một EVAL, không client khác chen giữa.
- Direct mutation gắn nhãn FIXTURE_INJECTION chỉ để tạo tiền trạng thái lỗi; request Checkout chỉ gọi guard. Không validation sau khi ghi request sai. Không thêm schema/giá/tổng tiền/quota vào mô hình một mặt hàng này.
- Reset cuối xóa Product bổ sung và đưa năm key chung về baseline; cleanup cuối theo bảy key manifest. Redis không tự có FK/CHECK; Lua không full rollback khi runtime error sau ghi. Không chứng minh chặn direct writes của client khác hoặc OOM/crash.

---

## Hồ sơ cũ — SUPERSEDED

Các kết quả dưới đây chỉ mô tả phiên bản cũ; không dùng để nghiệm thu code hiện hành. Mọi transcript cũ giữ nguyên để truy vết.

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
