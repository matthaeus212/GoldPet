import { useState, useRef, useEffect, useCallback } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { CACHE_TIME } from '../../config/queryConfig';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { walkService, getTodayStats } from '../../services/walkService';
import { courseService } from '../../services/courseService';
import type { WalkRecord, WalkRankingEntry, WalkSession } from '../../services/walkService';
import { useShare } from '../share';
import type { CourseFilters } from '../course/utils/courseHelpers';
import { WalkRouteThumbnail } from './components/WalkRouteThumbnail';
import { WalkPhotoCollection } from './components/WalkPhotoCollection';
import { PlacementBanner } from '../../components/common/PlacementBanner';
import { CourseCard } from '../course/components/CourseCard';
import { CourseFilterWalkTab } from '../course/components/CourseFilterWalkTab';
import { RegionFilter } from '../../components/common/RegionFilter';
import { useDefaultProvince } from '../../hooks/useDefaultProvince';
import '../course/CoursePage.css';
import './WalkPage.css';

function getCurrentYearMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function CoursesTabContent({ initialRegion }: { initialRegion: string | undefined }) {
    const navigate = useNavigate();
    const [filters, setFilters] = useState<CourseFilters>({ sortBy: 'latest', region: initialRegion });
    const regionInitRef = useRef(false);

    useEffect(() => {
        if (!regionInitRef.current && initialRegion !== undefined) {
            regionInitRef.current = true;
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setFilters(prev => ({ ...prev, region: initialRegion }));
        }
    }, [initialRegion]);

    const {
        data,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
        isLoading,
    } = useInfiniteQuery({
        queryKey: ['courses', 'popular', 'walkTab', filters.difficulty, filters.sortBy, filters.region],
        queryFn: ({ pageParam = 0 }) =>
            courseService.getPopularCourses({
                difficulty: filters.difficulty,
                sortBy: filters.sortBy,
                region: filters.region,
                page: pageParam as number,
                size: 10,
            }),
        getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
        initialPageParam: 0,
        // PERF-013: staleTime 기본값 0 이면 탭을 오갈 때마다 전 페이지를 다시 받아온다.
        ...CACHE_TIME.DYNAMIC,
    });

    const courses = data?.pages.flatMap((p) => p.content) ?? [];

    const courseSentinelRef = useRef<HTMLDivElement>(null);
    const handleCourseIntersect = useCallback(
        (entries: IntersectionObserverEntry[]) => {
            if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
                fetchNextPage();
            }
        },
        [hasNextPage, isFetchingNextPage, fetchNextPage]
    );

    useEffect(() => {
        const el = courseSentinelRef.current;
        if (!el) return;
        const observer = new IntersectionObserver(handleCourseIntersect, { rootMargin: '200px' });
        observer.observe(el);
        return () => observer.disconnect();
    }, [handleCourseIntersect]);

    return (
        <div className="walk_courses_list">
            <CourseFilterWalkTab filters={filters} onFilterChange={setFilters} />

            {isLoading ? (
                <p style={{ color: '#727272', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>불러오는 중...</p>
            ) : courses.length === 0 ? (
                <div className="walk_records_card">
                    <div className="walk_record_empty">
                        등록된 코스가 없습니다.
                    </div>
                </div>
            ) : (
                <div className="walk_courses_card_list">
                    {courses.map((course) => (
                        <CourseCard
                            key={course.id}
                            course={course}
                            onClick={() => navigate(`/courses/${course.id}`)}
                        />
                    ))}
                </div>
            )}

            {isFetchingNextPage && (
                <p style={{ textAlign: 'center', padding: '16px 0', color: '#888', fontSize: 14 }}>불러오는 중...</p>
            )}
            <div ref={courseSentinelRef} style={{ height: 1 }} />

            <div className="walk_courses_create_btn_wrap">
                <button
                    type="button"
                    className="walk_courses_create_btn"
                    onClick={() => navigate('/courses/create')}
                >
                    산책 코스 등록하기
                </button>
            </div>
        </div>
    );
}

export const WalkPage = () => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const tabParam = searchParams.get('tab');
    const activeTab: 'records' | 'courses' | 'ranking' =
        tabParam === 'courses' || tabParam === 'ranking' ? tabParam : 'records';
    const setActiveTab = (tab: 'records' | 'courses' | 'ranking') => {
        if (tab === 'records') {
            setSearchParams({}, { replace: true });
        } else {
            setSearchParams({ tab }, { replace: true });
        }
    };
    const [yearMonth, setYearMonth] = useState<string>(getCurrentYearMonth());
    const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
    const [rankingSize, setRankingSize] = useState<number>(10);
    const currentYearMonth = getCurrentYearMonth();
    const [showMonthPicker, setShowMonthPicker] = useState(false);
    const [pickerYear, setPickerYear] = useState(() => Number(yearMonth.split('-')[0]));
    const [showRankingMonthPicker, setShowRankingMonthPicker] = useState(false);
    const [rankingPickerYear, setRankingPickerYear] = useState(() => Number(yearMonth.split('-')[0]));
    const [recordsFilterOpen, setRecordsFilterOpen] = useState(false);
    const [rankingFilterOpen, setRankingFilterOpen] = useState(false);

    const defaultProvince = useDefaultProvince();
    const [recordsProvince, setRecordsProvince] = useState<string | undefined>(undefined);
    const [rankingProvince, setRankingProvince] = useState<string | undefined>(undefined);
    const provinceInitRef = useRef(false);
    useEffect(() => {
        if (!provinceInitRef.current && defaultProvince !== undefined) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setRecordsProvince(defaultProvince);
            setRankingProvince(defaultProvince);
            provinceInitRef.current = true;
        }
    }, [defaultProvince]);

    const { data: rankingData, isLoading: rankingLoading } = useQuery({
        queryKey: ['walk', 'monthlyRanking', yearMonth, rankingSize, rankingProvince],
        queryFn: () => walkService.getMonthlyRanking(yearMonth, rankingSize, rankingProvince),
        enabled: activeTab === 'ranking',
        // PERF-013: 탭 토글마다 재요청되던 것을 막는다.
        ...CACHE_TIME.DYNAMIC,
    });

    function prevMonth() {
        const [y, m] = yearMonth.split('-').map(Number);
        const d = new Date(y, m - 2, 1);
        setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
        setSelectedUserId(null);
        setRankingSize(10);
    }

    function nextMonth() {
        const [y, m] = yearMonth.split('-').map(Number);
        const d = new Date(y, m, 1);
        setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
        setSelectedUserId(null);
        setRankingSize(10);
    }

    const RECORDS_PREVIEW_SIZE = 10;
    const { data: recordsPage } = useQuery({
        queryKey: ['walk', 'public', 'paged', yearMonth, recordsProvince, 'preview'],
        queryFn: () => walkService.getPublicWalksPaged(0, RECORDS_PREVIEW_SIZE, yearMonth, recordsProvince),
        enabled: activeTab === 'records',
        // PERF-013: 탭 토글마다 재요청되던 것을 막는다.
        ...CACHE_TIME.DYNAMIC,
    });

    const walkRecords = recordsPage?.content ?? [];
    const hasMoreRecords = recordsPage ? !recordsPage.last : false;
    const todayStats = getTodayStats(walkRecords);

    return (
        <div id="walkContainer">
            <div className="walk_content">
                {/* Today Stats Section */}
                <div className="walk_today_section">
                    <div className="walk_today_card">
                        <div className="walk_today_title">
                            오늘
                        </div>
                        <div className="walk_today_stats">
                            <div className="walk_stat_item">
                                <div className="walk_stat_content">
                                    <span className="walk_stat_label">산책 거리(km)</span>
                                    <span className="walk_stat_value">{todayStats.distance.toFixed(2)}</span>
                                </div>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <path d="M9 18l6-6-6-6" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                                </svg>
                            </div>
                            <div className="walk_stat_item">
                                <div className="walk_stat_content">
                                    <span className="walk_stat_label">소요 칼로리(Kcal)</span>
                                    <span className="walk_stat_value">{todayStats.calories.toLocaleString()}</span>
                                </div>
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <path d="M9 18l6-6-6-6" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                                </svg>
                            </div>
                        </div>
                    </div>

                    <div className="walk_action_btns">
                        <button
                            type="button"
                            className="walk_start_btn"
                            onClick={() => navigate('/walk/map')}
                        >
                            산책 시작
                        </button>
                        <button
                            type="button"
                            className="walk_course_create_btn"
                            onClick={() => navigate('/courses/create')}
                        >
                            코스 만들기
                        </button>
                    </div>
                </div>

                {/* Records / Ranking Tabs */}
                <div className="walk_records_section">
                    <div className="walk_group_tab">
                        <button
                            type="button"
                            className={activeTab === 'records' ? 'active' : ''}
                            onClick={() => setActiveTab('records')}
                        >
                            산책 기록
                        </button>
                        <button
                            type="button"
                            className={activeTab === 'courses' ? 'active' : ''}
                            onClick={() => setActiveTab('courses')}
                        >
                            추천 코스
                        </button>
                        <button
                            type="button"
                            className={activeTab === 'ranking' ? 'active' : ''}
                            onClick={() => setActiveTab('ranking')}
                        >
                            베스트 랭킹
                        </button>
                    </div>

                    {/* Records Tab */}
                    {activeTab === 'records' && (
                        <div className="walk_records_list">
                            <div className="walk_my_record_btn_wrap">
                                <button
                                    type="button"
                                    className="walk_my_record_btn"
                                    onClick={() => navigate('/my-walks')}
                                >
                                    나의 산책 기록
                                </button>
                            </div>
                            <div className="walk_month_selector">
                                <div className="walk_month_nav">
                                    <button type="button" className="walk_month_arrow" onClick={prevMonth}>
                                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                            <path d="M10 4L6 8L10 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                        </svg>
                                    </button>
                                    <span className="walk_month_label">
                                        {yearMonth.replace('-', '.')}
                                    </span>
                                    <button
                                        type="button"
                                        className="walk_month_arrow"
                                        onClick={nextMonth}
                                        disabled={yearMonth >= currentYearMonth}
                                        style={{ opacity: yearMonth >= currentYearMonth ? 0.3 : 1 }}
                                    >
                                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                            <path d="M6 4L10 8L6 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                        </svg>
                                    </button>
                                </div>
                                <button
                                    type="button"
                                    className="walk_month_calendar_btn"
                                    onClick={() => {
                                        setPickerYear(Number(yearMonth.split('-')[0]));
                                        setShowMonthPicker(true);
                                    }}
                                >
                                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                        <rect x="2" y="3" width="12" height="11" rx="2" stroke="#614108" strokeWidth="1.2"/>
                                        <path d="M2 7H14" stroke="#614108" strokeWidth="1.2"/>
                                        <rect x="5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                                        <rect x="9.5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                                    </svg>
                                </button>
                                {showMonthPicker && (
                                    <MonthPicker
                                        selectedYearMonth={yearMonth}
                                        currentYearMonth={currentYearMonth}
                                        pickerYear={pickerYear}
                                        onChangePickerYear={setPickerYear}
                                        onSelect={(ym) => {
                                            setYearMonth(ym);
                                            setSelectedUserId(null);
                                            setRankingSize(10);
                                            setShowMonthPicker(false);
                                        }}
                                        onClose={() => setShowMonthPicker(false)}
                                    />
                                )}
                            </div>
                            <div className="course_filter_wt_wrap">
                                <div className="course_filter_wt_toprow">
                                    <button
                                        type="button"
                                        className="course_filter_wt_pill"
                                        onClick={() => setRecordsFilterOpen(!recordsFilterOpen)}
                                    >
                                        필터
                                        <svg
                                            className={`course_filter_wt_pill_icon${recordsFilterOpen ? ' open' : ''}`}
                                            width="16"
                                            height="16"
                                            viewBox="0 0 16 16"
                                            fill="none"
                                        >
                                            <path d="M4 6L8 10L12 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                                        </svg>
                                    </button>
                                </div>
                                {recordsProvince && (
                                    <div className="walk_filter_chips_row">
                                        <button
                                            type="button"
                                            className="walk_filter_chip"
                                            onClick={() => setRecordsProvince(undefined)}
                                        >
                                            <span>지역</span>
                                            <span className="walk_filter_chip_value">{recordsProvince}</span>
                                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                                <path d="M11 5L5 11M5 5l6 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round"/>
                                            </svg>
                                        </button>
                                    </div>
                                )}
                                <div className={`course_filter_wt_panel_wrapper${recordsFilterOpen ? ' open' : ''}`}>
                                    <div className="course_filter_wt_panel_inner">
                                        <div className="course_filter_wt_panel">
                                            <div className="course_filter_wt_section">
                                                <span className="course_filter_wt_label">지역</span>
                                                <RegionFilter
                                                    selectedRegion={recordsProvince}
                                                    onRegionChange={setRecordsProvince}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
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
                                {walkRecords.length === 0 && (
                                    <div className="walk_record_empty">
                                        산책 기록이 없습니다.
                                    </div>
                                )}
                            </div>
                            {hasMoreRecords && (
                                <button
                                    type="button"
                                    className="walk_records_more_btn"
                                    onClick={() => {
                                        const params = new URLSearchParams({ yearMonth });
                                        if (recordsProvince) params.set('province', recordsProvince);
                                        navigate(`/walks?${params.toString()}`);
                                    }}
                                >
                                    더보기
                                </button>
                            )}
                            <WalkPhotoCollection />
                            <PlacementBanner placement="WALK" />
                        </div>
                    )}

                    {/* Courses Tab */}
                    {activeTab === 'courses' && (
                        <div className="walk_courses_tab">
                            <CoursesTabContent initialRegion={defaultProvince} />
                        </div>
                    )}

                    {/* Ranking Tab */}
                    {activeTab === 'ranking' && (
                        <div className="walk_ranking_wrap">
                            {/* Month Selector */}
                            <div className="walk_month_selector">
                                <div className="walk_month_nav">
                                    <button type="button" className="walk_month_arrow" onClick={prevMonth}>
                                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                            <path d="M10 4L6 8L10 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                        </svg>
                                    </button>
                                    <span className="walk_month_label">
                                        {yearMonth.replace('-', '.')}
                                    </span>
                                    <button
                                        type="button"
                                        className="walk_month_arrow"
                                        onClick={nextMonth}
                                        disabled={yearMonth >= currentYearMonth}
                                        style={{ opacity: yearMonth >= currentYearMonth ? 0.3 : 1 }}
                                    >
                                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                            <path d="M6 4L10 8L6 12" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                        </svg>
                                    </button>
                                </div>
                                <button
                                    type="button"
                                    className="walk_month_calendar_btn"
                                    onClick={() => {
                                        setRankingPickerYear(Number(yearMonth.split('-')[0]));
                                        setShowRankingMonthPicker(true);
                                    }}
                                >
                                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                        <rect x="2" y="3" width="12" height="11" rx="2" stroke="#614108" strokeWidth="1.2"/>
                                        <path d="M2 7H14" stroke="#614108" strokeWidth="1.2"/>
                                        <rect x="5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                                        <rect x="9.5" y="1" width="1.5" height="4" rx="0.75" fill="#614108"/>
                                    </svg>
                                </button>
                                {showRankingMonthPicker && (
                                    <MonthPicker
                                        selectedYearMonth={yearMonth}
                                        currentYearMonth={currentYearMonth}
                                        pickerYear={rankingPickerYear}
                                        onChangePickerYear={setRankingPickerYear}
                                        onSelect={(ym) => {
                                            setYearMonth(ym);
                                            setSelectedUserId(null);
                                            setRankingSize(10);
                                            setShowRankingMonthPicker(false);
                                        }}
                                        onClose={() => setShowRankingMonthPicker(false)}
                                    />
                                )}
                            </div>

                            <div className="course_filter_wt_wrap">
                                <div className="course_filter_wt_toprow">
                                    <button
                                        type="button"
                                        className="course_filter_wt_pill"
                                        onClick={() => setRankingFilterOpen(!rankingFilterOpen)}
                                    >
                                        필터{rankingProvince ? ` · ${rankingProvince}` : ''}
                                        <svg
                                            className={`course_filter_wt_pill_icon${rankingFilterOpen ? ' open' : ''}`}
                                            width="16"
                                            height="16"
                                            viewBox="0 0 16 16"
                                            fill="none"
                                        >
                                            <path d="M4 6L8 10L12 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                                        </svg>
                                    </button>
                                </div>
                                <div className={`course_filter_wt_panel_wrapper${rankingFilterOpen ? ' open' : ''}`}>
                                    <div className="course_filter_wt_panel_inner">
                                        <div className="course_filter_wt_panel">
                                            <div className="course_filter_wt_section">
                                                <span className="course_filter_wt_label">지역</span>
                                                <RegionFilter
                                                    selectedRegion={rankingProvince}
                                                    onRegionChange={(r) => { setRankingProvince(r); setSelectedUserId(null); setRankingSize(10); }}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {rankingLoading ? (
                                <p style={{ color: '#727272', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>
                                    불러오는 중...
                                </p>
                            ) : !rankingData || !rankingData.rankings || rankingData.rankings.length === 0 ? (
                                <div className="walk_records_card">
                                    <div className="walk_record_empty">
                                        이 달의 랭킹 기록이 없습니다.
                                    </div>
                                </div>
                            ) : (
                                <>
                                    {/* Best Couple Card */}
                                    {rankingData.bestCouple && (
                                        <BestCoupleCard entry={rankingData.bestCouple} />
                                    )}

                                    {/* Ranking List (excluding best couple) */}
                                    <div className="walk_ranking_list">
                                        {rankingData.rankings
                                            .filter(e => !rankingData.bestCouple || e.userId !== rankingData.bestCouple.userId)
                                            .map((entry) => (
                                                <RankingCard
                                                    key={entry.userId}
                                                    entry={entry}
                                                    isHighlighted={entry.userId === selectedUserId}
                                                    onSelect={() => setSelectedUserId(prev => prev === entry.userId ? null : entry.userId)}
                                                />
                                            ))}
                                    </div>
                                </>
                            )}

                            {rankingData && rankingData.rankings && rankingData.rankings.length >= rankingSize && rankingSize < 50 && (
                                <button
                                    type="button"
                                    className="walk_ranking_more_btn"
                                    onClick={() => setRankingSize(50)}
                                >
                                    더보기
                                    <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                        <path d="M4 6L8 10L12 6" stroke="#505050" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                    </svg>
                                </button>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

function BestCoupleCard({ entry }: { entry: WalkRankingEntry }) {
    return (
        <div className="walk_best_couple">
            <div className="walk_best_couple_badge">
                <img src="/assets/images/walk/best_icon.svg" alt="" />
                BEST 산책 커플
            </div>
            <div className="walk_best_couple_avatars">
                <div className="walk_best_couple_avatar">
                    <img
                        src={entry.petProfileImageUrl || '/assets/images/common/pet_none_img.svg'}
                        alt={entry.petName || ''}
                    />
                </div>
                <div className="walk_best_couple_avatar">
                    <img
                        src={entry.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
                        alt={entry.nickname}
                    />
                </div>
            </div>
            <div className="walk_best_couple_names">
                <span>{entry.petName || '반려동물'}</span>
                <svg width="18" height="16" viewBox="0 0 18 16" fill="none">
                    <path d="M9 14.65L7.84 13.595C3.72 9.845 1 7.365 1 4.325C1 1.845 2.936 0 5.4 0C6.792 0 8.128 0.6525 9 1.6775C9.872 0.6525 11.208 0 12.6 0C15.064 0 17 1.845 17 4.325C17 7.365 14.28 9.845 10.16 13.605L9 14.65Z" fill="#FF5A5F"/>
                </svg>
                <span>{entry.nickname}</span>
                <svg width="6" height="10" viewBox="0 0 6 10" fill="none">
                    <path d="M1 1L5 5L1 9" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
            </div>
            <div className="walk_best_couple_stats">
                <dl>
                    <dt>총 거리(km)</dt>
                    <dd>{entry.totalDistanceKm.toFixed(1)}</dd>
                </dl>
                <div className="walk_best_couple_divider" />
                <dl>
                    <dt>총 시간(분)</dt>
                    <dd>{entry.totalMinutes.toLocaleString()}</dd>
                </dl>
                <div className="walk_best_couple_divider" />
                <dl>
                    <dt>총 적립(Gold)</dt>
                    <dd>{entry.totalGold.toLocaleString()}</dd>
                </dl>
            </div>
        </div>
    );
}

function RankingCard({ entry, isHighlighted, onSelect }: { entry: WalkRankingEntry; isHighlighted: boolean; onSelect: () => void }) {
    return (
        <div
            className={`walk_ranking_card${isHighlighted ? ' walk_ranking_card--highlighted' : ''}`}
            onClick={onSelect}
            style={{ cursor: 'pointer' }}
        >
            <span className="walk_ranking_badge">{entry.rank}</span>
            <div className="walk_ranking_profiles">
                <div className="walk_ranking_profile_img">
                    <img
                        src={entry.petProfileImageUrl || '/assets/images/common/pet_none_img.svg'}
                        alt={entry.petName || ''}
                    />
                </div>
                <div className="walk_ranking_profile_img">
                    <img
                        src={entry.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
                        alt={entry.nickname}
                    />
                </div>
            </div>
            <div className="walk_ranking_info">
                <strong className="walk_ranking_name">
                    {entry.petName ? `${entry.petName}와 ${entry.nickname}` : entry.nickname}
                </strong>
                <div className="walk_ranking_stats_row">
                    <span>{entry.totalDistanceKm.toFixed(1)} Km</span>
                    <svg width="3" height="3" viewBox="0 0 3 3"><circle cx="1.5" cy="1.5" r="1.5" fill="#614108"/></svg>
                    <span>총 {entry.totalMinutes.toLocaleString()}분</span>
                </div>
                <div className="walk_ranking_gold_row">
                    <span>{entry.totalGold.toLocaleString()}G 적립</span>
                </div>
            </div>
        </div>
    );
}

const MONTHS = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];

function MonthPicker({
    selectedYearMonth,
    currentYearMonth,
    pickerYear,
    onChangePickerYear,
    onSelect,
    onClose,
}: {
    selectedYearMonth: string;
    currentYearMonth: string;
    pickerYear: number;
    onChangePickerYear: (y: number) => void;
    onSelect: (ym: string) => void;
    onClose: () => void;
}) {
    const currentYear = Number(currentYearMonth.split('-')[0]);
    const currentMonth = Number(currentYearMonth.split('-')[1]);

    return (
        <div className="walk_month_picker_overlay" onClick={onClose}>
            <div className="walk_month_picker" onClick={e => e.stopPropagation()}>
                <div className="walk_month_picker_handle" />
                <div className="walk_month_picker_header">
                    <button
                        type="button"
                        className="walk_month_picker_arrow"
                        onClick={() => onChangePickerYear(pickerYear - 1)}
                    >
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                            <path d="M10 4L6 8L10 12" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                    </button>
                    <span className="walk_month_picker_year">{pickerYear}</span>
                    <button
                        type="button"
                        className="walk_month_picker_arrow"
                        onClick={() => onChangePickerYear(pickerYear + 1)}
                        disabled={pickerYear >= currentYear}
                        style={{ opacity: pickerYear >= currentYear ? 0.3 : 1 }}
                    >
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                            <path d="M6 4L10 8L6 12" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                    </button>
                </div>
                <div className="walk_month_picker_grid">
                    {MONTHS.map((label, i) => {
                        const m = String(i + 1).padStart(2, '0');
                        const ym = `${pickerYear}-${m}`;
                        const isFuture = pickerYear > currentYear || (pickerYear === currentYear && i + 1 > currentMonth);
                        const isSelected = ym === selectedYearMonth;
                        return (
                            <button
                                key={i}
                                type="button"
                                className={`walk_month_picker_item${isSelected ? ' selected' : ''}`}
                                disabled={isFuture}
                                onClick={() => onSelect(ym)}
                            >
                                {label}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}

interface WalkRecordItemProps {
    record: WalkRecord;
    onClick: () => void;
}

export function WalkRecordItem({ record, onClick, showUser = false }: WalkRecordItemProps & { showUser?: boolean }) {
    const { share } = useShare();
    const petNames = record.petNames ?? [];
    const petImages = record.petProfileImageUrls ?? [];
    const hasPets = petNames.length > 0;
    const displayName = hasPets
        ? (petNames.length > 1 ? `${petNames[0]} 외 ${petNames.length - 1}` : petNames[0])
        : record.userNickname;

    return (
        <div className="walk_record_item" onClick={onClick} style={{ cursor: 'pointer' }}>
            {/* Top row: pet/user avatars + name (left), date + share (right) */}
            {showUser && (
                <div className="walk_record_top_row">
                    <div className="walk_record_user_info">
                        {hasPets ? (
                            <div className="walk_record_avatars">
                                {petImages.slice(0, 3).map((url, i) => (
                                    <img
                                        key={i}
                                        src={url || '/assets/images/common/pet_none_img.svg'}
                                        alt={petNames[i] || ''}
                                        className="walk_record_avatar"
                                    />
                                ))}
                            </div>
                        ) : (
                            <img
                                src={record.userProfileImageUrl || '/assets/images/common/profile_none_img.svg'}
                                alt={record.userNickname || ''}
                                className="walk_record_avatar"
                            />
                        )}
                        <span className="walk_record_nickname">{displayName}</span>
                    </div>
                    <div className="walk_record_meta">
                        <span className="walk_record_date">{record.date}</span>
                        <div className="walk_record_actions" onClick={e => e.stopPropagation()}>
                            <button
                                type="button"
                                className="walk_record_action_btn"
                                title="공유"
                                onClick={() => {
                                    const session: WalkSession = {
                                        id: record.id,
                                        startTime: record.startTime ?? '',
                                        distance: (record.distanceKm ?? 0) * 1000,
                                        calories: record.caloriesBurned ?? 0,
                                        durationSeconds: record.durationSeconds,
                                        startAddress: record.startAddress,
                                        petNames: record.petNames,
                                        petProfileImageUrls: record.petProfileImageUrls,
                                    };
                                    void share({ kind: 'walk-summary', walkId: record.id, session, watermark: true });
                                }}
                            >
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                    <rect width="24" height="24" rx="12" fill="#FFE5FA"/>
                                    <path d="M6 13.5V16C6 17.1046 6.89543 18 8 18H16C17.1046 18 18 17.1046 18 16V13.5" stroke="#8A497D" strokeWidth="2" strokeLinecap="round"/>
                                    <path d="M12 15V6M12 6L8.625 9.375M12 6L15.375 9.375" stroke="#8A497D" strokeWidth="2" strokeLinecap="round"/>
                                </svg>
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Bottom row: map thumbnail (left), stats + location (right) */}
            <div className="walk_record_body">
                <WalkRouteThumbnail walkId={record.id} hasPath={record.hasPath ?? true} size={60} />
                <div className="walk_record_info_col">
                    <div className="walk_record_stats">
                        <span>{record.distance}</span>
                        <svg width="3" height="3" viewBox="0 0 3 3">
                            <circle cx="1.5" cy="1.5" r="1.5" fill="#614108" />
                        </svg>
                        <span>{record.duration}</span>
                        <svg width="3" height="3" viewBox="0 0 3 3">
                            <circle cx="1.5" cy="1.5" r="1.5" fill="#614108" />
                        </svg>
                        <span>{record.points}</span>
                    </div>
                    {record.startAddress && (
                        <span className="walk_record_location">
                            {`${record.startAddress} ~ ${record.endAddress || '미지정'}`}
                        </span>
                    )}
                </div>
            </div>
        </div>
    );
}

export default WalkPage;
