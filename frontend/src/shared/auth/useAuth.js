import { useContext } from 'react';
import { AuthContext } from './AuthContext';

/**
 * Người dùng hiện tại. Trả { user, isAdmin, loginAs(role), logout }.
 * user: null khi chưa đăng nhập, hoặc { userId, email, fullName, role: 'CUSTOMER' | 'ADMIN' }.
 */
export default function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải được gọi bên trong <AuthProvider>.');
  return ctx;
}
