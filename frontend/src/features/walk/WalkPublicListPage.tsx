import { useRef, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { walkService } from '../../services/walkService';
import type { WalkRecord } from '../../services/walkService';
import { WalkRecordItem } from './WalkPage';
import './WalkPage.css';

export default function WalkPublicListPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const yearMonth = searchParams.get('yearMonth') ?? undefined;
    const province = searchParams.get('province') ?? undefined;

    const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useInfiniteQuery({
        queryKey: ['walk', 'public', 'paged', 'all', yearMonth, province],
        queryFn: ({ pageParam = 0 }) =>
            walkService.getPublicWalksPaged(pageParam, 20, yearMonth, province),
        getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
        initialPageParam: 0,
    });

    const walkRecords = data?.pages.flatMap((p) => p.content) ?? [];

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

    const filterLabel = [
        yearMonth ? yearMonth.replace('-', '.') : null,
        province ?? null,
    ]
        .filter(Boolean)
        .join(' · ');

    return (
        <div
            style={{
                display: 'flex',
                flexDirection: 'column',
                minHeight: 'var(--app-height, 100dvh)',
                background: 'var(--color-bg, #fff7e6)',
                fontFamily: 'var(--font-family)',
            }}
        >
            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    height: 60,
                    padding: '0 16px',
                    background: 'var(--color-bg, #fff7e6)',
                    flexShrink: 0,
                }}
            >
                <button
                    type="button"
                    onClick={() => navigate(-1)}
                    style={{
                        background: 'none',
                        border: 'none',
                        padding: 0,
                        cursor: 'pointer',
                        width: 32,
                        height: 32,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                        <path
                            d="M15 6L9 12L15 18"
                            stroke="#614108"
                            strokeWidth="1.8"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                        />
                    </svg>
                </button>
                <span
                    style={{
                        fontSize: 16,
                        fontWeight: 600,
                        color: 'var(--color-text-primary, #614108)',
                        marginLeft: 8,
                    }}
                >
                    산책 기록
                </span>
                {filterLabel && (
                    <span
                        style={{
                            marginLeft: 'auto',
                            fontSize: 13,
                            color: 'var(--color-text-secondary, #727272)',
                        }}
                    >
                        {filterLabel}
                    </span>
                )}
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '8px 16px 32px' }}>
                <div className="walk_records_card">
                    {walkRecords.map((record: WalkRecord, index: number) => (
                        <div key={record.id}>
                            <WalkRecordItem
                                record={record}
                                onClick={() => navigate(`/walk/detail/${record.id}`)}
                                showUser={true}
                            />
                            {index < walkRecords.length - 1 && (
                                <div className="walk_record_divider" />
                            )}
                        </div>
                    ))}
                    {!isLoading && walkRecords.length === 0 && (
                        <div className="walk_record_empty">산책 기록이 없습니다.</div>
                    )}
                </div>
                {isFetchingNextPage && (
                    <p
                        style={{
                            color: '#727272',
                            fontSize: 14,
                            textAlign: 'center',
                            padding: '16px 0',
                        }}
                    >
                        불러오는 중...
                    </p>
                )}
                <div ref={sentinelRef} style={{ height: 1 }} />
            </div>
        </div>
    );
}
