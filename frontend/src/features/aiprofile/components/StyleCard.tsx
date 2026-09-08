import type { AIStyleOption } from '../../../services/aiProfileService';

interface StyleCardProps {
  style: AIStyleOption;
  selected: boolean;
  onClick: () => void;
}

export default function StyleCard({ style, selected, onClick }: StyleCardProps) {
  return (
    <div
      onClick={onClick}
      style={{
        border: selected ? '2px solid var(--color-primary)' : '2px solid #F9ECD2',
        borderRadius: '4.44vw',
        overflow: 'hidden',
        cursor: 'pointer',
        backgroundColor: selected ? '#FFFDF1' : '#fff',
        transition: 'all 0.2s',
      }}
    >
      <div style={{ width: '100%', aspectRatio: '1', overflow: 'hidden' }}>
        <img
          src={style.previewUrl}
          alt={style.name}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
      <div style={{ padding: '12px' }}>
        <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--color-primary)', marginBottom: '1.11vw' }}>
          {style.name}
        </div>
        <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginBottom: '2.22vw' }}>
          {style.description}
        </div>
        <div style={{ fontSize: '3.89vw', fontWeight: 600, color: '#FF9800' }}>
          {style.goldCost} 골드
        </div>
      </div>
    </div>
  );
}
