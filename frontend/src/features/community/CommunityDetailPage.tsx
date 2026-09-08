import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useParams, useNavigate } from 'react-router-dom';
import { useOverlayColor } from '../../hooks/useOverlayColor';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { communityService } from '../../services/communityService';
import { blockService } from '../../services/blockService';
import { reportService } from '../../services/reportService';
import { BackButton } from '../../components/common/BackButton';
import { useHandleProfileTap } from '../../hooks/useHandleProfileTap';
import { ReportModal } from '../../components/common/ReportModal';
import { ImageGalleryModal } from '../../components/common/ImageGalleryModal';
import { GpImage } from '../../components/common/GpImage';
import { Loading } from '../../components/common/Loading';
import { ContentRenderer } from '../../utils/contentRenderer';
import './CommunityPage.css';

const AUTHOR_FALLBACK = '/assets/images/common/profile_none_img.svg';
import { useAlert } from '../../contexts/AlertContext';
import { useToast } from '../../contexts/ToastContext';
import { CACHE_TIME } from '../../config/queryConfig';

export default function CommunityDetailPage() {
  const { postId } = useParams<{ postId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const handleProfileTap = useHandleProfileTap();
  const { showAlert, showConfirm } = useAlert();
  const { showToast } = useToast();
  const [commentInput, setCommentInput] = useState('');
  const [replyTargetId, setReplyTargetId] = useState<number | null>(null);

  /* Action Sheet State */
  const [actionSheetTarget, setActionSheetTarget] = useState<{ type: 'POST' | 'COMMENT' | 'OTHER_POST' | 'OTHER_COMMENT', id: number } | null>(null);

  useOverlayColor(!!actionSheetTarget, '#000000', '#EFE1C4');

  const [showReportModal, setShowReportModal] = useState(false);
  const [reportTarget, setReportTarget] = useState<{ type: 'POST' | 'COMMENT', targetId: number } | null>(null);
  const [isReportSubmitting, setIsReportSubmitting] = useState(false);
  const [galleryState, setGalleryState] = useState<{ open: boolean; images: string[]; index: number }>({ open: false, images: [], index: 0 });

  /* Editing State */
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null);
  const [editCommentInput, setEditCommentInput] = useState('');

  const isInputFocusedRef = useRef(false);
  const detailWrapRef = useRef<HTMLDivElement>(null);

  const scrollToPosition = useCallback(() => {
    const container = detailWrapRef.current;
    if (!container) return;
    if (replyTargetId) {
      const parentComment = container.querySelector(`[data-comment-id="${replyTargetId}"]`);
      if (parentComment instanceof HTMLElement) {
        const containerRect = container.getBoundingClientRect();
        const commentRect = parentComment.getBoundingClientRect();
        const targetTop = container.scrollTop + (commentRect.top - containerRect.top) - 80;
        container.scrollTop = Math.max(0, targetTop);
      }
    } else {
      container.scrollTop = container.scrollHeight;
    }
  }, [replyTargetId]);

  // Inline keyboard detection (from ChatDetailPage pattern)
  // TODO: extract useKeyboardScroll when a third consumer appears
  useEffect(() => {
    const viewport = window.visualViewport;
    let prevHeight = viewport ? viewport.height : window.innerHeight;
    let prevWidth = viewport ? viewport.width : window.innerWidth;
    let resizeTimer: ReturnType<typeof setTimeout> | null = null;

    const handleResize = () => {
      const currentHeight = viewport ? viewport.height : window.innerHeight;
      const currentWidth = viewport ? viewport.width : window.innerWidth;
      const keyboardOpened = currentHeight < prevHeight && currentWidth === prevWidth;
      prevHeight = currentHeight;
      prevWidth = currentWidth;

      if (keyboardOpened && isInputFocusedRef.current) {
        scrollToPosition();
        if (resizeTimer) clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
          requestAnimationFrame(() => scrollToPosition());
        }, 300);
      }
    };

    if (viewport) viewport.addEventListener('resize', handleResize);
    window.addEventListener('resize', handleResize);
    return () => {
      if (resizeTimer) clearTimeout(resizeTimer);
      if (viewport) viewport.removeEventListener('resize', handleResize);
      window.removeEventListener('resize', handleResize);
    };
  }, [scrollToPosition]);

  const handleInputFocus = () => {
    isInputFocusedRef.current = true;
    const nav = document.getElementById('bottomNav');
    if (nav) nav.style.display = 'none';
    scrollToPosition();
    setTimeout(() => scrollToPosition(), 300);
  };

  const handleInputBlur = () => {
    isInputFocusedRef.current = false;
    setTimeout(() => {
      const active = document.activeElement;
      if (active && active.tagName === 'TEXTAREA') {
        isInputFocusedRef.current = true;
        return;
      }
      const nav = document.getElementById('bottomNav');
      if (nav) nav.style.display = '';
    }, 150);
  };

  const handleOptionClick = (type: 'POST' | 'COMMENT' | 'OTHER_POST' | 'OTHER_COMMENT', id: number) => {
    setActionSheetTarget({ type, id });
  };

  const handleEdit = () => {
    if (!actionSheetTarget) return;

    if (actionSheetTarget.type === 'POST') {
        navigate('/community/write', { state: { mode: 'EDIT', post: post } });
        setActionSheetTarget(null);
    } else {
        // Comment Edit
        const content = post?.comments?.find(c => c.id === actionSheetTarget.id)?.content;
        if (content) {
            setEditingCommentId(actionSheetTarget.id);
            setEditCommentInput(content);
        }
        setActionSheetTarget(null);
    }
  };

  const updateCommentMutation = useMutation({
      mutationFn: (variables: { id: number, content: string }) => communityService.updateComment(variables.id, variables.content),
      onSuccess: () => {
          setEditingCommentId(null);
          queryClient.invalidateQueries({ queryKey: ['community', 'post', postId] });
          showToast('댓글이 수정되었습니다.', 'success');
      },
      onError: () => showAlert('댓글 수정에 실패했습니다.')
  });

  const handleUpdateComment = () => {
      if (editingCommentId && editCommentInput.trim()) {
          updateCommentMutation.mutate({ id: editingCommentId, content: editCommentInput });
      }
  };



  /* Ref for Main Comment Input */
  const mainInputRef = useRef<HTMLTextAreaElement>(null);

  /* Reset main input height when cleared */
  useEffect(() => {
      if (commentInput === '' && mainInputRef.current) {
          mainInputRef.current.style.height = '32px';
      }
  }, [commentInput]);


  useEffect(() => {
    return () => {
      const nav = document.getElementById('bottomNav');
      if (nav) nav.style.display = '';
    };
  }, []);

  const { data: post, isLoading } = useQuery({
    queryKey: ['community', 'post', postId],
    queryFn: () => communityService.getPostDetail(Number(postId)),
    enabled: !!postId,
    ...CACHE_TIME.DYNAMIC,
  });

  const replyTargetNickname = replyTargetId
    ? post?.comments?.find(c => c.id === replyTargetId)?.authorNickname
    : null;

  const likeMutation = useMutation({
    mutationFn: () => communityService.toggleLike(Number(postId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['community', 'post', postId] });
    },
    onError: () => showToast('오류가 발생했습니다.', 'error'),
  });

  const commentLikeMutation = useMutation({
    mutationFn: (commentId: number) => communityService.toggleCommentLike(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['community', 'post', postId] });
    },
  });

  const commentMutation = useMutation({
    mutationFn: (variables: { content: string, parentId?: number }) =>
      communityService.createComment(Number(postId), variables.content, variables.parentId),
    onSuccess: () => {
      setCommentInput('');
      setReplyTargetId(null);
      showToast('댓글이 등록되었습니다.', 'success');
      queryClient.invalidateQueries({ queryKey: ['community', 'post', postId] });
    },
    onError: () => showAlert('댓글 등록에 실패했습니다.'),
  });

  const deletePostMutation = useMutation({
    mutationFn: (id: number) => communityService.deletePost(id),
    onSuccess: () => {
      showAlert('게시글이 삭제되었습니다.', () => {
          navigate('/community', { replace: true });
      });
    },
    onError: () => showAlert('게시글 삭제에 실패했습니다.')
  });

  const deleteCommentMutation = useMutation({
    mutationFn: (id: number) => communityService.deleteComment(id),
    onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['community', 'post', postId] });
        showToast('댓글이 삭제되었습니다.', 'success');
    },
    onError: () => showAlert('댓글 삭제에 실패했습니다.')
  });

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const handleSubmitComment = () => {
    if (commentInput.trim()) {
      commentMutation.mutate({
        content: commentInput,
        parentId: replyTargetId ?? undefined,
      });
    }
  };

  const handleBlockUser = () => {
      if (!actionSheetTarget || !post) return;
      setActionSheetTarget(null);
      showConfirm('이 사용자를 차단하시겠습니까?\n차단하면 서로의 게시글과 프로필이 보이지 않습니다.', async () => {
          try {
              await blockService.blockUser(post.authorId);
              showAlert('사용자를 차단했습니다.', () => {
                  navigate('/community', { replace: true });
              });
          } catch {
              showAlert('차단에 실패했습니다.');
          }
      });
  };

  const handleReport = () => {
      if (!actionSheetTarget) return;
      const type = actionSheetTarget.type === 'OTHER_POST' ? 'POST' as const : 'COMMENT' as const;
      const targetId = actionSheetTarget.id;
      setActionSheetTarget(null);
      setReportTarget({ type, targetId });
      setShowReportModal(true);
  };

  const handleReportSubmit = async (reason: string) => {
      if (!reportTarget) return;
      setIsReportSubmitting(true);
      try {
          await reportService.createReport({ ...reportTarget, reason });
          setShowReportModal(false);
          setReportTarget(null);
          showAlert('신고가 접수되었습니다.');
      } catch (error: unknown) {
          const err = error as { response?: { status?: number; data?: { message?: string } } };
          setShowReportModal(false);
          setReportTarget(null);
          if (err.response?.status === 400) {
              showAlert(err.response?.data?.message || '이미 신고한 대상입니다.');
          } else if (err.response?.status === 404) {
              showAlert('신고 대상을 찾을 수 없습니다.');
          } else {
              showAlert('신고 접수에 실패했습니다.');
          }
      } finally {
          setIsReportSubmitting(false);
      }
  };

  const handleDelete = () => {
      if (!actionSheetTarget) return;

      if (actionSheetTarget.type === 'POST') {
          showConfirm('정말 삭제하시겠습니까?', () => {
              deletePostMutation.mutate(actionSheetTarget.id);
              setActionSheetTarget(null);
          });
      } else {
          showConfirm('댓글을 삭제하시겠습니까?', () => {
              deleteCommentMutation.mutate(actionSheetTarget.id);
              setActionSheetTarget(null);
          });
      }
  }

  const keyboardDismiss = useKeyboardDismiss();

  if (isLoading) {
    return <Loading description="게시글을 불러오고 있어요" />;
  }

  if (!post) {
    return <div className="error">게시글을 찾을 수 없습니다.</div>;
  }

  return (
    <div id="communityContainer" className="community-detail-mode" {...keyboardDismiss}>
      {/* Top Header */}
      <div className="history_util">
        <BackButton onClick={() => navigate(-1)} />
        <button 
            type="button" 
            className="option_btn" 
            onClick={() => post.isMine ? handleOptionClick('POST', post.id) : handleOptionClick('OTHER_POST', post.id)}
        >
          <img src="/assets/images/common/option_icon.svg" alt="Option" />
        </button>
      </div>

      <div className="detail_wrap" ref={detailWrapRef}>
        <div className="view_contents">
          {/* Profile */}
          <div
            className="detail_profile"
            onClick={(e) => handleProfileTap(post.authorId, e)}
            style={{ cursor: 'pointer' }}
          >
            <div className="profile_img">
              {post.authorProfileUrl ? (
                <GpImage
                  src={post.authorProfileUrl}
                  thumbnailSrc={post.authorProfileUrlThumbnail}
                  viewerSrc={post.authorProfileUrlViewer}
                  variant="thumbnail"
                  alt=""
                />
              ) : (
                <img src={AUTHOR_FALLBACK} alt="" />
              )}
            </div>
            <div className="txt_wrap">
              <strong>{post.authorNickname}</strong>
              <p>{formatDate(post.createdAt)}</p>
            </div>
          </div>

          {/* Text Content */}
          <div className="detail_txt">
            <strong>{post.title}</strong>
            <ContentRenderer content={post.content} />
          </div>

          {/* Images */}
          {/* Images */}
          <div className="img_wrap">
             {post.imageUrls && post.imageUrls.length > 0 ? (() => {
                 const normalize = (u: string) =>
                     u.startsWith('http') ? u : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'}${u}`;
                 const resolvedUrls = post.imageUrls.map(normalize);
                 const resolvedThumbs = post.imageUrlsThumbnail?.map(normalize);
                 const resolvedMediums = post.imageUrlsMedium?.map(normalize);
                 const resolvedViewers = post.imageUrlsViewer?.map(normalize);
                 // 갤러리 모달은 viewer variant 우선 (없으면 원본)
                 const galleryImages = resolvedViewers ?? resolvedUrls;
                 return resolvedUrls.map((fullUrl, index) => (
                     <GpImage
                         key={index}
                         src={fullUrl}
                         thumbnailSrc={resolvedThumbs?.[index]}
                         mediumSrc={resolvedMediums?.[index]}
                         viewerSrc={resolvedViewers?.[index]}
                         variant="medium"
                         alt={`Post image ${index + 1}`}
                         style={{ marginBottom: '16px', cursor: 'pointer' }}
                         loading="lazy"
                         decoding="async"
                         onClick={() => setGalleryState({ open: true, images: galleryImages, index })}
                     />
                 ));
             })() : post.thumbnailUrl ? (
                 <img src={post.thumbnailUrl.startsWith('http') ? post.thumbnailUrl : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'}${post.thumbnailUrl}`} alt="Thumbnail" />
             ) : null}
          </div>

          {/* Like/Comment Counts */}
          <div className="like_comment_count">
            <div className="count_wrap">
              <div 
                className={`icon_img like_icon ${post.isLiked ? 'active' : ''}`}
                onClick={() => likeMutation.mutate()}
                style={{ cursor: 'pointer' }}
              ></div> 
              {post.likeCount}
            </div>
            <div className="count_wrap">
              <div className={`icon_img comment_icon ${post.isCommentedByMe ? 'active' : ''}`}></div> 
              {post.commentCount}
            </div>
          </div>
        </div>

        {/* Comments */}
        <div className="comment_wrap">
          <strong className="comment_count">댓글 {post.comments?.length || 0}</strong>

          {(!post.comments || post.comments.length === 0) && (
             <div className="comment_none">
                <img src="/assets/images/common/comment_icon03.svg" alt="" />
                등록된 댓글이 없어요ㅠ
            </div>
          )}

          <ul className="comment_list">
            {post.comments?.map((comment) => (
              <li
                key={comment.id}
                data-comment-id={comment.id}
                className={`comment_item depth_${comment.depth}`}
                style={{ paddingLeft: comment.depth > 1 ? `${(comment.depth - 1) * 40}px` : '0' }}
              >
                 <div
                    className="detail_profile"
                    onClick={(e) => handleProfileTap(comment.authorId, e)}
                    style={{ cursor: 'pointer' }}
                  >
                    <div className="profile_img">
                      {comment.authorProfileUrl ? (
                        <GpImage
                          src={comment.authorProfileUrl}
                          thumbnailSrc={comment.authorProfileUrlThumbnail}
                          viewerSrc={comment.authorProfileUrlViewer}
                          variant="thumbnail"
                          alt=""
                        />
                      ) : (
                        <img src={AUTHOR_FALLBACK} alt="" />
                      )}
                    </div>
                </div>
                <div className="comment_info">
                  <div className="profile_txt">
                      <strong>{comment.authorNickname}</strong>
                      <p>{formatDate(comment.createdAt)}</p>
                  </div>

                  {editingCommentId === comment.id ? (
                      <div className="comment_edit_wrap" style={{ marginTop: '8px' }}>
                          <textarea
                             autoFocus
                             onFocus={handleInputFocus}
                             onBlur={handleInputBlur}
                             value={editCommentInput}
                             onChange={(e) => {
                               setEditCommentInput(e.target.value);
                               e.target.style.height = '60px';
                               e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`;
                               e.target.style.overflowY = e.target.scrollHeight > 120 ? 'auto' : 'hidden';
                               e.target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                             }}
                             style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #ddd', resize: 'none', minHeight: '60px', maxHeight: '120px', overflowY: 'hidden', fontFamily: 'inherit' }}
                          />
                          <div style={{ display: 'flex', gap: '8px', marginTop: '8px', justifyContent: 'flex-end' }}>
                              <button onClick={() => setEditingCommentId(null)} style={{ padding: '6px 12px', borderRadius: '14px', border: '1px solid #ddd', background: '#fff', fontSize: '13px' }}>취소</button>
                              <button onClick={handleUpdateComment} style={{ padding: '6px 12px', borderRadius: '14px', border: 'none', background: 'var(--color-primary)', color: '#fff', fontSize: '13px' }}>저장</button>
                          </div>
                      </div>
                  ) : (
                      <p className="comment_txt" style={{ whiteSpace: 'pre-wrap' }}>{comment.content}</p>
                  )}
                  
                  <div className="like_comment_count">
                      <div className="count_wrap">
                        <div 
                          className={`icon_img like_icon ${comment.isLiked ? 'active' : ''}`}
                          onClick={() => commentLikeMutation.mutate(comment.id)}
                          style={{ cursor: 'pointer' }}
                        ></div> 
                        {comment.likeCount}
                      </div>
                      <div className="count_wrap">
                        <div className={`icon_img comment_icon ${comment.isRepliedByMe ? 'active' : ''}`}></div> 
                        {comment.replyCount > 0 && comment.replyCount}
                      </div>
                      {/* Allow reply only if depth < 3 */}
                      {comment.depth < 3 && (
                        <button type="button" onClick={() => {
                            const newTarget = replyTargetId === comment.id ? null : comment.id;
                            setReplyTargetId(newTarget);
                            if (newTarget) {
                              setTimeout(() => mainInputRef.current?.focus(), 100);
                            }
                        }}>
                            답글쓰기
                        </button>
                      )}
                  </div>

                </div>

                {/* Comment Option Button */}
                {comment.isMine && (
                    <button
                        type="button"
                        className="comment_util_btn"
                        onClick={() => handleOptionClick('COMMENT', comment.id)}
                    >
                        <img src="/assets/images/common/option_icon02.svg" alt="Options" />
                    </button>
                )}
                {!comment.isMine && (
                    <button
                        type="button"
                        className="comment_util_btn"
                        onClick={() => handleOptionClick('OTHER_COMMENT', comment.id)}
                    >
                        <img src="/assets/images/community/icon_more.svg" alt="더보기" />
                    </button>
                )}




              </li>
            ))}
          </ul>

        </div>
      </div>

      {/* Comment Input: direct child of container for flex layout (not inside detail_wrap) */}
      <div className="comment_submit" data-testid="comment-submit-area">
        {replyTargetId && replyTargetNickname && (
          <div className="reply_indicator">
            <span>@{replyTargetNickname} 에게 답글 중</span>
            <button type="button" onClick={() => setReplyTargetId(null)}>✕</button>
          </div>
        )}
        <div className="comment_input_row">
          <textarea
            ref={mainInputRef}
            data-testid="comment-textarea"
            placeholder={replyTargetId ? '답글을 남겨보세요' : '댓글을 남겨보세요'}
            value={commentInput}
            rows={1}
            onFocus={handleInputFocus}
            onBlur={handleInputBlur}
            onChange={(e) => {
              setCommentInput(e.target.value);
              e.target.style.height = '32px';
              e.target.style.height = `${Math.min(e.target.scrollHeight, 108)}px`;
              e.target.style.overflowY = e.target.scrollHeight > 108 ? 'auto' : 'hidden';
            }}
          />
          <button type="button" onClick={handleSubmitComment}>전송</button>
        </div>
      </div>
      {/* Action Sheet Modal */}
      {actionSheetTarget && createPortal(
          <div className="friend_card_bottomsheet_overlay" onClick={() => setActionSheetTarget(null)}>
              <div className="friend_card_bottomsheet" onClick={(e) => e.stopPropagation()}>
                  <div className="friend_card_bottomsheet_menu">
                      {actionSheetTarget.type === 'OTHER_POST' ? (
                          <>
                              <button type="button" className="friend_card_bottomsheet_item" onClick={handleBlockUser}>
                                  차단하기
                              </button>
                              <div className="friend_card_bottomsheet_divider" />
                              <button type="button" className="friend_card_bottomsheet_item" onClick={handleReport}>
                                  신고하기
                              </button>
                          </>
                      ) : actionSheetTarget.type === 'OTHER_COMMENT' ? (
                          <button type="button" className="friend_card_bottomsheet_item" onClick={handleReport}>
                              신고하기
                          </button>
                      ) : (
                          <>
                              <button type="button" className="friend_card_bottomsheet_item" onClick={handleEdit}>
                                  수정
                              </button>
                              <div className="friend_card_bottomsheet_divider" />
                              <button type="button" className="friend_card_bottomsheet_item" onClick={handleDelete}>
                                  삭제
                              </button>
                          </>
                      )}
                  </div>
              </div>
          </div>,
          document.body
      )}
      <ReportModal
          isOpen={showReportModal}
          onClose={() => { setShowReportModal(false); setReportTarget(null); }}
          onSubmit={handleReportSubmit}
          isSubmitting={isReportSubmitting}
      />
      <ImageGalleryModal
          isOpen={galleryState.open}
          images={galleryState.images}
          initialIndex={galleryState.index}
          onClose={() => setGalleryState(s => ({ ...s, open: false }))}
      />
    </div>
  );
}
