import { Outlet } from 'react-router-dom';
import { MainHeader } from '../common/MainHeader';
import { BottomNav } from '../common/BottomNav';

interface TypeBLayoutProps {
    variant?: 'logo-with-icons';
}

export const TypeBLayout = ({ variant = 'logo-with-icons' }: TypeBLayoutProps) => {
    return (
        <div id="wrap" className="gp-layout-page">
            <MainHeader variant={variant} />
            <Outlet />
            <BottomNav />
        </div>
    );
};

