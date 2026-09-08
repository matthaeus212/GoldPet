import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon: ReactNode;
  text: string;
  subText?: string;
}

export function EmptyState({ icon, text, subText }: EmptyStateProps) {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '48px 16px',
      gap: '12px',
    }}>
      <div style={{ fontSize: 48, lineHeight: 1 }}>{icon}</div>
      <p style={{
        fontSize: 'var(--font-size-body-sm)',
        color: 'var(--color-text-placeholder)',
        fontWeight: 'var(--font-weight-medium)',
        textAlign: 'center',
        margin: 0,
      }}>
        {text}
      </p>
      {subText && (
        <p style={{
          fontSize: 'var(--font-size-caption)',
          color: 'var(--color-text-placeholder)',
          textAlign: 'center',
          margin: 0,
          lineHeight: 1.5,
        }}>
          {subText}
        </p>
      )}
    </div>
  );
}
