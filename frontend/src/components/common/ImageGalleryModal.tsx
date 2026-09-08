import { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { Swiper, SwiperSlide } from 'swiper/react';
import { Navigation } from 'swiper/modules';
import 'swiper/css';
import 'swiper/css/navigation';
import { getImageUrl } from '../../utils/imageUrl';
import { useOverlayColor } from '../../hooks/useOverlayColor';

interface ImageGalleryModalProps {
    isOpen: boolean;
    images: string[];
    initialIndex?: number;
    onClose: () => void;
}

export const ImageGalleryModal = ({
    isOpen,
    images,
    initialIndex = 0,
    onClose,
}: ImageGalleryModalProps) => {
    const [activeIndex, setActiveIndex] = useState(initialIndex);

    // Reset index when modal opens - handled via Swiper key prop below

    // ESC key close
    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    // Body scroll lock
    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
            return () => { document.body.style.overflow = ''; };
        }
    }, [isOpen]);

    useOverlayColor(isOpen);

    if (!isOpen) return null;

    const content = (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                background: 'rgba(0,0,0,0.92)',
                zIndex: 9999,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}
            onClick={onClose}
        >
            {/* Close button */}
            <button
                type="button"
                className="gp-gallery-close"
                onClick={onClose}
                style={{
                    position: 'absolute',
                    top: '16px',
                    right: '16px',
                    width: '36px',
                    height: '36px',
                    background: 'rgba(255,255,255,0.2)',
                    borderRadius: '50%',
                    border: 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 10000,
                    padding: 0,
                }}
                aria-label="닫기"
            >
                <svg width="18" height="18" viewBox="0 0 18 18" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <line x1="2" y1="2" x2="16" y2="16" stroke="white" strokeWidth="2" strokeLinecap="round"/>
                    <line x1="16" y1="2" x2="2" y2="16" stroke="white" strokeWidth="2" strokeLinecap="round"/>
                </svg>
            </button>

            {/* Swiper container — stop propagation to prevent backdrop close */}
            <div
                className="gp-gallery-swiper"
                onClick={(e) => e.stopPropagation()}
                style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center' }}
            >
                <Swiper
                    key={`${initialIndex}-${isOpen}`}
                    modules={[Navigation]}
                    navigation={images.length > 1}
                    initialSlide={initialIndex}
                    onSlideChange={(swiper) => setActiveIndex(swiper.activeIndex)}
                    style={{ width: '100%', height: '100%' }}
                >
                    {images.map((img, index) => (
                        <SwiperSlide key={index} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <img
                                src={getImageUrl(img, 'original')}
                                alt={`이미지 ${index + 1}`}
                                style={{
                                    maxWidth: '100%',
                                    maxHeight: '100%',
                                    objectFit: 'contain',
                                    display: 'block',
                                }}
                                onError={(e) => {
                                    if (!e.currentTarget.dataset.fallback) {
                                        e.currentTarget.dataset.fallback = 'true';
                                        e.currentTarget.src = img;
                                    }
                                }}
                            />
                        </SwiperSlide>
                    ))}
                </Swiper>
            </div>

            {/* Counter */}
            {images.length > 1 && (
                <div
                    className="gp-gallery-counter"
                    style={{
                        position: 'absolute',
                        bottom: '24px',
                        left: '50%',
                        transform: 'translateX(-50%)',
                        color: 'white',
                        fontSize: '14px',
                        fontWeight: 500,
                        background: 'rgba(0,0,0,0.4)',
                        borderRadius: '12px',
                        padding: '4px 12px',
                        zIndex: 10000,
                        pointerEvents: 'none',
                    }}
                >
                    {activeIndex + 1} / {images.length}
                </div>
            )}
        </div>
    );

    return ReactDOM.createPortal(content, document.body);
};
