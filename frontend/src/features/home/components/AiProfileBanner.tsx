import { useNavigate } from 'react-router-dom';

interface AiProfileBannerProps {
    petName?: string;
    onClose: () => void;
}

export function AiProfileBanner({ petName, onClose }: AiProfileBannerProps) {
    const navigate = useNavigate();
    const subject = petName ?? '반려동물';

    return (
        <div className="home_ai_banner" data-testid="home-ai-banner">
            <button
                type="button"
                className="home_ai_banner__main"
                onClick={() => navigate('/ai-profile')}
            >
                <img
                    className="home_ai_banner__img"
                    src="/assets/images/home/ai_banner_qmark.png"
                    alt=""
                    loading="lazy"
                    decoding="async"
                />
                <div className="home_ai_banner__txt">
                    <strong>아직 AI 프로필이 없으시네요?</strong>
                    <span>{subject}의 다양한 모습을 지금 만나 보세요!</span>
                </div>
            </button>
            <button
                type="button"
                className="home_ai_banner__close"
                onClick={onClose}
                aria-label="배너 닫기"
            >
                <img src="/assets/images/home/ai_banner_close.svg" alt="" />
            </button>
        </div>
    );
}
