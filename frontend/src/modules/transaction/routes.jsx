import { lazy } from 'react';

// Module transaction (TV4): F10 giỏ hàng, F7 đặt hàng, F8 lịch sử và hủy đơn. Metadata route: xem frontend/AGENTS.md.
const CartPage = lazy(() => import('./pages/CartPage'));
const CheckoutPage = lazy(() => import('./pages/CheckoutPage'));
const OrdersPage = lazy(() => import('./pages/OrdersPage'));
const OrderDetailPage = lazy(() => import('./pages/OrderDetailPage'));

export default [
  { path: '/cart', element: <CartPage />, title: 'Giỏ hàng' },
  { path: '/checkout', element: <CheckoutPage />, title: 'Thanh toán' },
  { path: '/orders', element: <OrdersPage />, title: 'Đơn hàng của tôi' },
  { path: '/orders/:orderId', element: <OrderDetailPage />, title: 'Chi tiết đơn hàng' },
];
