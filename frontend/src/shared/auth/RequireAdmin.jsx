import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import useAuth from './useAuth';

// Chặn khu quản trị: chưa đăng nhập hoặc không phải ADMIN thì chuyển tới /login?next=<trang đang mở>.
export default function RequireAdmin({ children }) {
  const { isAdmin, loading } = useAuth();
  const { pathname, search } = useLocation();
  if (loading) return <Spin tip="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: 80 }} /></Spin>;
  if (!isAdmin) return <Navigate to={`/login?next=${encodeURIComponent(pathname + search)}`} replace />;
  return children;
}
