interface GenerationProgressProps {
  status: string;
}

export default function GenerationProgress({ status }: GenerationProgressProps) {
  return (
    <div style={{ textAlign: 'center', padding: '11.11vw 0' }}>
      <div
        style={{
          width: '22.22vw',
          height: '22.22vw',
          margin: '0 auto 6.67vw',
          border: '3px solid var(--color-primary)',
          borderTop: '3px solid transparent',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
        }}
      />
      <style>
        {`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}
      </style>
      <p style={{ fontSize: '16px', fontWeight: 600, color: 'var(--color-primary)', marginBottom: '2.22vw' }}>
        AI가 프로필을 생성하고 있어요...
      </p>
      <p style={{ fontSize: '14px', color: 'var(--color-text-secondary)' }}>
        잠시만 기다려주세요
      </p>
      <p style={{ fontSize: '12px', color: '#999', marginTop: '2.22vw' }}>
        상태: {status}
      </p>
    </div>
  );
}
