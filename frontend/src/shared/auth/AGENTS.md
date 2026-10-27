# shared/auth (F0: đăng nhập, đăng ký)

Người phụ trách: TV4 sau khi TV5 đồng ý bàn giao F0 ngày 26/10/2026. F0 dùng API Oracle và JWT theo OpenSpec change `transaction-checkout-orders-study`; TV5 tiếp tục F9.

## Hợp đồng phải giữ khi làm bản thật

Các trang và layout đã dùng những thứ dưới đây. Đổi cách cài đặt thoải mái, nhưng giữ đúng tên và hình dạng để không trang nào phải sửa.

- `import { AuthProvider, useAuth, RequireAdmin, safeNext } from '@/shared/auth'`.
- `<AuthProvider>` bọc ứng dụng trong `src/app/App.jsx`, nằm trong `ThemeProvider`, ngoài `RouterProvider`.
- `useAuth()` trả `{ user, isAdmin, loading, login({ email, password }), logout() }`:
  - `user`: `null` khi chưa đăng nhập, hoặc `{ userId, email, fullName, role }` với `role` là `'CUSTOMER' | 'ADMIN'` (khớp `USERS.role`).
  - `isAdmin`: `user?.role === 'ADMIN'`.
  - `logout()`: xóa phiên; layout gọi rồi tự chuyển về `/`.
  - `login({ email, password })`: trả Promise, ném lỗi đã chuẩn hóa của `shared/api/client`.
  - `loading`: đang khôi phục phiên qua `/api/auth/me`; `RequireAdmin` hiện Spin, không chuyển hướng vội.
- `<RequireAdmin>`: chưa đăng nhập hoặc không phải ADMIN thì `Navigate` tới `/login?next=<đường dẫn hiện tại>`. Router đã bọc `AdminLayout` bằng nó; trang admin không tự kiểm tra lại.
- `/login?next=...`: đăng nhập xong quay lại `next` (lọc qua `safeNext`, chỉ nhận đường dẫn nội bộ).
- Route `/login`, `/register` khai báo trong `routes.jsx` ở thư mục này, nằm trong `CustomerLayout`.

## Cách dùng JWT

- `LoginPage.jsx` và `RegisterPage.jsx` gửi form tới `/api/auth/login` và `/api/auth/register`.
- `AuthProvider.jsx` giữ JWT trong `sessionStorage` của tab; lúc tải lại gọi `/api/auth/me`. `shared/api/client.js` tự gắn Bearer vào yêu cầu `/api` và xóa phiên khi API bảo vệ trả `401`.
- Đăng xuất xóa JWT ở tab. JWT đã cấp ở nơi khác vẫn có hiệu lực đến `exp` hoặc khi tài khoản bị vô hiệu hóa; không có refresh token.
