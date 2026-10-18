import { lazy } from 'react';
import { ThunderboltOutlined } from '@ant-design/icons';

// Module concurrency (TV5): F9 flash sale, F10 giỏ hàng. Metadata route: xem frontend/AGENTS.md.
const FlashSalePage = lazy(() => import('./pages/FlashSalePage'));
const CartPage = lazy(() => import('./pages/CartPage'));
const AdminFlashSalesPage = lazy(() => import('./pages/AdminFlashSalesPage'));

export default [
  { path: '/flash-sale', element: <FlashSalePage />, title: 'Flash sale' },
  { path: '/cart', element: <CartPage />, title: 'Giỏ hàng' },
  {
    path: '/admin/flash-sales',
    element: <AdminFlashSalesPage />,
    title: 'Quản lý flash sale',
    menu: { label: 'Chương trình flash sale', icon: <ThunderboltOutlined />, group: 'Flash sale', order: 1, owner: 'TV5', feature: 'F9', status: 'todo' },
  },
];
