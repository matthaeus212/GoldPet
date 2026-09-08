import { useState, useRef } from 'react';
import type { AppNoticeResponse } from '../../services/noticeService';
import './NoticePopupModal.css';
import { useOverlayColor } from '../../hooks/useOverlayColor';

interface NoticePopupModalProps {
  notices: AppNoticeResponse[];
}

function getTodayKey(id: number): string {
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  return `notice_dismissed_${id}_${today}`;
}

function isDismissedToday(id: number): boolean {
  return localStorage.getItem(getTodayKey(id)) === 'true';
}

export default function NoticePopupModal({ notices }: NoticePopupModalProps) {
  const popupNotices = notices
    .filter((n) => n.type === 'POPUP_MODAL' && !isDismissedToday(n.id))
    .sort((a, b) => b.priority - a.priority);

  const [currentIndex, setCurrentIndex] = useState(0);
  const [imageIndex, setImageIndex] = useState(0);
  const [dismissChecked, setDismissChecked] = useState(false);
  const touchStartX = useRef(0);

  useOverlayColor(popupNotices.length > 0 && currentIndex < popupNotices.length);

  if (popupNotices.length === 0 || currentIndex >= popupNotices.length) {
    return null;
  }

  const notice = popupNotices[currentIndex];

  const handleClose = () => {
    if (dismissChecked && notice.isDismissible) {
      localStorage.setItem(getTodayKey(notice.id), 'true');
    }
    setDismissChecked(false);
    setImageIndex(0);
    setCurrentIndex((prev) => prev + 1);
  };

  const handleLinkClick = () => {
    if (notice.linkUrl) {
      window.location.href = notice.linkUrl;
    }
  };

  return (
    <div className="notice-popup-overlay">
      <div className="notice-popup-modal">
        <div className="notice-popup-content-wrap">
          {/* Top row: dismiss checkbox (left) + close button (right) */}
          <div className="notice-popup-top-row">
            {notice.isDismissible ? (
              <div className="notice-popup-dismiss-row">
                <input
                  type="checkbox"
                  id="notice-dismiss-check"
                  checked={dismissChecked}
                  onChange={(e) => setDismissChecked(e.target.checked)}
                />
                <label htmlFor="notice-dismiss-check" className="notice-popup-dismiss-label">
                  오늘 하루 열지 않기
                </label>
              </div>
            ) : (
              <div />
            )}
            <button className="notice-popup-close-btn" onClick={handleClose} aria-label="닫기">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M18 6L6 18M6 6l12 12" stroke="#614108" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </button>
          </div>

          {/* Image carousel */}
          {notice.imageUrls.length > 0 && (
            <div className="notice-popup-images">
              <div
                className="notice-popup-images-track"
                style={{ transform: `translateX(-${imageIndex * 100}%)` }}
                onTouchStart={(e) => { touchStartX.current = e.touches[0].clientX; }}
                onTouchEnd={(e) => {
                  const diff = touchStartX.current - e.changedTouches[0].clientX;
                  if (Math.abs(diff) > 50) {
                    if (diff > 0 && imageIndex < notice.imageUrls.length - 1) setImageIndex(imageIndex + 1);
                    if (diff < 0 && imageIndex > 0) setImageIndex(imageIndex - 1);
                  }
                }}
              >
                {notice.imageUrls.map((url, i) => (
                  <img
                    key={i}
                    src={url}
                    alt={`${notice.title} ${i + 1}`}
                    className="notice-popup-image"
                    onClick={notice.linkUrl ? handleLinkClick : undefined}
                    style={{ cursor: notice.linkUrl ? 'pointer' : 'default' }}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Dot indicators */}
          {notice.imageUrls.length > 1 && (
            <div className="notice-popup-dots">
              {notice.imageUrls.map((_, i) => (
                <span
                  key={i}
                  className={`notice-popup-dot${i === imageIndex ? ' active' : ''}`}
                  onClick={() => setImageIndex(i)}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
