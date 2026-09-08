
import React, { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { FriendCard } from './FriendCard';
import { friendService } from '../../services/friendService';
import { CACHE_TIME } from '../../config/queryConfig';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';

import { Loading } from '../../components/common/Loading';

const FILTER_STORAGE_KEY = 'friendFindFilter';

interface SavedFilter {
    distance: string;
    sortOrder: string;
    petTypes: string[];
    genders: string[];
}

function loadSavedFilter(): SavedFilter | null {
    try {
        const raw = localStorage.getItem(FILTER_STORAGE_KEY);
        if (!raw) return null;
        return JSON.parse(raw) as SavedFilter;
    } catch {
        return null;
    }
}

export const FriendFindPage = () => {
    const navigate = useNavigate();

    // Load saved filter on mount
    const saved = loadSavedFilter();
    const hasSaved = saved !== null;

    // Filter visibility state: collapsed if saved filter exists
    const [isFilterVisible, setIsFilterVisible] = useState(!hasSaved);

    // Filter Selection State (UI only) - initialize from saved
    const [selectedDistance, setSelectedDistance] = useState<string>(saved?.distance ?? '전체');
    const [selectedSortOrder, setSelectedSortOrder] = useState<string>(saved?.sortOrder ?? 'registered');
    const [selectedPetTypes, setSelectedPetTypes] = useState<string[]>(saved?.petTypes ?? []);
    const [selectedGenders, setSelectedGenders] = useState<string[]>(saved?.genders ?? []);

    // Applied Filter State (Triggers Query) - initialize from saved
    const [appliedDistance, setAppliedDistance] = useState<string>(saved?.distance ?? '전체');
    const [appliedSortOrder, setAppliedSortOrder] = useState<string>(saved?.sortOrder ?? 'registered');
    const [appliedPetTypes, setAppliedPetTypes] = useState<string[]>(saved?.petTypes ?? []);
    const [appliedGenders, setAppliedGenders] = useState<string[]>(saved?.genders ?? []);

    // Fetch Friends with Pagination
    const { 
        data: friendsData, 
        isLoading, 
        isFetchingNextPage, 
        hasNextPage, 
        fetchNextPage 
    } = useInfiniteQuery({
        queryKey: ['friends', appliedDistance, appliedSortOrder, appliedPetTypes, appliedGenders],
        queryFn: async ({ pageParam = 0 }) => {
            const filterPayload = {
                distance: appliedDistance,
                sortOrder: appliedSortOrder,
                petTypes: appliedPetTypes,
                genders: appliedGenders,
            };

            return friendService.getFriends(JSON.stringify(filterPayload), pageParam, 10);
        },
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) => {
            return lastPage.last ? undefined : allPages.length;
        },
        enabled: true,
        ...CACHE_TIME.DYNAMIC,
    });

    const { data: petSpecies } = useQuery({
        queryKey: ['pets', 'species'],
        queryFn: friendService.getSpecies,
        staleTime: Infinity, // Static data - cache indefinitely
    });

    const handleDistanceChange = (value: string) => {
        setSelectedDistance(value);
    };

    const handleSortChange = (value: string) => {
        setSelectedSortOrder(value);
    };

    const toggleFilter = (value: string, currentList: string[], setList: React.Dispatch<React.SetStateAction<string[]>>) => {
        if (currentList.includes(value)) {
            setList(currentList.filter(item => item !== value));
        } else {
            setList([...currentList, value]);
        }
    };

    const handleApply = () => {
        setAppliedDistance(selectedDistance);
        setAppliedSortOrder(selectedSortOrder);
        setAppliedPetTypes(selectedPetTypes);
        setAppliedGenders(selectedGenders);
        // Save to localStorage
        localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify({
            distance: selectedDistance,
            sortOrder: selectedSortOrder,
            petTypes: selectedPetTypes,
            genders: selectedGenders,
        }));
        // Hide filter with animation
        setIsFilterVisible(false);
    };

    const handleReset = () => {
        // Reset all filters - 거리는 기본값 '전체'(무제한)로 복귀
        setSelectedDistance('전체');
        setSelectedSortOrder('');
        setSelectedPetTypes([]);
        setSelectedGenders([]);
        // Clear saved filter
        localStorage.removeItem(FILTER_STORAGE_KEY);
    };

    const toggleFilterVisibility = () => {
        setIsFilterVisible(!isFilterVisible);
    };

    const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });

    const [hiddenFriendIds, setHiddenFriendIds] = useState<number[]>([]);

    // PERF-009: 매 렌더 새 배열이면 아래 FriendCard 들의 data prop 참조가 통째로 바뀐다.
    const flattenedFriends = useMemo(
        () => (friendsData?.pages.flatMap(page => page.content) || [])
            .filter(friend => !hiddenFriendIds.includes(friend.id)),
        [friendsData, hiddenFriendIds],
    );

    // PERF-009: 인라인 콜백을 카드마다 새로 만들면 memo(FriendCard) 가 매번 깨진다.
    // FriendCard 가 friendId 를 넘겨주므로 안정된 콜백 하나를 모든 카드가 공유한다.
    const handleLikeStateChange = useCallback((friendId: number) => {
        setHiddenFriendIds(prev => [...prev, friendId]);
    }, []);

    const handleOpenGallery = useCallback((images: string[], index: number) => {
        setGalleryState({ open: true, images, index });
    }, []);

    if (isLoading) return <Loading description="친구를 찾고 있어요..." />;

    return (
        <div id="friendFind">
            {/* button class - active로 전환 */}
            <div className="group_tab">
                <button type="button" className="active">친구찾기</button>
                <button type="button" onClick={() => navigate('/friend-list')}>친구목록</button>
            </div>

            {/* Applied filters bar - shown when filter panel is hidden */}
            {!isFilterVisible && (() => {
                const items: { key: string; label: string }[] = [];
                if (appliedDistance) items.push({ key: 'dist', label: appliedDistance === '전체' ? '전체' : `${appliedDistance} 이내` });
                if (appliedPetTypes.length >= 2) {
                    items.push({ key: 'pet-all', label: '강아지/고양이' });
                } else {
                    appliedPetTypes.forEach(pt => items.push({ key: `pet-${pt}`, label: pt === 'DOG' ? '강아지' : pt === 'CAT' ? '고양이' : pt }));
                }
                if (appliedGenders.length >= 2) {
                    items.push({ key: 'gender-all', label: '남/여아' });
                } else {
                    appliedGenders.forEach(g => items.push({ key: `gender-${g}`, label: g === 'MALE' ? '남아' : '여아' }));
                }
                if (appliedSortOrder) {
                    const sortLabel = appliedSortOrder === 'registered' ? '등록순'
                        : appliedSortOrder === 'popular' ? '인기순'
                        : appliedSortOrder === 'compatible' ? '궁합순'
                        : appliedSortOrder;
                    items.push({ key: 'sort', label: sortLabel });
                }
                return (
                    <div className="applied_filter_bar" onClick={toggleFilterVisibility}>
                        <div className="filter_bar_inner">
                            {items.map((item, i) => (
                                <React.Fragment key={item.key}>
                                    {i > 0 && <span className="divider" />}
                                    <span>{item.label}</span>
                                </React.Fragment>
                            ))}
                            <button type="button" className="reset_btn" onClick={(e) => {
                                e.stopPropagation();
                                handleReset();
                                setAppliedDistance('전체');
                                setAppliedSortOrder('');
                                setAppliedPetTypes([]);
                                setAppliedGenders([]);
                                setIsFilterVisible(true);
                            }}>초기화</button>
                        </div>
                    </div>
                );
            })()}

            {/* Filter area with animation */}
            <div 
                className="find_filter"
                style={{
                    maxHeight: isFilterVisible ? '500px' : '0',
                    paddingTop: isFilterVisible ? '' : '0',
                    paddingBottom: isFilterVisible ? '' : '0',
                    overflow: 'hidden',
                    opacity: isFilterVisible ? 1 : 0,
                    transform: isFilterVisible ? 'translateY(0)' : 'translateY(-10px)',
                    transition: 'all 0.5s ease-in-out',
                    visibility: isFilterVisible ? 'visible' : 'hidden',
                }}
            >
                <ul>
                    <li>
                        <p>거리순</p>
                        <div className="filter_list">
                            <div className="check_wrap" onClick={() => handleDistanceChange('전체')}>
                                <input type="checkbox" id="for0" checked={selectedDistance === '전체'} readOnly />
                                <label>전체</label>
                            </div>
                            <div className="check_wrap" onClick={() => handleDistanceChange('1km')}>
                                <input type="checkbox" id="for1" checked={selectedDistance === '1km'} readOnly />
                                <label>1km 이내</label>
                            </div>
                            <div className="check_wrap" onClick={() => handleDistanceChange('3km')}>
                                <input type="checkbox" id="for2" checked={selectedDistance === '3km'} readOnly />
                                <label>3km 이내</label>
                            </div>
                            <div className="check_wrap" onClick={() => handleDistanceChange('5km')}>
                                <input type="checkbox" id="for3" checked={selectedDistance === '5km'} readOnly />
                                <label>5km 이내</label>
                            </div>
                        </div>
                    </li>
                    <li>
                        <p>반려동물 <br />종류</p>
                        <div className="filter_list">
                            {petSpecies?.map((species) => (
                                <div className="check_wrap" key={species.id}>
                                    <input 
                                        type="checkbox" 
                                        id={`species-${species.id}`} 
                                        checked={selectedPetTypes.includes(species.code)} 
                                        onChange={() => toggleFilter(species.code, selectedPetTypes, setSelectedPetTypes)} 
                                    />
                                    <label htmlFor={`species-${species.id}`}>{species.name}</label>
                                </div>
                            ))}
                        </div>
                    </li>
                    <li>
                        <p>성별</p>
                        <div className="filter_list">
                            <div className="check_wrap">
                                <input type="checkbox" id="for10" checked={selectedGenders.includes('MALE')} onChange={() => toggleFilter('MALE', selectedGenders, setSelectedGenders)} />
                                <label htmlFor="for10">남아</label>
                            </div>
                            <div className="check_wrap">
                                <input type="checkbox" id="for11" checked={selectedGenders.includes('FEMALE')} onChange={() => toggleFilter('FEMALE', selectedGenders, setSelectedGenders)} />
                                <label htmlFor="for11">여아</label>
                            </div>
                        </div>
                    </li>
                    <li>
                        <p>정렬</p>
                        <div className="filter_list">
                            <div className="check_wrap">
                                <input type="checkbox" id="for12" checked={selectedSortOrder === 'registered'} onChange={() => handleSortChange('registered')} />
                                <label htmlFor="for12">등록순</label>
                            </div>
                            <div className="check_wrap">
                                <input type="checkbox" id="for13" checked={selectedSortOrder === 'popular'} onChange={() => handleSortChange('popular')} />
                                <label htmlFor="for13">인기순</label>
                            </div>
                            <div className="check_wrap">
                                <input type="checkbox" id="for14" checked={selectedSortOrder === 'compatible'} onChange={() => handleSortChange('compatible')} />
                                <label htmlFor="for14">궁합순</label>
                            </div>
                        </div>
                    </li>
                </ul>
                <div className="btn_wrap">
                    <button type="button" onClick={handleReset}>초기화</button>
                    <button type="button" onClick={handleApply} disabled={!selectedDistance && !selectedSortOrder && selectedPetTypes.length === 0 && selectedGenders.length === 0}>적용</button>
                </div>
            </div>

            <ImageGalleryModal
                isOpen={galleryState.open}
                images={galleryState.images}
                initialIndex={galleryState.index}
                onClose={() => setGalleryState(s => ({ ...s, open: false }))}
            />

            <div className="friend_list">
                {flattenedFriends.map((friend) => (
                    <FriendCard
                        key={friend.id}
                        data={friend}
                        onLikeStateChange={handleLikeStateChange}
                        onOpenGallery={handleOpenGallery}
                        source={appliedSortOrder}
                    />
                ))}
                
                {!isLoading && flattenedFriends.length === 0 && (
                     <div className="no_data" style={{ padding: '50px 0', textAlign: 'center', color: '#999' }}>
                         조건에 맞는 친구가 없습니다.
                     </div>
                )}
            </div>
            
            {hasNextPage && (
                <div className="more_wrap">
                    <button type="button" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
                        {isFetchingNextPage ? '로딩중...' : '더보기'} 
                        {!isFetchingNextPage && <img src="/assets/images/common/more_btn.svg" alt="" />}
                    </button>
                </div>
            )}
        </div>
    );
};
