import React, { useState } from 'react';
import { useAuthStore } from '../../stores/authStore';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { petService } from '../../services/petService';
import { userService } from '../../services/userService';
import { goldService } from '../../services/goldService';
import { CACHE_TIME } from '../../config/queryConfig';
import { ProfileImageSlider } from '../../components/common/ProfileImageSlider';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { ProfileSettingsSection } from './components/ProfileSettingsSection';

const ProfileOwnerTab: React.FC = () => {
    const { user: authUser, updateUser } = useAuthStore();
    const navigate = useNavigate();
    const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });

    // Fetch user data from API (uses default staleTime, invalidated after profile update)
    const { data: apiUser, isLoading: isUserLoading } = useQuery({
        queryKey: ['users', 'me'],
        queryFn: userService.getMe,
        ...CACHE_TIME.DYNAMIC,
    });

    // Merge API data with auth store (API data takes precedence)
    const user = apiUser || authUser;

    // Fetch My Pet for Secondary Image
    const { data: myPets } = useQuery({ queryKey: ['pets', 'my'], queryFn: petService.getMyPets, ...CACHE_TIME.DYNAMIC });
    const representativePet = myPets && myPets.length > 0 ? myPets[0] : null;

    // Fetch gold balance
    const { data: goldBalance } = useQuery({
        queryKey: ['gold', 'balance'],
        queryFn: goldService.getBalance,
        ...CACHE_TIME.DYNAMIC,
    });

    const { data: stats } = useQuery({
        queryKey: ['users', 'stats'],
        queryFn: () => userService.getMyStats(),
        ...CACHE_TIME.DYNAMIC,
    });

    const formatCount = (n: number): string => {
        if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
        return String(n);
    };

    if (isUserLoading) {
        return <div className="loading">로딩 중...</div>;
    }

    if (!user) {
        return <div>로그인이 필요합니다.</div>;
    }

    const handleNotificationChange = async (enabled: boolean) => {
        if (!user) return;
        
        try {
            const updatedUser = await userService.updateNotification(enabled);
            updateUser(updatedUser);
        } catch (error) {
            console.error('Failed to update notification setting:', error);
            throw error; // Re-throw to let ProfileSettingsSection handle UI revert
        }
    };

    const handleEdit = () => {
        navigate('/profile/owner/edit');
    };

    const ownerImages = user.profileImageUrls && user.profileImageUrls.length > 0
        ? user.profileImageUrls
        : [user.profileImageUrl || "/assets/images/common/profile_none_img.svg"];
    const ownerImagesThumbnail = user.profileImageUrls && user.profileImageUrls.length > 0
        ? user.profileImageUrlsThumbnail
        : (user.profileImageUrlThumbnail ? [user.profileImageUrlThumbnail] : undefined);
    const ownerImagesViewer = user.profileImageUrls && user.profileImageUrls.length > 0
        ? user.profileImageUrlsViewer
        : (user.profileImageUrlViewer ? [user.profileImageUrlViewer] : undefined);
    const petSecondaryThumbnail = representativePet?.profileImageUrlThumbnail ?? null;

    return (
        <div className="profile_info_wrap">
            <ImageGalleryModal
                isOpen={galleryState.open}
                images={galleryState.images}
                initialIndex={galleryState.index}
                onClose={() => setGalleryState(s => ({ ...s, open: false }))}
            />
            <div className="profile_name_info">
                <ProfileImageSlider
                    className="couple_img"
                    swiperWrapperClass="profile_img_wrap"
                    images={ownerImages}
                    imagesThumbnail={ownerImagesThumbnail}
                    imagesViewer={ownerImagesViewer}
                    secondaryImage={representativePet?.profileImageUrl || "/assets/images/common/pet_none_img.svg"}
                    secondaryImageThumbnail={petSecondaryThumbnail}
                    secondaryImageClass="butler_img pet_img"
                    onMainImageClick={(index) => setGalleryState({ open: true, images: ownerImages, index })}
                    onSecondaryImageClick={() => {
                        const petImages = representativePet?.profileImageUrls && representativePet.profileImageUrls.length > 0
                            ? representativePet.profileImageUrls
                            : representativePet?.profileImageUrl ? [representativePet.profileImageUrl] : [];
                        if (petImages.length > 0) setGalleryState({ open: true, images: petImages, index: 0 });
                    }}
                    navigation={true}
                    innerChildren={
                        <>
                            <div className="swiper-pet-prev"></div>
                            <div className="swiper-pet-next"></div>
                        </>
                    }
                />
                <strong className="name_txt">{user.nickname}</strong>
                <div className="count_wrap">
                    <dl>
                        <dt>매칭</dt>
                        <dd>{formatCount(stats?.matchingCount ?? 0)}</dd>
                    </dl>
                    <div></div>
                    <dl>
                        <dt>좋아요</dt>
                        <dd>{formatCount(stats?.likesCount ?? 0)}</dd>
                    </dl>
                    <div></div>
                    <dl>
                        <dt>친구</dt>
                        <dd>{formatCount(stats?.friendsCount ?? 0)}</dd>
                    </dl>
                </div>
            </div>

            {/* Gold Balance */}
            <div className="gold-balance-link" onClick={() => navigate('/gold')} style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                backgroundColor: 'var(--color-bg)', borderRadius: '4.44vw', padding: '4.44vw',
                margin: '4.44vw 0', cursor: 'pointer'
            }}>
                <span style={{ fontSize: '14px', color: '#727272' }}>보유 골드</span>
                <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--color-primary)' }}>
                    {goldBalance?.balance?.toLocaleString() || '0'}G
                </span>
            </div>

            <div className="profile_info_box">
                <strong>기본 정보</strong>
                <div className="box_wrap pink_color">
                    <dl>
                        <dt>이름</dt>
                        <dd>{user.name || '-'}</dd> 
                    </dl>
                    <dl>
                        <dt>닉네임</dt>
                        <dd>{user.nickname}</dd>
                    </dl>
                    <dl>
                        <dt>생년월일</dt>
                        <dd>{user.birthDate || '-'}</dd>
                    </dl>
                    <dl>
                        <dt>휴대폰 번호</dt>
                        <dd>{user.phoneNumber || '-'}</dd>
                    </dl>
                    <dl>
                        <dt>성별</dt>
                        <dd>{user.gender === 'MALE' ? '남성' : user.gender === 'FEMALE' ? '여성' : '-'}</dd>
                    </dl>
                     {/* Age calculation or mocked */}
                    <dl>
                        <dt>나이</dt>
                        <dd>
                            {user.birthDate && user.birthDate.length >= 4 
                                ? `${new Date().getFullYear() - parseInt(user.birthDate.substring(0, 4), 10) + 1}세` 
                                : (user.birthYear ? `${new Date().getFullYear() - user.birthYear + 1}세` : '-')}
                        </dd>
                    </dl>
                    <dl className="full_w">
                        <dt>반려동물 유무</dt>
                        <dd>있음</dd>
                    </dl>
                    <dl className="full_w">
                        <dt>간단한 자기소개</dt>
                        <dd style={{ whiteSpace: 'pre-line' }}>{user.intro || '-'}</dd>
                    </dl>
                </div>

                <strong>추가 정보</strong>
                <div className="box_wrap purple_color">
                    <dl>
                        <dt>MBTI</dt>
                        <dd>{user.mbti || '-'}</dd>
                    </dl>
                    <dl>
                         <dt>관심사</dt>
                         <dd>{user.interests?.join(', ') || '-'}</dd>
                    </dl>
                     <dl className="full_w">
                        <dt>취미</dt>
                        <dd>{user.hobbies?.join(', ') || '-'}</dd>
                    </dl>
                </div>
            </div>

            <ProfileSettingsSection 
                onEdit={handleEdit} 
                idPrefix="owner" 
                isNotificationEnabled={user.isNotificationEnabled}
                onNotificationChange={handleNotificationChange}
            />
        </div>
    );
};

export default ProfileOwnerTab;
