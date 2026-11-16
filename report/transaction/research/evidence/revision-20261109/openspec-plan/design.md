# Design

## Context
Xem proposal.md. Harness SQL*Plus hiện dùng prompt marker nhưng chưa fail khi có lỗi SQL. B4 giữ connection A được, thiếu atomic branch. C2 chưa guarded. Fixture Redis chung có cart/3 inventory/quota, không có Product. OpenSpec cũ còn trong change chưa archive; change mới bổ sung hợp đồng thay thế ba case, giữ lịch sử.

## Goals / Non-Goals
**Goals:** Điều phối rõ, assertion dựa DBMS thật, snapshot đầy đủ và tái lập.
**Non-Goals:** Checkout production, đổi schema/Java hoặc đo TPS/latency.

## Decisions
- B2 tái sử dụng Harness bằng subclass riêng cho B2 để thu output/raise khi ORA/SP2, không đổi B1/B3. Read/decision bằng bind và PL/SQL; nhánh plain A UPDATE trước B SELECT, nhánh locked B SELECT trước A UPDATE. Kiểm tra chưa có completion trong cửa sổ 1,5s; WAIT 30 cho B đủ thời gian A hoàn tất. Sau COMMIT assert stock 98 và read/decision 100/1 hoặc 99/0; reset 100 và lấy khóa từ session khác. Không dùng stale overwrite hay timeout làm đối chứng.
- B4 hai redis-cli độc lập khởi động bằng thread barrier, so tập phản hồi thay vì thứ tự. Giữ connection A cho WATCH/HGET/MULTI/HSET/EXEC; giá trị HSET tính từ phản hồi thực. Snapshot ở các mốc và reset stock giữa hai nhánh.
- C2 dùng một mặt hàng của Cart ba dòng; Product Hash bổ sung chỉ cho C2 (product_id/status), Inventory key phải khớp prefix và Product ID. EVAL nhận cart/order/product/inventory, request id/quantity. Kiểm tra type trước HGET; số nguyên canonical trong miền an toàn <= 1e9 tránh overflow/float/HINCRBY lỗi sau ghi; version phải tăng được; không TTL trên key. Chỉ sau tất cả tiền điều kiện mới HSET Order, HSET inventory và HDEL Cart. Không dùng HINCRBY sau ghi khi còn khả năng lỗi parse. Quota không thuộc Checkout NORMAL này và giữ nguyên. So snapshot tất cả key của run gồm type/TTL/value trước/sau case lỗi. Dùng reset chung, thêm product/order vào manifest cho cleanup. Thay vì sửa setup chung, harness C2 tạo Product riêng sau setup và dọn bằng manifest.
- Evidence mới riêng revision-20261109, năm lần correctness lặp lại (không coi thời gian chờ là phép đo latency). Snapshot và assertion từng lần. Lưu fingerprint source ngoài scope để xác nhận không đổi. Dùng setup/reset/cleanup hiện có, không wildcard delete.

## Risks / Trade-offs
- [Chậm client bị hiểu là chờ khóa] → đảm bảo A giữ khóa đúng row, xác nhận B chưa hoàn tất và hoàn tất sau COMMIT; output lỗi làm fail.
- [Lua không rollback] → tiền kiểm kiểu/giá trị trước mọi ghi; không tuyên bố bảo vệ direct writes từ client khác, lỗi hạ tầng/OOM không được chứng minh.
- [Dữ liệu ngoài scope] → run ID mới, manifest chính xác, không sửa DB app; lưu snapshot và hậu kiểm.

## Migration Plan
Không migration. Audit → plan → code → chạy năm lần → verify cleanup → đồng bộ báo cáo. Khôi phục file code nếu cần; dữ liệu dọn theo manifest. Không archive tự động.
