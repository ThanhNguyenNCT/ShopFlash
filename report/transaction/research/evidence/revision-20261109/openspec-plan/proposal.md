# Proposal

## Why
B2 hiện dùng old_stock và nhánh protected/timeout; B4 thiếu đối chứng atomic command và chưa tính giá trị HSET; C2 ghi sai rồi validation. Cần thay bằng bằng chứng đúng mục tiêu Isolation, Concurrency và Consistency.

## What Changes
- B2: hai nhánh quyết định ưu đãi khi đọc 100, UPDATE tương đối; cả hai tồn cuối 98, số quyết định đủ điều kiện 2/1.
- B4: hai client HINCRBY độc lập; WATCH conflict với HSET tính từ lần đọc, rồi đọc lại/retry.
- C2: guarded Checkout trong một EVAL, validation toàn bộ trước ghi; snapshot lỗi không đổi.
- Giữ transcript cũ với nhãn superseded, chạy lại và cập nhật đúng các mục báo cáo liên quan.

## Capabilities

### New Capabilities
- `transaction-research-revisions`: hợp đồng thực nghiệm thay thế B2/B4/C2. Capability gốc transaction-native-research mới nằm trong change chưa archive; không sửa hoặc archive change cũ.

### Modified Capabilities
Không có main spec cần sửa.

## Impact
Chỉ report/transaction/research và các mục B2/B4/C2 trong tài liệu Transaction. Tái sử dụng SQL*Plus/redis-cli harness, setup/reset/cleanup/snapshot và fixture có manifest. Không đổi Java, schema, API, benchmark hoặc B3/B5/D1/D2. OpenSpec root là local_docs.
