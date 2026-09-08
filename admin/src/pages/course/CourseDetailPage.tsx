import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { courseManagementService } from '../../services/courseManagementService';
import { DetailPageLayout } from '../../components/common/DetailPageLayout';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const DIFFICULTY_BADGE: Record<string, { label: string; className: string }> = {
  EASY: { label: '초급', className: 'bg-green-100 text-green-800' },
  MODERATE: { label: '중급', className: 'bg-amber-100 text-amber-800' },
  HARD: { label: '상급', className: 'bg-red-100 text-red-800' },
};

const SPOT_TYPE_EMOJI: Record<string, string> = {
  CONVENIENCE_STORE: '🏪',
  WATER_FOUNTAIN: '🚰',
  RESTROOM: '🚻',
  PET_CAFE: '☕',
  PARK: '🌳',
  DANGER_ZONE: '⚠️',
  REST_AREA: '🪑',
  TRASH_CAN: '🗑️',
  PHOTO_SPOT: '📸',
  OTHER: '📍',
};

function RatingStars({ rating }: { rating: number | null }) {
  if (rating === null) return null;
  const stars = Math.round(rating);
  return (
    <span className="text-amber-400 text-sm">
      {'★'.repeat(stars)}{'☆'.repeat(5 - stars)}
      <span className="text-gray-500 ml-1">({rating.toFixed(1)})</span>
    </span>
  );
}

export default function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()

  const { data: course, isLoading } = useQuery({
    queryKey: ['courses', 'detail', courseId],
    queryFn: () => courseManagementService.getCourseDetail(Number(courseId)),
    enabled: !!courseId,
  });

  const hideMutation = useMutation({
    mutationFn: () => courseManagementService.hideCourse(Number(courseId)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 숨김에 실패했습니다.'),
  });

  const unhideMutation = useMutation({
    mutationFn: () => courseManagementService.unhideCourse(Number(courseId)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 숨김 해제에 실패했습니다.'),
  });

  const deleteMutation = useMutation({
    mutationFn: () => courseManagementService.deleteCourse(Number(courseId)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 삭제에 실패했습니다.'),
  });

  const hideCommentMutation = useMutation({
    mutationFn: (commentId: number) => courseManagementService.hideComment(commentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', 'detail', courseId] }),
    onError: () => toast.error('댓글 숨김에 실패했습니다.'),
  });

  const unhideCommentMutation = useMutation({
    mutationFn: (commentId: number) => courseManagementService.unhideComment(commentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', 'detail', courseId] }),
    onError: () => toast.error('댓글 숨김 해제에 실패했습니다.'),
  });

  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: number) => courseManagementService.deleteComment(commentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses', 'detail', courseId] }),
    onError: () => toast.error('댓글 삭제에 실패했습니다.'),
  });

  const handleHide = async () => {
    if (!(await confirmDialog({ description: '이 코스를 숨기시겠습니까?' }))) return;
    hideMutation.mutate();
  };

  const handleUnhide = async () => {
    if (!(await confirmDialog({ description: '이 코스의 숨김을 해제하시겠습니까?' }))) return;
    unhideMutation.mutate();
  };

  const handleDelete = async () => {
    if (!(await confirmDialog({ description: '정말 이 코스를 삭제하시겠습니까? 이 작업은 취소할 수 없습니다.', variant: 'destructive' }))) return;
    deleteMutation.mutate();
  };

  const handleHideComment = async (commentId: number) => {
    if (!(await confirmDialog({ description: '이 댓글을 숨기시겠습니까?' }))) return;
    hideCommentMutation.mutate(commentId);
  };

  const handleUnhideComment = async (commentId: number) => {
    if (!(await confirmDialog({ description: '이 댓글의 숨김을 해제하시겠습니까?' }))) return;
    unhideCommentMutation.mutate(commentId);
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!(await confirmDialog({ description: '이 댓글을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    deleteCommentMutation.mutate(commentId);
  };

  if (isLoading) {
    return (
      <DetailPageLayout title="코스 상세" backPath="/courses">
        <div className="py-12 text-center text-gray-500">로딩 중...</div>
      </DetailPageLayout>
    );
  }

  if (!course) {
    return (
      <DetailPageLayout title="코스 상세" backPath="/courses">
        <div className="py-12 text-center text-gray-500">코스를 찾을 수 없습니다.</div>
      </DetailPageLayout>
    );
  }

  const difficultyBadge = DIFFICULTY_BADGE[course.difficulty] ?? { label: course.difficulty, className: 'bg-gray-100 text-gray-800' };

  return (
    <DetailPageLayout
      title="코스 상세"
      backPath="/courses"
      actions={
        <>
          {!course.isHidden ? (
            <Button variant="warning" onClick={handleHide} loading={hideMutation.isPending}>
              숨김 처리
            </Button>
          ) : (
            <Button variant="success" onClick={handleUnhide} loading={unhideMutation.isPending}>
              숨김 해제
            </Button>
          )}
          <Button variant="danger" onClick={handleDelete} loading={deleteMutation.isPending}>
            삭제
          </Button>
        </>
      }
    >
      {/* Course Info */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6 space-y-4">
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <span className={`px-2 py-0.5 text-xs font-semibold rounded-full ${difficultyBadge.className}`}>
            {difficultyBadge.label}
          </span>
          <span>·</span>
          <span>{course.region}</span>
          <span>·</span>
          <span>{course.authorNickname}</span>
          <span>·</span>
          <span>{course.createdAt ?? '-'}</span>
          {course.isHidden && (
            <span className="ml-auto px-2 py-0.5 bg-red-100 text-red-800 text-xs font-semibold rounded-full">
              숨김
            </span>
          )}
        </div>

        <h2 className="text-xl font-semibold text-gray-900">{course.title}</h2>

        {course.description && (
          <p className="text-gray-700 whitespace-pre-wrap leading-relaxed">{course.description}</p>
        )}

        {course.thumbnailUrl && (
          <a href={course.thumbnailUrl} target="_blank" rel="noopener noreferrer">
            <img
              src={course.thumbnailUrl}
              alt="코스 썸네일"
              className="w-48 h-32 object-cover rounded-lg border border-gray-200 hover:opacity-90 transition-opacity"
            />
          </a>
        )}

        <div className="flex flex-wrap gap-6 text-sm text-gray-500 border-t pt-4">
          <span>거리 <strong className="text-gray-900">{course.distanceKm.toFixed(1)}km</strong></span>
          <span>예상시간 <strong className="text-gray-900">{course.estimatedMinutes}분</strong></span>
          <span>좋아요 <strong className="text-gray-900">{course.likeCount}</strong></span>
          <span>댓글 <strong className="text-gray-900">{course.commentCount}</strong></span>
          <span>산책수 <strong className="text-gray-900">{course.walkCount}</strong></span>
          <span>
            평점 <strong className="text-gray-900">{course.rating.toFixed(1)}</strong>
            <span className="text-gray-400"> ({course.ratingCount}개)</span>
          </span>
          <span className="ml-auto text-xs text-gray-400">작성자 ID: {course.authorId}</span>
        </div>
      </div>

      {/* Spots */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          스팟 <span className="text-gray-500 font-normal text-base">({course.spots.length})</span>
        </h3>

        {course.spots.length === 0 ? (
          <p className="text-sm text-gray-500">스팟이 없습니다.</p>
        ) : (
          <div className="space-y-2">
            {course.spots
              .slice()
              .sort((a, b) => a.orderIndex - b.orderIndex)
              .map((spot) => {
                const emoji = SPOT_TYPE_EMOJI[spot.type] ?? '📍';
                return (
                  <div key={spot.id} className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                    <span className="text-lg flex-shrink-0">{emoji}</span>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {spot.name ?? spot.type}
                        </span>
                        <span className="text-xs text-gray-400">#{spot.orderIndex + 1}</span>
                      </div>
                      {spot.description && (
                        <p className="text-xs text-gray-500 mt-0.5">{spot.description}</p>
                      )}
                      <p className="text-xs text-gray-400 mt-0.5">
                        {spot.latitude.toFixed(6)}, {spot.longitude.toFixed(6)}
                      </p>
                    </div>
                  </div>
                );
              })}
          </div>
        )}
      </div>

      {/* Comments */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          댓글 <span className="text-gray-500 font-normal text-base">({course.comments.length})</span>
        </h3>

        {course.comments.length === 0 ? (
          <p className="text-sm text-gray-500">댓글이 없습니다.</p>
        ) : (
          <div className="space-y-3">
            {course.comments.map((comment) => (
              <div
                key={comment.id}
                className={`p-4 rounded-lg flex justify-between items-start gap-4 ${
                  comment.isHidden ? 'bg-red-50 border border-red-100' : 'bg-gray-50'
                }`}
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm font-medium text-gray-900">{comment.authorNickname}</span>
                    {comment.isHidden && (
                      <span className="px-1.5 py-0.5 bg-red-100 text-red-700 text-xs rounded">숨김</span>
                    )}
                    {comment.rating != null && <RatingStars rating={comment.rating} />}
                    <span className="text-xs text-gray-400">{comment.createdAt ?? '-'}</span>
                  </div>
                  <p className="text-sm text-gray-700">{comment.content}</p>
                </div>
                <div className="flex gap-2 flex-shrink-0">
                  {!comment.isHidden ? (
                    <Button
                      variant="link"
                      size="sm"
                      className="text-orange-600 hover:text-orange-900"
                      onClick={() => handleHideComment(comment.id)}
                      loading={hideCommentMutation.isPending}
                    >
                      숨김
                    </Button>
                  ) : (
                    <Button
                      variant="link"
                      size="sm"
                      className="text-green-600 hover:text-green-900"
                      onClick={() => handleUnhideComment(comment.id)}
                      loading={unhideCommentMutation.isPending}
                    >
                      숨김 해제
                    </Button>
                  )}
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => handleDeleteComment(comment.id)}
                    loading={deleteCommentMutation.isPending}
                  >
                    삭제
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    {ConfirmDialog}
    </DetailPageLayout>
  );
}
