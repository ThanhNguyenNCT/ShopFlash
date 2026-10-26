import { lazy } from 'react';

const LoginPage = lazy(() => import('./LoginPage'));
const RegisterPage = lazy(() => import('./RegisterPage'));

// F0 (TV4 sau bàn giao). Hai trang này nằm trong CustomerLayout.
export default [
  { path: '/login', element: <LoginPage />, title: 'Đăng nhập' },
  { path: '/register', element: <RegisterPage />, title: 'Đăng ký' },
];
