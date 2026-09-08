import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { petService, FALLBACK_ATTRIBUTES_SCHEMA } from '../../services/petService';
import { userService } from '../../services/userService';
import { CACHE_TIME } from '../../config/queryConfig';
import { Loading } from '../../components/common/Loading';
import { ProfileImageSlider } from '../../components/common/ProfileImageSlider';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { ProfileSettingsSection } from './components/ProfileSettingsSection';

const ProfilePetTab: React.FC = () => {
    const navigate = useNavigate();
    const { user, updateUser } = useAuthStore();
    const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });
    
    // Fetch my pets list (uses default staleTime, invalidated after pet updates)
    const { data: myPets, isLoading: isPetsLoading } = useQuery({
        queryKey: ['pets', 'my'],
        queryFn: petService.getMyPets,
    });

    // Fetch user data (uses default staleTime, invalidated after profile update)
    const { data: fetchedUser } = useQuery({
        queryKey: ['users', 'me'],
        queryFn: userService.getMe,
    });

    const currentUser = fetchedUser || user;

    const { data: stats } = useQuery({
        queryKey: ['users', 'stats'],
        queryFn: () => userService.getMyStats(),
        ...CACHE_TIME.DYNAMIC,
    });

    const formatCount = (n: number): string => {
        if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
        return String(n);
    };

    const handleNotificationChange = async (enabled: boolean) => {
        if (!user) return;
        
        try {
            const updatedUser = await userService.updateNotification(enabled);
            updateUser(updatedUser);
        } catch (error) {
            console.error('Failed to update notification setting:', error);
            throw error;
        }
    };
    
    const hasPet = myPets && myPets.length > 0;
    const pet = hasPet ? myPets[0] : null;

    const handleRegister = () => {
        navigate('/profile/pet/edit');
    };

    const handleEdit = () => {
        if (pet?.id) {
            navigate(`/profile/pet/edit?petId=${pet.id}`);
        } else {
            navigate('/profile/pet/edit');
        }
    };

    // Helper to get label for attribute value
    const getAttributeLabel = (code: string, value: string): string => {
        const attributeDef = FALLBACK_ATTRIBUTES_SCHEMA.find(attr => attr.code === code);
        if (!attributeDef) return value;
        
        if (attributeDef.inputType === 'SELECT') {
            const option = attributeDef.options.find(opt => opt.value === value);
            return option ? option.label : value;
        }
        return value;
    };
    
    // Parse temperamentTags string (e.g., "TRAIT_ACTIVITY:HIGH, TRAIT_SPECIAL_NOTES:Cute")
    const parsedAttributes = (() => {
        if (!pet?.temperamentTags) return {} as Record<string, string>;

        // Handle if it's an array (mock) or string (backend)
        const tagsString = Array.isArray(pet.temperamentTags)
            ? pet.temperamentTags.join(',')
            : pet.temperamentTags; // It is String in backend entity now

        if (!tagsString) return {} as Record<string, string>;

        const result: Record<string, string> = {};
        tagsString.split(',').forEach(tag => {
            const parts = tag.trim().split(':');
            if (parts.length === 2) {
                const [code, value] = parts;
                result[code] = value;
            } else if (parts.length === 1 && parts[0]) {
                // If just code exists or legacy format
                result[parts[0]] = parts[0];
            }
        });
        return result;
    })();

    // Helper to render sections
    const renderAttributeSection = (category: string, boxColorClass: string) => {
        const items = FALLBACK_ATTRIBUTES_SCHEMA.filter(attr => attr.category === category);
        if (items.length === 0) return null;

        return (
             <div className={`box_wrap ${boxColorClass}`}>
                {items.map(attr => {
                    const value = parsedAttributes[attr.code];
                    // Special case for Special Notes -> show full width
                    if (attr.code === 'Note') {
                        return (
                            <dl key={attr.code} className="full_w">
                                <dt>{attr.name}</dt>
                                <dd style={{ whiteSpace: 'pre-line' }}>{value ? value : '-'}</dd>
                            </dl>
                        );
                    }
                    
                    return (
                        <dl key={attr.code}>
                            <dt>{attr.name}</dt>
                            <dd>{value ? getAttributeLabel(attr.code, value) : '-'}</dd>
                        </dl>
                    );
                })}
            </div>
        );
    };

    if (isPetsLoading) {
        return <Loading description="반려동물 정보를 불러오고 있어요" />;
    }

    const ownerProfileImage = (currentUser?.profileImageUrls && currentUser.profileImageUrls.length > 0)
        ? currentUser.profileImageUrls[0]
        : (currentUser?.profileImageUrl || "/assets/images/common/profile_none_img.svg");

    // EMPTY STATE (Matches profile_pet_info_not.html)
    if (!hasPet) {
        return (
            <div className="profile_info_wrap">
                <div className="profile_name_info">
                    <div className="couple_img">
                         <div className="profile_img_wrap">
                            <div className="ai_generation_wrap">
                                <button type="button" onClick={handleRegister}>
                                    <img src="/assets/images/common/ai_profile_icon.svg" alt="AI 아이콘" />
                                    프로필 생성하기
                                </button>
                            </div>
                         </div>
                          <div className="butler_img pet_img">
                                <img 
                                    src={ownerProfileImage} 
                                    alt="반려인 이미지" 
                                />
                          </div>
                    </div>
                     <strong className="name_txt">망고</strong> {/* Mock name from HTML, or should be '반려동물 이름' or hidden if completely empty? HTML shows '망고' in Not state. I'll use a placeholder */}
                     <div className="count_wrap">
                        <dl><dt>매칭</dt><dd>0</dd></dl>
                        <div></div>
                        <dl><dt>좋아요</dt><dd>0</dd></dl>
                        <div></div>
                        <dl><dt>친구</dt><dd>0</dd></dl>
                    </div>
                </div>

                <div className="guide_box">
                    <p>반려동물 프로필을 등록해 주세요!</p>
                </div>
                
                <ProfileSettingsSection 
                    onEdit={handleRegister} 
                    idPrefix="pet_empty"
                    isNotificationEnabled={user?.isNotificationEnabled}
                    onNotificationChange={handleNotificationChange} 
                />
            </div>
        );
    }

    const petImages = pet?.profileImageUrls && pet.profileImageUrls.length > 0
        ? pet.profileImageUrls
        : [pet?.profileImageUrl || "/assets/images/common/pet_none_img.svg"];
    const petImagesThumbnail = pet?.profileImageUrls && pet.profileImageUrls.length > 0
        ? pet.profileImageUrlsThumbnail
        : (pet?.profileImageUrlThumbnail ? [pet.profileImageUrlThumbnail] : undefined);
    const petImagesViewer = pet?.profileImageUrls && pet.profileImageUrls.length > 0
        ? pet.profileImageUrlsViewer
        : (pet?.profileImageUrlViewer ? [pet.profileImageUrlViewer] : undefined);

    const ownerGalleryImages = (currentUser?.profileImageUrls && currentUser.profileImageUrls.length > 0)
        ? currentUser.profileImageUrls
        : currentUser?.profileImageUrl ? [currentUser.profileImageUrl] : [];
    const ownerSecondaryThumbnail = (currentUser?.profileImageUrlsThumbnail?.[0])
        ?? currentUser?.profileImageUrlThumbnail
        ?? null;

    // HAS PET STATE (Matches profile_pet_info.html)
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
                    images={petImages}
                    imagesThumbnail={petImagesThumbnail}
                    imagesViewer={petImagesViewer}
                    secondaryImage={ownerProfileImage}
                    secondaryImageThumbnail={ownerSecondaryThumbnail}
                    secondaryImageClass="butler_img pet_img"
                    onMainImageClick={(index) => setGalleryState({ open: true, images: petImages, index })}
                    onSecondaryImageClick={() => {
                        if (ownerGalleryImages.length > 0) setGalleryState({ open: true, images: ownerGalleryImages, index: 0 });
                    }}
                    innerChildren={
                        <>
                            <div className="swiper-pet-prev"></div>
                            <div className="swiper-pet-next"></div>
                        </>
                    }
                    navigation={true}
                />
                <div className="ai_generation_wrap" style={{ marginTop: '2.22vw' }}>
                    <button type="button" onClick={() => navigate('/ai-profile')} style={{
                        display: 'inline-flex', alignItems: 'center', gap: '1.11vw',
                        background: 'none', border: 'none', fontSize: '12px', color: 'var(--color-primary)',
                        fontWeight: 600, cursor: 'pointer'
                    }}>
                        <img src="/assets/images/common/ai_profile_icon.svg" alt="AI" style={{ width: '4.44vw', height: '4.44vw' }} />
                        AI 프로필 만들기
                    </button>
                </div>
                <strong className="name_txt">{pet?.name}</strong>
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

            <div className="profile_info_box">
                <strong>기본 정보</strong>
                 <div className="box_wrap pink_color">
                    <dl>
                        <dt>나이</dt>
                        <dd>{pet?.birthDate ? `${new Date().getFullYear() - new Date(pet.birthDate).getFullYear() + 1}세` : '-'}</dd>
                    </dl>
                    <dl>
                        <dt>성별</dt>
                        <dd>{pet?.gender === 'MALE' ? '남아' : pet?.gender === 'FEMALE' ? '여아' : '-'}</dd>
                    </dl>
                    <dl>
                        <dt>중성화</dt>
                        <dd>{pet?.isNeutered ? '완료' : '안함'}</dd>
                    </dl>
                    <dl>
                        <dt>품종</dt>
                        <dd>{pet?.breed || pet?.species || '-'}</dd>
                    </dl>
                </div>

                <strong>성향</strong>
                {renderAttributeSection('TRAIT', 'purple_color')}

                <strong>관심사</strong>
                {renderAttributeSection('INTEREST', 'green_color')}

                <strong>알러지 정보</strong>
                {renderAttributeSection('ALLERGY', 'blue_color')}
            </div>
            
            {pet && (
                <button
                    type="button"
                    onClick={() => navigate(`/health?petId=${pet.id}`)}
                    style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        width: '100%', padding: '14px 16px', marginTop: 24, marginBottom: 8,
                        background: '#fff', border: '1px solid #EFE1C4', borderRadius: 14,
                        color: '#614108', fontSize: 15, fontWeight: 600, cursor: 'pointer',
                    }}
                >
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 18 }}>💩</span>
                        건강 분석 기록
                    </span>
                    <svg width="8" height="14" viewBox="0 0 8 14" fill="none">
                        <path d="M1 1l6 6-6 6" stroke="#AAAAAA" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </button>
            )}

            <ProfileSettingsSection
                onEdit={handleEdit}
                idPrefix="pet"
                isNotificationEnabled={user?.isNotificationEnabled}
                onNotificationChange={handleNotificationChange}
            />
        </div>
    );
};

export default ProfilePetTab;
