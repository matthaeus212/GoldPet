
import { memo, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useOverlayColor } from '../../hooks/useOverlayColor';
import { m, AnimatePresence } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { FLIP_HALF_DURATION, FLIP_EASING } from '../../config/cardFlipAnimation';
import { Navigation } from 'swiper/modules';
import type { Friend } from '../../services/friendService';
import { friendService, formatPetTag } from '../../services/friendService';
import { chatService } from '../../services/chatService';
import { blockService } from '../../services/blockService';
import { reportService } from '../../services/reportService';
import { ProfileImageSlider } from '../../components/common/ProfileImageSlider';
import { ReportModal } from '../../components/common/ReportModal';
import { useAlert } from '../../contexts/AlertContext';
import { truncateLocation, formatAgeGroup, formatDistanceKm } from '../../utils/formatters';

interface FriendCardProps {
    data: Friend;
    /**
     * 좋아요/차단 등으로 카드 상태가 바뀌었음을 부모에 알린다.
     * PERF-009: friendId 를 인자로 넘겨야 부모가 `() => handler(friend.id)` 같은 per-item
     * 클로저를 만들지 않고 안정된 콜백 하나를 재사용할 수 있다(memo 유지).
     */
    onLikeStateChange?: (friendId: number) => void;
    viewMode?: 'default' | 'sent_like' | 'received_like' | 'mutual';
    onOpenGallery?: (images: string[], startIndex: number) => void;
    /** Originating sort/list for like_events analytics (compatible|distance|popular|received …). */
    source?: string;
}

const OWNER_FALLBACK = "/assets/images/common/profile_none_img.svg";
const PET_FALLBACK = "/assets/images/main/main_slide_img01.png";

// PERF-009: 카드 하나가 Swiper + AnimatePresence 를 품고 있어 리렌더 비용이 크다. 목록 부모의
// 로컬 state(갤러리 열림, 필터, 좋아요 갱신)가 바뀔 때마다 전체 카드가 다시 그려지던 것을 막는다.
// 부모가 콜백을 안정적으로 넘겨야(useCallback) 이 memo 가 실제로 효과를 낸다.
export const FriendCard = memo(function FriendCard({ data, onLikeStateChange, viewMode = 'default', onOpenGallery, source }: FriendCardProps) {
    const navigate = useNavigate();
    const { showAlert, showConfirm } = useAlert();
    const [isLoading, setIsLoading] = useState(false);
    const [isLiked, setIsLiked] = useState(false);
    const [isMutual, setIsMutual] = useState(false);
    const [showMenu, setShowMenu] = useState(false);
    const [showReportModal, setShowReportModal] = useState(false);
    const [isReportSubmitting, setIsReportSubmitting] = useState(false);
    const [isPetMode, setIsPetMode] = useState(true);

    useOverlayColor(showMenu || showReportModal, '#000000', '#EFE1C4');

    const isAnimatingRef = useRef(false);
    const handleToggle = () => {
        if (isAnimatingRef.current) return;
        isAnimatingRef.current = true;
        setIsPetMode(prev => !prev);
    };

    const handleStartChat = async () => {
        if (isLoading) return;
        setIsLoading(true);
        try {
            const room = await chatService.getOrCreateDirectRoom(data.id);
            navigate(`/chat/${room.id}`);
        } catch (error: unknown) {
            const err = error as { message?: string };
            showAlert(err.message || '채팅방을 열 수 없습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleLike = async () => {
        if (isLoading) return;
        setIsLoading(true);
        try {
            const result = await friendService.likeUser(data.id, source);
            setIsLiked(true);
            setIsMutual(result.isMutual);

            if (result.isMutual) {
                showAlert(`${data.petName}과 서로 좋아해가 되었어요! 💕`);
            } else {
                showAlert(`${data.petName}에게 좋아요를 보냈어요! ❤️`);
            }

            onLikeStateChange?.(data.id);
        } catch {
            showAlert('좋아요를 보내는데 실패했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleDislike = async () => {
        if (isLoading) return;
        setIsLoading(true);
        try {
            if (viewMode === 'received_like') {
                await friendService.rejectLike(data.id);
                showAlert(`${data.petName}님의 좋아요를 거절했어요 😢`);
            } else {
                await friendService.unlikeUser(data.id, source);

                if (viewMode === 'sent_like') {
                    showAlert(`${data.petName}님에게 보낸 좋아요를 취소했어요 ↩️`);
                } else if (viewMode === 'mutual' || isMutual) {
                    showAlert(`${data.petName}님과의 매칭을 해제했어요 👋`);
                } else {
                    showAlert(`${data.petName}님을 패스했어요 🙅`);
                }
            }
            setIsLiked(false);
            setIsMutual(false);
            onLikeStateChange?.(data.id);
        } catch {
            showAlert('작업에 실패했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleBlockUser = () => {
        setShowMenu(false);
        showConfirm('이 사용자를 차단하시겠습니까?\n차단하면 서로의 게시글과 프로필이 보이지 않습니다.', async () => {
            try {
                await blockService.blockUser(data.id);
                showAlert('사용자를 차단했습니다.');
                onLikeStateChange?.(data.id);
            } catch {
                showAlert('차단에 실패했습니다.');
            }
        });
    };

    const handleReportUser = () => {
        setShowMenu(false);
        setShowReportModal(true);
    };

    const handleReportSubmit = async (reason: string) => {
        setIsReportSubmitting(true);
        try {
            await reportService.createReport({ type: 'USER', targetId: data.id, reason });
            setShowReportModal(false);
            showAlert('신고가 접수되었습니다.');
        } catch (error: unknown) {
            const err = error as { response?: { status?: number; data?: { message?: string } } };
            setShowReportModal(false);
            if (err.response?.status === 400) {
                showAlert(err.response?.data?.message || '이미 신고한 대상입니다.');
            } else if (err.response?.status === 404) {
                showAlert('신고 대상을 찾을 수 없습니다.');
            } else {
                showAlert('신고 접수에 실패했습니다.');
            }
        } finally {
            setIsReportSubmitting(false);
        }
    };

    const ownerImages = data.profileImages.length > 0 ? data.profileImages : (data.profileImageUrl ? [data.profileImageUrl] : [OWNER_FALLBACK]);
    const ownerImagesThumbnail = data.profileImages.length > 0
        ? data.profileImagesThumbnail
        : (data.profileImageUrlThumbnail ? [data.profileImageUrlThumbnail] : undefined);
    const ownerImagesViewer = data.profileImages.length > 0
        ? data.profileImagesViewer
        : (data.profileImageUrlViewer ? [data.profileImageUrlViewer] : undefined);
    const sliderImages = isPetMode ? data.images : ownerImages;
    const sliderImagesThumbnail = isPetMode ? data.imagesThumbnail : ownerImagesThumbnail;
    const sliderImagesViewer = isPetMode ? data.imagesViewer : ownerImagesViewer;
    const sliderSecondary = isPetMode ? (data.profileImages[0] || data.profileImageUrl || OWNER_FALLBACK) : (data.images[0] || PET_FALLBACK);
    const sliderSecondaryThumbnail = isPetMode
        ? (data.profileImagesThumbnail?.[0] || data.profileImageUrlThumbnail || null)
        : (data.imagesThumbnail?.[0] || null);



    return (
        <div className="list_wrap" style={{ position: 'relative' }}>
            {/* Menu Button - 세로 3점 (케밥 메뉴) */}
            <button
                className="friend_card_menu_btn"
                onClick={(e) => { e.stopPropagation(); setShowMenu(!showMenu); }}
                aria-label="메뉴"
            >
                <span className="friend_card_kebab_dot" />
                <span className="friend_card_kebab_dot" />
                <span className="friend_card_kebab_dot" />
            </button>
            {showMenu && createPortal(
                <div className="friend_card_bottomsheet_overlay" onClick={() => setShowMenu(false)}>
                    <div className="friend_card_bottomsheet" onClick={(e) => e.stopPropagation()}>
                        <div className="friend_card_bottomsheet_menu">
                            <button type="button" className="friend_card_bottomsheet_item" onClick={handleBlockUser}>
                                차단하기
                            </button>
                            <div className="friend_card_bottomsheet_divider" />
                            <button type="button" className="friend_card_bottomsheet_item" onClick={handleReportUser}>
                                신고하기
                            </button>
                        </div>
                    </div>
                </div>,
                document.body
            )}

                <AnimatePresence mode="wait"
                    onExitComplete={() => { isAnimatingRef.current = false; }}>
                    <m.div
                        key={isPetMode ? 'pet' : 'owner'}
                        initial={{ scaleX: 0, opacity: 0 }}
                        animate={{ scaleX: 1, opacity: 1 }}
                        exit={{ scaleX: 0, opacity: 0 }}
                        transition={{ duration: FLIP_HALF_DURATION, ease: FLIP_EASING }}
                        style={{ transformOrigin: 'center center' }}
                    >
                        <ProfileImageSlider
                            className="img_wrap"
                            swiperWrapperClass="pet_img_wrap"
                            images={sliderImages}
                            imagesThumbnail={sliderImagesThumbnail}
                            imagesViewer={sliderImagesViewer}
                            secondaryImage={sliderSecondary}
                            secondaryImageThumbnail={sliderSecondaryThumbnail}
                            secondaryImageClass="butler_img"
                            onSecondaryImageClick={handleToggle}
                            onMainImageClick={(index) => onOpenGallery?.(sliderImages, index)}
                            swiperProps={{
                                modules: [Navigation],
                                navigation: {
                                    prevEl: `.swiper-pet-prev-${data.id}`,
                                    nextEl: `.swiper-pet-next-${data.id}`,
                                }
                            }}
                            innerChildren={
                                <>
                                    <div className={`swiper-pet-prev swiper-pet-prev-${data.id}`}></div>
                                    <div className={`swiper-pet-next swiper-pet-next-${data.id}`}></div>
                                </>
                            }
                        >
                            {/* Icon Top Left */}
                            <img src="/assets/images/main/main_slide_icon01.svg" className="icon_img" alt="" />
                        </ProfileImageSlider>

                        {isPetMode ? (
                            <>
                                <div className="txt_wrap">
                                    <h2>{data.petName} <img src={data.petGender === 'FEMALE' ? "/assets/images/main/woman_icon.svg" : "/assets/images/main/man_icon.svg"} alt="성별 icon" /></h2>
                                    {data.isNeutered && <p>중성화 OK</p>}
                                </div>
                                <div className="pet_detail">
                                    <p>
                                        {data.petAge}살 ∙ {data.petBreed} <br />
                                        {truncateLocation(data.locationText)} ∙ {formatDistanceKm(data.distance)}
                                    </p>
                                    <br />
                                    <p>{data.description}</p>
                                    <div className="wish_dot">
                                        {data.tags
                                            .map(tag => formatPetTag(tag))
                                            .filter(tag => tag !== '')
                                            .map((tag, i) => (
                                                <p key={i} className={`label${(i % 4) + 1}`}>{tag}</p>
                                            ))
                                        }
                                    </div>

                                    {/* Like/Dislike buttons */}
                                    <div className="like_wrap">
                                        {viewMode === 'sent_like' ? (
                                            <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading} style={{ width: '100%' }}>
                                                <img src="/assets/images/common/dislike_btn.svg" alt="" />취소하기
                                            </button>
                                        ) : viewMode === 'mutual' || isMutual ? (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />싫어요
                                                </button>
                                                <button type="button" className="like_btn active" onClick={handleStartChat} disabled={isLoading}>
                                                    <img src="/assets/images/common/chat_icon.svg" alt="" style={{ marginRight: '6px' }} />채팅하기
                                                </button>
                                            </>
                                        ) : isLiked ? (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />취소
                                                </button>
                                                <button type="button" className="like_btn active" disabled>
                                                    <img src="/assets/images/common/like_btn.svg" alt="" />좋아요 완료
                                                </button>
                                            </>
                                        ) : (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />싫어요
                                                </button>
                                                <button type="button" className="like_btn" onClick={handleLike} disabled={isLoading}>
                                                    <img src="/assets/images/common/like_btn.svg" alt="" />좋아요
                                                </button>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                <div className="txt_wrap">
                                    <h2>
                                        {data.nickname}{' '}
                                        {data.ownerGender && (
                                            <img
                                                src={data.ownerGender === 'FEMALE'
                                                    ? '/assets/images/main/woman_icon.svg'
                                                    : '/assets/images/main/man_icon.svg'}
                                                alt="성별 icon"
                                            />
                                        )}
                                    </h2>
                                </div>
                                <div className="pet_detail">
                                    <p>
                                        {formatAgeGroup(data.ownerAge)}{data.ownerAge && data.ownerMbti ? ' ∙ ' : ''}{data.ownerMbti || ''}<br />
                                        {truncateLocation(data.locationText)} ∙ {formatDistanceKm(data.distance)}
                                    </p>
                                    <br />
                                    {data.ownerIntro && <p>{data.ownerIntro}</p>}
                                    {(data.ownerInterests.length > 0 || data.ownerHobbies.length > 0) && (
                                        <div className="wish_dot">
                                            {data.ownerInterests.map((interest, i) => (
                                                <p key={`interest-${i}`} className={`label${(i % 4) + 1}`}>{interest}</p>
                                            ))}
                                            {data.ownerHobbies.map((hobby, i) => (
                                                <p key={`hobby-${i}`} className={`label${((data.ownerInterests.length + i) % 4) + 1}`}>{hobby}</p>
                                            ))}
                                        </div>
                                    )}

                                    {/* Like/Dislike buttons */}
                                    <div className="like_wrap">
                                        {viewMode === 'sent_like' ? (
                                            <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading} style={{ width: '100%' }}>
                                                <img src="/assets/images/common/dislike_btn.svg" alt="" />취소하기
                                            </button>
                                        ) : viewMode === 'mutual' || isMutual ? (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />싫어요
                                                </button>
                                                <button type="button" className="like_btn active" onClick={handleStartChat} disabled={isLoading}>
                                                    <img src="/assets/images/common/chat_icon.svg" alt="" style={{ marginRight: '6px' }} />채팅하기
                                                </button>
                                            </>
                                        ) : isLiked ? (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />취소
                                                </button>
                                                <button type="button" className="like_btn active" disabled>
                                                    <img src="/assets/images/common/like_btn.svg" alt="" />좋아요 완료
                                                </button>
                                            </>
                                        ) : (
                                            <>
                                                <button type="button" className="dislike_btn" onClick={handleDislike} disabled={isLoading}>
                                                    <img src="/assets/images/common/dislike_btn.svg" alt="" />싫어요
                                                </button>
                                                <button type="button" className="like_btn" onClick={handleLike} disabled={isLoading}>
                                                    <img src="/assets/images/common/like_btn.svg" alt="" />좋아요
                                                </button>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </>
                        )}
                    </m.div>
                </AnimatePresence>
            <ReportModal
                isOpen={showReportModal}
                onClose={() => setShowReportModal(false)}
                onSubmit={handleReportSubmit}
                isSubmitting={isReportSubmitting}
            />
        </div>
    );
});
