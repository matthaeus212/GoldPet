import { Outlet } from 'react-router-dom';
import { MainHeader } from '../common/MainHeader';

interface TypeALayoutProps {
    variant: 'logo-only' | 'back-only' | 'close-only';
}

export const TypeALayout = ({ variant }: TypeALayoutProps) => {
    return (
        <div className="gp-layout-page">
            <MainHeader variant={variant} className="intro_header" />
            <div id="mainContainer">
                <Outlet />
            </div>
        </div>
    );
};
