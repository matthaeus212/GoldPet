import type { CourseSpotType } from '../../../services/courseService';

import spotStore from '../assets/spot-store.png';
import spotWater from '../assets/spot-water.png';
import spotWc from '../assets/spot-wc.png';
import spotCafe from '../assets/spot-cafe.png';
import spotPark from '../assets/spot-park.png';
import spotDanger from '../assets/spot-danger.png';
import spotRest from '../assets/spot-rest.png';
import spotWaste from '../assets/spot-waste.png';
import spotPhoto from '../assets/spot-photo.png';
import spotMore from '../assets/spot-more.png';

// eslint-disable-next-line react-refresh/only-export-components
export const SPOT_ICON_MAP: Record<CourseSpotType, string> = {
  CONVENIENCE_STORE: spotStore,
  WATER_FOUNTAIN: spotWater,
  RESTROOM: spotWc,
  PET_CAFE: spotCafe,
  PARK: spotPark,
  DANGER_ZONE: spotDanger,
  REST_AREA: spotRest,
  TRASH_CAN: spotWaste,
  PHOTO_SPOT: spotPhoto,
  OTHER: spotMore,
};

// eslint-disable-next-line react-refresh/only-export-components
export function spotIconUrl(type: CourseSpotType | string): string {
  return SPOT_ICON_MAP[type as CourseSpotType] ?? spotMore;
}

interface SpotIconProps {
  type: CourseSpotType;
  size?: number;
  className?: string;
}

export function SpotIcon({ type, size = 32, className }: SpotIconProps) {
  const src = SPOT_ICON_MAP[type] ?? spotMore;
  return (
    <img
      src={src}
      alt=""
      width={size}
      height={size}
      className={className}
      draggable={false}
      style={{ display: 'block' }}
    />
  );
}
