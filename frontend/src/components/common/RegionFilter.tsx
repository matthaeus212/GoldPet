import { REGION_OPTIONS } from '../../constants/regions';

interface RegionFilterProps {
  selectedRegion: string | undefined;
  onRegionChange: (region: string | undefined) => void;
}

export function RegionFilter({ selectedRegion, onRegionChange }: RegionFilterProps) {
  return (
    <div className="course_filter_wt_chips">
      {REGION_OPTIONS.map((r) => (
        <button
          key={r}
          type="button"
          className={`course_filter_wt_chip${(r === '전체' ? selectedRegion === undefined : selectedRegion === r) ? ' active' : ''}`}
          onClick={() => onRegionChange(r === '전체' ? undefined : r)}
        >
          {r}
        </button>
      ))}
    </div>
  );
}
