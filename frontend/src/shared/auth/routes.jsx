import { lazy } from 'react';

const LoginPage = lazy(() => import('./LoginPage'));
const RegisterPage = lazy(() => import('./RegisterPage'));

// F0 (TV5). Hai trang này nằm trong CustomerLayout.
export default [
  { path: '/login', element: <LoginPage />, title: 'Đăng nhập' },
  { path: '/register', element: <RegisterPage />, title: 'Đăng ký' },
];
