import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { homeService } from '../../services/homeService';

interface MainHeaderProps {
    variant: 'logo-only' | 'back-only' | 'logo-with-icons' | 'back-with-menu' | 'close-only';
    className?: string;
}

export const MainHeader = ({ variant, className = '' }: MainHeaderProps) => {
    const navigate = useNavigate();

    const { data: notificationStatus } = useQuery({
        queryKey: ['notificationUnread'],
        queryFn: homeService.getNotifications,
        staleTime: 30_000,
        enabled: variant === 'logo-with-icons',
    });

    const handleBack = () => {
        navigate(-1);
    };

    return (
        <header id="header" className={className}>
            <div className="header_wrap">
                {/* Variant: Logo Only or Logo With Icons */}
                {(variant === 'logo-only' || variant === 'logo-with-icons') && (
                    <a href="" onClick={(e) => { e.preventDefault(); navigate('/home'); }}>
                        <img src="/assets/images/layout/logo.svg" alt="골든 펫 로고" />
                    </a>
                )}

                {/* Variant: Back Only or Back With Menu */}
                {(variant === 'back-only' || variant === 'back-with-menu') && (
                    <button type="button" className="btn_back" onClick={handleBack}>
                        <img src="/assets/images/layout/header_icon_back.svg" alt="뒤로가기" />
                    </button>
                )}
                
                {/* Right Utility Area */}
                <ul className="header_util">
                    {variant === 'logo-with-icons' && (
                        <>
                            <li><button type="button" onClick={() => navigate('/gold')}><img src="/assets/images/layout/header_icon01.svg" alt="G아이콘" /></button></li>
                            <li style={{ position: 'relative' }}><button type="button" onClick={() => navigate('/notifications')}><img src="/assets/images/layout/header_icon02.svg" alt="알림" /></button>{notificationStatus?.hasUnread && <span className="notification_dot" />}</li>
                            <li><button type="button" onClick={() => navigate('/mypage')}><img src="/assets/images/layout/header_icon03.svg" alt="마이페이지" /></button></li>
                        </>
                    )}

                    {variant === 'back-with-menu' && (
                        <li><button type="button"><img src="/assets/images/layout/header_icon_menu.svg" alt="메뉴" /></button></li>
                    )}
                </ul>

                {/* Variant: Close Only (Layout E) */}
                {variant === 'close-only' && (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
                        <button type="button" className="btn_close" onClick={handleBack}>
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                                <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                        </button>
                    </div>
                )}
            </div>
        </header>
    );
};
