import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Home, LogOut } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/common/Button';

export default function ForbiddenPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-xl shadow-lg p-8 text-center">
        <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <ShieldAlert className="w-8 h-8 text-red-600" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">접근 권한 없음</h1>
        <p className="text-sm text-gray-500 mb-6">
          이 페이지에 접근할 수 있는 권한이 없습니다.
          {user?.role && ` (현재 권한: ${user.role})`}
        </p>
        <div className="space-y-2">
          <Button
            type="button"
            variant="primary"
            onClick={() => navigate('/', { replace: true })}
            className="w-full flex items-center justify-center gap-2"
          >
            <Home className="w-4 h-4" />
            홈으로
          </Button>
          <Button
            type="button"
            variant="link"
            onClick={() => {
              logout();
              navigate('/login', { replace: true });
            }}
            className="w-full text-sm text-gray-500 hover:text-gray-700 inline-flex items-center justify-center gap-2"
          >
            <LogOut className="w-4 h-4" />
            로그아웃
          </Button>
        </div>
      </div>
    </div>
  );
}
