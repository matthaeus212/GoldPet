import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { homeService } from '../../services/homeService';
import { friendService } from '../../services/friendService';
import { noticeService } from '../../services/noticeService';
import type { AppNoticeResponse } from '../../services/noticeService';
import { useAlert } from '../../contexts/AlertContext';
import { CACHE_TIME } from '../../config/queryConfig';
import { Loading } from '../../components/common/Loading';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import NoticePopupModal from '../../components/common/NoticePopupModal';
import MaintenanceBanner from '../../components/common/MaintenanceBanner';
import EventBanner from '../../components/common/EventBanner';
import { FriendRecommendCard } from './components/FriendRecommendCard';
import { TodayWalkCard } from './components/TodayWalkCard';
import { PlacementBanner } from '../../components/common/PlacementBanner';
import { HomeQuickMenu } from './components/HomeQuickMenu';
import { AiProfileBanner } from './components/AiProfileBanner';
import { StreakWidget } from './components/StreakWidget';
import { streakService } from '../../services/streakService';
import './HomePage.css';

function ArrowRight() {
    return (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path
                d="M6 3.5L10.5 8L6 12.5"
                stroke="#614108"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    );
}

export default function HomePage() {
    const navigate = useNavigate();
    const { showAlert } = useAlert();
    const [likedIds, setLikedIds] = useState<Set<number>>(new Set());
    const [loadingId, setLoadingId] = useState<number | null>(null);
    const [notices, setNotices] = useState<AppNoticeResponse[]>([]);
    const [maintenanceNotice, setMaintenanceNotice] = useState<AppNoticeResponse | null>(null);
    const [showAiBanner, setShowAiBanner] = useState(true);
    const [galleryState, setGalleryState] = useState<{
        open: boolean; images: string[]; index: number;
    }>({ open: false, images: [], index: 0 });

    const { data: homeData, isLoading } = useQuery({
        queryKey: ['homeData'],
        queryFn: homeService.getHomeData,
        ...CACHE_TIME.DYNAMIC,
    });

    const { data: streakData } = useQuery({
        queryKey: ['streak', 'me'],
        queryFn: streakService.getMyStreak,
        ...CACHE_TIME.DYNAMIC,
    });

    useEffect(() => {
        const loadNotices = async () => {
            try {
                const [activeNotices, maintenance] = await Promise.all([
                    noticeService.getActiveNotices('home'),
                    noticeService.getMaintenanceNotice(),
                ]);
                setNotices(activeNotices);
                setMaintenanceNotice(maintenance);
            } catch (e) {
                console.warn('Notice loading failed:', e);
            }
        };
        loadNotices();
    }, []);

    const handleLike = async (id: number, name: string) => {
        if (loadingId) return;
        setLoadingId(id);
        try {
            const result = await friendService.likeUser(id, 'home');
            setLikedIds((prev) => new Set(prev).add(id));
            showAlert(
                result.isMutual
                    ? `${name}과 서로 좋아해가 되었어요! 💕`
                    : `${name}에게 좋아요를 보냈어요! ❤️`,
            );
        } catch {
            showAlert('좋아요를 보내는데 실패했습니다.');
        } finally {
            setLoadingId(null);
        }
    };

    if (isLoading) {
        return <Loading />;
    }

    if (!homeData) return null;

    const recommendations = homeData.recommendations ?? [];

    return (
        <>
        <div className={`home_page${showAiBanner ? ' home_page--with-banner' : ''}`}>
            {maintenanceNotice && <MaintenanceBanner notice={maintenanceNotice} />}
            <NoticePopupModal notices={notices} />
            <EventBanner notices={notices} />

            <img className="home_page__bg" src="/assets/images/home/home_redesign_bg.svg" alt="" />

            {/* 새로운 친구 추천 */}
            <section className="home_section" data-testid="home-friend-recommend">
                <div className="home_section__head">
                    <h2>새로운 친구 추천</h2>
                    <button
                        type="button"
                        className="home_section__more"
                        onClick={() => navigate('/friend-find')}
                    >
                        전체보기
                        <ArrowRight />
                    </button>
                </div>
                {recommendations.length > 0 ? (
                    <div className="home_friends__scroll">
                        {recommendations.map((item, idx) => (
                            <FriendRecommendCard
                                key={item.id}
                                item={item}
                                liked={likedIds.has(item.id)}
                                loading={loadingId === item.id}
                                onLike={handleLike}
                                onOpenGallery={(images, index) =>
                                    setGalleryState({ open: true, images, index })
                                }
                                isLCP={idx === 0}
                            />
                        ))}
                    </div>
                ) : (
                    <p className="home_friends__empty">주변 반려동물 친구를 찾아보세요!</p>
                )}
            </section>

            <PlacementBanner placement="HOME" />

            {/* 오늘의 산책 */}
            <section className="home_section">
                <div className="home_section__head">
                    <h2>오늘의 산책</h2>
                    <button
                        type="button"
                        className="home_section__more"
                        onClick={() => navigate('/my-walks')}
                    >
                        기록보기
                        <ArrowRight />
                    </button>
                </div>
                <TodayWalkCard
                    data={homeData.todayWalk}
                    onCreateCourse={() => navigate('/courses/create')}
                    onStartWalk={() => navigate('/walk')}
                />
            </section>

            {/* 산책 스트릭 */}
            {streakData && (
                <section className="home_section">
                    <div className="home_section__head">
                        <h2>산책 스트릭</h2>
                    </div>
                    <StreakWidget data={streakData} />
                </section>
            )}

            <HomeQuickMenu />
        </div>

        {showAiBanner && (
            <AiProfileBanner
                petName={homeData.myPetNames[0]}
                onClose={() => setShowAiBanner(false)}
            />
        )}

        <ImageGalleryModal
            isOpen={galleryState.open}
            images={galleryState.images}
            initialIndex={galleryState.index}
            onClose={() => setGalleryState((s) => ({ ...s, open: false }))}
        />
        </>
    );
}
