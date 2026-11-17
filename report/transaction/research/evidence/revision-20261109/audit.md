# Audit và tài sản tái sử dụng

Ngày 09/11/2026, Compose project local ShopFlash có oracle/redis healthy, chỉ bind loopback. Oracle audit có một SHOP session ACTIVE (session audit), schema SHOP/FREEPDB1; Redis 7.4.7 standalone/AOF everysec. Hai digest image giữ theo compose.yaml. Không có dấu hiệu session nghiên cứu khác chạy đồng thời; chỉ tạo fixture run mới. Không suy ra quyền truy cập hay môi trường dùng chung từ loopback riêng lẻ.

P0 chỉ đọc trước ghi: [Oracle](../revision-20261109-attempt1/oracle-audit.txt), [Redis](../revision-20261109-attempt1/redis-audit.txt). Audit dùng lại script cũ, không in credential. Mật khẩu chỉ đọc bên trong container và SQL*Plus nhận qua stdin.

| Case | Hiện trạng trước sửa | Thay đổi và reuse |
|---|---|---|
| B2 | old_stock overwrite, protected UPDATE/timeout | reuse hai SQL*Plus session, bind P1/P2 và prompt barrier; thêm subclass strict riêng B2, read/decision/stock assertion |
| B4 | WATCH + HINCRBY, chưa có atomic branch | reuse một redis-cli connection A và client B riêng; barrier HINCRBY độc lập; HSET tính từ HGET, conflict/retry |
| C2 | HSET sai trước, validation sau; không map Product–Inventory đầy đủ | giữ namespace/Cart/Inventory, bổ sung Product Hash riêng; guard validation trước mọi ghi Checkout, snapshot và reset từng case |

Reuse setup/reset/cleanup/preview/snapshot của hai DBMS; không đổi schema/Java/API/benchmark. [scope-check.json](scope-check.json) và [historical-integrity.json](historical-integrity.json) đối chiếu source ngoài scope, transcript lịch sử và fixture chung với Git HEAD. Các thay đổi có sẵn frontend/package-lock.json và report/transaction.zip không thuộc đợt này.

OpenSpec gốc nằm tại local_docs/openspec (bị gitignore theo convention repo), không nằm ở root. Không khởi tạo OpenSpec thứ hai. Bản chụp artifact của change đi cùng evidence để bàn giao được qua Git.
