import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import './SignupCompletePage.css';

export const SignupCompletePage = () => {
  const navigate = useNavigate();
  const nickname = useAuthStore((state) => state.user?.nickname);

  useEffect(() => {
    // 3초 후 자동으로 홈 화면으로 이동
    const timer = setTimeout(() => {
      navigate('/home', { replace: true });
    }, 3000);

    return () => clearTimeout(timer);
  }, [navigate]);

  const handleGoToHome = () => {
    navigate('/home', { replace: true });
  };

  return (
    <div>
      <div className="signup-complete-content">
        <img
          src="/assets/images/common/logo.svg"
          alt="Gold Pet Logo"
          className="signup-complete-logo"
        />
        <p className="signup-complete-message">
          회원가입이 완료되었습니다.<br />
          {nickname || '회원'}님 회원이 되신 것을 환영합니다!
        </p>
        <div className="signup-complete-button-group">
          <button
            type="button"
            className="signup-complete-button"
            onClick={handleGoToHome}
          >
            로그인
          </button>
        </div>
      </div>
    </div>
  );
};

