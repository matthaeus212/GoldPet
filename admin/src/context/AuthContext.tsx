import { createContext, useContext, useState, type ReactNode } from 'react';
import type { User } from '../services/authService';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  mustChangePassword: boolean;
  login: (token: string, user: User, mustChangePassword?: boolean) => void;
  clearMustChangePassword: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('admin_user');
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('admin_token');
  });
  const [mustChangePassword, setMustChangePassword] = useState<boolean>(() => {
    return localStorage.getItem('admin_must_change_password') === 'true';
  });

  const login = (newToken: string, newUser: User, mustChange?: boolean) => {
    setToken(newToken);
    setUser(newUser);
    setMustChangePassword(mustChange ?? false);
    localStorage.setItem('admin_token', newToken);
    localStorage.setItem('admin_user', JSON.stringify(newUser));
    localStorage.setItem('admin_must_change_password', String(mustChange ?? false));
  };

  const clearMustChangePassword = () => {
    setMustChangePassword(false);
    localStorage.setItem('admin_must_change_password', 'false');
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    setMustChangePassword(false);
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_user');
    localStorage.removeItem('admin_must_change_password');
  };

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated: !!token, mustChangePassword, login, clearMustChangePassword, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
