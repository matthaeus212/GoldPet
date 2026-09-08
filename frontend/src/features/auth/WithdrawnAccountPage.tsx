import React from 'react';
import { useNavigate } from 'react-router-dom';
import './WithdrawnAccountPage.css';

const WithdrawnAccountPage: React.FC = () => {
  const navigate = useNavigate();

  const handleCancel = () => {
    navigate('/login');
  };

  return (
    <div className="withdrawn-account-page">
      <div className="withdrawn-account-body">
        <div className="typea-title-group-bc">
          <h1 className="typea-title">탈퇴 신청 계정 안내</h1>
        </div>
        <div className="withdrawn-account-description">
          <p>현재 탈퇴를 신청한 계정으로 로그인이 불가능합니다.<br />서비스 이용을 위한 재가입은 탈퇴 요청일로부터<br />7일 이후로 가능합니다.</p>
        </div>

        <div className="withdrawn-account-info-box">
          <p>탈퇴 요청일 : 2023. 03. 29</p>
        </div>

        <div className="withdrawn-account-notice">
          <p>탈퇴 취소를 통해 입력하신 계정으로<br />다시 로그인할 수 있습니다.</p>
        </div>
      </div>

      <div className="withdrawn-account-button-container">
        <button className="withdrawn-account-button" onClick={handleCancel}>
          탈퇴 취소
        </button>
      </div>
    </div>
  );
};

export default WithdrawnAccountPage;
