## Purpose

Cung cấp hợp đồng kiểm chứng thay thế cho B2, B4 và C2 trong nghiên cứu Transaction ShopFlash, với dữ liệu DBMS thật và bằng chứng tái lập.

## ADDED Requirements

### Requirement: B2 quyết định ưu đãi theo thời điểm đọc
Harness SHALL chạy hai nhánh READ COMMITTED với stock đầu 100, UPDATE tương đối và quyết định đủ điều kiện khi giá trị đọc bằng 100; SHALL xác nhận chờ trước A COMMIT, tồn cuối 98 và không có timeout Oracle cố ý.

#### Scenario: SELECT thông thường
- **WHEN** A đọc 100, UPDATE chưa commit, B đọc và quyết định rồi UPDATE cùng row
- **THEN** B đọc 100, đủ ưu đãi như A, UPDATE chờ A COMMIT; cả hai COMMIT cho stock 98.

#### Scenario: SELECT FOR UPDATE
- **WHEN** A khóa và đọc 100, B SELECT FOR UPDATE WAIT trước A UPDATE/COMMIT
- **THEN** B chờ, lấy khóa rồi đọc 99, không đủ ưu đãi; B UPDATE/COMMIT cho stock 98.

### Requirement: B4 phân biệt atomic command và WATCH
Harness SHALL ghi nhận hai HINCRBY độc lập không WATCH, và một lượt WATCH conflict/retry dùng HSET được tính từ HGET trên cùng connection A.

#### Scenario: Atomic command
- **WHEN** hai client cùng HINCRBY stock -1 từ 100
- **THEN** hai phản hồi là 99 và 98 bất kể thứ tự client, tồn cuối 98.

#### Scenario: Conflict và retry
- **WHEN** A WATCH đọc 100 tính 99, B HINCRBY thành 99 trước A MULTI/HSET/EXEC
- **THEN** EXEC A nil và tồn 99; A WATCH lại đọc 99 tính 98 rồi EXEC thành công, tồn 98.

### Requirement: C2 guarded Checkout
Luồng Checkout thử nghiệm SHALL chỉ ghi qua một EVAL, kiểm tra toàn bộ tham chiếu Product, ánh xạ Inventory, số lượng nguyên dương, Cart, stock/reserved/version, kiểu key và Order chưa tồn tại trước ghi đầu tiên.

#### Scenario: Từ chối request hoặc fixture sai
- **WHEN** Product không tồn tại, Inventory không khớp Product, quantity sai, thiếu stock, Cart sai, kiểu key sai hoặc giá trị Inventory không hợp lệ
- **THEN** trả mã từ chối cụ thể và snapshot đầy đủ trước/sau bằng nhau.

#### Scenario: Checkout hợp lệ
- **WHEN** Product ACTIVE, request khớp Cart và đủ stock khả dụng
- **THEN** tạo Order, trừ stock/tăng version, bỏ đúng dòng Cart đã mua; reserved, dòng Cart khác và quota không đổi; invariant được assertion.

### Requirement: Bằng chứng và khôi phục
Mỗi nhánh SHALL có timeline input/output, expected/actual assertion, snapshot và hậu kiểm reset/cleanup; transcript cũ SHALL giữ nguyên và đánh dấu superseded. Các experiment khác SHALL không bị sửa.

#### Scenario: Hoàn tất run
- **WHEN** chạy lại trên Oracle/Redis local theo manifest
- **THEN** chỉ báo PASS sau assertion, lấy lại khóa Oracle và xác minh fixture/key được dọn; báo cáo không suy rộng thành FK tự động hoặc full rollback Redis.
