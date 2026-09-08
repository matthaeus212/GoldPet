import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { GpImage } from '../../components/common/GpImage';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { ReportModal } from '../../components/common/ReportModal';
import { BackButton } from '../../components/common/BackButton';
import { blockService } from '../../services/blockService';
import { reportService } from '../../services/reportService';
import { chatService, type ChatServiceError } from '../../services/chatService';
import { friendService } from '../../services/friendService';
import { useAlert } from '../../contexts/AlertContext';
import { useUserPublicProfile } from './useUserPublicProfile';
import { useUserCommunityPhotos } from './useUserCommunityPhotos';
import { useUserWalkPhotos } from './useUserWalkPhotos';
import { useRouterRecoveryGuard } from '../../hooks/useRouterRecoveryGuard';

type ProfileTab = 'posts' | 'walks' | 'pets';
const VALID_TABS: ProfileTab[] = ['posts', 'walks', 'pets'];

const PROFILE_FALLBACK = '/assets/images/common/profile_none_img.svg';

/* ─── Skeleton ─────────────────────────────────────────────── */
function ProfileSkeleton() {
  return (
    <div className="up-skeleton">
      <div className="up-skeleton-header">
        <div className="up-skeleton-avatar" />
        <div className="up-skeleton-info">
          <div className="up-skeleton-line up-skeleton-name" />
          <div className="up-skeleton-line up-skeleton-count" />
        </div>
      </div>
      <div className="up-grid-3">
        {Array.from({ length: 9 }).map((_, i) => (
          <div key={i} className="up-grid-item up-skeleton-tile" />
        ))}
      </div>
    </div>
  );
}

/* ─── More Menu ─────────────────────────────────────────────── */
interface MoreMenuProps {
  blockedByMe: boolean;
  onChat: () => void;
  onReport: () => void;
  onBlock: () => void;
  onUnblock: () => void;
  onClose: () => void;
}

function MoreMenu({ blockedByMe, onChat, onReport, onBlock, onUnblock, onClose }: MoreMenuProps) {
  return (
    <>
      <div className="up-menu-backdrop" onClick={onClose} />
      <div className="up-menu-sheet">
        {/* 차단한 상대는 매칭 여부와 무관하게 채팅 진입점을 사전 억제 — 백엔드가 403(FORBIDDEN)으로 거부하기 전에 UI에서 숨긴다 */}
        {!blockedByMe && (
          <button type="button" className="up-menu-item" onClick={onChat}>
            채팅하기
          </button>
        )}
        <button type="button" className="up-menu-item" onClick={onReport}>
          신고하기
        </button>
        <button
          type="button"
          className="up-menu-item up-menu-item--danger"
          onClick={blockedByMe ? onUnblock : onBlock}
        >
          {blockedByMe ? '차단 해제' : '차단하기'}
        </button>
      </div>
    </>
  );
}

/* ─── Gallery state type ─────────────────────────────────────── */
interface GalleryState {
  open: boolean;
  images: string[];
  index: number;
}

/* ─── Main Component ─────────────────────────────────────────── */
export default function UserProfilePage() {
  const { userId: userIdParam } = useParams<{ userId: string }>();
  const userId = Number(userIdParam);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showAlert, showConfirm } = useAlert();

  const { data: profile, isLoading: profileLoading, isError, error } = useUserPublicProfile(userId);

  /* ── Tab state (URL ?tab= 동기화 — 공유링크/뒤로가기 친화) ── */
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab') as ProfileTab | null;
  const tab: ProfileTab = rawTab && VALID_TABS.includes(rawTab) ? rawTab : 'posts';

  const setTab = useCallback(
    (next: ProfileTab) => {
      const params = new URLSearchParams(searchParams);
      if (next === 'posts') {
        params.delete('tab');
      } else {
        params.set('tab', next);
      }
      setSearchParams(params, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  /* ── Tab-scoped pagination hooks ── */
  const communityQuery = useUserCommunityPhotos(userId);
  const walkQuery = useUserWalkPhotos(userId, tab === 'walks');

  const {
    data: photosData,
    isFetchingNextPage,
    isLoading: photosLoading,
  } = communityQuery;

  const [menuOpen, setMenuOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [gallery, setGallery] = useState<GalleryState>({ open: false, images: [], index: 0 });

  const sentinelRef = useRef<HTMLDivElement>(null);

  /* ── §S3 router recovery: patch history stack on direct entry ── */
  useRouterRecoveryGuard({ fallbackPath: '/community' });

  /* ── isMe → redirect to /mypage ── */
  useEffect(() => {
    if (profile?.isMe) {
      navigate('/mypage', { replace: true });
    }
  }, [profile?.isMe, navigate]);

  /* ── 404 auto-back after 2s ── */
  const is404 =
    isError &&
    (error as { response?: { status?: number } })?.response?.status === 404;

  useEffect(() => {
    if (!is404) return;
    const timer = setTimeout(() => navigate(-1), 2000);
    return () => clearTimeout(timer);
  }, [is404, navigate]);

  /* ── IntersectionObserver sentinel — 활성 탭의 pagination 훅으로 fetchNext 분기 ── */
  const activePagination = tab === 'walks' ? walkQuery : communityQuery;

  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      if (
        entries[0].isIntersecting &&
        activePagination.hasNextPage &&
        !activePagination.isFetchingNextPage
      ) {
        activePagination.fetchNextPage();
      }
    },
    [activePagination],
  );

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(handleIntersect, { rootMargin: '200px' });
    observer.observe(el);
    return () => observer.disconnect();
  }, [handleIntersect]);

  /* ── Block / Unblock mutations ── */
  const blockMutation = useMutation({
    mutationFn: () => blockService.blockUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', userId, 'publicProfile'] });
      setMenuOpen(false);
    },
  });

  const unblockMutation = useMutation({
    mutationFn: () => blockService.unblockUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', userId, 'publicProfile'] });
      setMenuOpen(false);
    },
  });

  /* ── Report mutation ── */
  const [isReporting, setIsReporting] = useState(false);
  const handleReportSubmit = async (reason: string) => {
    setIsReporting(true);
    try {
      await reportService.createReport({ type: 'USER', targetId: userId, reason });
      setReportOpen(false);
      setMenuOpen(false);
    } finally {
      setIsReporting(false);
    }
  };

  /* ── 채팅하기: 비매칭(NO_MATCH) 시 실행형 좋아요 CTA로 전환 ── */
  const handleSendLikeFromChatCta = async () => {
    try {
      const result = await friendService.likeUser(userId, 'profile');
      if (result.isMutual) {
        showAlert('서로 좋아요가 되었어요! 이제 채팅할 수 있어요. 💕');
      } else {
        showAlert('좋아요를 보냈어요! 상대방도 좋아요를 보내면 채팅할 수 있어요. ❤️');
      }
    } catch {
      showAlert('좋아요를 보내는데 실패했습니다.');
    }
  };

  const handleChat = async () => {
    try {
      const room = await chatService.getOrCreateDirectRoom(userId);
      navigate(`/chat/${room.id}`);
    } catch (error: unknown) {
      const err = error as ChatServiceError;
      if (err.code === 'NO_MATCH') {
        showConfirm(
          '아직 친구가 아니에요. 좋아요를 보내고 서로 좋아요가 되면 채팅할 수 있어요.',
          handleSendLikeFromChatCta,
          undefined,
          { confirmText: '좋아요 보내기', cancelText: '닫기' },
        );
      } else if (err.code === 'FORBIDDEN') {
        // 방어적 백업 분기 — 정상 경로는 MoreMenu의 blockedByMe 사전 억제로 여기 도달이 희박함.
        showAlert('차단된 상대와는 채팅할 수 없습니다.');
      } else {
        showAlert(err.message || '채팅방을 열 수 없습니다.');
      }
    }
  };

  /* ── Flatten photo pages & filter out posts without images ── */
  const allPosts = (photosData?.pages.flatMap((p) => p.posts) ?? []).filter(
    (post) => post.thumbnailUrl || post.mediumUrl || post.viewerUrl,
  );

  /* ── Walk photos flatten (탭 활성 시 채워짐) ── */
  const allWalkPhotos = useMemo(
    () =>
      (walkQuery.data?.pages.flatMap((p) => p.photos) ?? []).filter(
        (ph) => ph.imageUrlThumb || ph.imageUrlViewer || ph.imageUrl,
      ),
    [walkQuery.data],
  );

  /* ── Tile click → gallery (single image) ── */
  const openGallery = (viewerUrl: string | null, mediumUrl: string | null, thumbnailUrl: string | null) => {
    const img = viewerUrl ?? mediumUrl ?? thumbnailUrl;
    if (!img) return;
    setGallery({ open: true, images: [img], index: 0 });
  };

  /* ── Gallery with multiple images ── */
  const openGalleryMulti = (images: string[], startIndex = 0) => {
    if (images.length === 0) return;
    setGallery({ open: true, images, index: startIndex });
  };

  /* ────────── RENDER ────────── */

  if (profileLoading || photosLoading) {
    return (
      <div className="up-page">
        <div className="up-header-bar">
          <BackButton onClick={() => navigate(-1)} />
        </div>
        <ProfileSkeleton />
      </div>
    );
  }

  if (is404 || (isError && !profile)) {
    return (
      <div className="up-page">
        <div className="up-header-bar">
          <BackButton onClick={() => navigate(-1)} />
        </div>
        <div className="up-not-found">
          <img src="/assets/images/common/profile_none_img.svg" alt="" className="up-not-found-img" />
          <p className="up-not-found-text">프로필을 불러올 수 없어요</p>
          <p className="up-not-found-sub">잠시 후 자동으로 돌아갑니다</p>
          <button type="button" className="up-not-found-btn" onClick={() => navigate(-1)}>
            돌아가기
          </button>
        </div>
      </div>
    );
  }

  if (!profile) return null;

  const isBlockedByMe = profile.status === 'BLOCKED_BY_ME';

  return (
    <div className="up-page">
      {/* ── Header bar ── */}
      <div className="up-header-bar">
        <BackButton onClick={() => navigate(-1)} />
        <div className="up-header-center">
          <span className="up-header-nickname">{profile.nickname ?? '사용자'}</span>
        </div>
        {/* ⋯ menu — only for other users */}
        <button
          type="button"
          className="up-more-btn"
          onClick={() => setMenuOpen((v) => !v)}
          aria-label="더보기"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="5" r="1.5" fill="var(--color-text-secondary)" />
            <circle cx="12" cy="12" r="1.5" fill="var(--color-text-secondary)" />
            <circle cx="12" cy="19" r="1.5" fill="var(--color-text-secondary)" />
          </svg>
        </button>
      </div>

      {/* ── Profile header ── */}
      <div className="up-profile-header">
        <div
          className="up-avatar-wrap"
          onClick={() => {
            const viewerList = profile.profileUrlsViewer?.filter(Boolean) ?? [];
            const urlList = profile.profileUrls?.filter(Boolean) ?? [];
            const galleryImages = viewerList.length > 0 ? viewerList : urlList;
            if (galleryImages.length > 0) {
              openGalleryMulti(galleryImages);
            } else if (profile.profileUrlViewer || profile.profileUrl) {
              openGallery(profile.profileUrlViewer ?? null, null, profile.profileUrl ?? null);
            }
          }}
          style={profile.profileUrlViewer || profile.profileUrlThumbnail || profile.profileUrl ? { cursor: 'pointer' } : undefined}
        >
          {profile.profileUrlViewer || profile.profileUrlThumbnail || profile.profileUrl ? (
            <GpImage
              src={profile.profileUrl}
              thumbnailSrc={profile.profileUrlThumbnail}
              viewerSrc={profile.profileUrlViewer}
              variant="thumbnail"
              alt={profile.nickname ?? ''}
              className="up-avatar"
            />
          ) : (
            <img src={PROFILE_FALLBACK} alt="" className="up-avatar" />
          )}
        </div>
        <div className="up-profile-info">
          <strong className="up-nickname">{profile.nickname ?? (isBlockedByMe ? '차단한 사용자' : '사용자')}</strong>
          {!isBlockedByMe && (
            <span className="up-post-count">게시글 {profile.publicPostCount}개</span>
          )}
        </div>
      </div>

      {/* ── Intro (탭과 무관하게 상단에 유지) ── */}
      {!isBlockedByMe && profile.intro && (
        <div style={{ padding: '0 16px 16px' }}>
          <p style={{ fontSize: '14px', color: 'var(--color-text-primary, #333)', margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.4 }}>
            {profile.intro}
          </p>
        </div>
      )}

      {/* ── BLOCKED_BY_ME: 탭 없이 차단 해제 UI만 노출 ── */}
      {isBlockedByMe ? (
        <div className="up-blocked-wrap">
          <p className="up-blocked-text">차단한 사용자입니다</p>
          <button
            type="button"
            className="up-unblock-btn"
            onClick={() => unblockMutation.mutate()}
            disabled={unblockMutation.isPending}
          >
            {unblockMutation.isPending ? '처리 중...' : '차단 해제'}
          </button>
        </div>
      ) : (
        <>
          {/* ── Tabs (community-author-profile-gallery Phase 2 F1) ── */}
          <nav className="up-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'posts'}
              className={`up-tab ${tab === 'posts' ? 'up-tab--active' : ''}`}
              onClick={() => setTab('posts')}
            >
              게시글
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'walks'}
              className={`up-tab ${tab === 'walks' ? 'up-tab--active' : ''}`}
              onClick={() => setTab('walks')}
            >
              산책
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'pets'}
              className={`up-tab ${tab === 'pets' ? 'up-tab--active' : ''}`}
              onClick={() => setTab('pets')}
            >
              반려동물
            </button>
          </nav>

          {/* ── Posts tab ── */}
          {tab === 'posts' && (
            <>
              {allPosts.length === 0 && !isFetchingNextPage ? (
                <div className="up-empty">
                  <p>아직 공유한 사진이 없어요</p>
                </div>
              ) : (
                <ul className="up-grid-3">
                  {allPosts.map((post) => (
                    <li
                      key={post.id}
                      className="up-grid-item"
                      onClick={() => openGallery(post.viewerUrl, post.mediumUrl, post.thumbnailUrl)}
                    >
                      <GpImage
                        src={post.thumbnailUrl}
                        thumbnailSrc={post.thumbnailUrl}
                        mediumSrc={post.mediumUrl}
                        viewerSrc={post.viewerUrl}
                        variant="thumbnail"
                        alt=""
                        className="up-grid-img"
                      />
                      {post.imageCount > 1 && (
                        <span className="up-img-count-badge">{post.imageCount}</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {isFetchingNextPage && <div className="up-loading-more">로딩 중...</div>}
            </>
          )}

          {/* ── Walks tab (lazy — useUserWalkPhotos enabled on tab entry) ── */}
          {tab === 'walks' && (
            <>
              {walkQuery.isLoading ? (
                <ul className="up-grid-3">
                  {Array.from({ length: 9 }).map((_, i) => (
                    <li key={i} className="up-grid-item up-skeleton-tile" />
                  ))}
                </ul>
              ) : allWalkPhotos.length === 0 && !walkQuery.isFetchingNextPage ? (
                <div className="up-empty">
                  <p>공개된 산책 사진이 없어요</p>
                </div>
              ) : (
                <ul className="up-grid-3">
                  {allWalkPhotos.map((photo) => (
                    <li
                      key={photo.id}
                      className="up-grid-item"
                      onClick={() =>
                        openGallery(
                          photo.imageUrlViewer,
                          null,
                          photo.imageUrlThumb ?? photo.imageUrl,
                        )
                      }
                    >
                      <GpImage
                        src={photo.imageUrlThumb ?? photo.imageUrl}
                        thumbnailSrc={photo.imageUrlThumb}
                        viewerSrc={photo.imageUrlViewer}
                        variant="thumbnail"
                        alt=""
                        className="up-grid-img"
                      />
                    </li>
                  ))}
                </ul>
              )}
              {walkQuery.isFetchingNextPage && <div className="up-loading-more">로딩 중...</div>}
            </>
          )}

          {/* ── Pets tab (기존 렌더링 로직 유지 — API/DTO 변경 없음) ── */}
          {tab === 'pets' && (
            <>
              {profile.pets && profile.pets.length > 0 ? (
                <div style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {profile.pets.map((pet) => {
                    const petViewerUrls = pet.profileImageUrlsViewer?.filter(Boolean) ?? [];
                    const petOrigUrls = pet.profileImageUrls?.filter(Boolean) ?? [];
                    const petGalleryImages = petViewerUrls.length > 0 ? petViewerUrls : petOrigUrls;
                    const petThumb = pet.profileImageUrlThumbnail ?? pet.profileImageUrl;
                    return (
                      <div key={pet.id} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <div
                          onClick={() => {
                            if (petGalleryImages.length > 0) openGalleryMulti(petGalleryImages);
                            else if (pet.profileImageUrlViewer || pet.profileImageUrl) {
                              openGallery(pet.profileImageUrlViewer ?? null, null, pet.profileImageUrl ?? null);
                            }
                          }}
                          style={{ flexShrink: 0, cursor: petThumb ? 'pointer' : 'default' }}
                        >
                          {petThumb ? (
                            <GpImage
                              src={pet.profileImageUrl}
                              thumbnailSrc={pet.profileImageUrlThumbnail}
                              viewerSrc={pet.profileImageUrlViewer}
                              variant="thumbnail"
                              alt={pet.name}
                              style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover', background: 'var(--color-border, #f0f0f0)' }}
                            />
                          ) : (
                            <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'var(--color-border, #f0f0f0)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '20px' }}>🐾</div>
                          )}
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                          <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--color-text-primary, #333)' }}>{pet.name}</span>
                          <span style={{ fontSize: '12px', color: 'var(--color-text-secondary, #666)' }}>
                            {pet.species}{pet.breed ? ` · ${pet.breed}` : ''}
                          </span>
                        </div>
                        {petOrigUrls.length > 1 && (
                          <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--color-text-tertiary, #999)', background: 'var(--color-bg-secondary, #f5f5f5)', padding: '2px 6px', borderRadius: '8px' }}>
                            📷 {petOrigUrls.length}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="up-empty">
                  <p>등록된 반려동물이 없어요</p>
                </div>
              )}
            </>
          )}

          {/* ── Infinite scroll sentinel — posts/walks 탭에서 활용, pets 탭에서는 no-op ── */}
          <div ref={sentinelRef} style={{ height: 1 }} />
        </>
      )}

      {/* ── More menu ── */}
      {menuOpen && (
        <MoreMenu
          blockedByMe={isBlockedByMe}
          onChat={() => {
            setMenuOpen(false);
            void handleChat();
          }}
          onReport={() => {
            setMenuOpen(false);
            setReportOpen(true);
          }}
          onBlock={() => blockMutation.mutate()}
          onUnblock={() => unblockMutation.mutate()}
          onClose={() => setMenuOpen(false)}
        />
      )}

      {/* ── Report modal ── */}
      <ReportModal
        isOpen={reportOpen}
        onClose={() => setReportOpen(false)}
        onSubmit={handleReportSubmit}
        isSubmitting={isReporting}
      />

      {/* ── Image gallery modal ── */}
      <ImageGalleryModal
        isOpen={gallery.open}
        images={gallery.images}
        initialIndex={gallery.index}
        onClose={() => setGallery((g) => ({ ...g, open: false }))}
      />
    </div>
  );
}
