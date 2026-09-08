import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../services/api/client';
import './FindUsernamePage.css';
import { Header } from '../../components/common/Header';
import { FindAccountTabs } from './components/FindAccountTabs';
import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';

type FindResultState = 'intro' | 'input' | 'not-found' | 'found-general' | 'found-sns';

export const FindUsernamePage = () => {
  const navigate = useNavigate();
  const { showAlert } = useAlert();

  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [resultState, setResultState] = useState<FindResultState>('intro');
  const [foundUsername, setFoundUsername] = useState('');
  const [snsProvider, setSnsProvider] = useState('');

  const handleFindUsername = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!email) {
      showAlert('이메일을 입력해주세요.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await apiClient.post('/auth/find-username', {
        email,
      });

      if (response.data.username) {
        setFoundUsername(response.data.username);
        if (response.data.snsProvider) {
          setSnsProvider(response.data.snsProvider);
          setResultState('found-sns');
        } else {
          setResultState('found-general');
        }
      } else {
        setResultState('not-found');
      }
    } catch (error: unknown) {
      console.error('Find username error:', error);
      const err = error as { response?: { status?: number; data?: { message?: string } } };
      if (err.response?.status === 404) {
        setResultState('not-found');
      } else {
        showAlert(err.response?.data?.message || '아이디 찾기에 실패했습니다.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleBackToLogin = () => {
    navigate('/login');
  };

  const handleResetPassword = () => {
    navigate('/reset-password');
  };

  const handleSignup = () => {
    navigate('/signup');
  };

  const keyboardDismiss = useKeyboardDismiss();

  // Intro Screen
  if (resultState === 'intro') {
    return (
      <div className="page-container">
        <Header onBack={handleBackToLogin} />
        <FindAccountTabs activeTab="username" />

        <div className="find-username-body">
          <div className="find-username-intro-section">
            <div className="find-username-intro-text">
              <h1 className="find-username-intro-title">
                본인 인증을 통해<br />
                아이디를 찾을 수 있습니다.
              </h1>
              <p className="find-username-intro-subtitle">
                본인 인증을 진행 하시겠습니까?
              </p>
            </div>

            <button
              type="button"
              className="find-username-button"
              onClick={() => setResultState('input')}
            >
              PASS 본인인증 진행
            </button>
          </div>

          <div className="find-username-signup-section">
            <p className="find-username-signup-text">
              아직 회원이 아니신가요?<br />
              회원가입을 통해 GoldPet 서비스를 이용하실 수 있습니다.
            </p>
            <button
              type="button"
              className="find-username-signup-button"
              onClick={handleSignup}
            >
              회원 가입 바로가기
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 결과 화면: 아이디 없을 경우
  if (resultState === 'not-found') {
    return (
      <div className="page-container">
        <Header onBack={() => setResultState('input')} />

        {/* Title Section */}
        <div className="find-username-title-section">
          <div className="find-username-logo-container">
            <img
              src="/assets/images/common/logo.svg"
              alt="GoldPet"
              className="find-username-logo"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          </div>
        </div>

        <div className="find-username-body centered">
          <div className="find-username-result">
            <div className="find-username-result-icon">⚠️</div>
            <p className="find-username-result-text">
              입력하신 이메일로 가입된<br />
              아이디를 찾을 수 없습니다.
            </p>
          </div>
          <div className="find-username-button-group bottom">
            <button
              type="button"
              className="find-username-button"
              onClick={() => setResultState('input')}
            >
              다시 찾기
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 결과 화면: 아이디 찾았을 경우 (일반가입회원)
  if (resultState === 'found-general') {
    return (
      <div className="page-container">
        <Header onBack={handleBackToLogin} />

        {/* Title Section */}
        <div className="find-username-title-section">
          <div className="find-username-logo-container">
            <img
              src="/assets/images/common/logo.svg"
              alt="GoldPet"
              className="find-username-logo"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          </div>
        </div>

        <div className="find-username-body centered">
          <div className="find-username-result">
            <div className="find-username-result-icon">✓</div>
            <p className="find-username-result-text">
              입력하신 이메일로 가입된<br />
              아이디를 찾았습니다.
            </p>
            <div className="find-username-result-box">
              <span className="find-username-label">아이디</span>
              <span className="find-username-value">{foundUsername}</span>
            </div>
          </div>
          <div className="find-username-button-group bottom">
            <button
              type="button"
              className="find-username-button"
              onClick={handleBackToLogin}
            >
              로그인하기
            </button>
            <button
              type="button"
              className="find-username-button secondary"
              onClick={handleResetPassword}
            >
              비밀번호 변경
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 결과 화면: 아이디 찾았을 경우 (SNS회원)
  if (resultState === 'found-sns') {
    return (
      <div className="page-container">
        <Header onBack={handleBackToLogin} />

        {/* Title Section */}
        <div className="find-username-title-section">
          <div className="find-username-logo-container">
            <img
              src="/assets/images/common/logo.svg"
              alt="GoldPet"
              className="find-username-logo"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          </div>
        </div>

        <div className="find-username-body centered">
          <div className="find-username-result">
            <div className="find-username-result-icon">✓</div>
            <p className="find-username-result-text">
              입력하신 이메일로 가입된<br />
              아이디를 찾았습니다.
            </p>
            <div className="find-username-result-box">
              <span className="find-username-label">아이디</span>
              <span className="find-username-value">{foundUsername}</span>
            </div>
            <div className="find-username-sns-info">
              <span className="find-username-sns-text">
                {snsProvider} 계정으로 가입된 계정입니다.
              </span>
            </div>
          </div>
          <div className="find-username-button-group bottom">
            <button
              type="button"
              className="find-username-button"
              onClick={handleBackToLogin}
            >
              로그인하기
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 입력 화면
  return (
    <div className="find-username-page" {...keyboardDismiss}>
      <Header onBack={() => setResultState('intro')} />

      {/* Title Section */}
      <div className="find-username-title-section">
        <div className="find-username-logo-container">
          <img
            src="/assets/images/common/logo.svg"
            alt="GoldPet"
            className="find-username-logo"
            onError={(e) => {
              (e.target as HTMLImageElement).style.display = 'none';
            }}
          />
        </div>
        <p className="find-username-subtitle">가입 시 입력한 이메일을 입력해주세요</p>
      </div>

      {/* Form */}
      <div className="find-username-body">
        <form className="find-username-form" onSubmit={handleFindUsername}>
          <div className="find-username-input-group">
            <input
              type="email"
              id="email"
              name="email"
              className="find-username-input"
              placeholder="이메일"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}            />
          </div>

          <div className="find-username-button-group">
            <button
              type="submit"
              className="find-username-button"
              disabled={isLoading || !email}
            >
              {isLoading ? '찾는 중...' : '아이디 찾기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

