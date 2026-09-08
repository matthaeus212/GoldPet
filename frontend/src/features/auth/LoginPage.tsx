import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import apiClient from '../../services/api/client';
import { authService } from '../../services/authService';
import type { LinkSuggestionInfo } from '../../services/authService';
import { nativeBridge } from '../../bridge/nativeBridge';
import { userService } from '../../services/userService';
import { useAlert } from '../../contexts/AlertContext';
import { AccountLinkModal } from '../../components/auth/AccountLinkModal';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import './LoginPage.css';

export const LoginPage = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const login = useAuthStore((state) => state.login);
  const updateUser = useAuthStore((state) => state.updateUser);
  const { showAlert } = useAlert();

  // 개발자 패널 노출 여부는 **서버가 IP 로 판정**한다. 예전에는 빌드 모드로 판정해
  // 라이브 번들(vite build --mode dev)에 패널이 그대로 실려 모든 사용자에게 보였다.
  const [devLoginAvailable, setDevLoginAvailable] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [autoLogin, setAutoLogin] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [linkSuggestion, setLinkSuggestion] = useState<LinkSuggestionInfo | null>(null);
  const [linkProvider, setLinkProvider] = useState<string>('');
  const isAndroid = /Android/i.test(navigator.userAgent);
  const [lastSnsProvider, setLastSnsProvider] = useState<string | null>(null);

  useEffect(() => {
    // 앱 WebView 에서는 아예 묻지 않는다(패널은 브라우저 전용).
    if (nativeBridge.isAvailable()) return;
    let cancelled = false;
    void authService.isDevLoginAvailable().then((ok) => {
      if (!cancelled) setDevLoginAvailable(ok);
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const saved = localStorage.getItem('lastSnsProvider');
    if (saved) setLastSnsProvider(saved);
  }, []);

  // Check for OAuth error in URL query parameters
  useEffect(() => {
    const error = searchParams.get('error');
    if (error) {
      showAlert(decodeURIComponent(error));
      // Remove error from URL
      searchParams.delete('error');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, setSearchParams, showAlert]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username || !password) {
      showAlert('아이디와 비밀번호를 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await apiClient.post('/auth/login', {
        username,
        password,
      });

      const { accessToken, refreshToken, user } = response.data;
      login(accessToken, refreshToken, user);

      // 전체 프로필 조회하여 위치 등 누락 필드 보충
      try {
        const fullProfile = await userService.getMe();
        updateUser(fullProfile);
      } catch (e) {
        console.warn('프로필 조회 실패:', e);
      }

      // 자동 로그인 설정
      if (autoLogin) {
        localStorage.setItem('autoLogin', 'true');
      }

      navigate('/home', { replace: true });
    } catch (error: unknown) {
      console.error('Login error:', error);
      const err = error as { response?: { data?: { message?: string } } };
      showAlert(err.response?.data?.message || '로그인에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSnsLogin = async (provider: 'naver' | 'kakao' | 'apple' | 'google', email?: string) => {
    if (isLoading) return;

    const executeLogin = async (loginEmail?: string) => {
        try {
            const result = await authService.openLoginPopup(provider, loginEmail);

            // Check for account link suggestion
            if (result.linkSuggestion) {
              setLinkSuggestion(result.linkSuggestion);
              setLinkProvider(provider);
              return;
            }

            // AuthResponse type - login succeeded
            if (result.accessToken) {
              localStorage.setItem('lastSnsProvider', provider);
              setLastSnsProvider(provider);
              login(result.accessToken, result.refreshToken, result.user);
              // 전체 프로필 조회하여 위치 등 누락 필드 보충
              try {
                const fullProfile = await userService.getMe();
                updateUser(fullProfile);
              } catch (e) {
                console.warn('프로필 조회 실패:', e);
              }
              navigate('/home', { replace: true });
            }
          } catch (error: unknown) {
            console.error('SNS Login failed:', error);
            // Check if signup is required (new user)
            const err = error as { message?: string; signupRequired?: boolean; accessToken?: string; refreshToken?: string; user?: { id: number; username: string; nickname: string; email: string }; provider?: string };
            if (err.message?.includes('SIGNUP_REQUIRED') || err.signupRequired) {
              // SNS 회원가입 API 호출을 위해 토큰 저장 (snsSignup에 인증 필요)
              if (err.accessToken) {
                login(err.accessToken, err.refreshToken || '', err.user || { id: 0, username: '', nickname: '', email: '' });
              }
              navigate('/signup/sns', { state: { provider: err.provider || provider } });
            } else {
                showAlert(err.message || 'SNS 로그인에 실패했습니다.');
            }
          }
    };

    setIsLoading(true);
    try {
      if (email) {
          await executeLogin(email);
          return;
      }

      // STYLE-003: VITE_USE_MOCK 은 4개 env 모두 'false' 고정이라 아래 분기의 mock 프롬프트 로그인은
      // 도달 불가능한 죽은 코드였다.
      await executeLogin();
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirmLink = async () => {
    if (!linkSuggestion) return;
    try {
      const result = await authService.confirmLink(linkSuggestion.tempToken);
      setLinkSuggestion(null);
      login(result.accessToken, result.refreshToken, result.user);
      try {
        const fullProfile = await userService.getMe();
        updateUser(fullProfile);
      } catch (e) {
        console.warn('프로필 조회 실패:', e);
      }
      navigate('/home', { replace: true });
    } catch (error: unknown) {
      console.error('Account link failed:', error);
      const err = error as { message?: string };
      showAlert(err.message || '계정 연동에 실패했습니다.');
    }
  };

  const handleCancelLink = () => {
    setLinkSuggestion(null);
    setLinkProvider('');
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <>
    <div className='login-page-container' data-name="로그인_텍스트홀더" data-node-id="4017:22523" {...keyboardDismiss}>
      {/* Title Section */}
      <div className="login-title-section" data-name="Title" data-node-id="4017:22554">
        <div className="login-logo-container">
          <img
            src="/assets/images/common/logo.svg"
            alt="GoldPet"
            className="login-logo"
          />
        </div>
        <p className="login-subtitle">로그인 정보를 입력해 주세요</p>
      </div>

      {/* Login Form */}
      <div className="login-body" data-name="Body" data-node-id="4017:22525">
        <form className="login-form" onSubmit={handleLogin} data-name="login" data-node-id="4017:22526">
          <div className="login-input-group">
            <input
              type="text"
              className="login-input login-input-top"
              placeholder="아이디"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={isLoading}
            />
            <div className="login-input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                className="login-input login-input-bottom"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
              />
              <button
                type="button"
                className="login-password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                data-name="ic_invisible"
                data-node-id="4017:22532"
              >
                <img
                  src={showPassword ? "/assets/images/auth/ic_eye_off.svg" : "/assets/images/auth/ic_eye.svg"}
                  alt={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                  className="login-password-icon"
                  onError={(e) => {
                    console.error('Password icon failed to load');
                    // 아이콘 로드 실패 시 텍스트로 대체
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    const text = document.createTextNode(showPassword ? '👁️‍🗨️' : '👁️');
                    target.parentElement?.appendChild(text);
                  }}
                />
              </button>
            </div>
          </div>

          <div className="login-button-group" data-name="btn" data-node-id="4017:22536">
            <button
              type="submit"
              className="login-button btn-effect"
              disabled={isLoading}
              data-name="Btn/Basic/Full"
              data-node-id="4017:22539"
            >
              {isLoading ? '로그인 중...' : '로그인'}
            </button>
          </div>

          <div className="login-options" data-node-id="4017:22540">
            <label className="login-auto-login" data-name="auto" data-node-id="4017:22541">
              <input
                type="checkbox"
                checked={autoLogin}
                onChange={(e) => setAutoLogin(e.target.checked)}
                className="login-checkbox"
              />
              <span className="login-checkbox-label">자동 로그인</span>
            </label>
            <div className="login-links">
              <button
                type="button"
                className="login-link btn-effect"
                onClick={() => navigate('/find-username')}
              >
                아이디 찾기
              </button>
              <button
                type="button"
                className="login-link btn-effect"
                onClick={() => navigate('/reset-password')}
              >
                비밀번호 변경
              </button>
              <button
                type="button"
                className="login-link btn-effect"
                onClick={() => navigate('/signup')}
              >
                회원가입
              </button>
            </div>
          </div>
        </form>

        {/* SNS Login */}
        <div className="login-sns" data-name="sns" data-node-id="4017:22547">
          <p className="login-sns-title">SNS 계정으로 로그인/회원가입</p>
          <div className="login-sns-buttons" data-name="Logo" data-node-id="4017:22549">
            <div className="login-sns-button-wrapper">
              <button
                type="button"
                className="login-sns-button btn-effect"
                onClick={() => handleSnsLogin('naver')}
                disabled={isLoading}
                aria-label="네이버 로그인"
              >
                <img
                  src="/assets/images/auth/naver_logo.svg"
                  alt="네이버"
                  className="login-sns-logo"
                  onError={(e) => {
                    console.error('Naver logo failed to load');
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    if (target.parentElement) {
                      target.parentElement.textContent = 'N';
                      target.parentElement.style.backgroundColor = '#03C75A';
                      target.parentElement.style.color = '#FFFFFF';
                      target.parentElement.style.fontWeight = 'bold';
                    }
                  }}
                />
              </button>
              {lastSnsProvider === 'naver' && (
                <div className="login-recent-tag">
                  <span className="login-recent-tag-arrow" />
                  <span>최근 로그인</span>
                </div>
              )}
            </div>
            <div className="login-sns-button-wrapper">
              <button
                type="button"
                className="login-sns-button btn-effect"
                onClick={() => handleSnsLogin('kakao')}
                disabled={isLoading}
                aria-label="카카오 로그인"
              >
                <img
                  src="/assets/images/auth/kakao_logo.svg"
                  alt="카카오"
                  className="login-sns-logo"
                  onError={(e) => {
                    console.error('Kakao logo failed to load');
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    if (target.parentElement) {
                      target.parentElement.textContent = 'K';
                      target.parentElement.style.backgroundColor = '#FEE500';
                      target.parentElement.style.color = '#000000';
                      target.parentElement.style.fontWeight = 'bold';
                    }
                  }}
                />
              </button>
              {lastSnsProvider === 'kakao' && (
                <div className="login-recent-tag">
                  <span className="login-recent-tag-arrow" />
                  <span>최근 로그인</span>
                </div>
              )}
            </div>
            {!isAndroid && (
              <div className="login-sns-button-wrapper">
                <button
                  type="button"
                  className="login-sns-button login-sns-button-apple btn-effect"
                  onClick={() => handleSnsLogin('apple')}
                  disabled={isLoading}
                  aria-label="애플 로그인"
                >
                  <img
                    src="/assets/images/auth/apple_logo.svg"
                    alt="애플"
                    className="login-sns-logo"
                    onError={(e) => {
                      console.error('Apple logo failed to load');
                      const target = e.target as HTMLImageElement;
                      target.style.display = 'none';
                      if (target.parentElement) {
                        target.parentElement.textContent = '🍎';
                        target.parentElement.style.fontSize = '24px';
                      }
                    }}
                  />
                </button>
                {lastSnsProvider === 'apple' && (
                  <div className="login-recent-tag">
                    <span className="login-recent-tag-arrow" />
                    <span>최근 로그인</span>
                  </div>
                )}
              </div>
            )}
            <div className="login-sns-button-wrapper">
              <button
                type="button"
                className="login-sns-button login-sns-button-google btn-effect"
                onClick={() => handleSnsLogin('google')}
                disabled={isLoading}
                aria-label="구글 로그인"
              >
                <img
                  src="/assets/images/auth/google_logo.svg"
                  alt="구글"
                  className="login-sns-logo"
                  onError={(e) => {
                    console.error('Google logo failed to load');
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    if (target.parentElement) {
                      target.parentElement.textContent = 'G';
                      target.parentElement.style.backgroundColor = '#FFFFFF';
                      target.parentElement.style.color = '#4285F4';
                      target.parentElement.style.fontWeight = 'bold';
                    }
                  }}
                />
              </button>
              {lastSnsProvider === 'google' && (
                <div className="login-recent-tag">
                  <span className="login-recent-tag-arrow" />
                  <span>최근 로그인</span>
                </div>
              )}
            </div>
          </div>
        </div>
        {/* Test Login Section - local/dev 웹 브라우저에서만 표시 */}
        {!nativeBridge.isAvailable() && devLoginAvailable && (
        <div className="mt-8 pt-4 border-t border-gray-100">
            <p className="text-xs text-gray-400 text-center mb-2 font-mono">---------- DEVELOPER ONLY ----------</p>
            <form
                className="flex gap-2 justify-center items-center px-4"
                onSubmit={(e) => {
                    e.preventDefault();
                    const input = e.currentTarget.elements.namedItem('testEmail') as HTMLInputElement;
                    if (input.value) {
                         handleSnsLogin('google', input.value);
                    }
                }}
            >
                <input
                    name="testEmail"
                    type="email"
                    defaultValue="sungchuli@test.com"
                    className="flex-1 max-w-[200px] h-8 text-sm px-2 border border-gray-300 rounded focus:border-amber-500 outline-none"
                    placeholder="테스트 계정 이메일"
                />
                <button
                    type="submit"
                    className="h-8 px-3 text-sm bg-gray-800 text-white rounded hover:bg-gray-700 transition-colors"
                >
                    즉시 로그인
                </button>
            </form>
        </div>
        )}
      </div>
    </div>

    {linkSuggestion && (
      <AccountLinkModal
        linkSuggestion={linkSuggestion}
        newProvider={linkProvider}
        onLink={handleConfirmLink}
        onCancel={handleCancelLink}
      />
    )}
    </>
  );
};

