import React, { useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { m } from 'motion/react';
import { petService } from '../../services/petService';
import { CACHE_TIME } from '../../config/queryConfig';
import { LocationStatus } from '../../components/common/LocationStatus';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { TAB_DURATION, TAB_EASING } from '../../config/cardFlipAnimation';
import ProfileOwnerTab from './ProfileOwnerTab';
import ProfilePetTab from './ProfilePetTab';
import './ProfilePage.css';

const ProfilePage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const { data: myPets } = useQuery({
    queryKey: ['pets', 'my'],
    queryFn: petService.getMyPets,
    ...CACHE_TIME.DYNAMIC,
  });
  const tabParam = searchParams.get('tab');
  const [activeTab, setActiveTab] = useState<'owner' | 'pet'>(tabParam === 'pet' ? 'pet' : 'owner');
  const isAnimatingRef = useRef(false);

  const handleTabChange = (tab: 'owner' | 'pet') => {
    if (isAnimatingRef.current || tab === activeTab) return;
    isAnimatingRef.current = true;
    setActiveTab(tab);
  };

  const settingsButton = (
    <button type="button" onClick={() => navigate('/settings')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
        <path d="M12 15a3 3 0 100-6 3 3 0 000 6z" />
        <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
      </svg>
    </button>
  );

  return (
    <SubPageLayout title="프로필" onBack={() => navigate('/mypage')} rightAction={settingsButton}>
      <div id="profileContainer">
        <LocationStatus className="talk_wrap">
          반려동물과 함께하는 <br />
          행복한 일상을 같이 공유하고 싶어요
        </LocationStatus>

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

        <m.div
          animate={{ opacity: activeTab === 'owner' ? 1 : 0 }}
          transition={{ duration: TAB_DURATION, ease: TAB_EASING }}
          onAnimationComplete={() => { isAnimatingRef.current = false; }}
          style={activeTab === 'owner'
            ? { position: 'relative' as const, width: '100%' }
            : { visibility: 'hidden' as const, pointerEvents: 'none' as const, position: 'absolute' as const, width: '100%' }}
        >
          <ProfileOwnerTab />
        </m.div>
        <m.div
          animate={{ opacity: activeTab === 'pet' ? 1 : 0 }}
          transition={{ duration: TAB_DURATION, ease: TAB_EASING }}
          onAnimationComplete={() => { isAnimatingRef.current = false; }}
          style={activeTab === 'pet'
            ? { position: 'relative' as const, width: '100%' }
            : { visibility: 'hidden' as const, pointerEvents: 'none' as const, position: 'absolute' as const, width: '100%' }}
        >
          <ProfilePetTab />
        </m.div>

        {/* Bottom action bar */}
        <div className="profile-bottom-bar">
          <button
            type="button"
            className="profile-bottom-btn confirm"
            onClick={() => navigate('/mypage')}
          >
            확인
          </button>
          <button
            type="button"
            className="profile-bottom-btn edit"
            onClick={() => {
              if (activeTab === 'owner') {
                navigate('/profile/owner/edit');
              } else {
                const petId = myPets?.[0]?.id;
                navigate(petId ? `/profile/pet/edit?petId=${petId}` : '/profile/pet/edit');
              }
            }}
          >
            수정
          </button>
        </div>
      </div>
    </SubPageLayout>
  );
};

export default ProfilePage;
