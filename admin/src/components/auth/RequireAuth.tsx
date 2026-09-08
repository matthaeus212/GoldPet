import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import type { ReactNode } from 'react';

type AdminUserRole = 'SUPER_ADMIN' | 'OPERATOR' | 'VIEWER';

interface RequireAuthProps {
  children: ReactNode;
  requiredRoles?: AdminUserRole[];
}

export default function RequireAuth({ children, requiredRoles }: RequireAuthProps) {
  const { isAuthenticated, user, mustChangePassword } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  if (requiredRoles && requiredRoles.length > 0 && user) {
    if (!requiredRoles.includes(user.role)) {
      return <Navigate to="/forbidden" replace />;
    }
  }

  return children;
}
