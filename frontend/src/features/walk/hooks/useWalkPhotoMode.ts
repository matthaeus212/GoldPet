import { useMatch } from 'react-router-dom';

export type WalkPhotoMode = 'owned' | 'shared';

export function useWalkPhotoMode(explicit?: WalkPhotoMode): WalkPhotoMode {
  const ownedMatch = useMatch('/walk-photos/:walkId/:spotId');
  const sharedMatch = useMatch('/walk-shared-photos/:walkId/:spotId');
  const ownedListMatch = useMatch('/walk-photos');
  const sharedListMatch = useMatch('/walk-shared-photos');
  if (explicit) return explicit;
  if (ownedMatch || ownedListMatch) return 'owned';
  if (sharedMatch || sharedListMatch) return 'shared';
  throw new Error('WalkPhoto mode not resolvable from route');
}
