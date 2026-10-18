import { createContext } from 'react';

// Giá trị: { user, isAdmin, loginAs(role), logout }. Xem AGENTS.md trong thư mục này.
export const AuthContext = createContext(null);
