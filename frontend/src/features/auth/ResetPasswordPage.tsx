import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../services/api/client';
import './ResetPasswordPage.css';
import { Header } from '../../components/common/Header';
import { FindAccountTabs } from './components/FindAccountTabs';
import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';

type ResetPasswordState = 'intro' | 'input-username' | 'input-password' | 'complete';

export const ResetPasswordPage = () => {
  const navigate = useNavigate();
  const { showAlert } = useAlert();

  const [state, setState] = useState<ResetPasswordState>('intro');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [resetToken, setResetToken] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState<{ [key: string]: string }>({});

  const validatePassword = (pwd: string): string => {
    if (!pwd) {
      return '';
    }
    if (pwd.length < 8) {
      return '비밀번호는 8자 이상이어야 합니다.';
    }
    if (!/[a-zA-Z]/.test(pwd)) {
      return '영문자를 포함해야 합니다.';
    }
    if (!/[0-9]/.test(pwd)) {
      return '숫자를 포함해야 합니다.';
    }
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(pwd)) {
      return '특수문자를 포함해야 합니다.';
    }
    return '';
  };

  const handleFindAccount = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username || !email) {
      showAlert('아이디와 이메일을 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await apiClient.post('/auth/verify-account', {
        username,
        email,
      });
      setResetToken(response.data.resetToken);
      setState('input-password');
    } catch (error: unknown) {
      console.error('Verify account error:', error);
      const err = error as { response?: { data?: { message?: string } } };
      showAlert(err.response?.data?.message || '계정 확인에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    if (name === 'password') {
      setPassword(value);
      const passwordError = validatePassword(value);
      setErrors(prev => ({ ...prev, password: passwordError }));

      if (passwordConfirm && value !== passwordConfirm) {
        setErrors(prev => ({ ...prev, passwordConfirm: '비밀번호가 일치하지 않습니다.' }));
      } else {
        setErrors(prev => {
          const newErrors = { ...prev };
          delete newErrors.passwordConfirm;
          return newErrors;
        });
      }
    } else if (name === 'passwordConfirm') {
      setPasswordConfirm(value);
      if (value && value !== password) {
        setErrors(prev => ({ ...prev, passwordConfirm: '비밀번호가 일치하지 않습니다.' }));
      } else {
        setErrors(prev => {
          const newErrors = { ...prev };
          delete newErrors.passwordConfirm;
          return newErrors;
        });
      }
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();

    const passwordError = validatePassword(password);
    if (passwordError) {
      setErrors(prev => ({ ...prev, password: passwordError }));
      return;
    }

    if (password !== passwordConfirm) {
      setErrors(prev => ({ ...prev, passwordConfirm: '비밀번호가 일치하지 않습니다.' }));
      return;
    }

    setIsLoading(true);
    try {
      await apiClient.post('/auth/reset-password', {
        username,
        email,
        password,
        resetToken,
      });
      setState('complete');
    } catch (error: unknown) {
      console.error('Reset password error:', error);
      const err = error as { response?: { data?: { message?: string } } };
      showAlert(err.response?.data?.message || '비밀번호 변경에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const isFormValid = () => {
    if (state === 'input-username') {
      return username && email;
    } else {
      return password && passwordConfirm && !errors.password && !errors.passwordConfirm && password === passwordConfirm;
    }
  };

  const handleSignup = () => {
    navigate('/signup');
  };

  const keyboardDismiss = useKeyboardDismiss();

  // Intro Screen
  if (state === 'intro') {
    return (
      <div className="page-container">
        <Header onBack={() => navigate('/login')} />
        <FindAccountTabs activeTab="password" />

        <div className="reset-password-body">
          <div className="reset-password-intro-section">
            <div className="reset-password-intro-text">
              <div className="reset-password-intro-title">
                <p>혹시 비밀번호가 기억나지 않으세요?</p>
                <p>본인인증을 통해 비밀번호를<br />재설정하실 수 있습니다.</p>
              </div>
              <p className="reset-password-intro-question">
                본인 인증을 진행 하시겠습니까?
              </p>
            </div>

            <button
              type="button"
              className="reset-password-button"
              onClick={() => setState('input-username')}
            >
              PASS 본인인증 진행
            </button>
          </div>

          <div className="reset-password-signup-section">
            <p className="reset-password-signup-text">
              아직 회원이 아니신가요?<br />
              회원가입을 통해 GoldPet 서비스를 이용하실 수 있습니다.
            </p>
            <button
              type="button"
              className="reset-password-signup-button"
              onClick={handleSignup}
            >
              회원 가입 바로가기
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 완료 화면
  if (state === 'complete') {
    return (
      <div className="page-container">
        <Header onBack={() => navigate('/login')} />

        <div className="page-title-section">
          <h1 className="page-title">비밀번호 변경</h1>
        </div>

        <div className="reset-password-body centered">
          <div className="reset-password-result">
            <div className="reset-password-result-icon">✓</div>
            <p className="reset-password-result-text">
              비밀번호가 성공적으로<br />
              변경되었습니다.
            </p>
            <button
              type="button"
              className="reset-password-button"
              onClick={() => navigate('/login')}
            >
              로그인하기
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 비밀번호 입력 화면
  if (state === 'input-password') {
    return (
      <div className="page-container" {...keyboardDismiss}>
        <Header onBack={() => setState('input-username')} />

        <div className="page-title-section">
          <h1 className="page-title">비밀번호 변경</h1>
          <p className="page-subtitle">새로운 비밀번호를 입력해주세요.</p>
        </div>

        <div className="reset-password-body">
          <form className="reset-password-form" onSubmit={handleResetPassword}>
            <div className="reset-password-input-group">
              <label htmlFor="password" className="reset-password-label">비밀번호</label>
              <div className="reset-password-input-wrapper">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  name="password"
                  className={`reset-password-input reset-password-input-rounded ${errors.password ? 'error' : ''}`}
                  placeholder="8-20자, 영문, 숫자, 특수문자"
                  value={password}
                  onChange={handlePasswordChange}
                  disabled={isLoading}
                />
                <button
                  type="button"
                  className="reset-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  <img
                    src={showPassword ? "/assets/images/auth/ic_eye_off.svg" : "/assets/images/auth/ic_eye.svg"}
                    alt={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                    className="reset-password-icon"
                  />
                </button>
              </div>
              {errors.password && (
                <span className="reset-password-error-message">{errors.password}</span>
              )}
            </div>

            <div className="reset-password-input-group">
              <label htmlFor="passwordConfirm" className="reset-password-label">비밀번호 확인</label>
              <div className="reset-password-input-wrapper">
                <input
                  type={showPasswordConfirm ? 'text' : 'password'}
                  id="passwordConfirm"
                  name="passwordConfirm"
                  className={`reset-password-input reset-password-input-rounded ${errors.passwordConfirm ? 'error' : ''}`}
                  placeholder="비밀번호 확인"
                  value={passwordConfirm}
                  onChange={handlePasswordChange}
                  disabled={isLoading}
                />
                <button
                  type="button"
                  className="reset-password-toggle"
                  onClick={() => setShowPasswordConfirm(!showPasswordConfirm)}
                >
                  <img
                    src={showPasswordConfirm ? "/assets/images/auth/ic_eye_off.svg" : "/assets/images/auth/ic_eye.svg"}
                    alt={showPasswordConfirm ? "비밀번호 숨기기" : "비밀번호 보기"}
                    className="reset-password-icon"
                  />
                </button>
              </div>
              {errors.passwordConfirm && (
                <span className="reset-password-error-message">{errors.passwordConfirm}</span>
              )}
            </div>

            <div className="reset-password-button-group">
              <button
                type="submit"
                className="reset-password-button"
                disabled={isLoading || !isFormValid()}
              >
                {isLoading ? '변경 중...' : '변경하기'}
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  // 아이디/이메일 입력 화면 (input-username)
  return (
    <div className="page-container" {...keyboardDismiss}>
      <Header onBack={() => setState('intro')} />

      <div className="page-title-section">
        <h1 className="page-title">비밀번호 변경</h1>
        <p className="page-subtitle">아이디와 이메일을 입력해주세요.</p>
      </div>

      <div className="reset-password-body">
        <form className="reset-password-form" onSubmit={handleFindAccount}>
          <div className="reset-password-input-group">
            <label htmlFor="username" className="reset-password-label">아이디</label>
            <input
              type="text"
              id="username"
              name="username"
              className="reset-password-input reset-password-input-rounded"
              placeholder="아이디를 입력해주세요"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={isLoading}            />
          </div>

          <div className="reset-password-input-group">
            <label htmlFor="email" className="reset-password-label">이메일</label>
            <input
              type="email"
              id="email"
              name="email"
              className="reset-password-input reset-password-input-rounded"
              placeholder="이메일을 입력해주세요"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}            />
          </div>

          <div className="reset-password-button-group">
            <button
              type="submit"
              className="reset-password-button"
              disabled={isLoading || !isFormValid()}
            >
              {isLoading ? '확인 중...' : '다음'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

