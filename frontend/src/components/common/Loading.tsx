import { BeatLoader } from 'react-spinners';

interface LoadingProps {
  description?: string;
  size?: number;
  fullScreen?: boolean;
}

export const Loading = ({ description, size = 10, fullScreen = false }: LoadingProps) => {
  const content = (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        width: '100%',
        height: '100%',
        minHeight: fullScreen ? '100vh' : '200px',
        flex: 1,
      }}
    >
      <BeatLoader color="var(--color-primary)" size={size} margin={4} />
      {description && (
        <p
            className="loading_text"
          style={{
            marginTop: '16px',
            color: 'var(--color-primary)',
            fontSize: '14px',
            fontWeight: 500,
          }}
        >
          {description}
        </p>
      )}
    </div>
  );

  if (fullScreen) {
    return (
      <div
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(255, 255, 255, 0.8)',
          zIndex: 9999,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
        onClick={(e) => e.stopPropagation()} // Prevent clicks behind
      >
        {content}
      </div>
    );
  }

  return content;
};
