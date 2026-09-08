import { useState } from 'react';
import { useKeyboardDismiss } from '../../../hooks/useKeyboardDismiss';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { courseService } from '../../../services/courseService';
import type { CourseCommentResponse } from '../../../services/courseService';
import { useAuthStore } from '../../../stores/authStore';
import { getImageUrl } from '../../../utils/imageUrl';
import { useAlert } from '../../../contexts/AlertContext';
import { useToast } from '../../../contexts/ToastContext';
import './CourseReviewSection.css';

// ── Helpers ──

function formatRelativeDate(isoString: string): string {
  const now = Date.now();
  const diff = now - new Date(isoString).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}일 전`;
  const months = Math.floor(days / 30);
  return `${months}달 전`;
}

function StarDisplay({ rating }: { rating: number }) {
  return (
    <span className="course_review_stars">
      {Array.from({ length: 5 }, (_, i) => (
        <svg key={i} width="13" height="13" viewBox="0 0 24 24" fill={i < rating ? '#FFB800' : '#D9D9D9'}>
          <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
        </svg>
      ))}
    </span>
  );
}

function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const [hovered, setHovered] = useState(0);
  const display = hovered || value;
  return (
    <span className="course_review_star_input">
      {Array.from({ length: 5 }, (_, i) => (
        <button
          key={i}
          type="button"
          className="course_review_star_input_btn"
          onMouseEnter={() => setHovered(i + 1)}
          onMouseLeave={() => setHovered(0)}
          onClick={() => onChange(i + 1)}
          aria-label={`${i + 1}점`}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill={i < display ? '#FFB800' : '#D9D9D9'}>
            <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
          </svg>
        </button>
      ))}
    </span>
  );
}

// ── Icons ──

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill={filled ? '#E53935' : 'none'} stroke={filled ? '#E53935' : 'currentColor'} strokeWidth="2">
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  );
}

function ReplyIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6M14 11v6" />
      <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  );
}

// ── Sub-components ──

interface CommentItemProps {
  comment: CourseCommentResponse;
  courseId: number;
  currentUserId: number | null;
  isReply?: boolean;
  onReply: (comment: CourseCommentResponse) => void;
}

function CommentItem({ comment, courseId, currentUserId, isReply = false, onReply }: CommentItemProps) {
  const queryClient = useQueryClient();
  const { showConfirm } = useAlert();
  const { showToast } = useToast();

  const [editMode, setEditMode] = useState(false);
  const [editContent, setEditContent] = useState(comment.content);
  const [editRating, setEditRating] = useState(comment.rating ?? 0);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(comment.likeCount);

  const isOwn = currentUserId !== null && comment.userId === currentUserId;

  const likeMutation = useMutation({
    mutationFn: () => courseService.toggleCommentLike(courseId, comment.id),
    onMutate: () => {
      setLiked((prev) => {
        setLikeCount((c) => (prev ? c - 1 : c + 1));
        return !prev;
      });
    },
    onError: () => {
      setLiked((prev) => {
        setLikeCount((c) => (prev ? c - 1 : c + 1));
        return !prev;
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      courseService.updateCourseComment(courseId, comment.id, {
        content: editContent,
        rating: !isReply && editRating > 0 ? editRating : undefined,
      }),
    onSuccess: () => {
      setEditMode(false);
      queryClient.invalidateQueries({ queryKey: ['course', 'comments', courseId] });
      showToast('댓글이 수정되었습니다.', 'success');
    },
    onError: () => showToast('수정에 실패했습니다.', 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: () => courseService.deleteCourseComment(courseId, comment.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['course', 'comments', courseId] });
      showToast('댓글이 삭제되었습니다.', 'success');
    },
    onError: () => showToast('삭제에 실패했습니다.', 'error'),
  });

  const handleDelete = () => {
    showConfirm('댓글을 삭제하시겠습니까?', () => {
      deleteMutation.mutate();
    });
  };

  if (comment.isHidden) {
    return (
      <div className={isReply ? 'course_review_item--reply' : 'course_review_item'}>
        <p className="course_review_content course_review_content--hidden">숨겨진 댓글입니다</p>
      </div>
    );
  }

  return (
    <div className={isReply ? 'course_review_item--reply' : 'course_review_item'}>
      <div className="course_review_author">
        <img
          className={`course_review_author_img${isReply ? ' course_review_author_img--sm' : ''}`}
          src={comment.userProfileImageUrl ? getImageUrl(comment.userProfileImageUrl) : '/img/default_profile.png'}
          alt={comment.userNickname}
          onError={(e) => { (e.target as HTMLImageElement).src = '/img/default_profile.png'; }}
        />
        <div className="course_review_author_info">
          <span className="course_review_author_name">{comment.userNickname}</span>
          <span className="course_review_author_date">{formatRelativeDate(comment.createdAt)}</span>
        </div>
      </div>

      {!isReply && comment.rating != null && !editMode && (
        <StarDisplay rating={comment.rating} />
      )}

      {editMode ? (
        <div className="course_review_edit_wrap">
          {!isReply && (
            <StarInput value={editRating} onChange={setEditRating} />
          )}
          <textarea
            className="course_review_edit_textarea"
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
          />
          <div className="course_review_edit_actions">
            <button type="button" className="course_review_edit_btn" onClick={() => setEditMode(false)}>취소</button>
            <button
              type="button"
              className="course_review_edit_btn course_review_edit_btn--primary"
              disabled={updateMutation.isPending || !editContent.trim()}
              onClick={() => updateMutation.mutate()}
            >
              저장
            </button>
          </div>
        </div>
      ) : (
        <p className="course_review_content">{comment.content}</p>
      )}

      <div className="course_review_actions">
        <button
          type="button"
          className={`course_review_action_btn${liked ? ' course_review_action_btn--liked' : ''}`}
          onClick={() => likeMutation.mutate()}
          disabled={likeMutation.isPending}
        >
          <HeartIcon filled={liked} />
          {likeCount > 0 && <span>{likeCount}</span>}
        </button>

        {!isReply && (
          <button
            type="button"
            className="course_review_action_btn"
            onClick={() => onReply(comment)}
          >
            <ReplyIcon />
            <span>답글</span>
          </button>
        )}

        {isOwn && !editMode && (
          <>
            <button
              type="button"
              className="course_review_action_btn"
              onClick={() => {
                setEditContent(comment.content);
                setEditRating(comment.rating ?? 0);
                setEditMode(true);
              }}
            >
              <EditIcon />
              <span>수정</span>
            </button>
            <button
              type="button"
              className="course_review_action_btn course_review_action_btn--danger"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
            >
              <TrashIcon />
              <span>삭제</span>
            </button>
          </>
        )}
      </div>
    </div>
  );
}

// ── Main Component ──

interface Props {
  courseId: number;
}

export const CourseReviewSection = ({ courseId }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const user = useAuthStore((s) => s.user);
  const currentUserId = user?.id ?? null;

  const [replyTarget, setReplyTarget] = useState<CourseCommentResponse | null>(null);
  const [formContent, setFormContent] = useState('');
  const [formRating, setFormRating] = useState(0);

  const PAGE_SIZE = 50;

  const { data, isFetching } = useQuery({
    queryKey: ['course', 'comments', courseId],
    queryFn: () => courseService.getCourseComments(courseId, { page: 0, size: PAGE_SIZE }),
  });

  const allComments = data?.content ?? [];
  const hasLoadedOnce = !!data;

  const createMutation = useMutation({
    mutationFn: () =>
      courseService.createCourseComment(courseId, {
        content: formContent,
        rating: !replyTarget && formRating > 0 ? formRating : undefined,
        parentCommentId: replyTarget?.id,
      }),
    onSuccess: () => {
      setFormContent('');
      setFormRating(0);
      setReplyTarget(null);
      queryClient.invalidateQueries({ queryKey: ['course', 'comments', courseId] });
      showToast('댓글이 등록되었습니다.', 'success');
    },
    onError: () => showToast('댓글 등록에 실패했습니다.', 'error'),
  });

  // Group comments: top-level + their replies
  const topLevel = allComments.filter((c) => c.parentCommentId === null);
  const repliesOf = (parentId: number) => allComments.filter((c) => c.parentCommentId === parentId);
  const totalCount = data?.totalElements ?? 0;

  const handleReply = (comment: CourseCommentResponse) => {
    setReplyTarget(comment);
    setFormContent('');
  };

  const handleSubmit = () => {
    if (!formContent.trim()) return;
    createMutation.mutate();
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <section className="course_review_section" {...keyboardDismiss}>
      <div className="course_review_header">
        <span className="course_review_header_title">리뷰</span>
        {hasLoadedOnce && totalCount > 0 && (
          <span className="course_review_header_count">{totalCount}</span>
        )}
      </div>

      {/* Comment form */}
      <div className="course_review_form">
        {replyTarget && (
          <div className="course_review_form_reply_label">
            <ReplyIcon />
            <span>@ {replyTarget.userNickname}에게 답글</span>
            <button
              type="button"
              className="course_review_form_reply_cancel"
              onClick={() => setReplyTarget(null)}
              aria-label="답글 취소"
            >
              <CloseIcon />
            </button>
          </div>
        )}

        {!replyTarget && (
          <StarInput value={formRating} onChange={setFormRating} />
        )}

        <div className="course_review_form_row">
          <textarea
            className="course_review_form_textarea"
            placeholder="리뷰를 남겨보세요"
            value={formContent}
            onChange={(e) => setFormContent(e.target.value)}
            rows={3}
          />
          <button
            type="button"
            className="course_review_form_submit"
            disabled={!formContent.trim() || createMutation.isPending}
            onClick={handleSubmit}
          >
            등록
          </button>
        </div>
      </div>

      {/* Comment list */}
      {isFetching && !hasLoadedOnce ? (
        <p className="course_review_loading">불러오는 중...</p>
      ) : hasLoadedOnce && topLevel.length === 0 ? (
        <p className="course_review_empty">첫 번째 리뷰를 남겨보세요!</p>
      ) : (
        <>
          {topLevel.map((comment) => (
            <div key={comment.id}>
              <CommentItem
                comment={comment}
                courseId={courseId}
                currentUserId={currentUserId}
                onReply={handleReply}
              />
              {repliesOf(comment.id).map((reply) => (
                <CommentItem
                  key={reply.id}
                  comment={reply}
                  courseId={courseId}
                  currentUserId={currentUserId}
                  isReply
                  onReply={handleReply}
                />
              ))}
            </div>
          ))}

        </>
      )}
    </section>
  );
};
