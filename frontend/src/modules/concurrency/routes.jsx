import { lazy } from 'react';
import { ExperimentOutlined, ThunderboltOutlined } from '@ant-design/icons';

// Module concurrency (TV5): F9 flash sale và Concurrency Lab. Metadata route: xem frontend/AGENTS.md.
const FlashSalePage = lazy(() => import('./pages/FlashSalePage'));
const FlashCheckoutPage = lazy(() => import('./pages/FlashCheckoutPage'));
const AdminFlashSalesPage = lazy(() => import('./pages/AdminFlashSalesPage'));
const ConcurrencyLabPage = lazy(() => import('./pages/ConcurrencyLabPage'));

export default [
  { path: '/flash-sale', element: <FlashSalePage />, title: 'Flash sale' },
  { path: '/flash-sale/checkout/:reservationId', element: <FlashCheckoutPage />, title: 'Đặt đơn flash sale' },
  {
    path: '/admin/flash-sales',
    element: <AdminFlashSalesPage />,
    title: 'Quản lý flash sale',
    menu: { label: 'Chương trình flash sale', icon: <ThunderboltOutlined />, group: 'Flash sale', order: 1, owner: 'TV5', feature: 'F9' },
  },
  {
    path: '/admin/flash-sales/lab',
    element: <ConcurrencyLabPage />,
    title: 'Concurrency Lab',
    menu: { label: 'Concurrency Lab', icon: <ExperimentOutlined />, group: 'Flash sale', order: 2, owner: 'TV5', feature: 'F9' },
  },
];
