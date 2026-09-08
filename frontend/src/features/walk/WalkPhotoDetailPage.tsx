import { Navigate, useLocation, useParams } from 'react-router-dom';
import { WalkPhotoGalleryViewer } from './viewer/WalkPhotoGalleryViewer';
import { WalkPhotoErrorBoundary } from './viewer/WalkPhotoErrorBoundary';
import type { WalkPhotoItem } from '../../services/walkService';
import { useOverlayColor } from '../../hooks/useOverlayColor';
import './WalkPhotoDetail.css';

export default function WalkPhotoDetailPage() {
  const { walkId, spotId } = useParams<{ walkId: string; spotId: string }>();
  const location = useLocation();
  const photoFromState = (location.state ?? null) as WalkPhotoItem | null;

  useOverlayColor(true, '#000000', '#000000');

  if (!walkId || !spotId) {
    return <Navigate to="/walk-photos" replace />;
  }

  return (
    <WalkPhotoErrorBoundary fallbackHref="/walk-photos">
      <WalkPhotoGalleryViewer
        variant="page"
        mode="owned"
        walkId={Number(walkId)}
        initialSpotId={Number(spotId)}
        initialPhoto={photoFromState}
      />
    </WalkPhotoErrorBoundary>
  );
}
