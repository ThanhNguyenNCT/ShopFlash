import { lazy } from 'react';
import { AppstoreOutlined, DatabaseOutlined, ShoppingOutlined } from '@ant-design/icons';

// Module storage (TV1): F1 danh mục, F2 sản phẩm. Metadata route: xem frontend/AGENTS.md.
const AdminCategoriesPage = lazy(() => import('./pages/AdminCategoriesPage'));
const AdminProductsPage = lazy(() => import('./pages/AdminProductsPage'));
const StorageLabPage = lazy(() => import('./pages/StorageLabPage'));

export default [
  {
    path: '/admin/categories',
    element: <AdminCategoriesPage />,
    title: 'Quản lý danh mục',
    menu: { label: 'Danh mục', icon: <AppstoreOutlined />, group: 'Catalog', order: 1, owner: 'TV1' },
  },
  {
    path: '/admin/products',
    element: <AdminProductsPage />,
    title: 'Quản lý sản phẩm',
    menu: { label: 'Sản phẩm', icon: <ShoppingOutlined />, group: 'Catalog', order: 2, owner: 'TV1' },
  },
  {
    path: '/admin/products/storage-lab',
    element: <StorageLabPage />,
    title: 'So sánh Oracle–Redis',
    menu: { label: 'So sánh Oracle–Redis', icon: <DatabaseOutlined />, group: 'Catalog', order: 3, owner: 'TV1' },
  },
];
