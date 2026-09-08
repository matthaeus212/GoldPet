import { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { authService } from '../../services/authService';
import { Shield, ShieldAlert, ShieldCheck, QrCode, X, Loader2, Copy, Check } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

export default function ProfilePage() {
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const { user, login, token } = useAuth(); // login used effectively to update user state
  const [isSetupModalOpen, setIsSetupModalOpen] = useState(false);
  
  // Setup State
  const [secret, setSecret] = useState('');
  const [qrUrl, setQrUrl] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [isCopied, setIsCopied] = useState(false);

  // Disable State
  const [isDisabling, setIsDisabling] = useState(false);

  const startSetup = async () => {
    setIsLoading(true);
    setError('');
    try {
      const { secret, qrUrl } = await authService.setup2fa();
      setSecret(secret);
      setQrUrl(qrUrl);
      setIsSetupModalOpen(true);
    } catch (err) {
      toast.error('2FA 설정 시작 실패: ' + (err instanceof Error ? err.message : 'Unknown error'));
    } finally {
      setIsLoading(false);
    }
  };

  const verifyAndEnable = async () => {
    if (!verificationCode || verificationCode.length !== 6) {
      setError('6자리 코드를 입력해주세요.');
      return;
    }
    
    setIsLoading(true);
    setError('');
    
    try {
      await authService.confirm2fa(secret, verificationCode);
      
      // Update local user state
      if (user && token) {
        const updatedUser = { ...user, isTwoFactorEnabled: true };
        login(token, updatedUser); // Re-save to context/localstorage
      }
      
      setIsSetupModalOpen(false);
      setVerificationCode('');
      setSecret('');
      setQrUrl('');
      toast.info('2단계 인증이 활성화되었습니다.');
    } catch {
       setError('인증에 실패했습니다. 코드를 다시 확인해주세요.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleDisable = async () => {
    if (!(await confirmDialog({ description: '정말로 2단계 인증을 해제하시겠습니까? 계정 보안이 취약해질 수 있습니다.' }))) return;
    
    setIsDisabling(true);
    try {
      await authService.remove2fa();
      if (user && token) {
        const updatedUser = { ...user, isTwoFactorEnabled: false };
        login(token, updatedUser);
      }
      toast.info('2단계 인증이 해제되었습니다.');
    } catch (err) {
      toast.error('해제 실패: ' + (err instanceof Error ? err.message : 'Unknown error'));
    } finally {
      setIsDisabling(false);
    }
  };

  const copySecret = () => {
    navigator.clipboard.writeText(secret);
    setIsCopied(true);
    setTimeout(() => setIsCopied(false), 2000);
  };

  if (!user) return <div>Loading...</div>;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">내 정보 관리</h1>
      
      <div className="bg-white shadow rounded-lg overflow-hidden">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-lg font-medium text-gray-900">기본 정보</h2>
        </div>
        <div className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-500">이름</label>
            <div className="mt-1 text-gray-900">{user.name}</div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500">이메일</label>
            <div className="mt-1 text-gray-900">{user.email}</div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500">역할</label>
            <div className="mt-1 text-gray-900">{user.role}</div>
          </div>
        </div>
      </div>

      <div className="bg-white shadow rounded-lg overflow-hidden">
        <div className="p-6 border-b border-gray-200 flex items-center justify-between">
          <h2 className="text-lg font-medium text-gray-900 flex items-center">
            <Shield className="w-5 h-5 mr-2 text-gray-500" />
            보안 설정
          </h2>
          {user.isTwoFactorEnabled ? (
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
              <ShieldCheck className="w-3 h-3 mr-1" />
              활성화됨
            </span>
          ) : (
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
              <ShieldAlert className="w-3 h-3 mr-1" />
              비활성화
            </span>
          )}
        </div>
        <div className="p-6">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="text-base font-medium text-gray-900">2단계 인증 (2FA)</h3>
              <p className="mt-1 text-sm text-gray-500">
                로그인 시 비밀번호 외에 인증 앱(Google Authenticator 등)의 코드를 추가로 입력하여 계정을 보호합니다.
              </p>
            </div>
            <div className="ml-4 flex-shrink-0">
              {user.isTwoFactorEnabled ? (
                <Button
                  variant="danger"
                  size="sm"
                  onClick={handleDisable}
                  disabled={isDisabling}
                >
                  {isDisabling ? '해제 중...' : '해제하기'}
                </Button>
              ) : (
                <Button
                  variant="warning"
                  size="sm"
                  onClick={startSetup}
                  disabled={isLoading}
                  className="flex items-center"
                >
                  {isLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <QrCode className="w-4 h-4 mr-2" />}
                  설정하기
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Setup Modal */}
      {isSetupModalOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6 animate-in fade-in zoom-in duration-200">
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-lg font-bold text-gray-900">2단계 인증 설정</h3>
              <button 
                onClick={() => setIsSetupModalOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="space-y-6">
              <div className="text-center">
                <div className="bg-white p-2 inline-block rounded-lg shadow-sm border border-gray-100 mb-4">
                  <img src={qrUrl} alt="2FA QR Code" className="w-48 h-48" />
                </div>
                <p className="text-sm text-gray-600 mb-2">
                  Google Authenticator 앱으로 QR 코드를 스캔하세요.
                </p>
                <div className="flex items-center justify-center gap-2">
                  <code className="bg-gray-100 px-2 py-1 rounded text-xs font-mono text-gray-500">
                    {secret}
                  </code>
                  <button onClick={copySecret} className="text-gray-400 hover:text-gray-600">
                    {isCopied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">인증 코드 입력</label>
                <input
                  type="text"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 outline-none text-center text-lg tracking-widest"
                  placeholder="000000"
                  autoFocus
                />
                {error && <p className="mt-2 text-sm text-red-600 flex items-center"><ShieldAlert className="w-4 h-4 mr-1"/>{error}</p>}
              </div>

              <Button
                variant="warning"
                onClick={verifyAndEnable}
                disabled={isLoading || verificationCode.length !== 6}
                className="w-full"
              >
                {isLoading ? '확인 중...' : '인증 및 활성화'}
              </Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </div>
  );
}
