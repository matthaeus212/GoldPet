
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { FriendCard } from './FriendCard';
import { friendService } from '../../services/friendService';
import { CACHE_TIME } from '../../config/queryConfig';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';

import { Loading } from '../../components/common/Loading';

export const FriendsListPage = () => {
    const navigate = useNavigate(); 
    
    // Sub-tab State
    const [activeSubTab, setActiveSubTab] = useState<'mutual' | 'i_like' | 'likes_me'>('mutual');

    // Fetch Mutual Likes (서로 좋아해)
    const { data: mutualLikes, isLoading: isMutualLoading, refetch: refetchMutual } = useQuery({
        queryKey: ['friends', 'likes', 'mutual'],
        queryFn: friendService.getMutualLikes,
        ...CACHE_TIME.DYNAMIC,
    });

    // Fetch My Likes (내가 좋아해)
    const { data: myLikes, isLoading: isMyLoading, refetch: refetchMy } = useQuery({
        queryKey: ['friends', 'likes', 'my'],
        queryFn: friendService.getMyLikes,
        ...CACHE_TIME.DYNAMIC,
    });

    // For now, "likes_me" can reuse mutual or show empty (backend doesn't have this endpoint yet)
    // We'll use the mutual endpoint as placeholder
    const { data: likesMe, isLoading: isLikesMeLoading, refetch: refetchLikesMe } = useQuery({
        queryKey: ['friends', 'likes', 'received'],
        queryFn: friendService.getReceivedLikes,
        ...CACHE_TIME.DYNAMIC,
    });

    const isLoading = 
        (activeSubTab === 'mutual' && isMutualLoading) ||
        (activeSubTab === 'i_like' && isMyLoading) ||
        (activeSubTab === 'likes_me' && isLikesMeLoading);

    const currentList = 
        activeSubTab === 'mutual' ? mutualLikes :
        activeSubTab === 'i_like' ? myLikes :
        likesMe;

    const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });

    // PERF-009: memo(FriendCard) 가 유지되도록 콜백을 안정화한다.
    const handleLikeStateChange = useCallback(() => {
        refetchMutual();
        refetchMy();
        refetchLikesMe();
    }, [refetchMutual, refetchMy, refetchLikesMe]);

    const handleOpenGallery = useCallback((images: string[], index: number) => {
        setGalleryState({ open: true, images, index });
    }, []);

    if (isLoading) return <Loading description="친구 목록을 불러오고 있어요..." />;

    return (
        <div id="friendFind">
            {/* Tab Section */}
            <div className="group_tab">
                <button type="button" onClick={() => navigate('/friend-find')}>친구찾기</button>
                <button type="button" className="active">친구목록</button>
            </div>

            {/* Sub-tab Filter Section */}
            <div className="find_filter" style={{ paddingBottom: '16px' }}>
                 <div className="filter_list" style={{ display: 'flex', gap: '8px', width: '100%' }}>
                    {/* Tab 1: Mutual */}
                    <button
                        type="button"
                        onClick={() => setActiveSubTab('mutual')}
                        style={{
                            flex: 1,
                            height: '42px',
                            background: activeSubTab === 'mutual' ? '#FFFDF1' : '#F9ECD2',
                            border: activeSubTab === 'mutual' ? '1.5px solid var(--color-primary)' : 'none',
                            borderRadius: '16px',
                            color: 'var(--color-primary)',
                            fontWeight: activeSubTab === 'mutual' ? 600 : 500,
                            fontSize: '14px',
                            cursor: 'pointer'
                        }}
                    >
                        서로 좋아해 {mutualLikes?.length ? `(${mutualLikes.length})` : ''}
                    </button>

                    {/* Tab 2: I Like */}
                    <button
                        type="button"
                        onClick={() => setActiveSubTab('i_like')}
                        style={{
                            flex: 1,
                            height: '42px',
                            background: activeSubTab === 'i_like' ? '#FFFDF1' : '#F9ECD2',
                            border: activeSubTab === 'i_like' ? '1.5px solid var(--color-primary)' : 'none',
                            borderRadius: '16px',
                            color: 'var(--color-primary)',
                            fontWeight: activeSubTab === 'i_like' ? 600 : 500,
                            fontSize: '14px',
                            cursor: 'pointer'
                        }}
                    >
                        내가 좋아해 {myLikes?.length ? `(${myLikes.length})` : ''}
                    </button>

                    {/* Tab 3: Likes Me */}
                    <button
                         type="button"
                         onClick={() => setActiveSubTab('likes_me')}
                         style={{
                            flex: 1,
                            height: '42px',
                            background: activeSubTab === 'likes_me' ? '#FFFDF1' : '#F9ECD2',
                            border: activeSubTab === 'likes_me' ? '1.5px solid var(--color-primary)' : 'none',
                            borderRadius: '16px',
                            color: 'var(--color-primary)',
                            fontWeight: activeSubTab === 'likes_me' ? 600 : 500,
                            fontSize: '14px',
                            cursor: 'pointer'
                        }}
                    >
                        나를 좋아해 {likesMe?.length ? `(${likesMe.length})` : ''}
                    </button>
                </div>
            </div>

            {/* Friend List */}
            <ImageGalleryModal
                isOpen={galleryState.open}
                images={galleryState.images}
                initialIndex={galleryState.index}
                onClose={() => setGalleryState(s => ({ ...s, open: false }))}
            />

            <div className="friend_list">
                {currentList && currentList.length > 0 ? (
                    currentList.map((friend) => (
                        <FriendCard
                            key={friend.id}
                            data={friend}
                            onLikeStateChange={handleLikeStateChange}
                            onOpenGallery={handleOpenGallery}
                            viewMode={
                                activeSubTab === 'mutual' ? 'mutual' :
                                activeSubTab === 'i_like' ? 'sent_like' :
                                activeSubTab === 'likes_me' ? 'received_like' :
                                'default'
                            }
                            source={activeSubTab === 'likes_me' ? 'received' : undefined}
                        />
                    ))
                ) : (
                    <div className="no_data" style={{ padding: '50px 0', textAlign: 'center', color: '#999' }}>
                        {activeSubTab === 'mutual' && '아직 서로 좋아해한 친구가 없어요'}
                        {activeSubTab === 'i_like' && '아직 좋아요한 친구가 없어요'}
                        {activeSubTab === 'likes_me' && '아직 나를 좋아해한 친구가 없어요'}
                    </div>
                )}
            </div>
        </div>
    );
};
