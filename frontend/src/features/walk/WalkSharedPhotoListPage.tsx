import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { BottomNav } from '../../components/common/BottomNav';
import { walkService } from '../../services/walkService';
import type { WalkPhotoItem } from '../../services/walkService';
import { WalkPhotoGalleryModal } from './viewer/WalkPhotoGalleryModal';
import './WalkPhotoGallery.css';

type ViewMode = 'month' | 'day' | 'year';

const MODE_LABELS: Record<ViewMode, string> = {
  month: '월',
  day: '일',
  year: '년',
};

function getGroupKey(date: string, mode: ViewMode): string {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  if (mode === 'year') return `${year}`;
  if (mode === 'month') return `${year}.${month}`;
  return `${year}.${month}.${day}`;
}

export default function WalkSharedPhotoListPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<ViewMode>('month');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [modalPhoto, setModalPhoto] = useState<WalkPhotoItem | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['walk', 'public', 'photos'],
    queryFn: ({ pageParam = 0 }) => walkService.getPublicPhotos({ page: pageParam as number, size: 20 }),
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    initialPageParam: 0,
  });

  const photos = useMemo<WalkPhotoItem[]>(
    () => data?.pages.flatMap((p) => p.content) ?? [],
    [data],
  );

  const groups = useMemo(() => {
    const map = new Map<string, WalkPhotoItem[]>();
    photos.forEach((photo: WalkPhotoItem) => {
      const key = getGroupKey(photo.walkDate, mode);
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(photo);
    });
    return Array.from(map.entries()).map(([label, items]) => ({ label, items }));
  }, [photos, mode]);

  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage();
      }
    },
    [hasNextPage, isFetchingNextPage, fetchNextPage]
  );

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(handleIntersect, { rootMargin: '200px' });
    observer.observe(el);
    return () => observer.disconnect();
  }, [handleIntersect]);

  useEffect(() => {
    if (!dropdownOpen) return;
    const handler = () => setDropdownOpen(false);
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
  }, [dropdownOpen]);

  return (
    <div className="wgp_page">
      <div className="wgp_header">
        <button type="button" className="wgp_back_btn" onClick={() => navigate(-1)}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M15 6L9 12L15 18" stroke="#614108" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <span className="wgp_header_title">산책 사진 모아보기</span>
      </div>

      <div className="wgp_control_bar">
        <div
          className="wgp_mode_selector"
          onClick={(e) => {
            e.stopPropagation();
            setDropdownOpen((v) => !v);
          }}
        >
          <span className="wgp_mode_label">{MODE_LABELS[mode]}</span>
          <svg className={`wgp_chevron${dropdownOpen ? ' open' : ''}`} width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M4 6L8 10L12 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          {dropdownOpen && (
            <div className="wgp_dropdown">
              {(['month', 'day', 'year'] as ViewMode[]).map((m) => (
                <div
                  key={m}
                  className={`wgp_dropdown_item${mode === m ? ' active' : ''}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    setMode(m);
                    setDropdownOpen(false);
                  }}
                >
                  {MODE_LABELS[m]}
                </div>
              ))}
            </div>
          )}
        </div>
        <span className="wgp_source_label">공개 산책 사진</span>
      </div>

      <div className="wgp_content">
        {groups.length === 0 && !isFetchingNextPage && (
          <div className="wgp_empty">
            <span>공유된 산책 사진이 없습니다.</span>
          </div>
        )}
        {groups.map((group) => (
          <div key={group.label} className="wgp_group">
            <div className="wgp_group_header">
              <span className="wgp_group_label">{group.label}</span>
              <span className="wgp_group_count">{group.items.length}</span>
            </div>
            <div className="wgp_photo_grid">
              {group.items.map((photo: WalkPhotoItem) => {
                const src = photo.imageUrlThumb ?? photo.imageUrl;
                return (
                  <div
                    key={`${photo.walkId}-${photo.id}`}
                    className="wgp_photo_cell"
                    onClick={() => setModalPhoto(photo)}
                  >
                    {src ? (
                      <img src={src} alt="" className="wgp_photo_img" loading="lazy" decoding="async" />
                    ) : (
                      <div className="wgp_photo_placeholder" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
        {isFetchingNextPage && <p className="wgp_loading">불러오는 중...</p>}
        <div ref={sentinelRef} style={{ height: 1 }} />
      </div>

      <BottomNav />

      {modalPhoto && (
        <WalkPhotoGalleryModal
          mode="shared"
          walkId={modalPhoto.walkId}
          initialSpotId={modalPhoto.id}
          initialPhoto={modalPhoto}
          onClose={() => setModalPhoto(null)}
        />
      )}
    </div>
  );
}
