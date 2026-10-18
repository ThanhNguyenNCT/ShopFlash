import { useCallback, useMemo, useState } from 'react';
import { AuthContext } from './AuthContext';
import { MOCK_USERS } from './mockUsers';

// F0 TẠM (TV5 thay): chưa gọi backend, chỉ chọn một trong hai tài khoản dev seed và nhớ trong localStorage.
// Bản thật giữ nguyên hình dạng giá trị của context (xem AGENTS.md) để các trang không phải sửa.
const STORAGE_KEY = 'shopflash.auth.user';

function readStoredUser() {
  try {
    const role = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')?.role;
    return MOCK_USERS[role] ?? null;
  } catch {
    return null;
  }
}

function storeUser(user) {
  try {
    if (user) localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Trình duyệt chặn localStorage (chế độ riêng tư...): vẫn đăng nhập được trong phiên hiện tại.
  }
}

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser);

  const loginAs = useCallback((role) => {
    const next = MOCK_USERS[role];
    if (!next) throw new Error(`Vai trò không hợp lệ: ${role}`);
    storeUser(next);
    setUser(next);
    return next;
  }, []);

  const logout = useCallback(() => {
    storeUser(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, isAdmin: user?.role === 'ADMIN', loginAs, logout }),
    [user, loginAs, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
