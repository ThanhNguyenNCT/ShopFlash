import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiClient } from '@/shared/api/client';
import { AuthContext } from './AuthContext';
import { clearAccessToken, getAccessToken, saveAccessToken } from './session';

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const expired = () => { if (active) setUser(null); };
    window.addEventListener('shopflash:auth-expired', expired);
    const token = getAccessToken();
    if (token) {
      apiClient.get('/auth/me')
        .then(({ data }) => { if (active && getAccessToken() === token) setUser(data); })
        .catch(() => { if (active) setUser(null); })
        .finally(() => { if (active) setLoading(false); });
    } else {
      setLoading(false);
    }
    return () => { active = false; window.removeEventListener('shopflash:auth-expired', expired); };
  }, []);

  const login = useCallback(async ({ email, password }) => {
    const { data } = await apiClient.post('/auth/login', { email, password });
    saveAccessToken(data.accessToken);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(() => {
    clearAccessToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, isAdmin: user?.role === 'ADMIN', loading, login, logout }),
    [user, loading, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
