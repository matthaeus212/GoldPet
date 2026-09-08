import type { CourseResponse } from '../../../services/courseService';
import { difficultyLabel, difficultyClass } from '../utils/courseHelpers';

interface CourseCardProps {
  course: CourseResponse;
  onClick: () => void;
}

function StarRating({ rating }: { rating: number }) {
  const full = Math.floor(rating);
  const half = rating - full >= 0.5;
  return (
    <>
      {Array.from({ length: 5 }, (_, i) => {
        if (i < full) return <span key={i} className="course_card_rating_star">★</span>;
        if (i === full && half) return <span key={i} className="course_card_rating_star" style={{ opacity: 0.5 }}>★</span>;
        return <span key={i} className="course_card_rating_star course_card_rating_star--empty">★</span>;
      })}
    </>
  );
}

export function CourseCard({ course, onClick }: CourseCardProps) {
  return (
    <div className="course_card" onClick={onClick}>
      {/* Thumbnail */}
      <div className="course_card_thumbnail">
        {course.thumbnailUrl ? (
          <img src={course.thumbnailUrl} alt={course.title} />
        ) : (
          <div className="course_card_thumbnail_placeholder">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
              <path d="M20 8C13.373 8 8 13.373 8 20s5.373 12 12 12 12-5.373 12-12S26.627 8 20 8zm0 2a10 10 0 1 1 0 20A10 10 0 0 1 20 10zm0 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm-1 7v7h2v-7h-2z" fill="#C8A97E"/>
            </svg>
          </div>
        )}
        <span className={`course_difficulty_badge ${difficultyClass(course.difficulty)}`}>
          {difficultyLabel(course.difficulty)}
        </span>
      </div>

      {/* Info */}
      <div className="course_card_info">
        <div>
          <div className="course_card_title">{course.title}</div>
          <div className="course_card_region">{course.region}</div>
        </div>

        {/* Meta: distance / time */}
        <div className="course_card_meta">
          <div className="course_card_meta_item">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 1C4.239 1 2 3.239 2 6c0 3.75 5 8 5 8s5-4.25 5-8c0-2.761-2.239-5-5-5zm0 7a2 2 0 1 1 0-4 2 2 0 0 1 0 4z" fill="#A58A54"/>
            </svg>
            {course.distanceKm.toFixed(1)} km
          </div>
          <div className="course_card_meta_dot" />
          <div className="course_card_meta_item">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="5.5" stroke="#A58A54" strokeWidth="1.2"/>
              <path d="M7 4.5V7l2 1.5" stroke="#A58A54" strokeWidth="1.2" strokeLinecap="round"/>
            </svg>
            {course.estimatedMinutes}분
          </div>
        </div>

        {/* Stats: likes / walks / rating */}
        <div className="course_card_stats">
          <div className="course_card_stats_left">
            <div className="course_card_stat_item">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M7 12s-5.5-3.5-5.5-7A3.5 3.5 0 0 1 7 2.586 3.5 3.5 0 0 1 12.5 5c0 3.5-5.5 7-5.5 7z" fill="#FF6B6B"/>
              </svg>
              {course.likeCount.toLocaleString()}
            </div>
            <div className="course_card_stat_item">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <ellipse cx="5" cy="10" rx="2" ry="1" fill="#A58A54"/>
                <ellipse cx="9" cy="10" rx="2" ry="1" fill="#A58A54"/>
                <ellipse cx="3.5" cy="7.5" rx="1.5" ry="1" fill="#A58A54"/>
                <ellipse cx="10.5" cy="7.5" rx="1.5" ry="1" fill="#A58A54"/>
              </svg>
              {course.walkCount.toLocaleString()}
            </div>
          </div>
          {course.ratingCount > 0 && (
            <div className="course_card_rating">
              <StarRating rating={course.rating} />
              <span>({course.ratingCount})</span>
            </div>
          )}
        </div>

        {/* Author */}
        <div className="course_card_author">
          <img
            className="course_card_author_img"
            src={course.authorProfileImageUrl || '/assets/images/common/profile_none_img.svg'}
            alt={course.authorNickname}
          />
          <span className="course_card_author_name">{course.authorNickname}</span>
        </div>
      </div>
    </div>
  );
}
