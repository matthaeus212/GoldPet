import { useState } from 'react';
import { Swiper, SwiperSlide } from 'swiper/react';
import { Pagination } from 'swiper/modules';
import { GpImage } from '../../../components/common/GpImage';
import { getImageUrl } from '../../../utils/imageUrl';
import type { HomeRecommendation } from '../../../services/homeService';

const OWNER_FALLBACK = '/assets/images/common/profile_none_img.svg';
const PET_FALLBACK = '/assets/images/common/pet_none_img02.svg';

interface FriendRecommendCardProps {
    item: HomeRecommendation;
    liked: boolean;
    loading: boolean;
    onLike: (id: number, name: string) => void;
    onOpenGallery: (images: string[], index: number) => void;
    /** 첫 번째 카드이면 true — 첫 슬라이드를 LCP 우선순위로 로드 */
    isLCP?: boolean;
}

export function FriendRecommendCard({ item, liked, loading, onLike, onOpenGallery, isLCP = false }: FriendRecommendCardProps) {
    const [petMode, setPetMode] = useState(true);

    const ownerImages = item.profileImages.length > 0
        ? item.profileImages
        : (item.profileImageUrl ? [item.profileImageUrl] : [OWNER_FALLBACK]);
    const petImages = item.images.length > 0 ? item.images : [PET_FALLBACK];

    const currentImages = petMode ? petImages : ownerImages;
    const currentThumbs = petMode ? item.imagesThumbnail : item.profileImagesThumbnail;
    const currentWebpThumbs = petMode ? item.imagesThumbnailWebp : item.profileImagesThumbnailWebp;

    // 토글 아바타 = 반대 모드의 첫 이미지
    const toggleSrc = petMode
        ? (item.profileImages[0] || item.profileImageUrl || OWNER_FALLBACK)
        : (item.images[0] || PET_FALLBACK);

    const displayName = petMode ? item.name : item.nickname;
    const gender = petMode ? item.gender : item.ownerGender;
    const genderIcon = gender === 'FEMALE'
        ? '/assets/images/main/woman_icon.svg'
        : '/assets/images/main/man_icon.svg';
    // 위치 없는 후보(원시 distance<=0)는 homeService에서 이미 null로 처리됨 — "0 이내" 오표시 가드.
    const distanceText = item.distance ? `${item.distance} 이내` : null;
    const petBase = item.petBreed;
    const ownerBase = item.locationText || '위치 미설정';
    const metaText = petMode
        ? (distanceText ? `${petBase} ∙ ${distanceText}` : petBase)
        : (distanceText ? `${ownerBase} ∙ ${distanceText}` : ownerBase);

    return (
        <div className="friend_card">
            <div className="friend_card__top">
                <div className="friend_card__imgwrap">
                    <div className="friend_card__img">
                        <Swiper
                            modules={[Pagination]}
                            pagination={currentImages.length > 1 ? { clickable: true } : false}
                            spaceBetween={0}
                            key={petMode ? 'pet' : 'owner'}
                        >
                            {currentImages.map((img, imgIdx) => {
                                const thumb = currentThumbs?.[imgIdx] ?? null;
                                const webpThumb = currentWebpThumbs?.[imgIdx] ?? null;
                                const isFirstSlide = isLCP && imgIdx === 0;
                                return (
                                    <SwiperSlide key={imgIdx}>
                                        {thumb ? (
                                            <GpImage
                                                className="friend_card__photo"
                                                src={img}
                                                thumbnailSrc={thumb}
                                                webpThumbnailSrc={webpThumb}
                                                variant="thumbnail"
                                                alt={displayName}
                                                loading={isFirstSlide ? 'eager' : 'lazy'}
                                                decoding={isFirstSlide ? 'sync' : 'async'}
                                                fetchPriority={isFirstSlide ? 'high' : undefined}
                                                onClick={() => onOpenGallery(currentImages, imgIdx)}
                                            />
                                        ) : (
                                            <img
                                                className="friend_card__photo"
                                                src={getImageUrl(img, 'thumbnail')}
                                                alt={displayName}
                                                loading={isFirstSlide ? 'eager' : 'lazy'}
                                                decoding={isFirstSlide ? 'sync' : 'async'}
                                                fetchPriority={isFirstSlide ? 'high' : undefined}
                                                onClick={() => onOpenGallery(currentImages, imgIdx)}
                                                onError={(e) => {
                                                    const target = e.currentTarget;
                                                    if (!target.dataset.fallback) {
                                                        target.dataset.fallback = 'true';
                                                        target.src = img;
                                                    }
                                                }}
                                            />
                                        )}
                                    </SwiperSlide>
                                );
                            })}
                        </Swiper>
                    </div>
                    <button
                        type="button"
                        className="friend_card__toggle"
                        onClick={() => setPetMode((prev) => !prev)}
                        aria-label={petMode ? '보호자 보기' : '반려동물 보기'}
                    >
                        <img
                            src={toggleSrc}
                            alt=""
                            onError={(e) => {
                                e.currentTarget.src = petMode ? OWNER_FALLBACK : PET_FALLBACK;
                            }}
                        />
                    </button>
                </div>
                <div className="friend_card__txt">
                    <div className="friend_card__main">
                        <div className="friend_card__name">
                            <strong>{displayName}</strong>
                            {gender && (
                                <img src={genderIcon} alt={gender === 'FEMALE' ? '여성' : '남성'} />
                            )}
                        </div>
                        <p className="friend_card__meta">{metaText}</p>
                    </div>
                </div>
            </div>
            <button
                type="button"
                className="friend_card__like"
                onClick={() => onLike(item.id, item.name)}
                disabled={loading || liked}
            >
                <img src="/assets/images/home/heart_white.svg" alt="" />
                {liked ? '조하멍 완료' : '조하멍'}
            </button>
        </div>
    );
}
