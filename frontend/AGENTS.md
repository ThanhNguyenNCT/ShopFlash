# Frontend

React + Vite + Ant Design, giao diện cho khách hàng và quản trị viên.

## Chạy

- `npm run dev`: gọi backend thật qua proxy `/api` tới `:8080`.
- `npm run dev:mock` (`vite --mode mock`): không cần backend. Module nào có mock (vd. `query`) tự trả dữ liệu mẫu.

## Bố cục

- `src/app/`: router tổng và providers (`ThemeProvider` > `AuthProvider` > `RouterProvider`). Router chỉ gom `routes.jsx` của các module, không khai báo route trực tiếp.
- `src/shared/`:
  - `api/`: axios client chung, chuẩn hóa lỗi.
  - `auth/`: F0 (TV4 sau bàn giao từ TV5). `useAuth`, `RequireAdmin`, trang đăng nhập/đăng ký. Hợp đồng: `src/shared/auth/AGENTS.md`.
  - `design-system/`: tokens, theme, component riêng của ShopFlash.
  - `components/`: component dùng chung, vd. `ComingSoon`.
  - `layouts/`: `CustomerLayout`, `AdminLayout`.
- `src/modules/<module>/`: tên giống module backend. Mỗi module gồm:
  - `pages/`: màn hình, mỗi màn hình một file.
  - `components/`: component chỉ dùng trong module.
  - `api.js`: hàm gọi endpoint của module, dùng client trong `shared/api`.
  - `routes.jsx`: route của module.

## Route

Mỗi `routes.jsx` (và `src/shared/auth/routes.jsx`) export default một mảng:

```jsx
const MyPage = lazy(() => import('./pages/MyPage'));   // luôn lazy-load trang

export default [
  {
    path: '/admin/products',          // đường dẫn đầy đủ
    element: <MyPage />,
    title: 'Quản lý sản phẩm',        // tùy chọn: tiêu đề tab trình duyệt
    menu: {                           // tùy chọn, chỉ cho route /admin: hiện trong menu quản trị
      label: 'Sản phẩm',
      icon: <ShoppingOutlined />,
      group: 'Catalog',               // 'Tổng quan' | 'Catalog' | 'Flash sale' | 'Báo cáo' | 'Hệ thống'
      order: 2,                       // thứ tự trong nhóm
      owner: 'TV1',
      feature: 'F2',
      status: 'todo',                 // còn là placeholder; bỏ dòng này khi màn hình đã xong
    },
  },
];
```

- Path bắt đầu bằng `/admin`: nằm trong `AdminLayout`, sau `RequireAdmin` (chưa đăng nhập hoặc không phải ADMIN thì chuyển tới `/login?next=...`). Path đúng bằng `/admin` là trang Tổng quan (index).
- Path khác, kể cả `/login`, `/register`: nằm trong `CustomerLayout` (header có logo, ô tìm kiếm gửi sang `/search?q=`, link Flash Sale, Đơn hàng, giỏ hàng, menu tài khoản; mobile gom vào Drawer).
- Menu quản trị sắp theo nhóm (thứ tự trong `MENU_GROUPS` của `src/app/router.jsx`) rồi theo `menu.order`. Trang Tổng quan đọc cùng danh sách qua `useOutletContext().adminMenu` để dựng thẻ lối tắt, trạng thái "Đã có"/"Đang làm" lấy từ `menu.status`.
- Link trong cửa hàng (header, Drawer) viết cố định trong `CustomerLayout`; đổi path của trang khách hàng thì báo người giữ `shared/layouts`.
- Không có route nào khớp: trang 404 trong `CustomerLayout`. Trang lỗi khi render: `RouteError`.

## Màn hình chưa làm (placeholder)

Mọi màn hình trong kế hoạch đều đã có route. Màn hình chưa làm render `ComingSoon` (`src/shared/components/ComingSoon.jsx`): ghi chức năng, người phụ trách, file cần thay, việc cần làm và API gợi ý.

Để làm màn hình của mình: sửa đúng một file trang, vd. `src/modules/storage/pages/AdminProductsPage.jsx`, thay `<ComingSoon .../>` bằng màn hình thật, giữ `export default`. Route, menu, layout, phân quyền đã nối sẵn. Xong thì bỏ `status: 'todo'` trong `routes.jsx`. Trang chủ (`indexing/pages/HomePage.jsx`) đang có dữ liệu minh họa, có nhãn "Dữ liệu minh họa"; TV2 thay toàn bộ.

| Path | Module | Người phụ trách | Chức năng |
|---|---|---|---|
| `/` | indexing | TV2 | Trang chủ (F3, F4) |
| `/search` | indexing | TV2 | Tìm kiếm, lọc (F3, F4) |
| `/products/:productId` | indexing | TV2 | Chi tiết sản phẩm (nút "Thêm vào giỏ" từ transaction sau tích hợp) |
| `/flash-sale` | concurrency | TV5 | Flash sale (F9) |
| `/cart` | transaction | TV4 | Giỏ hàng (F10) |
| `/checkout` | transaction | TV4 | Thanh toán (F7) |
| `/orders`, `/orders/:orderId` | transaction | TV4 | Đơn hàng của tôi, chi tiết, hủy đơn (F8) |
| `/login`, `/register` | shared/auth | TV4 | F0 dùng JWT |
| `/admin` | query | TV3 | Tổng quan |
| `/admin/categories`, `/admin/products` | storage | TV1 | F1, F2 |
| `/admin/flash-sales` | concurrency | TV5 | Quản lý chương trình flash sale (F9) |
| `/admin/reports`, `/admin/reports/lab` | query | TV3 | Báo cáo, so sánh Oracle–Redis (F5, F6) |
| `/admin/backups` | backup | TV1 | Sao lưu (F11) |

## Quy ước

- Dùng component của Ant Design, không tự viết lại.
- IMPORTANT: Màu, chữ, khoảng cách lấy từ `src/shared/design-system` (xem `AGENTS.md` ở đó; bản xem trực quan: `design-system.html`). Không viết mã màu cố định.
- Giao diện tiếng Việt. Không cuộn ngang ở màn 375px.
- Form phải kiểm tra dữ liệu nhập. Có trạng thái loading, báo lỗi rõ ràng, và hỏi xác nhận trước khi xóa.
- Danh sách phải phân trang.
- Cần biết người dùng hiện tại thì dùng `useAuth()` từ `@/shared/auth`, không tự đọc `localStorage`.
