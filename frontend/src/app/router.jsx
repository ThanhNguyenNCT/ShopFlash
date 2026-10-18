import { Suspense } from 'react';
import { Button, Result, Spin } from 'antd';
import { createBrowserRouter, Navigate, Outlet, useNavigate } from 'react-router-dom';
import AdminLayout from '@/shared/layouts/AdminLayout';

// Gom routes.jsx của mọi module. Mỗi file export default một mảng:
//   { path: '/admin/...', element: <Page />, menu?: { label, icon } }
// Route bắt đầu bằng /admin được đặt trong AdminLayout; path đúng bằng /admin là trang chủ quản trị (index).
const moduleRoutes = Object.values(import.meta.glob('../modules/*/routes.jsx', { eager: true }))
  .flatMap((mod) => mod.default ?? []);

const isAdmin = (path) => path === '/admin' || path.startsWith('/admin/');

export const adminMenu = moduleRoutes.filter((r) => r.menu && isAdmin(r.path));

function HomeButton() {
  const navigate = useNavigate();
  return <Button type="primary" onClick={() => navigate('/')}>Về trang chủ</Button>;
}

// Khung gốc: trang của module được lazy-load nên mọi route cần một Suspense, kể cả trang ngoài AdminLayout.
function RootLayout() {
  return (
    <Suspense fallback={<Spin />}>
      <Outlet />
    </Suspense>
  );
}

function RouteError() {
  return <Result status="error" title="Đã xảy ra lỗi" subTitle="Trang không hiển thị được. Tải lại trang hoặc quay về trang chủ." extra={<HomeButton />} />;
}

function NotFound() {
  return <Result status="404" title="404" subTitle="Không tìm thấy trang bạn yêu cầu." extra={<HomeButton />} />;
}

const adminChildren = moduleRoutes
  .filter((r) => isAdmin(r.path))
  .map(({ path, element }) => (path === '/admin' ? { index: true, element } : { path: path.slice('/admin/'.length), element }));

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      // Tạm thời: chưa có trang chủ cho khách hàng nên chuyển thẳng vào báo cáo quản trị. Bỏ khi có trang chủ.
      { path: '/', element: <Navigate to="/admin/reports" replace /> },
      { path: '/admin', element: <AdminLayout menu={adminMenu} />, children: adminChildren },
      ...moduleRoutes.filter((r) => !isAdmin(r.path)).map(({ path, element }) => ({ path, element })),
      { path: '*', element: <NotFound /> },
    ],
  },
]);
