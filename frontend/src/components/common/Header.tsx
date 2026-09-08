import { BackButton } from './BackButton';
import './Header.css';

interface HeaderProps {
    onBack?: () => void;
    title?: string;
    rightAction?: React.ReactNode;
}

export const Header = ({ onBack, title, rightAction }: HeaderProps) => {
    return (
        <header className="app-header">
            <div className="header-left">
                {onBack && <BackButton onClick={onBack} />}
                {title && <h1 className="header-title">{title}</h1>}
            </div>
            {rightAction && (
                <div className="header-right-action">
                    {rightAction}
                </div>
            )}
        </header>
    );
};
