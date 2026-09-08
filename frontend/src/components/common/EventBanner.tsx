import type { AppNoticeResponse } from '../../services/noticeService';
import './EventBanner.css';

interface EventBannerProps {
  notices: AppNoticeResponse[];
}

export default function EventBanner({ notices }: EventBannerProps) {
  const eventNotices = notices
    .filter((n) => n.type === 'EVENT_BANNER')
    .sort((a, b) => b.priority - a.priority);

  if (eventNotices.length === 0) return null;

  const handleClick = (notice: AppNoticeResponse) => {
    if (notice.linkUrl) {
      window.location.assign(notice.linkUrl);
    }
  };

  return (
    <section className="event-banner-section">
      <div className="event-banner-scroll">
        {eventNotices.map((notice) => (
          <div
            key={notice.id}
            className="event-banner-card"
            onClick={() => handleClick(notice)}
            style={{ cursor: notice.linkUrl ? 'pointer' : 'default' }}
          >
            {notice.imageUrls.length > 0 && (
              <img
                src={notice.imageUrls[0]}
                alt={notice.title}
                className="event-banner-card-img"
              />
            )}
            <div className="event-banner-card-overlay">
              <p className="event-banner-card-title">{notice.title}</p>
              {notice.content && (
                <p className="event-banner-card-content">{notice.content}</p>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
