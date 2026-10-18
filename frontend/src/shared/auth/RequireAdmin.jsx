import { Navigate, useLocation } from 'react-router-dom';
import useAuth from './useAuth';

// Chặn khu quản trị: chưa đăng nhập hoặc không phải ADMIN thì chuyển tới /login?next=<trang đang mở>.
export default function RequireAdmin({ children }) {
  const { isAdmin } = useAuth();
  const { pathname, search } = useLocation();
  if (!isAdmin) return <Navigate to={`/login?next=${encodeURIComponent(pathname + search)}`} replace />;
  return children;
}
