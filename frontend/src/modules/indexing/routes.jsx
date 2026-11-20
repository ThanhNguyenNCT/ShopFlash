import { lazy } from 'react';

// Module indexing (TV2): F3 tìm kiếm, F4 lọc nâng cao. Metadata route: xem frontend/AGENTS.md.
const HomePage = lazy(() => import('./pages/HomePage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const ProductDetailPage = lazy(() => import('./pages/ProductDetailPage'));

export default [
  { path: '/', element: <HomePage />, title: 'Trang chủ' },
  { path: '/search', element: <SearchPage />, title: 'Tìm kiếm' },
  { path: '/products/:productId', element: <ProductDetailPage />, title: 'Chi tiết sản phẩm' },
];
