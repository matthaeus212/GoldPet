import { useNavigate } from 'react-router-dom';
import { Header } from '../common/Header';
import './SubPageLayout.css';

// MyPage cluster pages that should pass onBack for deterministic navigation:
// SettingsPage, ProfilePage, PasswordChangePage, MyPostsPage,
// BlockManagementPage, MyPagePetEditPage
// (GamificationTabPage / my-badges intentionally omits onBack — reachable from multiple entry points)
interface SubPageLayoutProps {
    title: string;
    rightAction?: React.ReactNode;
    onBack?: () => void;
    children: React.ReactNode;
}

export const SubPageLayout = ({ title, rightAction, onBack, children }: SubPageLayoutProps) => {
    const navigate = useNavigate();

    return (
        <div className="sub-page-layout">
            <Header
                onBack={onBack || (() => navigate(-1))}
                title={title}
                rightAction={rightAction}
            />
            <div className="sub-page-content">
                {children}
            </div>
        </div>
    );
};
