import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import './SplashPage.css';

// 이미지 경로
const logoImage = '/assets/images/splash/logo_foreground.png';
const logoIcon = '/assets/images/splash/logo_background.svg';

export const SplashPage = () => {
  const navigate = useNavigate();

  useEffect(() => {
    const timer = setTimeout(() => {
      const hasSeenOnboarding = localStorage.getItem('hasSeenOnboarding');
      const hasSeenPermissions = localStorage.getItem('hasSeenPermissions');
      const hasSeenBenefits = localStorage.getItem('hasSeenBenefits');
      const isAuthenticated = useAuthStore.getState().isAuthenticated;

      if (!hasSeenOnboarding) {
        navigate('/onboarding', { replace: true });
      } else if (!hasSeenPermissions) {
        navigate('/permissions', { replace: true });
      } else if (!hasSeenBenefits) {
        navigate('/benefits', { replace: true });
      } else if (isAuthenticated) {
        navigate('/home', { replace: true });
      } else {
        navigate('/login', { replace: true });
      }
    }, 2000);

    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <div className="splash-page" data-name="Splash" data-node-id="4017:21056">
      <div className="splash-logo-container">
        <div className="splash-logo-icon" data-name="Img" data-node-id="4017:21057">
          <img 
            src={logoIcon} 
            alt="GoldPet Logo" 
            className="splash-logo-svg"
            onError={(e) => {
              // 이미지 로드 실패 시 대체 처리
              (e.target as HTMLImageElement).style.display = 'none';
            }}
          />
        </div>
        <div className="splash-logo-image" data-name="Untitled file 1" data-node-id="4017:21064">
          <img 
            src={logoImage} 
            alt="GoldPet" 
            className="splash-logo-img"
            onError={(e) => {
              (e.target as HTMLImageElement).style.display = 'none';
            }}
          />
        </div>
      </div>
    </div>
  );
};

