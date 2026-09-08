import { useEffect, useState } from 'react';
import { walkService } from '../../../services/walkService';

interface WalkRouteThumbnailProps {
  walkId: number;
  hasPath?: boolean;
  size?: number;
}

const Placeholder = ({ size }: { size: number }) => (
  <div
    style={{
      width: size,
      height: size,
      borderRadius: 8,
      background: '#EDE8DF',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      flexShrink: 0,
    }}
  >
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
      <path
        d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"
        fill="#614108"
        opacity="0.3"
      />
    </svg>
  </div>
);

export function WalkRouteThumbnail({ walkId, hasPath = true, size = 60 }: WalkRouteThumbnailProps) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!hasPath) return;

    let revoked = false;
    // Request retina size for crisp display
    walkService.getStaticMapImage(walkId, size * 2, size * 2).then((url) => {
      if (revoked) {
        if (url) URL.revokeObjectURL(url);
        return;
      }
      if (url) {
        setBlobUrl(url);
      } else {
        setFailed(true);
      }
    });

    return () => {
      revoked = true;
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [walkId, hasPath, size]);

  if (!hasPath || failed) {
    return <Placeholder size={size} />;
  }

  if (!blobUrl) {
    // Loading state
    return (
      <div
        style={{
          width: size,
          height: size,
          borderRadius: 8,
          background: '#EDE8DF',
          flexShrink: 0,
        }}
      />
    );
  }

  return (
    <img
      src={blobUrl}
      alt="산책 경로"
      style={{
        width: size,
        height: size,
        borderRadius: 8,
        objectFit: 'cover',
        flexShrink: 0,
      }}
    />
  );
}
