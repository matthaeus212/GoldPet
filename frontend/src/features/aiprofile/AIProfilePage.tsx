import { useNavigate } from 'react-router-dom';
import './AIProfilePage.css';

function BtnBlobBg() {
  return (
    <svg className="ai-intro-btn-bg" preserveAspectRatio="none" viewBox="0 0 160 48" xmlns="http://www.w3.org/2000/svg">
      <path d="M0 24C0 37.2548 4.7 48 24 48H136C149.2 48 160 41.0824 160 24C160 10.7452 158.8 0.5 136 0H24C3.4 0 0 10.7452 0 24Z" fill="#614108"/>
    </svg>
  );
}

export default function AIProfilePage() {
  const navigate = useNavigate();

  return (
    <div className="ai-intro-container">
      <div className="ai-intro-txt-wrap">
        <strong className="ai-intro-title">나의 AI 반려동물을<br />만나보세요</strong>
        <p className="ai-intro-desc">
          평소에 상상했던 반려동물의 다양한 모습을<br />
          프로필로 만들어 보세요.<br /><br />
          반려동물이 없어도 걱정마세요.<br />
          당신이 원하는 AI 펫을 만들 수 있습니다.
        </p>
      </div>

      <div className="ai-intro-img-wrap">
        <img
          src="/assets/images/ai_make/intro_sample_3.png"
          alt="AI 프로필 예시"
          className="ai-intro-main-img"
        />
        <span className="ai-intro-bubble ai-intro-bubble--left">예쁘게 만들어달라멍</span>
        <span className="ai-intro-bubble ai-intro-bubble--right">빨리 만들어달라냥</span>
      </div>

      <div className="ai-intro-btn-wrap">
        <button
          type="button"
          className="ai-intro-btn"
          onClick={() => navigate('/ai-profile/simple')}
        >
          <BtnBlobBg />
          <span className="ai-intro-btn-text">간단하게 만들기</span>
        </button>
        <button
          type="button"
          className="ai-intro-btn"
          onClick={() => navigate('/ai-profile/custom')}
        >
          <BtnBlobBg />
          <span className="ai-intro-btn-text">원하는 대로 만들기</span>
        </button>
      </div>
    </div>
  );
}
