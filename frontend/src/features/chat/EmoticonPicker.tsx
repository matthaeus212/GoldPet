import React, { useState, useRef, useCallback, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { emoticonService } from '../../services/emoticonService';
import { GpImage } from '../../components/common/GpImage';

interface EmoticonPickerProps {
  onSelect: (emoticon: { id: number; imageUrl: string }) => void;
  onClose: () => void;
}

const ITEMS_PER_PAGE = 12;
const EMOTICON_LAZY = import.meta.env.VITE_CHAT_EMOTICON_LAZY_ENABLED === 'true';

interface LazyEmoticonPageProps {
  children: React.ReactNode;
  scrollRoot: React.RefObject<HTMLDivElement>;
}

/** Renders children only once the page enters the scroll viewport (IntersectionObserver). */
const LazyEmoticonPage = ({ children, scrollRoot }: LazyEmoticonPageProps) => {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(!EMOTICON_LAZY);

  useEffect(() => {
    if (!EMOTICON_LAZY) return;
    const el = ref.current;
    const root = scrollRoot.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => { if (entry.isIntersecting) setVisible(true); },
      { root, threshold: 0.01 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [scrollRoot]);

  return (
    <div ref={ref} className="chat_detail_emoticon_page">
      {visible ? children : null}
    </div>
  );
};

const EmoticonPicker = ({ onSelect }: EmoticonPickerProps) => {
  const [page, setPage] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null!);

  const { data: emoticons = [] } = useQuery({
    queryKey: ['emoticons'],
    queryFn: emoticonService.getEmoticons,
    staleTime: Infinity,
  });

  const totalPages = Math.max(1, Math.ceil(emoticons.length / ITEMS_PER_PAGE));

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el || el.clientWidth === 0) return;
    const newPage = Math.round(el.scrollLeft / el.clientWidth);
    setPage(Math.min(newPage, totalPages - 1));
  }, [totalPages]);

  const scrollToPage = (p: number) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ left: p * el.clientWidth, behavior: 'smooth' });
  };

  return (
    <div className="chat_detail_emoticon_picker" onClick={(e) => e.stopPropagation()}>
      {emoticons.length === 0 ? (
        <div className="chat_detail_emoticon_empty">사용 가능한 이모티콘이 없습니다</div>
      ) : (
        <>
          <div
            ref={scrollRef}
            className="chat_detail_emoticon_scroll"
            onScroll={handleScroll}
          >
            {Array.from({ length: totalPages }).map((_, pageIndex) => {
              const pageItems = emoticons.slice(
                pageIndex * ITEMS_PER_PAGE,
                (pageIndex + 1) * ITEMS_PER_PAGE
              );
              return (
                <LazyEmoticonPage key={pageIndex} scrollRoot={scrollRef}>
                  <div className="chat_detail_emoticon_grid">
                    {pageItems.map((emoticon) => (
                      <button
                        key={emoticon.id}
                        type="button"
                        className="chat_detail_emoticon_item"
                        onClick={() => onSelect({ id: emoticon.id, imageUrl: emoticon.imageUrl })}
                        title={emoticon.name || undefined}
                      >
                        <GpImage
                          src={emoticon.imageUrl}
                          thumbnailSrc={emoticon.imageUrlThumbnail}
                          viewerSrc={emoticon.imageUrlViewer}
                          variant="thumbnail"
                          alt={emoticon.name || '이모티콘'}
                        />
                      </button>
                    ))}
                  </div>
                </LazyEmoticonPage>
              );
            })}
          </div>
          {totalPages > 1 && (
            <div className="chat_detail_emoticon_dots">
              {Array.from({ length: totalPages }).map((_, i) => (
                <button
                  key={i}
                  type="button"
                  className={`chat_detail_emoticon_dot${i === page ? ' active' : ''}`}
                  onClick={() => scrollToPage(i)}
                  aria-label={`페이지 ${i + 1}`}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default EmoticonPicker;
