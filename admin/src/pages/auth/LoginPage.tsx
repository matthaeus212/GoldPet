import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Lock, Mail, ShieldCheck, ArrowRight, Loader2, AlertTriangle } from 'lucide-react';
import { authService } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';
import { Button } from '../../components/common/Button';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [step, setStep] = useState<'credentials' | 'otp'>('credentials');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  // EXT-CDX-005: verify-2fa 는 userId 가 아니라 login 이 발급한 challengeId 에 바인딩된다.
  const [tempChallengeId, setTempChallengeId] = useState<string | null>(null);
  const [tempMustChange, setTempMustChange] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const from = location.state?.from?.pathname || '/';

  const handleCredentialsSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      const response = await authService.login(email, password);
      if (response.requiresTwoFactor && response.twoFactorChallengeId) {
        setTempChallengeId(response.twoFactorChallengeId);
        setTempMustChange(response.mustChangePassword ?? false);
        setStep('otp');
      } else if (response.user && response.token) {
        login(response.token, response.user, response.mustChangePassword);
        navigate(response.mustChangePassword ? '/change-password' : from, { replace: true });
      }
    } catch {
      setError('로그인 정보가 올바르지 않습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      if (!tempChallengeId) {
         throw new Error('인증 세션이 만료되었습니다. 다시 로그인해주세요.');
      }
      const { token, user } = await authService.verifyOtp(tempChallengeId, otp);
      login(token, user, tempMustChange);
      navigate(tempMustChange ? '/change-password' : from, { replace: true });
    } catch {
      setError('인증 코드가 올바르지 않습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-xl shadow-lg overflow-hidden md:max-w-xl">
        <div className="md:flex">
          <div className="hidden md:block md:w-1/2 bg-amber-500 p-8 flex flex-col justify-between">
            <div>
              <div className="w-12 h-12 bg-white/20 rounded-lg flex items-center justify-center mb-4">
                <Lock className="w-6 h-6 text-white" />
              </div>
              <h2 className="text-2xl font-bold text-white mb-2">GoldPet Admin</h2>
              <p className="text-amber-100">안전한 서비스 운영을 위한 관리자 시스템입니다.</p>
            </div>
            <div className="text-amber-200 text-sm">
              © GoldPet Corp.
            </div>
          </div>

          <div className="w-full md:w-1/2 p-8">
            <h3 className="text-xl font-bold text-gray-900 mb-6">
              {step === 'credentials' ? '관리자 로그인' : '2단계 인증'}
            </h3>

            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-600 text-sm rounded-lg flex items-center">
                <AlertTriangle className="w-4 h-4 mr-2 flex-shrink-0" />
                {error}
              </div>
            )}

            {step === 'credentials' ? (
              <form onSubmit={handleCredentialsSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">이메일</label>
                  <div className="relative">
                    <Mail className="w-5 h-5 text-gray-400 absolute left-3 top-2.5" />
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent transition-all outline-none"
                      placeholder="admin@goldpet.com"
                      required
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
                  <div className="relative">
                    <Lock className="w-5 h-5 text-gray-400 absolute left-3 top-2.5" />
                    <input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent transition-all outline-none"
                      placeholder="••••••••"
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
                  {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : (
                    <>
                      로그인 <ArrowRight className="w-4 h-4 ml-2" />
                    </>
                  )}
                </Button>
              </form>
            ) : (
              <form onSubmit={handleOtpSubmit} className="space-y-4">
                <div className="bg-blue-50 p-4 rounded-lg flex items-start mb-4">
                  <ShieldCheck className="w-5 h-5 text-blue-600 mr-2 flex-shrink-0 mt-0.5" />
                  <p className="text-sm text-blue-700">
                    보안을 위해 2단계 인증이 필요합니다.<br/>
                    인증 앱의 6자리 코드를 입력해주세요.
                  </p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">인증 코드 (OTP)</label>
                  <input
                    type="text"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent text-center text-xl tracking-widest outline-none"
                    placeholder="000000"
                    required
                  />
                </div>
                <Button
                  type="submit"
                  variant="warning"
                  disabled={isLoading}
                  className="w-full flex items-center justify-center"
                >
                  {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : '인증 완료'}
                </Button>
                <Button
                  variant="link"
                  onClick={() => setStep('credentials')}
                  className="w-full text-sm text-gray-500 hover:text-gray-700"
                >
                  뒤로 가기
                </Button>
              </form>
            )}

            <div className="mt-8 text-center md:hidden">
              <span className="text-xs text-gray-400">© GoldPet Corp.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
