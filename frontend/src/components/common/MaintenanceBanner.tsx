import type { AppNoticeResponse } from '../../services/noticeService';
import './MaintenanceBanner.css';

interface MaintenanceBannerProps {
  notice: AppNoticeResponse;
}

export default function MaintenanceBanner({ notice }: MaintenanceBannerProps) {
  return (
    <div className="maintenance-banner">
      <span className="maintenance-banner-icon">⚠️</span>
      <div className="maintenance-banner-text">
        <p className="maintenance-banner-title">{notice.title}</p>
        {notice.content && (
          <p className="maintenance-banner-content">{notice.content}</p>
        )}
      </div>
    </div>
  );
}
