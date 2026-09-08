import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { ContentRenderer } from '../../utils/contentRenderer';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { communityManagementService } from '../../services/communityManagementService';
import { DetailPageLayout } from '../../components/common/DetailPageLayout';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

export default function CommunityDetailPage() {
  const { postId } = useParams<{ postId: string }>();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()

  const { data: post, isLoading } = useQuery({
    queryKey: ['community', 'detail', postId],
    queryFn: () => communityManagementService.getPostDetail(Number(postId)),
    enabled: !!postId,
  });

  const [isEditingPost, setIsEditingPost] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null);
  const [editCommentContent, setEditCommentContent] = useState('');

  const invalidateDetail = () =>
    queryClient.invalidateQueries({ queryKey: ['community', 'detail', postId] });

  const hideMutation = useMutation({
    mutationFn: () => communityManagementService.hidePost(Number(postId), '관리자에 의해 숨김'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['community'] });
    },
    onError: () => toast.error('게시글 숨김에 실패했습니다.'),
  });

  const deleteMutation = useMutation({
    mutationFn: () => communityManagementService.deletePost(Number(postId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['community'] });
    },
    onError: () => toast.error('게시글 삭제에 실패했습니다.'),
  });

  const unhideMutation = useMutation({
    mutationFn: () => communityManagementService.unhidePost(Number(postId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['community'] });
    },
    onError: () => toast.error('게시글 숨김 해제에 실패했습니다.'),
  });

  const updatePostMutation = useMutation({
    mutationFn: (payload: { title: string; content: string }) =>
      communityManagementService.updatePost(Number(postId), payload),
    onSuccess: () => {
      toast.success('게시글이 수정되었습니다.');
      setIsEditingPost(false);
      invalidateDetail();
      queryClient.invalidateQueries({ queryKey: ['community'] });
    },
    onError: () => toast.error('게시글 수정에 실패했습니다.'),
  });

  const updateCommentMutation = useMutation({
    mutationFn: ({ commentId, content }: { commentId: number; content: string }) =>
      communityManagementService.updateComment(commentId, content),
    onSuccess: () => {
      toast.success('댓글이 수정되었습니다.');
      setEditingCommentId(null);
      setEditCommentContent('');
      invalidateDetail();
    },
    onError: () => toast.error('댓글 수정에 실패했습니다.'),
  });

  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: number) => communityManagementService.deleteComment(commentId),
    onSuccess: () => {
      invalidateDetail();
    },
    onError: () => toast.error('댓글 삭제에 실패했습니다.'),
  });

  const handleHide = async () => {
    if (!(await confirmDialog({ description: '이 게시글을 숨기시겠습니까?' }))) return;
    hideMutation.mutate();
  };

  const handleUnhide = async () => {
    if (!(await confirmDialog({ description: '이 게시글의 숨김을 해제하시겠습니까?' }))) return;
    unhideMutation.mutate();
  };

  const handleDelete = async () => {
    if (!(await confirmDialog({ description: '정말 이 게시글을 삭제하시겠습니까? 이 작업은 취소할 수 없습니다.', variant: 'destructive' }))) return;
    deleteMutation.mutate();
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!(await confirmDialog({ description: '이 댓글을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    deleteCommentMutation.mutate(commentId);
  };

  const handleStartEditPost = () => {
    if (!post) return;
    setEditTitle(post.title);
    setEditContent(post.content);
    setIsEditingPost(true);
  };

  const handleCancelEditPost = () => {
    setIsEditingPost(false);
  };

  const handleSavePost = async () => {
    if (!editTitle.trim()) {
      toast.error('제목을 입력하세요.');
      return;
    }
    if (!(await confirmDialog({ description: '게시글 내용을 수정하시겠습니까? 원본이 변경됩니다.' }))) return;
    updatePostMutation.mutate({ title: editTitle, content: editContent });
  };

  const handleStartEditComment = (commentId: number, content: string) => {
    setEditingCommentId(commentId);
    setEditCommentContent(content);
  };

  const handleCancelEditComment = () => {
    setEditingCommentId(null);
    setEditCommentContent('');
  };

  const handleSaveComment = async (commentId: number) => {
    if (!editCommentContent.trim()) {
      toast.error('내용을 입력하세요.');
      return;
    }
    if (!(await confirmDialog({ description: '댓글 내용을 수정하시겠습니까? 원본이 변경됩니다.' }))) return;
    updateCommentMutation.mutate({ commentId, content: editCommentContent });
  };

  if (isLoading) {
    return (
      <DetailPageLayout title="게시글 상세" backPath="/community">
        <div className="py-12 text-center text-gray-500">로딩 중...</div>
      </DetailPageLayout>
    );
  }

  if (!post) {
    return (
      <DetailPageLayout title="게시글 상세" backPath="/community">
        <div className="py-12 text-center text-gray-500">게시글을 찾을 수 없습니다.</div>
      </DetailPageLayout>
    );
  }

  return (
    <DetailPageLayout
      title="게시글 상세"
      backPath="/community"
      actions={
        <>
          {!isEditingPost && (
            <Button variant="primary" onClick={handleStartEditPost}>
              내용 수정
            </Button>
          )}
          {!post.isHidden ? (
            <Button
              variant="warning"
              onClick={handleHide}
              loading={hideMutation.isPending}
            >
              숨김 처리
            </Button>
          ) : (
            <Button
              variant="success"
              onClick={handleUnhide}
              loading={unhideMutation.isPending}
            >
              숨김 해제
            </Button>
          )}
          <Button
            variant="danger"
            onClick={handleDelete}
            loading={deleteMutation.isPending}
          >
            삭제
          </Button>
        </>
      }
    >
      {/* Post Info */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6 space-y-4">
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <span className="px-2 py-0.5 bg-gray-100 rounded text-xs">{post.categoryName}</span>
          <span>·</span>
          <span>{post.userNickname}</span>
          <span>·</span>
          <span>{post.createdAt ?? '-'}</span>
          {post.updatedAt && post.updatedAt !== post.createdAt && (
            <>
              <span>·</span>
              <span className="text-xs text-gray-400">수정됨: {post.updatedAt}</span>
            </>
          )}
          {post.isHidden && (
            <span className="ml-auto px-2 py-0.5 bg-red-100 text-red-800 text-xs font-semibold rounded-full">
              숨김
            </span>
          )}
        </div>

        {isEditingPost ? (
          <div className="space-y-3">
            <input
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-lg font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="제목"
              maxLength={100}
            />
            <textarea
              value={editContent}
              onChange={(e) => setEditContent(e.target.value)}
              rows={10}
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 font-mono"
              placeholder="본문 내용"
            />
            <p className="text-xs text-gray-500">
              어뷰징/비속어 마스킹 등 원본을 직접 수정합니다. 작성자에게 통지되지 않습니다.
            </p>
            <div className="flex gap-2 justify-end">
              <Button variant="secondary" onClick={handleCancelEditPost} disabled={updatePostMutation.isPending}>
                취소
              </Button>
              <Button variant="primary" onClick={handleSavePost} loading={updatePostMutation.isPending}>
                저장
              </Button>
            </div>
          </div>
        ) : (
          <>
            <h2 className="text-xl font-semibold text-gray-900">{post.title}</h2>
            <div className="text-gray-700 leading-relaxed">
              <ContentRenderer content={post.content} />
            </div>
          </>
        )}

        {/* Images */}
        {!isEditingPost && post.imageUrls.length > 0 && (
          <div className="flex flex-wrap gap-3 pt-2">
            {post.imageUrls.map((url, idx) => (
              <a key={idx} href={url} target="_blank" rel="noopener noreferrer">
                <img
                  src={url}
                  alt={`첨부 이미지 ${idx + 1}`}
                  className="w-32 h-32 object-cover rounded-lg border border-gray-200 hover:opacity-90 transition-opacity"
                />
              </a>
            ))}
          </div>
        )}

        {/* Metadata */}
        <div className="flex gap-6 text-sm text-gray-500 border-t pt-4">
          <span>조회 <strong className="text-gray-900">{post.viewCount}</strong></span>
          <span>좋아요 <strong className="text-gray-900">{post.likeCount}</strong></span>
          <span>댓글 <strong className="text-gray-900">{post.commentCount}</strong></span>
          <span className="ml-auto text-xs text-gray-400">작성자 ID: {post.userId}</span>
        </div>
      </div>

      {/* Comments */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">
          댓글 <span className="text-gray-500 font-normal text-base">({post.comments.length})</span>
        </h3>

        {post.comments.length === 0 ? (
          <p className="text-sm text-gray-500">댓글이 없습니다.</p>
        ) : (
          <div className="space-y-3">
            {post.comments.map((comment) => {
              const isEditing = editingCommentId === comment.id;
              return (
                <div
                  key={comment.id}
                  className={`p-4 rounded-lg flex justify-between items-start gap-4 ${
                    comment.isHidden ? 'bg-red-50 border border-red-100' : 'bg-gray-50'
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium text-gray-900">{comment.userNickname}</span>
                      {comment.isHidden && (
                        <span className="px-1.5 py-0.5 bg-red-100 text-red-700 text-xs rounded">숨김</span>
                      )}
                      <span className="text-xs text-gray-400">{comment.createdAt ?? '-'}</span>
                    </div>
                    {isEditing ? (
                      <textarea
                        value={editCommentContent}
                        onChange={(e) => setEditCommentContent(e.target.value)}
                        rows={3}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      />
                    ) : (
                      <p className="text-sm text-gray-700 whitespace-pre-wrap">{comment.content}</p>
                    )}
                  </div>
                  <div className="flex flex-col gap-2 shrink-0">
                    {isEditing ? (
                      <>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleSaveComment(comment.id)}
                          loading={updateCommentMutation.isPending}
                        >
                          저장
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={handleCancelEditComment}
                          disabled={updateCommentMutation.isPending}
                        >
                          취소
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleStartEditComment(comment.id, comment.content)}
                        >
                          수정
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => handleDeleteComment(comment.id)}
                          loading={deleteCommentMutation.isPending}
                        >
                          삭제
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    {ConfirmDialog}
    </DetailPageLayout>
  );
}
