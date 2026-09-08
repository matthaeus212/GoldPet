import { logger } from '../../utils/logger';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { nativeBridge } from '../../bridge/nativeBridge';
import './PermissionsPage.css';

export const PermissionsPage = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);

  const handleRequestPermissions = async () => {
    if (isLoading) return;
    setIsLoading(true);
    try {
      // 모든 권한을 한 번에 요청 (Flutter PermissionService가 내부적으로 순차 처리)
      logger.debug('모든 권한 일괄 요청 중...');
      const results = await nativeBridge.callMethod('requestAllPermissions');
      logger.debug('모든 권한 요청 완료:', results);
      localStorage.setItem('hasSeenPermissions', 'true');
      navigate('/benefits', { replace: true });
    } catch (error) {
      console.error('권한 요청 중 오류 발생:', error);
      // 권한 거부 시에도 다음 화면으로 이동 (선택적)
      localStorage.setItem('hasSeenPermissions', 'true');
      navigate('/benefits', { replace: true });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="permissions-page">
      {/* Title Section */}
      <div className="typea-title-group-a">
        <h1 className="typea-title">앱 접근 권한 안내</h1>
      </div>

      <div className="permissions-content">
        <div className="permissions-list">
          <div className="permission-item">
            <div className="permission-icon-wrap">
              <img src="/assets/images/permissions/icon_location.png" alt="위치" />
            </div>
            <div className="permission-text">
              <h3>위치 정보</h3>
              <p>주변 친구 찾기 및 산책 경로 기록에 필요합니다.</p>
            </div>
          </div>

          <div className="permission-item">
            <div className="permission-icon-wrap">
              <img src="/assets/images/permissions/icon_camera.png" alt="카메라" />
            </div>
            <div className="permission-text">
              <h3>카메라</h3>
              <p>펫 사진 촬영 및 산책 중 사진 기록에 필요합니다.</p>
            </div>
          </div>

          <div className="permission-item">
            <div className="permission-icon-wrap">
              <img src="/assets/images/permissions/icon_photo.png" alt="사진" />
            </div>
            <div className="permission-text">
              <h3>사진 라이브러리</h3>
              <p>앨범에서 사진을 선택하여 업로드할 때 필요합니다.</p>
            </div>
          </div>

          <div className="permission-item">
            <div className="permission-icon-wrap">
              <img src="/assets/images/permissions/icon_notification.png" alt="알림" />
            </div>
            <div className="permission-text">
              <h3>알림</h3>
              <p>채팅, 친구 요청 등 주요 소식을 받을 때 필요합니다.</p>
            </div>
          </div>
        </div>
      </div>

      <div className="permissions-footer">
        <button
          className="permissions-button primary"
          onClick={handleRequestPermissions}
          disabled={isLoading}
        >
          {isLoading ? '요청 중...' : '다음'}
        </button>
      </div>
    </div>
  );
};
