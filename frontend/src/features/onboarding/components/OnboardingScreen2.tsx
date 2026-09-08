import './OnboardingScreen.css';

interface OnboardingScreen2Props {
  onNext: () => void;
}

export const OnboardingScreen2 = ({ onNext }: OnboardingScreen2Props) => {
  return (
    <div className="onboarding-screen" data-name="인트로_2" data-node-id="4017:20342">
      {/* Title Section */}
      <div className="typea-title-group-a" data-name="Title" data-node-id="4017:20536">
        <h1 className="typea-title">실시간 채팅</h1>
        <p className="typea-subtitle">채팅으로 먼저 얘기 나눠보세요</p>
      </div>

      {/* Main Content */}
      <div className="onboarding-content" data-name="Body" data-node-id="4017:20517">
        {/* Background - 화면 전체 너비 */}
        <div className="onboarding-card-bg" data-name="BG" data-node-id="4017:20519">
          <img
            src="/assets/images/onboarding/body/onboarding_body_bg.png"
            alt="Background"
            className="onboarding-bg-image"
            onError={() => {
              console.error('Background image failed to load');
            }}
          />
        </div>

        {/* Card: Img 오버레이 */}
        <div className="onboarding-card" data-name="Card" data-node-id="4017:20518">
          {/* Main Image (오버레이) */}
          <div className="onboarding-card-img" data-name="Img" data-node-id="4017:20521">
            <img
              src="/assets/images/onboarding/body/onboarding_2_body.png"
              alt="Onboarding Content"
              className="onboarding-main-image"
              onError={() => {
                console.error('Main image failed to load');
              }}
            />
          </div>
        </div>

        {/* Indicator - 오버레이 */}
        <div className="onboarding-indicator" data-name="Indigator" data-node-id="4017:20535">
          <div className="indicator-dot" />
          <div className="indicator-dot active" />
          <div className="indicator-dot" />
          <div className="indicator-dot" />
        </div>
      </div>

      {/* Button */}
      <div className="onboarding-button-section" data-name="Btn" data-node-id="4017:20515">
        <button
          className="onboarding-button"
          onClick={onNext}
          data-name="Property 1=Full, Property 2=Primary"
          data-node-id="168:6785"
        >
          지금 골드펫 시작하기
        </button>
      </div>
    </div>
  );
};

