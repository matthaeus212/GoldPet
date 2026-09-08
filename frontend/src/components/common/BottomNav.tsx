import { useNavigate, useLocation } from 'react-router-dom';

export const BottomNav = () => {
    const navigate = useNavigate();
    const location = useLocation();

    return (
        <div id="bottomNav">
            <ul>
                <li>
                    <button 
                        type="button" 
                        className={`btn-effect b_nav01 ${location.pathname === '/friend-find' ? 'active' : ''}`} 
                        onClick={() => navigate('/friend-find')}
                    >
                        친구찾기
                    </button>
                </li>
                <li>
                    <button 
                        type="button" 
                        className={`btn-effect b_nav02 ${location.pathname.startsWith('/chat') ? 'active' : ''}`} 
                        onClick={() => navigate('/chat')}
                    >
                        채팅
                    </button>
                </li>
                <li className="ai_btn">
                    <button type="button" className="btn-effect" onClick={() => navigate('/ai-profile')}>
                        <img src="/assets/images/layout/b_fix_center_icon.png" alt="AI" />
                    </button>
                </li>
                <li>
                    <button 
                        type="button" 
                        className={`btn-effect b_nav03 ${location.pathname.startsWith('/walk') ? 'active' : ''}`} 
                        onClick={() => navigate('/walk')}
                    >
                        산책
                    </button>
                </li>
                <li>
                    <button 
                        type="button" 
                        className={`btn-effect b_nav04 ${location.pathname.startsWith('/community') ? 'active' : ''}`} 
                        onClick={() => navigate('/community')}
                    >
                        커뮤니티
                    </button>
                </li>
            </ul>
        </div>
    );
};
