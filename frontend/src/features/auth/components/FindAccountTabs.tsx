import React from 'react';
import { useNavigate } from 'react-router-dom';
import './FindAccountTabs.css';

type TabType = 'username' | 'password';

interface FindAccountTabsProps {
    activeTab: TabType;
}

export const FindAccountTabs: React.FC<FindAccountTabsProps> = ({ activeTab }) => {
    const navigate = useNavigate();

    return (
        <div className="find-account-tabs">
            <button
                type="button"
                className={`find-account-tab ${activeTab === 'username' ? 'active' : ''}`}
                onClick={() => navigate('/find-username')}
            >
                아이디 찾기
            </button>
            <button
                type="button"
                className={`find-account-tab ${activeTab === 'password' ? 'active' : ''}`}
                onClick={() => navigate('/reset-password')}
            >
                비밀번호 변경
            </button>
        </div>
    );
};
