import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { walkService } from '../../services/walkService';
import type { WalkRecord, WalkPhotoItem } from '../../services/walkService';
import { WalkRouteThumbnail } from '../walk/components/WalkRouteThumbnail';
import { WalkPhotoGalleryModal } from '../walk/viewer/WalkPhotoGalleryModal';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { useMonthNavigation } from '../../hooks/useMonthNavigation';
import './MyWalkPage.css';

function PhotoCollection() {
    const navigate = useNavigate();
    const [modalPhoto, setModalPhoto] = useState<WalkPhotoItem | null>(null);

    const { data } = useQuery({
        queryKey: ['walk', 'my', 'photos', 'mywalk-collection'],
        queryFn: () => walkService.getMyPhotos({ page: 0, size: 32 }),
    });

    const photos: WalkPhotoItem[] = useMemo(
        () => (data?.content ?? []).filter((p) => !!p.imageUrl).slice(0, 8),
        [data],
    );

    if (photos.length === 0) return null;

    const rows = [photos.slice(0, 4), photos.slice(4, 8)];

    return (
        <div className="mywalk_photo_section">
            <div
                className="mywalk_photo_title_row"
                onClick={() => navigate('/walk-photos')}
                style={{ cursor: 'pointer' }}
            >
                <span className="mywalk_photo_title">산책 사진 모아보기</span>
                <span className="mywalk_photo_arrow">
                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <path d="M6 4L10 8L6 12" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </span>
            </div>
            <div className="mywalk_photo_grid">
                {rows.map((row, rowIdx) => (
                    <div key={rowIdx} className="mywalk_photo_row">
                        {row.map((photo) => {
                            const src = photo.imageUrlThumb ?? photo.imageUrl;
                            return (
                                <div
                                    key={photo.id}
                                    className="mywalk_photo_item"
                                    onClick={() => setModalPhoto(photo)}
                                    style={{ cursor: 'pointer', overflow: 'hidden' }}
                                >
                                    {src && (
                                        <img
                                            src={src}
                                            alt=""
                                            loading="lazy"
                                            decoding="async"
                                            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                                        />
                                    )}
                                </div>
                            );
                        })}
                    </div>
                ))}
            </div>

            {modalPhoto && (
                <WalkPhotoGalleryModal
                    mode="owned"
                    walkId={modalPhoto.walkId}
                    initialSpotId={modalPhoto.id}
                    initialPhoto={modalPhoto}
                    onClose={() => setModalPhoto(null)}
                />
            )}
        </div>
    );
}

export const MyWalkPage = () => {
    const navigate = useNavigate();
    const { currentMonth: yearMonth, goToPrevMonth, goToNextMonth, isCurrentMonth, formatMonth } = useMonthNavigation();

    const {
        data,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
    } = useInfiniteQuery({
        queryKey: ['walk', 'my', 'paged', yearMonth],
        queryFn: ({ pageParam = 0 }) => walkService.getWalkRecordsPaged(pageParam, 10, yearMonth),
        getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
        initialPageParam: 0,
    });

    const walkRecords = data?.pages.flatMap(p => p.content) ?? [];
    const isEmpty = data !== undefined && walkRecords.length === 0;

    const sentinelRef = useRef<HTMLDivElement>(null);
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

    return (
        <SubPageLayout title="내 산책 기록" onBack={() => navigate(-1)}>
            <div id="walkContainer">
                <div className="mywalk_content">
                    {/* Month selector */}
                    <div className="mywalk_month_bar">
                        <div className="mywalk_month_nav">
                            <button type="button" className="mywalk_month_arrow" onClick={goToPrevMonth}>
                                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                    <path d="M10 4L6 8L10 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                            <span className="mywalk_month_label">{formatMonth(yearMonth)}</span>
                            <button
                                type="button"
                                className="mywalk_month_arrow"
                                onClick={goToNextMonth}
                                disabled={isCurrentMonth}
                                style={{ opacity: isCurrentMonth ? 0.3 : 1 }}
                            >
                                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                    <path d="M6 4L10 8L6 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                        </div>
                        <button type="button" className="mywalk_calendar_btn" aria-label="캘린더">
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                <rect x="2" y="3" width="12" height="11" rx="2" stroke="#614108" strokeWidth="1.2"/>
                                <path d="M2 7H14" stroke="#614108" strokeWidth="1.2"/>
                                <rect x="5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                                <rect x="9.5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                            </svg>
                        </button>
                    </div>

                    {/* Filter row */}
                    <div className="mywalk_filter_row">
                        <button type="button" className="mywalk_filter_pill">
                            필터
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                <path d="M4 6L8 10L12 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                        </button>
                    </div>

                    {/* Walk records */}
                    {isEmpty ? (
                        <div className="mywalk_empty_card">
                            <span className="mywalk_empty_text">산책 기록이 없습니다.</span>
                        </div>
                    ) : (
                        <div className="mywalk_records_card">
                            {walkRecords.map((record: WalkRecord, index: number) => (
                                <div key={record.id}>
                                    <MyWalkRecordItem
                                        record={record}
                                        onClick={() => navigate(`/walk/detail/${record.id}`)}
                                    />
                                    {index < walkRecords.length - 1 && (
                                        <div className="mywalk_record_divider" />
                                    )}
                                </div>
                            ))}
                        </div>
                    )}

                    {isFetchingNextPage && (
                        <p style={{ color: '#727272', fontSize: 14, textAlign: 'center', padding: '8px 0' }}>
                            불러오는 중...
                        </p>
                    )}
                    <div ref={sentinelRef} style={{ height: 1 }} />

                    {/* 산책 사진 모아보기 section */}
                    {!isEmpty && <PhotoCollection />}

                    {/* Empty state CTA */}
                    {isEmpty && (
                        <div className="mywalk_start_btn_wrap">
                            <button
                                type="button"
                                className="mywalk_start_btn"
                                onClick={() => navigate('/walk/map')}
                            >
                                산책 하기
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </SubPageLayout>
    );
};

function MyWalkRecordItem({ record, onClick }: { record: WalkRecord; onClick: () => void }) {
    const petName = record.petNames?.[0] ?? '반려동물';
    const extraCount = (record.petNames?.length ?? 0) > 1 ? ` 외 ${(record.petNames?.length ?? 1) - 1}` : '';
    const petPhoto = record.petProfileImageUrls?.[0] ?? null;

    const formattedDate = record.date
        ? record.date.replace(/-/g, '.')
        : '';

    return (
        <div className="mywalk_record_item" onClick={onClick}>
            {/* Header: pet info + date + gold badge */}
            <div className="mywalk_record_header">
                <div className="mywalk_record_pet_info">
                    {petPhoto ? (
                        <img
                            className="mywalk_pet_photo"
                            src={petPhoto}
                            alt={petName}
                            onError={(e) => { (e.target as HTMLImageElement).src = '/assets/images/common/profile_none_img.svg'; }}
                        />
                    ) : (
                        <div className="mywalk_pet_photo" style={{ background: '#EFE1C4', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="8" r="4" stroke="#614108" strokeWidth="1.8"/>
                                <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" stroke="#614108" strokeWidth="1.8" strokeLinecap="round"/>
                            </svg>
                        </div>
                    )}
                    <span className="mywalk_pet_name">{petName}{extraCount}</span>
                </div>
                <div className="mywalk_record_right">
                    <span className="mywalk_record_date">{formattedDate}</span>
                    <span className="mywalk_gold_badge">10G 적립</span>
                </div>
            </div>

            {/* Body: map thumbnail + stats */}
            <div className="mywalk_record_body">
                <div className="mywalk_record_map">
                    <WalkRouteThumbnail walkId={record.id} hasPath={record.hasPath ?? !!record.path?.length} size={60} />
                </div>
                <div className="mywalk_record_stats_col">
                    <div className="mywalk_record_stats">
                        <span className="mywalk_record_stat">{record.distance}</span>
                        <span className="mywalk_record_dot" />
                        <span className="mywalk_record_stat">{record.duration}</span>
                    </div>
                    {record.startAddress && (
                        <span className="mywalk_record_location">
                            {record.startAddress}{record.endAddress ? ` ~ ${record.endAddress}` : ''}
                        </span>
                    )}
                </div>
            </div>
        </div>
    );
}

export default MyWalkPage;
