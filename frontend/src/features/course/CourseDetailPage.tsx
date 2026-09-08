import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { MainHeader } from '../../components/common/MainHeader';
import { nativeBridge } from '../../bridge/nativeBridge';
import { courseService } from '../../services/courseService';
import { useAuthStore } from '../../stores/authStore';
import { useAlert } from '../../contexts/AlertContext';
import { CourseMap } from './components/CourseMap';
import { difficultyLabel, difficultyClass, spotLabel as spotTypeLabel } from './utils/courseHelpers';
import { SpotIcon } from './components/SpotIcon';
import './CourseDetailPage.css';

// ── HeartIcon ──

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? '#E74C3C' : 'none'} stroke={filled ? '#E74C3C' : 'currentColor'} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  );
}

// ── Page ──

export const CourseDetailPage = () => {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated, user } = useAuthStore();
  const { showConfirm } = useAlert();

  const courseIdNum = Number(courseId);

  // Fetch course detail
  const {
    data: course,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['course', 'detail', courseIdNum],
    queryFn: () => courseService.getCourseDetail(courseIdNum),
    enabled: !!courseIdNum,
  });

  // Fetch like status (only when authenticated)
  const { data: likeStatus } = useQuery({
    queryKey: ['course', 'like', courseIdNum],
    queryFn: () => courseService.getCourseLikeStatus(courseIdNum),
    enabled: !!courseIdNum && isAuthenticated,
  });

  // Toggle like with optimistic update
  const likeMutation = useMutation({
    mutationFn: () => courseService.toggleCourseLike(courseIdNum),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['course', 'like', courseIdNum] });
      const prev = queryClient.getQueryData<typeof likeStatus>(['course', 'like', courseIdNum]);
      if (prev) {
        queryClient.setQueryData(['course', 'like', courseIdNum], {
          ...prev,
          liked: !prev.liked,
          likeCount: prev.liked ? prev.likeCount - 1 : prev.likeCount + 1,
        });
      }
      return { prev };
    },
    onError: (_err, _vars, context) => {
      if (context?.prev) {
        queryClient.setQueryData(['course', 'like', courseIdNum], context.prev);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['course', 'like', courseIdNum] });
      queryClient.invalidateQueries({ queryKey: ['course', 'detail', courseIdNum] });
    },
  });

  const handleLike = () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    if (likeMutation.isPending) return;
    likeMutation.mutate();
  };

  const handleStartWalk = async () => {
    if (!course) return;
    if (nativeBridge.isAvailable()) {
      try {
        await nativeBridge.callMethod('startCourseWalk', {
          courseId: courseIdNum,
          title: course.title,
          path: course.path ?? [],
          spots: (course.spots ?? []).map((s) => ({
            latitude: s.latitude,
            longitude: s.longitude,
            type: s.type,
            name: s.name ?? null,
            description: s.description ?? null,
          })),
        });
        return;
      } catch (e) {
        console.warn('Native bridge startCourseWalk failed:', e);
        alert('코스 산책을 시작할 수 없습니다. 앱을 업데이트해주세요.');
        return;
      }
    }
    navigate(`/walk/map?courseId=${courseIdNum}`);
  };

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => courseService.deleteCourse(courseIdNum),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['course'] });
      queryClient.invalidateQueries({ queryKey: ['courses'] });
      navigate('/courses', { replace: true });
    },
  });

  const handleDelete = () => {
    if (deleteMutation.isPending) return;
    showConfirm('삭제된 코스는 복구할 수 없습니다.\n이 코스를 삭제하시겠습니까?', () => {
      deleteMutation.mutate();
    });
  };

  const isOwner = isAuthenticated && user?.id === course?.authorId;
  const isLiked = likeStatus?.liked ?? false;
  const displayLikeCount = likeStatus?.likeCount ?? course?.likeCount ?? 0;

  if (isLoading) {
    return (
      <div className="course_detail_page">
        <MainHeader variant="back-only" className="intro_header" />
        <p className="course_detail_loading">코스 정보를 불러오는 중...</p>
      </div>
    );
  }

  if (isError || !course) {
    return (
      <div className="course_detail_page">
        <MainHeader variant="back-only" className="intro_header" />
        <p className="course_detail_error">코스 정보를 불러올 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="course_detail_page">
      <MainHeader variant="back-only" className="intro_header" />

      {/* Map */}
      <div className="course_detail_map">
        <CourseMap path={course.path} spots={course.spots} />
      </div>

      {/* Info section */}
      <div className="course_detail_info">
        {/* Badge + like + owner actions */}
        <div className="course_detail_badge_row">
          <span className={`course_difficulty_badge ${difficultyClass(course.difficulty)}`}>
            {difficultyLabel(course.difficulty)}
          </span>
          <div className="course_detail_badge_row_right">
            {isOwner && (
              <div className="course_detail_owner_actions">
                <button
                  type="button"
                  className="course_detail_edit_btn"
                  onClick={() => navigate(`/courses/${courseIdNum}/edit`)}
                >
                  수정
                </button>
                <button
                  type="button"
                  className="course_detail_delete_btn"
                  onClick={handleDelete}
                  disabled={deleteMutation.isPending}
                >
                  {deleteMutation.isPending ? '삭제 중...' : '삭제'}
                </button>
              </div>
            )}
            <button
              type="button"
              className={`course_detail_like_btn${isLiked ? ' liked' : ''}`}
              onClick={handleLike}
              aria-label={isLiked ? '좋아요 취소' : '좋아요'}
            >
              <HeartIcon filled={isLiked} />
              <span>{displayLikeCount}</span>
            </button>
          </div>
        </div>

        {/* Title */}
        <h1 className="course_detail_title">{course.title}</h1>

        {/* Author */}
        <div className="course_detail_author">
          <img
            className="course_detail_author_img"
            src={course.authorProfileImageUrl || '/assets/images/common/pet_none_img.svg'}
            alt={course.authorNickname}
            onError={(e) => { (e.currentTarget as HTMLImageElement).src = '/assets/images/common/pet_none_img.svg'; }}
          />
          <div className="course_detail_author_info">
            <span className="course_detail_author_label">작성자</span>
            <span className="course_detail_author_name">{course.authorNickname}</span>
          </div>
        </div>

        {/* Stats */}
        <div className="course_detail_stats">
          <div className="course_detail_stat_item">
            <span className="course_detail_stat_label">거리</span>
            <span className="course_detail_stat_value">
              {course.distanceKm.toFixed(1)}
              <span className="course_detail_stat_unit">km</span>
            </span>
          </div>
          <div className="course_detail_stat_item">
            <span className="course_detail_stat_label">예상시간</span>
            <span className="course_detail_stat_value">
              {course.estimatedMinutes}
              <span className="course_detail_stat_unit">분</span>
            </span>
          </div>
          <div className="course_detail_stat_item">
            <span className="course_detail_stat_label">산책수</span>
            <span className="course_detail_stat_value">
              {course.walkCount.toLocaleString()}
              <span className="course_detail_stat_unit">회</span>
            </span>
          </div>
          <div className="course_detail_stat_item">
            <span className="course_detail_stat_label">평점</span>
            <span className="course_detail_stat_value">
              {course.ratingCount > 0 ? course.rating.toFixed(1) : '-'}
              {course.ratingCount > 0 && <span className="course_detail_stat_unit">점</span>}
            </span>
          </div>
        </div>

        {/* Description */}
        {course.description && (
          <p className="course_detail_desc">{course.description}</p>
        )}
      </div>

      {/* Spots */}
      {course.spots.length > 0 && (
        <div className="course_detail_spots">
          <p className="course_detail_spots_title">코스 스팟 ({course.spots.length})</p>
          {course.spots
            .slice()
            .sort((a, b) => a.orderIndex - b.orderIndex)
            .map((spot) => (
              <div key={spot.id} className="course_detail_spot_item">
                <div className="course_detail_spot_icon" role="img" aria-label={spotTypeLabel(spot.type)}>
                  <SpotIcon type={spot.type} size={24} />
                </div>
                <div className="course_detail_spot_content">
                  {spot.name ? (
                    <p className="course_detail_spot_name">{spot.name}</p>
                  ) : null}
                  <p className="course_detail_spot_type">{spotTypeLabel(spot.type)}</p>
                  {spot.description && (
                    <p className="course_detail_spot_desc">{spot.description}</p>
                  )}
                </div>
              </div>
            ))}
        </div>
      )}

      {/* Floating CTA */}
      <div className="course_detail_float_btn">
        <button type="button" onClick={handleStartWalk}>
          이 코스로 산책하기
        </button>
      </div>
    </div>
  );
};

export default CourseDetailPage;
