# shared/auth (F0: đăng nhập, đăng ký)

Người phụ trách: TV5. Bản hiện tại là bản TẠM để cả nhóm dùng thử giao diện: chưa gọi backend, chỉ chọn một trong hai tài khoản dev seed (`customer@shopflash.local`, `admin@shopflash.local`) và nhớ vai trò trong `localStorage`.

## Hợp đồng phải giữ khi làm bản thật

Các trang và layout đã dùng những thứ dưới đây. Đổi cách cài đặt thoải mái, nhưng giữ đúng tên và hình dạng để không trang nào phải sửa.

- `import { AuthProvider, useAuth, RequireAdmin, safeNext } from '@/shared/auth'`.
- `<AuthProvider>` bọc ứng dụng trong `src/app/App.jsx`, nằm trong `ThemeProvider`, ngoài `RouterProvider`.
- `useAuth()` trả `{ user, isAdmin, loginAs(role), logout() }`:
  - `user`: `null` khi chưa đăng nhập, hoặc `{ userId, email, fullName, role }` với `role` là `'CUSTOMER' | 'ADMIN'` (khớp `USERS.role`).
  - `isAdmin`: `user?.role === 'ADMIN'`.
  - `logout()`: xóa phiên; layout gọi rồi tự chuyển về `/`.
  - `loginAs(role)`: chỉ dành cho bản tạm. Bản thật thêm `login({ email, password })` (trả Promise, ném lỗi đã chuẩn hóa của `shared/api/client`), có thể bỏ `loginAs` sau khi không còn chỗ nào dùng.
  - Nếu cần trạng thái đang khôi phục phiên (gọi `/api/auth/me` lúc tải trang), thêm `loading: boolean` và để `RequireAdmin` hiện Spin khi `loading`, không chuyển hướng vội.
- `<RequireAdmin>`: chưa đăng nhập hoặc không phải ADMIN thì `Navigate` tới `/login?next=<đường dẫn hiện tại>`. Router đã bọc `AdminLayout` bằng nó; trang admin không tự kiểm tra lại.
- `/login?next=...`: đăng nhập xong quay lại `next` (lọc qua `safeNext`, chỉ nhận đường dẫn nội bộ).
- Route `/login`, `/register` khai báo trong `routes.jsx` ở thư mục này, nằm trong `CustomerLayout`.

## Việc của bản thật

- `LoginPage.jsx`: form email + mật khẩu có kiểm tra dữ liệu, loading, báo lỗi "Email hoặc mật khẩu không đúng".
- `RegisterPage.jsx`: hiện là `ComingSoon`, thay bằng form đăng ký.
- `AuthProvider.jsx`: gọi API của `common/security` (backend băm mật khẩu bằng bcrypt), giữ phiên (cookie session hoặc token). Nếu dùng token thì gắn vào `apiClient` trong `shared/api/client.js` (interceptor request).
- Xóa `mockUsers.js` khi không còn dùng.
