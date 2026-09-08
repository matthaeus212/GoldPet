import React from 'react';
import { useNavigate } from 'react-router-dom';
import './SuspendedAccountPage.css';

const SuspendedAccountPage: React.FC = () => {
  const navigate = useNavigate();

  const handleConfirm = () => {
    navigate('/login');
  };

  return (
    <div className="suspended-account-page">
      <div className="suspended-account-body">
        <div className="typea-title-group-bc">
          <h1 className="typea-title">계정 정지 안내</h1>
        </div>
        <div className="suspended-account-description">
          <p>고객님의 계정이 정지되었습니다.<br />서비스 이용이 제한됩니다.</p>
        </div>

        <div className="suspended-account-notice">
          <p>계정 정지에 대한 문의는<br />고객센터로 연락해 주세요.</p>
        </div>
      </div>

      <div className="suspended-account-button-container">
        <button className="suspended-account-button" onClick={handleConfirm}>
          확인
        </button>
      </div>
    </div>
  );
};

export default SuspendedAccountPage;
