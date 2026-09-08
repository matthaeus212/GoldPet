import React, { useRef } from 'react';
import { Swiper, SwiperSlide } from 'swiper/react';
import { Navigation } from 'swiper/modules';
import type { SwiperModule } from 'swiper/types';
import type { Swiper as SwiperType } from 'swiper';
import 'swiper/css';
import 'swiper/css/navigation';
import { getImageUrl } from '../../utils/imageUrl';
import { GpImage } from './GpImage';

interface ProfileImageSliderProps {
    images: string[];
    /** T1-2 variant: backend 가 공급한 _thumb 파생 URL. images 와 같은 길이. */
    imagesThumbnail?: (string | null | undefined)[];
    /** T1-2 variant: backend 가 공급한 _viewer 파생 URL. images 와 같은 길이. */
    imagesViewer?: (string | null | undefined)[];
    /** T2-D2: WebP thumbnail variant URLs. images 와 같은 길이. */
    imagesWebpThumbnail?: (string | null | undefined)[];
    /** T2-D2: WebP viewer variant URLs. images 와 같은 길이. */
    imagesWebpViewer?: (string | null | undefined)[];
    secondaryImage?: string;
    /** T1-2 variant: 보조(butler/pet) 썸네일 URL. */
    secondaryImageThumbnail?: string | null;
    /** T2-D2: WebP thumbnail for secondary image. */
    secondaryImageWebpThumbnail?: string | null;
    secondaryImageClass?: string; // 'butler_img' or 'pet_img'
    onImageClick?: () => void;
    onSecondaryImageClick?: () => void;  // callback when small (butler) image is tapped
    onMainImageClick?: (index: number) => void;  // callback when large image is tapped (for gallery open)
    className?: string; // Wrapper class like 'profile_img_wrap'
    swiperWrapperClass?: string;
    children?: React.ReactNode;
    innerChildren?: React.ReactNode;
    swiperProps?: Record<string, unknown> & { onSlideChange?: (swiper: SwiperType) => void; modules?: SwiperModule[]; navigation?: boolean | Record<string, string> };
    navigation?: boolean;
    priority?: boolean;
}

const isSvg = (src: string) => src.endsWith('.svg');

export const ProfileImageSlider: React.FC<ProfileImageSliderProps> = ({
    images,
    imagesThumbnail,
    imagesViewer,
    imagesWebpThumbnail,
    imagesWebpViewer,
    secondaryImage,
    secondaryImageThumbnail,
    secondaryImageWebpThumbnail,
    secondaryImageClass = 'butler_img',
    onImageClick,
    onSecondaryImageClick,
    onMainImageClick,
    className = 'profile_img_wrap',
    swiperWrapperClass,
    children,
    innerChildren,
    swiperProps,
    navigation = false,
    priority = false,
}) => {
    const activeIndexRef = useRef(0);

    const modules = [...(swiperProps?.modules || [])];
    if (navigation) modules.push(Navigation);

    const defaultNavigationConfig = navigation ? {
        prevEl: '.swiper-pet-prev',
        nextEl: '.swiper-pet-next',
    } : {};

    const handleMainImageClick = (e: React.MouseEvent, index: number) => {
        e.stopPropagation();
        onImageClick?.();
        onMainImageClick?.(index);
    };

    // Single image: render directly without Swiper
    const useDirect = images.length <= 1;
    const singleImage = images[0] || "/assets/images/main/main_slide_img01.png";
    const singleThumb = imagesThumbnail?.[0] ?? null;
    const singleViewer = imagesViewer?.[0] ?? null;
    const singleWebpThumb = imagesWebpThumbnail?.[0] ?? null;
    const singleWebpViewer = imagesWebpViewer?.[0] ?? null;
    const hasVariants = Boolean(singleThumb || singleViewer);

    // Returns WebP variant path for known static /assets/images/ files
    const staticWebp = (src: string) =>
        src.startsWith('/assets/images/') && /\.(png|jpg)$/i.test(src)
            ? src.replace(/\.(png|jpg)$/i, '.webp')
            : null;

    // For direct rendering: SVG uses background-image (iOS WKWebView compat), GpImage for variant-aware, <picture> for legacy
    const directContent = isSvg(singleImage) ? (
        <div
            className="pet_img"
            onClick={(e) => handleMainImageClick(e, 0)}
            role="img"
            aria-label="프로필 이미지"
            style={{
                width: '100%',
                height: '100%',
                backgroundImage: `url(${singleImage})`,
                backgroundSize: 'cover',
                backgroundPosition: 'center 20%',
                backgroundRepeat: 'no-repeat',
                cursor: onMainImageClick ? 'pointer' : undefined,
            }}
        />
    ) : hasVariants ? (
        <GpImage
            src={singleImage}
            thumbnailSrc={singleThumb}
            viewerSrc={singleViewer}
            webpThumbnailSrc={singleWebpThumb}
            webpViewerSrc={singleWebpViewer}
            variant="medium"
            className="pet_img"
            alt="프로필 이미지"
            onClick={(e) => handleMainImageClick(e, 0)}
            loading={priority ? 'eager' : 'lazy'}
            decoding={priority ? 'sync' : 'async'}
            fetchPriority={priority ? 'high' : 'auto'}
            style={{ cursor: onMainImageClick ? 'pointer' : undefined }}
        />
    ) : (
        <picture>
            {staticWebp(singleImage) && (
                <source type="image/webp" srcSet={staticWebp(singleImage)!} />
            )}
            <img
                src={getImageUrl(singleImage, 'medium')}
                className="pet_img"
                alt="프로필 이미지"
                onClick={(e) => handleMainImageClick(e, 0)}
                loading={priority ? 'eager' : 'lazy'}
                decoding={priority ? 'sync' : 'async'}
                fetchPriority={priority ? 'high' : 'auto'}
                style={{ cursor: onMainImageClick ? 'pointer' : undefined }}
                onError={(e) => {
                    if (!e.currentTarget.dataset.fallback) {
                        e.currentTarget.dataset.fallback = 'true';
                        e.currentTarget.src = singleImage;
                    }
                }}
            />
        </picture>
    );

    const swiperContent = (
        <Swiper
            className="swiper-wrapper"
            modules={modules}
            navigation={swiperProps?.navigation || defaultNavigationConfig}
            style={{ height: '100%' }}
            onSlideChange={(swiper: SwiperType) => {
                activeIndexRef.current = swiper.activeIndex;
                swiperProps?.onSlideChange?.(swiper);
            }}
            {...swiperProps}
        >
            {images.map((img, index) => {
                const thumb = imagesThumbnail?.[index] ?? null;
                const viewer = imagesViewer?.[index] ?? null;
                const webpThumb = imagesWebpThumbnail?.[index] ?? null;
                const webpViewer = imagesWebpViewer?.[index] ?? null;
                const slideHasVariants = Boolean(thumb || viewer);
                const isLcp = priority && index === 0;
                const slideStyle: React.CSSProperties = {
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    objectPosition: 'center 20%',
                    cursor: onMainImageClick ? 'pointer' : undefined,
                };
                return (
                    <SwiperSlide key={index} className="swiper-slide">
                        {slideHasVariants ? (
                            <GpImage
                                src={img}
                                thumbnailSrc={thumb}
                                viewerSrc={viewer}
                                webpThumbnailSrc={webpThumb}
                                webpViewerSrc={webpViewer}
                                variant="medium"
                                className="pet_img"
                                alt={`슬라이드 이미지 ${index + 1}`}
                                onClick={(e) => handleMainImageClick(e, activeIndexRef.current)}
                                style={slideStyle}
                                loading={isLcp ? 'eager' : 'lazy'}
                                decoding={isLcp ? 'sync' : 'async'}
                                fetchPriority={isLcp ? 'high' : 'auto'}
                            />
                        ) : (
                            <img
                                src={getImageUrl(img, 'medium')}
                                className="pet_img"
                                alt={`슬라이드 이미지 ${index + 1}`}
                                onClick={(e) => handleMainImageClick(e, activeIndexRef.current)}
                                style={slideStyle}
                                loading={isLcp ? 'eager' : 'lazy'}
                                decoding={isLcp ? 'sync' : 'async'}
                                fetchPriority={isLcp ? 'high' : 'auto'}
                                onError={(e) => {
                                    if (!e.currentTarget.dataset.fallback) {
                                        e.currentTarget.dataset.fallback = 'true';
                                        e.currentTarget.src = img;
                                    }
                                }}
                            />
                        )}
                    </SwiperSlide>
                );
            })}
        </Swiper>
    );

    const imageContent = useDirect ? directContent : swiperContent;

    return (
        <div className={className}>
            {children}
            {swiperWrapperClass ? (
                <div className={swiperWrapperClass}>
                    {imageContent}
                    {!useDirect && innerChildren}
                </div>
            ) : (
                <>
                    {imageContent}
                    {!useDirect && innerChildren}
                </>
            )}

            {secondaryImage && (
                <div
                    className={secondaryImageClass}
                    onClick={onSecondaryImageClick ? (e) => { e.stopPropagation(); onSecondaryImageClick(); } : undefined}
                    style={onSecondaryImageClick ? { cursor: 'pointer' } : undefined}
                >
                    {secondaryImageThumbnail ? (
                        <GpImage
                            src={secondaryImage}
                            thumbnailSrc={secondaryImageThumbnail}
                            webpThumbnailSrc={secondaryImageWebpThumbnail}
                            variant="thumbnail"
                            alt="보조 이미지"
                        />
                    ) : (
                        <img src={secondaryImage} alt="보조 이미지" />
                    )}
                </div>
            )}
        </div>
    );
};
