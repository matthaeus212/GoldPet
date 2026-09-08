import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../stores/authStore';
import './SignupPage.css';
import { useAlert } from '../../contexts/AlertContext';
import CustomSelect from '../../components/common/CustomSelect';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';

export const SignupPage = () => {
  const navigate = useNavigate();
  const { showAlert } = useAlert();
  const login = useAuthStore((state) => state.login);

  const [formData, setFormData] = useState({
    username: '',
    password: '',
    passwordConfirm: '',
    email: '',
    nickname: '',
    name: '',
    birthDate: '',
    gender: '',
    phoneNumber: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isCheckingUsername, setIsCheckingUsername] = useState(false);
  const [isUsernameAvailable, setIsUsernameAvailable] = useState<boolean | null>(null);
  const [isCheckingNickname, setIsCheckingNickname] = useState(false);
  const [isNicknameAvailable, setIsNicknameAvailable] = useState<boolean | null>(null);
  const [isCheckingEmail, setIsCheckingEmail] = useState(false);
  const [emailDuplicateProvider, setEmailDuplicateProvider] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ [key: string]: string }>({});

  const providerLabel = (p: string): string => {
    switch (p.toUpperCase()) {
      case 'KAKAO': return '카카오';
      case 'NAVER': return '네이버';
      case 'GOOGLE': return 'Google';
      case 'APPLE': return 'Apple';
      default: return '';
    }
  };

  const handleCheckEmail = async () => {
    const email = formData.email.trim();
    if (!email) {
      setEmailDuplicateProvider(null);
      setErrors(prev => { const n = { ...prev }; delete n.email; return n; });
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailDuplicateProvider(null);
      setErrors(prev => ({ ...prev, email: '올바른 이메일 형식이 아닙니다.' }));
      return;
    }
    setIsCheckingEmail(true);
    try {
      const res = await authService.checkEmail(email);
      if (res.available) {
        setEmailDuplicateProvider(null);
        setErrors(prev => { const n = { ...prev }; delete n.email; return n; });
      } else {
        const provider = (res.provider || 'LOCAL').toUpperCase();
        setEmailDuplicateProvider(provider);
        const msg = provider === 'LOCAL'
          ? '이미 가입된 이메일입니다. 아이디/비밀번호 찾기를 이용해주세요.'
          : `이미 ${providerLabel(provider)} 계정으로 가입된 이메일입니다. ${providerLabel(provider)} 로그인을 이용해주세요.`;
        setErrors(prev => ({ ...prev, email: msg }));
      }
    } catch {
      setEmailDuplicateProvider(null);
    } finally {
      setIsCheckingEmail(false);
    }
  };

  const validatePassword = (password: string): string => {
    if (!password) {
      return '';
    }
    if (password.length < 8) {
      return '비밀번호는 8자 이상이어야 합니다.';
    }
    if (!/[a-zA-Z]/.test(password)) {
      return '영문자를 포함해야 합니다.';
    }
    if (!/[0-9]/.test(password)) {
      return '숫자를 포함해야 합니다.';
    }
    if (!/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
      return '특수문자를 포함해야 합니다.';
    }
    return '';
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));

    // 비밀번호 실시간 검증
    if (name === 'password') {
      const passwordError = validatePassword(value);
      setErrors(prev => ({ ...prev, password: passwordError }));

      // 비밀번호 확인도 다시 검증
      if (formData.passwordConfirm) {
        if (value !== formData.passwordConfirm) {
          setErrors(prev => ({ ...prev, passwordConfirm: '비밀번호가 일치하지 않습니다.' }));
        } else {
          setErrors(prev => {
            const newErrors = { ...prev };
            delete newErrors.passwordConfirm;
            return newErrors;
          });
        }
      }
    }

    // 비밀번호 확인 실시간 검증
    if (name === 'passwordConfirm') {
      if (value && value !== formData.password) {
        setErrors(prev => ({ ...prev, passwordConfirm: '비밀번호가 일치하지 않습니다.' }));
      } else {
        setErrors(prev => {
          const newErrors = { ...prev };
          delete newErrors.passwordConfirm;
          return newErrors;
        });
      }
    }

    // 에러 초기화 (비밀번호 제외)
    if (errors[name] && name !== 'password' && name !== 'passwordConfirm') {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
    // 아이디 변경 시 중복체크 상태 초기화
    if (name === 'username') {
      setIsUsernameAvailable(null);
    }
    // 닉네임 변경 시 중복체크 상태 초기화
    if (name === 'nickname') {
      setIsNicknameAvailable(null);
    }
    // 이메일 변경 시 중복체크 상태 초기화
    if (name === 'email') {
      setEmailDuplicateProvider(null);
    }
  };

  const handleCheckUsername = async () => {
    if (!formData.username || formData.username.length < 4) {
      setErrors(prev => ({ ...prev, username: '아이디는 4자 이상이어야 합니다.' }));
      return;
    }

    setIsCheckingUsername(true);
    try {
      const response = await authService.checkUsername(formData.username);
      setIsUsernameAvailable(response.available);
      if (!response.available) {
        setErrors(prev => ({ ...prev, username: '이미 사용 중인 아이디입니다.' }));
      } else {
        setErrors(prev => {
          const newErrors = { ...prev };
          delete newErrors.username;
          return newErrors;
        });
      }
    } catch (error: unknown) {
      console.error('Username check error:', error);
      const err = error as { response?: { data?: { message?: string } } };
      setIsUsernameAvailable(false);
      setErrors(prev => ({ ...prev, username: err.response?.data?.message || '아이디 확인에 실패했습니다.' }));
    } finally {
      setIsCheckingUsername(false);
    }
  };

  const handleCheckNickname = async () => {
    if (!formData.nickname || formData.nickname.length < 2) {
      setErrors(prev => ({ ...prev, nickname: '닉네임은 2자 이상이어야 합니다.' }));
      return;
    }

    setIsCheckingNickname(true);
    try {
      const response = await authService.checkNickname(formData.nickname);
      setIsNicknameAvailable(response.available);
      if (!response.available) {
        setErrors(prev => ({ ...prev, nickname: '이미 사용 중인 닉네임입니다.' }));
      } else {
        setErrors(prev => {
          const newErrors = { ...prev };
          delete newErrors.nickname;
          return newErrors;
        });
      }
    } catch (error: unknown) {
      console.error('Nickname check error:', error);
      const err = error as { response?: { data?: { message?: string } } };
      setIsNicknameAvailable(false);
      setErrors(prev => ({ ...prev, nickname: err.response?.data?.message || '닉네임 확인에 실패했습니다.' }));
    } finally {
      setIsCheckingNickname(false);
    }
  };

  const isFormValid = () => {
    // 아이디: 4-12자, 중복확인 완료
    if (!formData.username || formData.username.length < 4 || formData.username.length > 12 || isUsernameAvailable !== true) {
      return false;
    }

    // 비밀번호: 8자 이상
    if (!formData.password || formData.password.length < 8) {
      return false;
    }

    // 비밀번호 확인: 일치
    if (formData.password !== formData.passwordConfirm) {
      return false;
    }

    // 이름
    if (!formData.name) return false;

    // 생년월일·성별·휴대폰 번호: 선택 항목 (Apple 5.1.1(v)) — 미입력 허용

    // 닉네임: 2-10자, 중복확인 완료
    if (!formData.nickname || formData.nickname.length < 2 || formData.nickname.length > 10 || isNicknameAvailable !== true) {
      return false;
    }

    // 이메일 입력됐는데 중복이면 진행 불가
    if (formData.email && emailDuplicateProvider) {
      return false;
    }

    return true;
  };

  const validateForm = () => {
    const newErrors: { [key: string]: string } = {};

    // 아이디 중복체크 확인
    if (!formData.username || formData.username.length < 4) {
      newErrors.username = '아이디는 4자 이상이어야 합니다.';
    } else if (isUsernameAvailable !== true) {
      newErrors.username = '아이디 중복확인을 해주세요.';
    }

    // 비밀번호 검증
    const passwordError = validatePassword(formData.password);
    if (passwordError) {
      newErrors.password = passwordError;
    }

    // 비밀번호 확인 검증
    if (!formData.passwordConfirm) {
      newErrors.passwordConfirm = '비밀번호를 다시 입력해주세요.';
    } else if (formData.password !== formData.passwordConfirm) {
      newErrors.passwordConfirm = '비밀번호가 일치하지 않습니다.';
    }

    // 이름
    if (!formData.name) {
      newErrors.name = '이름을 입력해주세요.';
    }

    // 생년월일·성별·휴대폰 번호는 선택 항목 (Apple 5.1.1(v)) — 검증하지 않음

    if (!formData.nickname || formData.nickname.length < 2) {
      newErrors.nickname = '닉네임은 2자 이상이어야 합니다.';
    } else if (isNicknameAvailable !== true) {
      newErrors.nickname = '닉네임 중복확인을 해주세요.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsLoading(true);
    try {
      const response = await authService.signup({
        username: formData.username,
        password: formData.password,
        email: formData.email, // Optional
        nickname: formData.nickname,
        name: formData.name,
        // 선택 항목 (Apple 5.1.1(v)): 미입력 시 undefined로 정규화
        birthDate: formData.birthDate || undefined,
        gender: formData.gender ? (formData.gender as 'MALE' | 'FEMALE') : undefined,
        phoneNumber: formData.phoneNumber || undefined,
      });
      // Store tokens in memory via authStore (not localStorage)
      login(response.accessToken, response.refreshToken, response.user);
      // Navigate after successful signup
      navigate('/signup/complete');
    } catch (error: unknown) {
      console.error('Signup error:', error);
      const err = error as {
        response?: { status?: number; data?: { message?: string } };
        message?: string;
      };
      const serverMsg = err?.response?.data?.message;
      const status = err?.response?.status;
      if (status === 409 && serverMsg && /이메일/.test(serverMsg)) {
        // 이메일 중복: 인라인 에러로 표시 + provider CTA가 보이도록 추측 매핑
        setErrors(prev => ({ ...prev, email: serverMsg }));
        const guess = /카카오/.test(serverMsg) ? 'KAKAO'
          : /네이버/.test(serverMsg) ? 'NAVER'
          : /Google/i.test(serverMsg) ? 'GOOGLE'
          : /Apple/i.test(serverMsg) ? 'APPLE'
          : 'LOCAL';
        setEmailDuplicateProvider(guess);
      } else {
        showAlert(serverMsg || err?.message || '회원가입에 실패했습니다.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <div className="signup-page-container" {...keyboardDismiss}>
      {/* Title Section */}
      <div className="typea-title-group-bc">
        <h2 className="typea-title">회원가입</h2>
        <p className="typea-subtitle">서비스 이용을 위해 필요한 정보를 입력해주세요.</p>
      </div>

      {/* Signup Form */}
      <div className="signup-body">
        <form className="signup-form" onSubmit={handleSubmit}>
          {/* 아이디 입력 - 중복체크 버튼 포함 */}
          <div className="signup-input-group">
            <label className="signup-input-label">아이디</label>
            <div className="signup-input-row">
              <input
                type="text"
                name="username"
                className={`signup-input signup-input-username ${errors.username ? 'error' : ''} ${isUsernameAvailable === true ? 'success' : ''}`}
                placeholder="4-12자, 영문, 숫자"
                value={formData.username}
                onChange={handleChange}
                disabled={isLoading}              />
              <button
                type="button"
                className={`signup-check-button ${formData.username.length >= 4 ? 'active' : ''}`}
                onClick={handleCheckUsername}
                disabled={isLoading || isCheckingUsername || formData.username.length < 4}
              >
                {isCheckingUsername ? '확인 중...' : '중복확인'}
              </button>
            </div>
            {errors.username && (
              <span className="signup-error-message">{errors.username}</span>
            )}
            {isUsernameAvailable === true && !errors.username && (
              <span className="signup-success-message">사용 가능한 아이디입니다.</span>
            )}
          </div>

          {/* 비밀번호 입력 */}
          <div className="signup-input-group">
            <label className="signup-input-label">비밀번호</label>
            <div className="signup-input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                name="password"
                className={`signup-input signup-input-rounded ${errors.password ? 'error' : ''}`}
                placeholder="8자 이상, 영문, 숫자, 특수문자 포함"
                value={formData.password}
                onChange={handleChange}
                disabled={isLoading}              />
              <button
                type="button"
                className="signup-password-toggle"
                onClick={() => setShowPassword(!showPassword)}
              >
                <img
                  src={showPassword ? "/assets/images/auth/ic_eye_off.svg" : "/assets/images/auth/ic_eye.svg"}
                  alt={showPassword ? "비밀번호 숨기기" : "비밀번호 보기"}
                  className="signup-password-icon"
                />
              </button>
            </div>
            {errors.password && (
              <span className="signup-error-message">{errors.password}</span>
            )}
          </div>

          {/* 비밀번호 확인 입력 */}
          <div className="signup-input-group">
            <label className="signup-input-label">비밀번호 확인</label>
            <div className="signup-input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                name="passwordConfirm"
                className={`signup-input signup-input-rounded ${errors.passwordConfirm ? 'error' : ''}`}
                placeholder="비밀번호 확인"
                value={formData.passwordConfirm}
                onChange={handleChange}
                disabled={isLoading}              />
              <button
                type="button"
                className="signup-password-toggle"
                onClick={() => setShowPasswordConfirm(!showPasswordConfirm)}
              >
                <img
                  src={showPasswordConfirm ? "/assets/images/auth/ic_eye_off.svg" : "/assets/images/auth/ic_eye.svg"}
                  alt={showPasswordConfirm ? "비밀번호 숨기기" : "비밀번호 보기"}
                  className="signup-password-icon"
                />
              </button>
            </div>
            {errors.passwordConfirm && (
              <span className="signup-error-message">{errors.passwordConfirm}</span>
            )}
          </div>

          {/* 이름 */}
          <div className="signup-input-group">
            <label className="signup-input-label">이름</label>
            <input
              type="text"
              name="name"
              className={`signup-input signup-input-rounded ${errors.name ? 'error' : ''}`}
              placeholder="이름을 입력해주세요"
              value={formData.name}
              onChange={handleChange}
              disabled={isLoading}
            />
            {errors.name && (
              <span className="signup-error-message">{errors.name}</span>
            )}
          </div>

          {/* 생년월일 */}
          <div className="signup-input-group">
            <label className="signup-input-label">생년월일 <span style={{ color: '#999', fontWeight: 400 }}>(선택)</span></label>
            <div className="signup-input-wrapper">
              <input
                type="date"
                name="birthDate"
                className={`signup-input signup-input-rounded ${errors.birthDate ? 'error' : ''}`}
                value={formData.birthDate}
                onChange={handleChange}
                disabled={isLoading}
              />
              <div className="signup-input-icon-wrapper">
                <img
                  src="/assets/images/auth/ic_calendar.svg"
                  alt="달력"
                  className="signup-input-icon"
                />
              </div>
            </div>
            {errors.birthDate && (
              <span className="signup-error-message">{errors.birthDate}</span>
            )}
          </div>

          {/* 성별 */}
          <div className="signup-input-group">
            <label className="signup-input-label">성별 <span style={{ color: '#999', fontWeight: 400 }}>(선택)</span></label>
            <CustomSelect
              className={errors.gender ? 'error' : ''}
              value={formData.gender}
              options={[
                { label: '남성', value: 'MALE' },
                { label: '여성', value: 'FEMALE' },
              ]}
              placeholder="선택사항 (선택하지 않아도 됩니다)"
              onChange={(value) => {
                setFormData(prev => ({ ...prev, gender: value }));
                if (errors.gender) setErrors(prev => ({ ...prev, gender: '' }));
              }}
              disabled={isLoading}
            />
            {errors.gender && (
              <span className="signup-error-message">{errors.gender}</span>
            )}
          </div>

          {/* 휴대폰 번호 */}
          <div className="signup-input-group">
            <label className="signup-input-label">휴대폰 번호 <span style={{ color: '#999', fontWeight: 400 }}>(선택)</span></label>
            <input
              type="tel"
              name="phoneNumber"
              className={`signup-input signup-input-rounded ${errors.phoneNumber ? 'error' : ''}`}
              placeholder="선택사항 (입력하지 않아도 됩니다, - 제외)"
              value={formData.phoneNumber}
              onChange={handleChange}
              disabled={isLoading}
            />
            {errors.phoneNumber && (
              <span className="signup-error-message">{errors.phoneNumber}</span>
            )}
          </div>

          {/* 이메일 (Optional) */}
          <div className="signup-input-group">
            <label className="signup-input-label">이메일 (선택)</label>
            <input
              type="email"
              name="email"
              className={`signup-input signup-input-rounded ${errors.email ? 'error' : ''}`}
              placeholder="이메일"
              value={formData.email}
              onChange={handleChange}
              onBlur={handleCheckEmail}
              disabled={isLoading || isCheckingEmail}
            />
            {errors.email && (
              <span className="signup-error-message">{errors.email}</span>
            )}
            {emailDuplicateProvider && emailDuplicateProvider !== 'LOCAL' && (
              <button
                type="button"
                className="signup-check-button active"
                style={{ marginTop: 8 }}
                onClick={() => navigate('/login')}
              >
                {providerLabel(emailDuplicateProvider)} 로그인으로 이동
              </button>
            )}
          </div>

          {/* 닉네임 */}
          <div className="signup-input-group">
            <label className="signup-input-label">닉네임</label>
            <div className="signup-input-row">
              <input
                type="text"
                name="nickname"
                className={`signup-input signup-input-username ${errors.nickname ? 'error' : ''} ${isNicknameAvailable === true ? 'success' : ''}`}
                placeholder="2-10자, 한글, 영문, 숫자"
                value={formData.nickname}
                onChange={handleChange}
                disabled={isLoading}              />
              <button
                type="button"
                className={`signup-check-button ${formData.nickname.length >= 2 ? 'active' : ''}`}
                onClick={handleCheckNickname}
                disabled={isLoading || isCheckingNickname || formData.nickname.length < 2}
              >
                {isCheckingNickname ? '확인 중...' : '중복확인'}
              </button>
            </div>
            {errors.nickname && (
              <span className="signup-error-message">{errors.nickname}</span>
            )}
            {isNicknameAvailable === true && !errors.nickname && (
              <span className="signup-success-message">사용 가능한 닉네임입니다.</span>
            )}
          </div>

          <div className="signup-button-group">
            <button
              type="submit"
              className="signup-button"
              disabled={isLoading || !isFormValid()}
            >
              {isLoading ? '가입 중...' : '가입하기'}
            </button>
          </div>

          <div className="signup-footer">
            <button
              type="button"
              className="signup-link"
              onClick={() => navigate('/login')}
            >
              이미 계정이 있으신가요? 로그인
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

