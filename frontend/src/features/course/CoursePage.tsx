import { useState, useRef, useEffect, useCallback } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { courseService } from '../../services/courseService';
import type { CourseFilters } from './utils/courseHelpers';
import { MainHeader } from '../../components/common/MainHeader';
import { CourseCard } from './components/CourseCard';
import { CourseFilter } from './components/CourseFilter';
import './CoursePage.css';

export function CoursePage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<CourseFilters>({ sortBy: 'popular' });

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
  } = useInfiniteQuery({
    queryKey: ['courses', 'popular', filters.difficulty, filters.sortBy, filters.region],
    queryFn: ({ pageParam = 0 }) =>
      courseService.getPopularCourses({
        difficulty: filters.difficulty,
        sortBy: filters.sortBy,
        region: filters.region,
        page: pageParam as number,
        size: 10,
      }),
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
    initialPageParam: 0,
  });

  const courses = data?.pages.flatMap((p) => p.content) ?? [];

  const sentinelRef = useRef<HTMLDivElement>(null);
  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage();
      }
    },
    [hasNextPage, isFetchingNextPage, fetchNextPage]
  );

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(handleIntersect, { rootMargin: '200px' });
    observer.observe(el);
    return () => observer.disconnect();
  }, [handleIntersect]);

  return (
    <div className="course_page">
      <MainHeader variant="back-only" className="intro_header" />
      <div className="course_content">
        <CourseFilter filters={filters} onFilterChange={setFilters} />

        {isLoading ? (
          <p className="course_loading">불러오는 중...</p>
        ) : courses.length === 0 ? (
          <div className="course_empty">
            <img src="/assets/images/common/comment_icon03.svg" alt="" />
            등록된 코스가 없어요.
          </div>
        ) : (
          <div className="course_list">
            {courses.map((course) => (
              <CourseCard
                key={course.id}
                course={course}
                onClick={() => navigate(`/courses/${course.id}`)}
              />
            ))}
          </div>
        )}

        {isFetchingNextPage && <p className="course_loading">불러오는 중...</p>}
        <div ref={sentinelRef} style={{ height: 1 }} />
      </div>

      <div className="course_create_float_btn">
        <button type="button" onClick={() => navigate('/courses/create')}>
          코스 만들기
        </button>
      </div>
    </div>
  );
}

export default CoursePage;
