import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import './TermsAgreementPage.css';

export const TermsAgreementPage = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const [allAgreed, setAllAgreed] = useState(false);
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [privacyAgreed, setPrivacyAgreed] = useState(false);
  const [thirdPartyAgreed, setThirdPartyAgreed] = useState(false);
  const [marketingAgreed, setMarketingAgreed] = useState(false);

  const handleAllAgreed = (checked: boolean) => {
    setAllAgreed(checked);
    setTermsAgreed(checked);
    setPrivacyAgreed(checked);
    setThirdPartyAgreed(checked);
    setMarketingAgreed(checked);
  };

  const handleTermsAgreed = (checked: boolean) => {
    setTermsAgreed(checked);
    if (checked && privacyAgreed && thirdPartyAgreed && marketingAgreed) {
      setAllAgreed(true);
    } else {
      setAllAgreed(false);
    }
  };

  const handlePrivacyAgreed = (checked: boolean) => {
    setPrivacyAgreed(checked);
    if (checked && termsAgreed && thirdPartyAgreed && marketingAgreed) {
      setAllAgreed(true);
    } else {
      setAllAgreed(false);
    }
  };

  const handleThirdPartyAgreed = (checked: boolean) => {
    setThirdPartyAgreed(checked);
    if (checked && termsAgreed && privacyAgreed && marketingAgreed) {
      setAllAgreed(true);
    } else {
      setAllAgreed(false);
    }
  };

  const handleMarketingAgreed = (checked: boolean) => {
    setMarketingAgreed(checked);
    if (checked && termsAgreed && privacyAgreed && thirdPartyAgreed) {
      setAllAgreed(true);
    } else {
      setAllAgreed(false);
    }
  };

  const handleNext = () => {
    if (termsAgreed && privacyAgreed) {
      if (location.state?.isSns) {
        navigate('/signup/sns', { state: location.state });
      } else {
        navigate('/signup/form');
      }
    }
  };

  return (
    <div>

      {/* Title Section */}
      <div className="typea-title-group-bc">
        <h2 className="typea-title">약관 동의</h2>
        <p className="typea-subtitle">서비스 이용을 위해 약관에 동의해주세요.</p>
      </div>

      {/* Terms Content */}
      <div className="terms-body">
        <div className="terms-agreement-list">
          {/* 전체 동의 */}
          <div className="terms-agreement-item terms-agreement-all">
            <label className={`terms-checkbox-label ${allAgreed ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={allAgreed}
                onChange={(e) => handleAllAgreed(e.target.checked)}
                className="terms-checkbox"
              />
              <span className="terms-checkbox-text">전체 동의</span>
            </label>
          </div>

          <div className="terms-divider" />

          {/* 서비스 이용약관 (필수) */}
          <div className="terms-agreement-item">
            <label className={`terms-checkbox-label ${termsAgreed ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={termsAgreed}
                onChange={(e) => handleTermsAgreed(e.target.checked)}
                className="terms-checkbox"
              />
              <span className="terms-checkbox-text">
                서비스 이용약관 <span className="terms-required">(필수)</span>
              </span>
            </label>
            <button
              type="button"
              className={`terms-view-button ${termsAgreed ? 'checked' : ''}`}
              aria-label="보기"
              onClick={() => navigate('/terms/service')}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>

          {/* 개인정보 수집 및 이용 동의 (필수) */}
          <div className="terms-agreement-item">
            <label className={`terms-checkbox-label ${privacyAgreed ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={privacyAgreed}
                onChange={(e) => handlePrivacyAgreed(e.target.checked)}
                className="terms-checkbox"
              />
              <span className="terms-checkbox-text">
                개인정보 수집 및 이용 동의 <span className="terms-required">(필수)</span>
              </span>
            </label>
            <button
              type="button"
              className={`terms-view-button ${privacyAgreed ? 'checked' : ''}`}
              aria-label="보기"
              onClick={() => navigate('/terms/privacy')}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>

          {/* 개인정보 제3자 제공 동의 (선택) */}
          <div className="terms-agreement-item">
            <label className={`terms-checkbox-label ${thirdPartyAgreed ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={thirdPartyAgreed}
                onChange={(e) => handleThirdPartyAgreed(e.target.checked)}
                className="terms-checkbox"
              />
              <span className="terms-checkbox-text">
                개인정보 제3자 제공 동의 <span className="terms-optional">(선택)</span>
              </span>
            </label>
            <button
              type="button"
              className={`terms-view-button ${thirdPartyAgreed ? 'checked' : ''}`}
              aria-label="보기"
              onClick={() => navigate('/terms/third-party')}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>

          {/* 마케팅 수신 동의 (선택) */}
          <div className="terms-agreement-item">
            <label className={`terms-checkbox-label ${marketingAgreed ? 'checked' : ''}`}>
              <input
                type="checkbox"
                checked={marketingAgreed}
                onChange={(e) => handleMarketingAgreed(e.target.checked)}
                className="terms-checkbox"
              />
              <span className="terms-checkbox-text">
                마케팅 수신 동의 <span className="terms-optional">(선택)</span>
              </span>
            </label>
            <button
              type="button"
              className={`terms-view-button ${marketingAgreed ? 'checked' : ''}`}
              aria-label="보기"
              onClick={() => navigate('/terms/marketing')}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M6 12L10 8L6 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>
        </div>

        {/* Next Button */}
        <div className="terms-button-group">
          <button
            type="button"
            className={`terms-button ${termsAgreed && privacyAgreed ? 'active' : ''}`}
            onClick={handleNext}
            disabled={!termsAgreed || !privacyAgreed}
          >
            지금 골드펫 시작하기
          </button>
        </div>
      </div>
    </div>
  );
};

