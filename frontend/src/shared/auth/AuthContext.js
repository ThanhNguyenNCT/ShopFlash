import { createContext } from 'react';

// Giá trị: { user, isAdmin, loading, login({ email, password }), logout }. Xem AGENTS.md.
export const AuthContext = createContext(null);
