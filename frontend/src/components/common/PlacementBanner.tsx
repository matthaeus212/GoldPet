import { useNavigate } from 'react-router-dom';
import { usePlacementBanners } from '../../hooks/usePlacementBanners';
import type { BannerPlacement } from '../../hooks/usePlacementBanners';

interface PlacementBannerProps {
  placement: BannerPlacement;
}

/**
 * 배치별 배너 컴포넌트.
 * - data === undefined (로딩) → 미렌더 (높이 미예약 → CLS 없음)
 * - data === [] (빈 배열)   → null
 * - 이미지 박스: aspect-ratio 16/5 고정 (CLS 가드)
 * - 클릭: 외부 링크 → window.location, 내부 경로 → navigate
 */
export function PlacementBanner({ placement }: PlacementBannerProps) {
  const navigate = useNavigate();
  const data = usePlacementBanners(placement);

  if (data === undefined) return null;
  if (data.length === 0) return null;

  const banner = data[0];
  const imageSrc = banner.imageUrlViewer ?? banner.imageUrl;
  if (!imageSrc) return null;

  const handleClick = () => {
    if (!banner.link) return;
    if (banner.link.startsWith('http://') || banner.link.startsWith('https://')) {
      window.location.href = banner.link;
    } else {
      navigate(banner.link);
    }
  };

  return (
    <div className="placement_banner">
      <button
        type="button"
        className="placement_banner__btn"
        onClick={handleClick}
        disabled={!banner.link}
        aria-label={banner.title}
      >
        <img
          className="placement_banner__img"
          src={imageSrc}
          alt={banner.title}
          loading="lazy"
          decoding="async"
        />
      </button>
    </div>
  );
}
