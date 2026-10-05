# Frontend

React + Vite + Ant Design, giao diện cho khách hàng và quản trị viên.

## Bố cục

- `src/app/`: router tổng và providers. Router chỉ gom `routes.jsx` của các module, không khai báo route trực tiếp.
- `src/shared/`: `api/` (axios client, gắn token), `auth/` (F0, AuthContext, màn đăng nhập/đăng ký), `design-system/` (tokens, theme, component riêng của ShopFlash), `components/`, `layouts/` (CustomerLayout, AdminLayout).
- `src/modules/<module>/`: tên giống module backend. Mỗi module gồm:
  - `pages/`: màn hình.
  - `components/`: component chỉ dùng trong module.
  - `api.js`: hàm gọi endpoint của module, dùng client trong `shared/api`.
  - `routes.jsx`: route của module.

## Quy ước

- Dùng component của Ant Design, không tự viết lại.
- IMPORTANT: Màu, chữ, khoảng cách lấy từ `src/shared/design-system` (xem `AGENTS.md` ở đó; bản xem trực quan: `design-system.html`). Không viết mã màu cố định.
- Form phải kiểm tra dữ liệu nhập. Có trạng thái loading, báo lỗi rõ ràng, và hỏi xác nhận trước khi xóa.
- Danh sách phải phân trang.
