import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

interface LocationStatusProps {
    children: React.ReactNode;
    location?: string;
    className?: string;
}

export const LocationStatus: React.FC<LocationStatusProps> = ({
    children,
    location,
    className = ''
}) => {
    const navigate = useNavigate();
    const user = useAuthStore(state => state.user);
    const displayLocation = location || user?.mainLocationText || '위치 미설정';

    return (
        <div className={`talk_wrap ${className}`}>
            <p>{children}</p>
            <button type="button" onClick={() => navigate('/location/setting')}>
                <img src="/assets/images/common/pin_icon.svg" alt="지도 마커" />
                {displayLocation}
            </button>
        </div>
    );
};
