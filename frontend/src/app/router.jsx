import { createBrowserRouter, Navigate } from 'react-router-dom';
import AdminLayout from '@/shared/layouts/AdminLayout';

// Gom routes.jsx của mọi module. Mỗi file export default một mảng:
//   { path: '/admin/...', element: <Page />, menu?: { label, icon } }
// Route bắt đầu bằng /admin được đặt trong AdminLayout.
const moduleRoutes = Object.values(import.meta.glob('../modules/*/routes.jsx', { eager: true }))
  .flatMap((mod) => mod.default ?? []);

export const adminMenu = moduleRoutes.filter((r) => r.menu && r.path.startsWith('/admin'));

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/admin/reports" replace /> },
  {
    path: '/admin',
    element: <AdminLayout menu={adminMenu} />,
    children: moduleRoutes
      .filter((r) => r.path.startsWith('/admin/'))
      .map(({ path, element }) => ({ path: path.slice('/admin/'.length), element })),
  },
  ...moduleRoutes.filter((r) => !r.path.startsWith('/admin')).map(({ path, element }) => ({ path, element })),
]);
