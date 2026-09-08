// 휴면 계정 안내 및 해제 화면
import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../../services/authService';
import { DORMANT_DETAILS_KEY, type DormantDetails } from '../../services/api/client';
import './DormantAccountPage.css';

/**
 * STYLE-001: 이 화면은 리터럴 'OOO', 하드코딩된 가짜 날짜(2021.01.29), 그리고 아무 API 도 부르지
 * 않고 /login 으로만 이동하는 '휴면 해제' 버튼을 단 채 라이브 라우트에 방치돼 있었다.
 * (당시 백엔드에는 휴면 전환 배치도 해제 API 도 없어 애초에 도달할 수 없는 화면이기도 했다.)
 *
 * 이제 로그인 시도 시 서버가 해제 토큰과 실제 날짜를 details 로 내려주고,
 * 이 화면이 그것으로 안내를 렌더하고 해제 API 를 호출한다.
 */

function formatDate(iso?: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getFullYear()}. ${String(d.getMonth() + 1).padStart(2, '0')}. ${String(d.getDate()).padStart(2, '0')}`;
}

const DormantAccountPage: React.FC = () => {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const details = useMemo<DormantDetails | null>(() => {
    const raw = sessionStorage.getItem(DORMANT_DETAILS_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as DormantDetails;
    } catch {
      return null;
    }
  }, []);

  const lastLoginAt = formatDate(details?.lastLoginAt);
  const dormantAt = formatDate(details?.dormantAt);

  const handleActivate = async () => {
    // 해제 토큰이 없으면(세션 만료·직접 진입) 본인 확인이 불가하므로 다시 로그인해야 한다.
    if (!details?.activationToken) {
      navigate('/login');
      return;
    }
    if (isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    try {
      await authService.activateDormantAccount(details.activationToken);
      sessionStorage.removeItem(DORMANT_DETAILS_KEY);
      navigate('/login');
    } catch {
      setError('휴면 해제에 실패했어요. 다시 로그인한 뒤 시도해주세요.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="dormant-account-page">
      <div className="dormant-account-body">
        <div className="typea-title-group-bc">
          <h1 className="typea-title">휴면 계정 안내</h1>
        </div>
        <div className="dormant-account-description">
          <p>골드펫을 오랫동안 이용하지 않아<br />휴면 상태로 전환되었습니다.</p>
          <p>다시 이용하시려면 아래 버튼을<br />눌러 휴면을 해제해주세요.</p>
        </div>

        {(lastLoginAt || dormantAt) && (
          <div className="dormant-account-info-box">
            {lastLoginAt && <p>최근 접속일 : {lastLoginAt}</p>}
            {dormantAt && <p>휴면 전환일 : {dormantAt}</p>}
          </div>
        )}

        {error && <p className="dormant-account-error">{error}</p>}
      </div>

      <div className="dormant-account-button-container">
        <button
          className="dormant-account-button"
          onClick={() => void handleActivate()}
          disabled={isSubmitting}
        >
          {isSubmitting ? '해제 중…' : '휴면 해제'}
        </button>
      </div>
    </div>
  );
};

export default DormantAccountPage;
