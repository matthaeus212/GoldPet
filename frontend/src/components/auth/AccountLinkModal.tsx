import ReactDOM from 'react-dom';
import './AccountLinkModal.css';

const PROVIDER_NAMES: Record<string, string> = {
  kakao: '카카오',
  naver: '네이버',
  google: '구글',
  apple: '애플',
};

interface AccountLinkModalProps {
  linkSuggestion: {
    maskedEmail: string;
    existingProvider: string;
    tempToken: string;
  };
  newProvider: string;
  onLink: () => void;
  onCancel: () => void;
}

export const AccountLinkModal = ({
  linkSuggestion,
  newProvider,
  onLink,
  onCancel,
}: AccountLinkModalProps) => {
  const existingProviderName = PROVIDER_NAMES[linkSuggestion.existingProvider.toLowerCase()] ?? linkSuggestion.existingProvider;
  const newProviderName = PROVIDER_NAMES[newProvider.toLowerCase()] ?? newProvider;

  const content = (
    <div className="account-link-overlay">
      <div className="account-link-container">
        <h2 className="account-link-title">계정 연동 안내</h2>
        <p className="account-link-body">
          이 이메일(<span className="account-link-email">{linkSuggestion.maskedEmail}</span>)은
          이미 <strong>{existingProviderName}</strong>로 가입되어 있습니다.
          <br />
          기존 계정에 <strong>{newProviderName}</strong> 계정을 연결하시겠습니까?
        </p>
        <div className="account-link-buttons">
          <button type="button" className="account-link-btn-cancel" onClick={onCancel}>
            취소
          </button>
          <button type="button" className="account-link-btn-confirm" onClick={onLink}>
            계정 연결
          </button>
        </div>
      </div>
    </div>
  );

  return ReactDOM.createPortal(content, document.body);
};
