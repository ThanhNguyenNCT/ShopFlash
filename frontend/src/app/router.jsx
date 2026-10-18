import { Suspense, useEffect } from 'react';
import { Button, Result, Spin } from 'antd';
import { createBrowserRouter, Outlet, useMatches, useNavigate } from 'react-router-dom';
import AdminLayout from '@/shared/layouts/AdminLayout';
import CustomerLayout from '@/shared/layouts/CustomerLayout';
import { RequireAdmin } from '@/shared/auth';
import authRoutes from '@/shared/auth/routes';

// Gom routes.jsx của mọi module (và shared/auth cho F0). Mỗi file export default một mảng:
//   {
//     path: '/admin/...' | '/...',
//     element: <Page />,
//     title?: 'Tiêu đề tab trình duyệt',
//     menu?: { label, icon, order, group, owner?, feature?, status?: 'todo' },   // chỉ dùng cho route /admin
//   }
// Route bắt đầu bằng /admin: nằm trong AdminLayout, sau RequireAdmin. Path đúng bằng /admin là trang chủ quản trị (index).
// Route khác (kể cả /login, /register): nằm trong CustomerLayout. Chi tiết: frontend/AGENTS.md.
const moduleRoutes = [
  ...authRoutes,
  ...Object.values(import.meta.glob('../modules/*/routes.jsx', { eager: true })).flatMap((mod) => mod.default ?? []),
];

const isAdmin = (path) => path === '/admin' || path.startsWith('/admin/');

// Thứ tự nhóm trong menu quản trị. Nhóm lạ xếp cuối, theo tên.
export const MENU_GROUPS = ['Tổng quan', 'Catalog', 'Flash sale', 'Báo cáo', 'Hệ thống'];
const groupRank = (g) => {
  const i = MENU_GROUPS.indexOf(g);
  return i === -1 ? MENU_GROUPS.length : i;
};

/** Mục menu quản trị đã sắp: theo nhóm (MENU_GROUPS), rồi menu.order, rồi path. */
export const adminMenu = moduleRoutes
  .filter((r) => r.menu && isAdmin(r.path))
  .sort((a, b) => groupRank(a.menu.group) - groupRank(b.menu.group)
    || String(a.menu.group ?? '').localeCompare(String(b.menu.group ?? ''))
    || (a.menu.order ?? 99) - (b.menu.order ?? 99)
    || a.path.localeCompare(b.path));

function HomeButton() {
  const navigate = useNavigate();
  return <Button type="primary" onClick={() => navigate('/')}>Về trang chủ</Button>;
}

// Tiêu đề tab: lấy `title` của route sâu nhất có khai báo.
function useDocumentTitle() {
  const matches = useMatches();
  const title = [...matches].reverse().find((m) => m.handle?.title)?.handle.title;
  useEffect(() => {
    document.title = title ? `${title} · ShopFlash` : 'ShopFlash';
  }, [title]);
}

// Khung gốc: trang của module được lazy-load nên mọi route cần một Suspense.
function RootLayout() {
  useDocumentTitle();
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

const toRoute = ({ path, element, title }, relPath) => ({
  ...(relPath === null ? { index: true } : { path: relPath }),
  element,
  handle: title ? { title } : undefined,
});

const adminChildren = moduleRoutes
  .filter((r) => isAdmin(r.path))
  .map((r) => toRoute(r, r.path === '/admin' ? null : r.path.slice('/admin/'.length)));

const customerChildren = moduleRoutes
  .filter((r) => !isAdmin(r.path))
  .map((r) => (r.path === '/' ? toRoute(r, null) : toRoute(r, r.path)));

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      {
        path: '/admin',
        element: <RequireAdmin><AdminLayout menu={adminMenu} /></RequireAdmin>,
        errorElement: <RouteError />,
        children: adminChildren,
      },
      {
        path: '/',
        element: <CustomerLayout />,
        errorElement: <RouteError />,
        children: [...customerChildren, { path: '*', element: <NotFound />, handle: { title: 'Không tìm thấy trang' } }],
      },
    ],
  },
]);
