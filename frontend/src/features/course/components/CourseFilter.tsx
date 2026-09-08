import type { CourseFilters } from '../utils/courseHelpers';
import { DIFFICULTY_OPTIONS } from '../utils/courseHelpers';

interface CourseFilterProps {
  filters: CourseFilters;
  onFilterChange: (filters: CourseFilters) => void;
}

const SORT_OPTIONS: { label: string; value: string }[] = [
  { label: '인기순', value: 'popular' },
  { label: '최신순', value: 'latest' },
  { label: '거리순', value: 'distance' },
];

export function CourseFilter({ filters, onFilterChange }: CourseFilterProps) {
  return (
    <div className="course_filter_wrap">
      <div className="course_filter_tab">
        {DIFFICULTY_OPTIONS.map((opt) => (
          <button
            key={opt.label}
            type="button"
            className={filters.difficulty === opt.value ? 'active' : ''}
            onClick={() => onFilterChange({ ...filters, difficulty: opt.value })}
          >
            {opt.label}
          </button>
        ))}
      </div>
      <div className="course_filter_row">
        {SORT_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`course_filter_chip${filters.sortBy === opt.value ? ' active' : ''}`}
            onClick={() => onFilterChange({ ...filters, sortBy: opt.value })}
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  );
}
