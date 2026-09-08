import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { m } from 'motion/react';
import { useAuthStore } from '../../stores/authStore';
import { userService } from '../../services/userService';
import { petService } from '../../services/petService';
import { goldService } from '../../services/goldService';
import { CACHE_TIME } from '../../config/queryConfig';
import { PAYMENT_ENABLED } from '../../config/featureFlags';
import { TAB_DURATION, TAB_EASING } from '../../config/cardFlipAnimation';
import { useGoldBalancePolling } from '../../hooks/useGoldBalancePolling';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { ProfileImageSlider } from '../../components/common/ProfileImageSlider';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { PetSelectBottomSheet } from '../../components/common/PetSelectBottomSheet';
import './MyPage.css';

const ChevronRight = () => (
  <svg className="mypage-menu-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M9 6l6 6-6 6" />
  </svg>
);

const GoldCoinIcon = () => (
  <img className="mypage-gold-coin" src="/assets/images/common/gold_icon.svg" alt="골드" />
);

const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { user: authUser } = useAuthStore();
  const [activeTab, setActiveTab] = useState<'owner' | 'pet'>('owner');
  const isAnimatingRef = useRef(false);
  const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });
  const [selectedPetId, setSelectedPetId] = useState<number | null>(null);
  const [petSelectOpen, setPetSelectOpen] = useState(false);

  const { data: apiUser } = useQuery({
    queryKey: ['users', 'me'],
    queryFn: userService.getMe,
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: myPets } = useQuery({
    queryKey: ['pets', 'my'],
    queryFn: petService.getMyPets,
    ...CACHE_TIME.DYNAMIC,
  });

  // ETag 기반 골드 잔액 폴링 (10 s 간격, 304 시 리렌더 스킵)
  useGoldBalancePolling();

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

  const handleTabChange = (tab: 'owner' | 'pet') => {
    if (isAnimatingRef.current || tab === activeTab) return;
    isAnimatingRef.current = true;
    setActiveTab(tab);
  };

  const formatCount = (n: number): string => {
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
    return String(n);
  };

  const user = apiUser || authUser;

  const profileImages = user?.profileImageUrls?.length
    ? user.profileImageUrls
    : [user?.profileImageUrl || '/assets/images/common/profile_none_img.svg'];
  const profileImagesThumbnail = user?.profileImageUrls?.length
    ? user.profileImageUrlsThumbnail
    : (user?.profileImageUrlThumbnail ? [user.profileImageUrlThumbnail] : undefined);
  const profileImagesViewer = user?.profileImageUrls?.length
    ? user.profileImageUrlsViewer
    : (user?.profileImageUrlViewer ? [user.profileImageUrlViewer] : undefined);

  const firstPet = myPets && myPets.length > 0 ? myPets[0] : null;
  const selectedPet = myPets?.find(p => p.id === selectedPetId) ?? firstPet;
  const petImages = selectedPet?.profileImageUrls?.length
    ? selectedPet.profileImageUrls
    : selectedPet?.profileImageUrl
      ? [selectedPet.profileImageUrl]
      : ['/assets/images/common/pet_none_img.svg'];
  const petImagesThumbnail = selectedPet?.profileImageUrls?.length
    ? selectedPet.profileImageUrlsThumbnail
    : (selectedPet?.profileImageUrlThumbnail ? [selectedPet.profileImageUrlThumbnail] : undefined);
  const petImagesViewer = selectedPet?.profileImageUrls?.length
    ? selectedPet.profileImageUrlsViewer
    : (selectedPet?.profileImageUrlViewer ? [selectedPet.profileImageUrlViewer] : undefined);

  const ownerHasProfile = (user?.profileImageUrls?.length ?? 0) > 0;
  const petHasProfile = myPets != null && myPets.length > 0;

  const hasProfile = activeTab === 'owner' ? ownerHasProfile : petHasProfile;

  const handleFloatingButtonClick = () => {
    if (activeTab === 'owner') {
      if (hasProfile) {
        navigate('/profile?tab=owner');
      } else {
        navigate('/profile/owner/edit');
      }
    } else {
      if (hasProfile) {
        navigate('/profile?tab=pet');
      } else {
        navigate('/mypage/pet/edit');
      }
    }
  };

  const settingsButton = (
    <button
      type="button"
      onClick={() => navigate('/settings')}
      style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
    >
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
        <path d="M12 15a3 3 0 100-6 3 3 0 000 6z" />
        <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
      </svg>
    </button>
  );

  const menuItems = [
    { label: '내 산책 기록', path: '/my-walks' },
    { label: '건강 분석 기록', path: '/health' },
    { label: '뱃지 & 미션', path: '/my-badges' },
    { label: '내가 쓴 글', path: '/mypage/posts' },
    { label: '차단 관리', path: '/block-management' },
  ];

  return (
    <SubPageLayout title="마이페이지" onBack={() => navigate('/home')} rightAction={settingsButton}>
      <ImageGalleryModal
        isOpen={galleryState.open}
        images={galleryState.images}
        initialIndex={galleryState.index}
        onClose={() => setGalleryState(s => ({ ...s, open: false }))}
      />
      <div id="mypageContainer" className="mypage-container">
        {/* Tab Menu */}
        <div className="group_tab">
          <button
            type="button"
            className={activeTab === 'owner' ? 'active' : ''}
            onClick={() => handleTabChange('owner')}
          >
            반려인
          </button>
          <button
            type="button"
            className={activeTab === 'pet' ? 'active' : ''}
            onClick={() => handleTabChange('pet')}
          >
            반려동물
          </button>
        </div>

        {/* Owner Tab */}
        <m.div
          animate={{ opacity: activeTab === 'owner' ? 1 : 0 }}
          transition={{ duration: TAB_DURATION, ease: TAB_EASING }}
          onAnimationComplete={() => { isAnimatingRef.current = false; }}
          style={activeTab === 'owner'
            ? { position: 'relative' as const, width: '100%' }
            : { visibility: 'hidden' as const, pointerEvents: 'none' as const, position: 'absolute' as const, width: '100%' }}
        >
          <div className="mypage-tab-content">
            {/* Profile Section */}
            <div className="mypage-profile">
              <div className="mypage-photo-slider">
                <ProfileImageSlider
                  className="mypage-couple-img"
                  swiperWrapperClass="mypage-img-wrap"
                  images={profileImages}
                  imagesThumbnail={profileImagesThumbnail}
                  imagesViewer={profileImagesViewer}
                  onMainImageClick={(index) => setGalleryState({ open: true, images: profileImages, index })}
                  navigation={true}
                  innerChildren={
                    <>
                      <div className="swiper-pet-prev"></div>
                      <div className="swiper-pet-next"></div>
                    </>
                  }
                />
              </div>

              <div className="mypage-nickname">{user?.nickname || '사용자'}</div>

              <div className="mypage-stats">
                <dl className="mypage-stats-item">
                  <dt>매칭</dt>
                  <dd>{formatCount(stats?.matchingCount ?? 0)}</dd>
                </dl>
                <div className="mypage-stats-divider" />
                <dl className="mypage-stats-item">
                  <dt>좋아요</dt>
                  <dd>{formatCount(stats?.likesCount ?? 0)}</dd>
                </dl>
                <div className="mypage-stats-divider" />
                <dl className="mypage-stats-item">
                  <dt>친구</dt>
                  <dd>{formatCount(stats?.friendsCount ?? 0)}</dd>
                </dl>
              </div>
            </div>

            {/* Gold Section */}
            <div className="mypage-gold-section">
              <div className="mypage-section-title">보유 골드</div>
              <div className="mypage-gold-card">
                <div className="mypage-gold-amount">
                  <GoldCoinIcon />
                  <span className="mypage-gold-value">
                    {(goldBalance?.balance || 0).toLocaleString()}
                  </span>
                </div>
                {PAYMENT_ENABLED && (
                  <button className="mypage-gold-charge-btn" onClick={() => navigate('/gold/purchase')}>
                    충전
                  </button>
                )}
              </div>
            </div>

            {/* Menu Section */}
            <div className="mypage-menu-section">
              {menuItems.map((item) => (
                <div key={item.label} className="mypage-menu-item" onClick={() => navigate(item.path)}>
                  <span>{item.label}</span>
                  <ChevronRight />
                </div>
              ))}
            </div>
          </div>
        </m.div>

        {/* Pet Tab */}
        <m.div
          animate={{ opacity: activeTab === 'pet' ? 1 : 0 }}
          transition={{ duration: TAB_DURATION, ease: TAB_EASING }}
          onAnimationComplete={() => { isAnimatingRef.current = false; }}
          style={activeTab === 'pet'
            ? { position: 'relative' as const, width: '100%' }
            : { visibility: 'hidden' as const, pointerEvents: 'none' as const, position: 'absolute' as const, width: '100%' }}
        >
          <div className="mypage-tab-content">
            {/* Pet Profile Section */}
            <div className="mypage-profile">
              <div className="mypage-photo-slider">
                <ProfileImageSlider
                  className="mypage-couple-img"
                  swiperWrapperClass="mypage-img-wrap"
                  images={petImages}
                  imagesThumbnail={petImagesThumbnail}
                  imagesViewer={petImagesViewer}
                  onMainImageClick={(index) => setGalleryState({ open: true, images: petImages, index })}
                  navigation={petImages.length > 1}
                  innerChildren={petImages.length > 1 ? (
                    <>
                      <div className="swiper-pet-prev"></div>
                      <div className="swiper-pet-next"></div>
                    </>
                  ) : null}
                />
              </div>

              <div
                className={`mypage-nickname${myPets && myPets.length > 1 ? ' mypage-nickname-selectable' : ''}`}
                onClick={() => myPets && myPets.length > 1 && setPetSelectOpen(true)}
              >
                {selectedPet?.name || '이름'}
                {myPets && myPets.length > 1 && (
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" className="mypage-pet-arrow" aria-hidden="true">
                    <path d="M4 6l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </div>

              {!firstPet && (
                <div className="mypage-pet-tab-empty">
                  <span>등록된 반려동물이 없습니다</span>
                </div>
              )}
            </div>

            {/* Menu Section */}
            <div className="mypage-menu-section">
              {menuItems.map((item) => (
                <div key={item.label} className="mypage-menu-item" onClick={() => navigate(item.path)}>
                  <span>{item.label}</span>
                  <ChevronRight />
                </div>
              ))}
            </div>
          </div>
        </m.div>

        {/* Floating Bottom Button */}
        <button
          className="mypage-floating-btn"
          onClick={handleFloatingButtonClick}
        >
          {hasProfile ? '프로필 보기' : '프로필 등록'}
        </button>
      </div>

      {myPets && myPets.length > 1 && (
        <PetSelectBottomSheet
          isOpen={petSelectOpen}
          pets={myPets}
          selectedPetId={selectedPetId ?? firstPet?.id}
          onSelect={(id) => setSelectedPetId(id)}
          onClose={() => setPetSelectOpen(false)}
        />
      )}
    </SubPageLayout>
  );
};

export default MyPage;
