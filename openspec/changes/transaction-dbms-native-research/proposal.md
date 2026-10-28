# Proposal

## Why

Phần Transaction đã có JUnit, Java DB benchmark, k6 và kết quả ban đầu, nhưng bằng chứng về cơ chế DBMS còn phụ thuộc vào Java. Cần bộ thực nghiệm chạy trực tiếp trên Oracle và Redis để tái lập, quan sát và giải thích 14 case đã chốt trong `report/transaction/RESEARCH_PLAN.md`; tài liệu đó là nguồn quyết định phạm vi và phương pháp, OpenSpec quản lý phân rã và triển khai.

## What Changes

- Bổ sung runbook, SQL/PLSQL và Redis CLI/Lua cho đúng 14 case A1–D2; dùng Checkout `NORMAL` trên bảng `SHOP` và mô phỏng logic tương ứng trong namespace Redis riêng.
- Bổ sung fixture có run ID, manifest ID/key, setup/reset/cleanup an toàn, kịch bản hai session và evidence trước/sau với trạng thái `PASS`/`FAIL`/`BLOCKED`/`NOT_APPLICABLE`.
- Cập nhật phần báo cáo Transaction sau khi có kết quả chạy thật; sau nghiên cứu cơ chế mới rà soát tính tương đương của benchmark hiện có.
- Giữ nguyên ứng dụng F0/F10/F7/F8/F9, migrations, JUnit, Java benchmark, k6 và dữ liệu benchmark cũ. Không thêm schema nghiệp vụ, không sửa dữ liệu dùng chung.

## Capabilities

### New Capabilities

- `transaction-native-research`: Chạy, kiểm chứng và báo cáo 14 thí nghiệm Transaction Oracle–Redis trực tiếp bằng công cụ DBMS, với fixture cách ly và chứng cứ tái lập.

### Modified Capabilities

Không có. Các đặc tả nền và change `transaction-checkout-orders-study` giữ nguyên.

## Impact

- File mới dự kiến trong `report/transaction/research/`; về sau cập nhật `report/transaction/SECTION_TRANSACTION.md` và tài liệu dẫn tới evidence. `report/transaction/results/` hiện có chỉ được đọc làm baseline.
- Tái sử dụng `SHOP` từ `V1__schema.sql`/`V2__transaction_cart_version_and_checkout_hash.sql`, quy ước Redis trong `db/DATABASE_DESIGN.md`, hợp đồng Checkout và các test/runner hiện có để đối chiếu. Redis mô phỏng không tạo Order thật và không đồng bộ ngược Oracle.
- Không thay API, routing, Java, frontend, Flyway, Redis index, key `flash:*`, `product:*`, `order:analytics:*`, `order:line:*` hay fixture `txlab:*` của các bài test cũ.
