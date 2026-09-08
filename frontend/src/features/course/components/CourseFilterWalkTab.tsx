import { useState } from 'react';
import type { CourseFilters } from '../utils/courseHelpers';
import { DIFFICULTY_OPTIONS, SORT_OPTIONS_WALK_TAB } from '../utils/courseHelpers';
import { RegionFilter } from '../../../components/common/RegionFilter';

interface CourseFilterWalkTabProps {
  filters: CourseFilters;
  onFilterChange: (filters: CourseFilters) => void;
}

export function CourseFilterWalkTab({ filters, onFilterChange }: CourseFilterWalkTabProps) {
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  return (
    <div className="course_filter_wt_wrap">
      {/* Top row: Filter pill + Sort chips */}
      <div className="course_filter_wt_toprow">
        <button
          type="button"
          className="course_filter_wt_pill"
          onClick={() => setIsFilterOpen(!isFilterOpen)}
        >
          필터{filters.region ? ` · ${filters.region}` : ''}
          <svg
            className={`course_filter_wt_pill_icon${isFilterOpen ? ' open' : ''}`}
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
          >
            <path d="M4 6L8 10L12 6" stroke="#614108" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <div className="course_filter_wt_sorts">
          {SORT_OPTIONS_WALK_TAB.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={`course_filter_wt_sort_chip${filters.sortBy === opt.value ? ' active' : ''}`}
              onClick={() => onFilterChange({ ...filters, sortBy: opt.value })}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Expandable filter panel */}
      <div className={`course_filter_wt_panel_wrapper${isFilterOpen ? ' open' : ''}`}>
        <div className="course_filter_wt_panel_inner">
          <div className="course_filter_wt_panel">
            {/* Difficulty */}
            <div className="course_filter_wt_section">
              <span className="course_filter_wt_label">난이도</span>
              <div className="course_filter_wt_chips">
                {DIFFICULTY_OPTIONS.map((opt) => (
                  <button
                    key={opt.label}
                    type="button"
                    className={`course_filter_wt_chip${filters.difficulty === opt.value ? ' active' : ''}`}
                    onClick={() => onFilterChange({ ...filters, difficulty: opt.value })}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="course_filter_wt_divider" />

            {/* Region */}
            <div className="course_filter_wt_section">
              <span className="course_filter_wt_label">지역</span>
              <RegionFilter
                selectedRegion={filters.region}
                onRegionChange={(region) => onFilterChange({ ...filters, region })}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
