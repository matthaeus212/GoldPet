import { useNavigate } from 'react-router-dom';
import './BenefitsPage.css';

export const BenefitsPage = () => {
  const navigate = useNavigate();

  const handleStart = () => {
    localStorage.setItem('hasSeenBenefits', 'true');
    navigate('/login', { replace: true });
  };

  return (
    <div className="benefits-page" data-name="Benefit" data-node-id="4017:20935">

      {/* Title Section */}
      <div className="typea-title-group-a" data-name="Title" data-node-id="4017:21050">
        <h1 className="typea-title">골드펫 혜택</h1>
        <p className="typea-subtitle">다양한 맞춤형 혜택을 누려보세요!</p>
      </div>

      {/* Content */}
      <div className="benefits-content" data-name="Body" data-node-id="4017:20938">
        <div className="benefits-list">
          {/* Benefit 1 */}
          <div className="benefit-section">
            <div className="benefit-header-item">
              <div className="benefit-number-icon">하나!</div>
            </div>
            <div className="benefit-card-row">
              <div className="benefit-card-image">
                <img 
                  src="/assets/images/benefits/benefit_1.png" 
                  alt="친구 추천" 
                  className="benefit-card-img"
                  onError={(e) => {
                    console.error('Benefit 1 image failed to load');
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              </div>
              <div className="benefit-text-content">
                <h3 className="benefit-text-title">
                  골드펫이 우리에게 잘 맞는<br />
                  친구를 똑똑하게 추천해드려요.
                </h3>
                <p className="benefit-text-description">
                  부담없어 추천 받고 채팅으로<br />
                  대화해보세요.
                </p>
              </div>
            </div>
          </div>

          <div className="benefit-divider" />

          {/* Benefit 2 */}
          <div className="benefit-section">
            <div className="benefit-header-item">
              <div className="benefit-number-icon">둘!</div>
            </div>
            <div className="benefit-card-row">
              <div className="benefit-card-image">
                <img 
                  src="/assets/images/benefits/benefit_2.png" 
                  alt="AI 프로필" 
                  className="benefit-card-img"
                  onError={(e) => {
                    console.error('Benefit 2 image failed to load');
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              </div>
              <div className="benefit-text-content">
                <h3 className="benefit-text-title">
                  내가 상상했던 반려동물을<br />
                  AI 프로필로 만들어드립니다.
                </h3>
                <p className="benefit-text-description">
                  다양한 모습으로 즐거운 시간을 보내세요.
                </p>
              </div>
            </div>
          </div>

          <div className="benefit-divider" />

          {/* Benefit 3 */}
          <div className="benefit-section">
            <div className="benefit-header-item">
              <div className="benefit-number-icon">셋!</div>
            </div>
            <div className="benefit-card-row">
              <div className="benefit-card-image">
                <img 
                  src="/assets/images/benefits/benefit_3.png" 
                  alt="산책 기록" 
                  className="benefit-card-img"
                  onError={(e) => {
                    console.error('Benefit 3 image failed to load');
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              </div>
              <div className="benefit-text-content">
                <h3 className="benefit-text-title">
                  당신과 반려동물의 산책 경로를 기록하고 관리해드립니다.
                </h3>
                <p className="benefit-text-description">
                  그리고 다른이들의 산책 정보도 확인해보세요.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer Button */}
      <div className="benefits-footer" data-name="Btn" data-node-id="4017:20936">
        <button 
          className="benefits-button"
          onClick={handleStart}
          data-name="Btn/Basic/Full"
          data-node-id="4017:20937"
        >
          가입하기
        </button>
      </div>
    </div>
  );
};

