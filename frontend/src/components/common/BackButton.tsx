import './BackButton.css';

interface BackButtonProps {
  onClick: () => void;
  className?: string;
}

export const BackButton = ({ onClick, className }: BackButtonProps) => (
  <button type="button" className={`header-back-button ${className || ''}`} onClick={onClick}>
    <img src="/assets/images/layout/header_icon_back.svg" alt="뒤로가기" />
  </button>
);
