import { useEffect, useState } from 'react';
import { noticeService } from '../../services/noticeService';
import type { AppNoticeResponse } from '../../services/noticeService';

interface MaintenanceGuardProps {
  children: React.ReactNode;
}

export default function MaintenanceGuard({ children }: MaintenanceGuardProps) {
  const [maintenance, setMaintenance] = useState<AppNoticeResponse | null>(null);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    const check = async () => {
      try {
        const notice = await noticeService.getMaintenanceNotice();
        setMaintenance(notice);
      } catch {
        // API 실패 시 정상 진행
        setMaintenance(null);
      } finally {
        setChecked(true);
      }
    };
    check();

    // 60초마다 재확인 (해제 시 자동 복구)
    const interval = setInterval(check, 60_000);
    return () => clearInterval(interval);
  }, []);

  if (!checked) return null;

  // 인증 관련 경로는 유지보수 모드에서도 접근 허용 (OAuth 콜백 팝업 등)
  const EXEMPT_PATHS = ['/loginSuccess', '/login', '/splash'];
  const isExempt = EXEMPT_PATHS.some(p => window.location.pathname.startsWith(p));

  if (maintenance && !isExempt) {
    return (
      <div className="gp-maintenance-overlay">
        <div className="gp-maintenance-content">
          {/* 브랜딩 영역 */}
          <div className="gp-maintenance-branding">
            <p className="gp-maintenance-tagline">펫 라이프 소셜 서비스</p>
            <img
              className="gp-maintenance-logo"
              src="/assets/images/common/logo.svg"
              alt="Gold pet"
            />
          </div>

          {/* 유지보수 안내 */}
          <div className="gp-maintenance-card">
            <div className="gp-maintenance-icon-circle">
              <img
                className="gp-maintenance-icon-img"
                src="/assets/images/common/maintenance_dog.svg"
                alt=""
              />
            </div>
            <div className="gp-maintenance-text">
              <h1 className="gp-maintenance-title">{maintenance.title}</h1>
              {maintenance.content && (
                <p className="gp-maintenance-desc">{maintenance.content}</p>
              )}
              <p className="gp-maintenance-footer">이용에 불편을 드려 죄송합니다.</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
