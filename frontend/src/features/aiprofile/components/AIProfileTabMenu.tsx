import { useNavigate } from 'react-router-dom';

interface AIProfileTabMenuProps {
  activeTab: 'simple' | 'custom';
}

export function AIProfileTabMenu({ activeTab }: AIProfileTabMenuProps) {
  const navigate = useNavigate();

  return (
    <div className="ai-tab-menu">
      <div className="ai-tab-buttons">
        <button
          type="button"
          className={`ai-tab ${activeTab === 'simple' ? 'ai-tab--active' : 'ai-tab--inactive'}`}
          onClick={() => activeTab !== 'simple' && navigate('/ai-profile/simple', { replace: true })}
        >
          간단하게 만들기
        </button>
        <button
          type="button"
          className={`ai-tab ${activeTab === 'custom' ? 'ai-tab--active' : 'ai-tab--inactive'}`}
          onClick={() => activeTab !== 'custom' && navigate('/ai-profile/custom', { replace: true })}
        >
          원하는 대로 만들기
        </button>
      </div>
      <div className="ai-tab-divider" />
    </div>
  );
}
