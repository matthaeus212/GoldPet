import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Loader2, AlertTriangle, CheckCircle } from 'lucide-react';
import axios from 'axios';
import { authService } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/common/Button';

const ADMIN_PASSWORD_REGEX = /^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{}|;:',.<>?/~`]).{8,}$/;
const PASSWORD_RULE_MESSAGE = '비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.';

export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const { clearMustChangePassword, mustChangePassword } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('새 비밀번호와 확인 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!ADMIN_PASSWORD_REGEX.test(newPassword)) {
      setError(PASSWORD_RULE_MESSAGE);
      return;
    }

    setIsLoading(true);
    try {
      await authService.changePassword(currentPassword, newPassword);
      clearMustChangePassword();
      navigate('/', { replace: true });
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? (err.response?.data as { message?: string } | undefined)?.message
        : undefined;
      setError(message ?? '비밀번호 변경에 실패했습니다. 현재 비밀번호를 확인해주세요.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-xl shadow-lg p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 bg-amber-100 rounded-lg flex items-center justify-center">
            <Lock className="w-5 h-5 text-amber-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">비밀번호 변경</h1>
            <p className="text-sm text-gray-500">
              {mustChangePassword ? '보안을 위해 초기 비밀번호를 변경해주세요.' : '새 비밀번호를 입력해주세요.'}
            </p>
          </div>
        </div>

        {mustChangePassword && (
          <div className="mb-4 p-3 bg-amber-50 border border-amber-200 text-amber-700 text-sm rounded-lg flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            임시 비밀번호로 로그인하셨습니다. 계속하려면 비밀번호를 변경해야 합니다.
          </div>
        )}

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 flex-shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">현재 비밀번호</label>
            <div className="relative">
              <Lock className="w-5 h-5 text-gray-400 absolute left-3 top-2.5" />
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent transition-all outline-none"
                placeholder="현재 비밀번호"
                required
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">새 비밀번호</label>
            <div className="relative">
              <Lock className="w-5 h-5 text-gray-400 absolute left-3 top-2.5" />
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent transition-all outline-none"
                placeholder="영문/숫자/특수문자 포함 8자 이상"
                required
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">새 비밀번호 확인</label>
            <div className="relative">
              <CheckCircle className={`w-5 h-5 absolute left-3 top-2.5 ${confirmPassword && confirmPassword === newPassword ? 'text-green-500' : 'text-gray-400'}`} />
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent transition-all outline-none"
                placeholder="새 비밀번호 재입력"
                required
              />
            </div>
          </div>
          <Button
            type="submit"
            variant="warning"
            disabled={isLoading}
            className="w-full flex items-center justify-center"
          >
            {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : '비밀번호 변경'}
          </Button>
          {!mustChangePassword && (
            <Button
              type="button"
              variant="link"
              onClick={() => navigate(-1)}
              className="w-full text-sm text-gray-500 hover:text-gray-700"
            >
              취소
            </Button>
          )}
        </form>
      </div>
    </div>
  );
}
