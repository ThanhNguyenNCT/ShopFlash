import { lazy } from 'react';
import { AppstoreOutlined, ShoppingOutlined } from '@ant-design/icons';

// Module storage (TV1): F1 danh mục, F2 sản phẩm. Metadata route: xem frontend/AGENTS.md.
const AdminCategoriesPage = lazy(() => import('./pages/AdminCategoriesPage'));
const AdminProductsPage = lazy(() => import('./pages/AdminProductsPage'));

export default [
  {
    path: '/admin/categories',
    element: <AdminCategoriesPage />,
    title: 'Quản lý danh mục',
    menu: { label: 'Danh mục', icon: <AppstoreOutlined />, group: 'Catalog', order: 1, owner: 'TV1', feature: 'F1', status: 'todo' },
  },
  {
    path: '/admin/products',
    element: <AdminProductsPage />,
    title: 'Quản lý sản phẩm',
    menu: { label: 'Sản phẩm', icon: <ShoppingOutlined />, group: 'Catalog', order: 2, owner: 'TV1', feature: 'F2', status: 'todo' },
  },
];
