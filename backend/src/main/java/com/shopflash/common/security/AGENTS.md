# F0 — Xác thực dùng chung

Người phụ trách: TV4, theo xác nhận TV5 bàn giao F0 ngày 26/10/2026. Phần này cung cấp đăng ký, đăng nhập và JWT cho mọi module; không chứa nghiệp vụ Cart/Checkout/Order hoặc Flash Sale.

Tài khoản lưu trong `USERS` của Oracle, mật khẩu dùng bcrypt. Backend kiểm tra JWT rồi tra vai trò/trạng thái hiện tại từ Oracle; không tin `userId` hay `role` do frontend tự khai. API quản trị yêu cầu ADMIN, Cart/Checkout/Order yêu cầu CUSTOMER. Hợp đồng danh tính và lỗi của Transaction nằm trong [backend/docs/transaction/CONTRACT.md](../../../../../../../docs/transaction/CONTRACT.md).

Giữ định dạng lỗi chung trong `common/web`, SQL dùng bind variable. Secret JWT lấy từ môi trường, không commit hoặc log. Frontend tương ứng ở `frontend/src/shared/auth/`; sửa `shared/api/client.js` chỉ để gắn JWT cho các lời gọi `/api` cùng origin và xử lý hết hạn.
