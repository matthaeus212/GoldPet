import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { communityService } from '../../services/communityService';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { EmptyState } from '../../components/common/EmptyState';
import { GpImage } from '../../components/common/GpImage';
import './MyPostsPage.css';

const AUTHOR_FALLBACK = '/assets/images/common/profile_none_img.svg';

type Tab = 'posts' | 'comments';

const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const hours = Math.floor(diff / (1000 * 60 * 60));

  if (hours < 1) return '방금 전';
  if (hours < 24) return `${hours}시간 전`;
  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
};

const getImageUrl = (url: string) =>
  url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'}${url}`;

const ProhibitionIcon = () => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary)" strokeWidth="1.5">
    <circle cx="12" cy="12" r="10" />
    <path d="M4.93 4.93l14.14 14.14" />
  </svg>
);

const HeartIcon = ({ filled }: { filled?: boolean }) => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
    <path
      d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
      fill={filled ? 'var(--color-primary)' : 'none'}
      stroke="var(--color-primary)"
      strokeWidth="1.5"
    />
  </svg>
);

const CommentIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
    <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z" fill="var(--color-primary)" />
    <line x1="7" y1="9" x2="17" y2="9" stroke="white" strokeWidth="1.5" strokeLinecap="round" />
    <line x1="7" y1="12" x2="14" y2="12" stroke="white" strokeWidth="1.5" strokeLinecap="round" />
    <line x1="7" y1="15" x2="11" y2="15" stroke="white" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

export default function MyPostsPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<Tab>('posts');

  const { data: postsData, isLoading: postsLoading } = useQuery({
    queryKey: ['community', 'myPosts'],
    queryFn: () => communityService.getMyPosts(),
    enabled: activeTab === 'posts',
  });

  const { data: commentsData, isLoading: commentsLoading } = useQuery({
    queryKey: ['community', 'myComments'],
    queryFn: () => communityService.getMyComments(),
    enabled: activeTab === 'comments',
  });

  return (
    <SubPageLayout title="내가 쓴 글" onBack={() => navigate('/mypage')}>
      <div id="myPostsContainer">
        {/* Tabs */}
        <div className="mp-tabs">
          <div className="mp-tabs-underline" />
          <button
            type="button"
            className={`mp-tab${activeTab === 'posts' ? ' active' : ''}`}
            onClick={() => setActiveTab('posts')}
          >
            작성한 글
          </button>
          <button
            type="button"
            className={`mp-tab${activeTab === 'comments' ? ' active' : ''}`}
            onClick={() => setActiveTab('comments')}
          >
            작성한 댓글
          </button>
        </div>

        {/* Posts Tab */}
        {activeTab === 'posts' && (
          <div className="mp-content">
            {postsLoading ? (
              <div className="mp-loading">로딩 중...</div>
            ) : !postsData?.posts.length ? (
              <EmptyState icon={<ProhibitionIcon />} text="작성한 글이 없어요" />
            ) : (
              <ul className="mp-post-list">
                {postsData.posts.map((post) => (
                  <li key={post.id} className="mp-post-item" onClick={() => navigate(`/community/${post.id}`)}>
                    <div className="mp-post-profile">
                      {post.authorProfileUrl ? (
                        <GpImage
                          className="mp-post-profile-img"
                          src={post.authorProfileUrl}
                          thumbnailSrc={post.authorProfileUrlThumbnail}
                          viewerSrc={post.authorProfileUrlViewer}
                          variant="thumbnail"
                          alt=""
                        />
                      ) : (
                        <img className="mp-post-profile-img" src={AUTHOR_FALLBACK} alt="" />
                      )}
                      <div className="mp-post-profile-txt">
                        <strong>{post.authorNickname}</strong>
                        <span>{formatDate(post.createdAt)}</span>
                      </div>
                    </div>

                    <div className="mp-post-txt">
                      <p className="mp-post-title">{post.title}</p>
                      <p className="mp-post-body">{post.content}</p>
                    </div>

                    {post.imageUrls && post.imageUrls.length > 0 && (
                      <div className="mp-post-img">
                        <img src={getImageUrl(post.imageUrls[0])} alt="" />
                      </div>
                    )}

                    <div className="mp-post-reactions">
                      <div className="mp-reaction-item">
                        <HeartIcon filled={post.isLiked} />
                        <span>{post.likeCount}</span>
                      </div>
                      <div className="mp-reaction-item">
                        <CommentIcon />
                        <span>{post.commentCount}</span>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {/* Comments Tab */}
        {activeTab === 'comments' && (
          <div className="mp-content">
            {commentsLoading ? (
              <div className="mp-loading">로딩 중...</div>
            ) : !commentsData?.comments.length ? (
              <EmptyState icon={<ProhibitionIcon />} text="작성한 댓글이 없어요" />
            ) : (
              <ul className="mp-post-list">
                {commentsData.comments.map((comment) => (
                  <li
                    key={comment.id}
                    className="mp-post-item"
                    onClick={() => navigate(`/community/${comment.postId}`)}
                  >
                    <div className="mp-post-profile">
                      {comment.authorProfileUrl ? (
                        <GpImage
                          className="mp-post-profile-img"
                          src={comment.authorProfileUrl}
                          thumbnailSrc={comment.authorProfileUrlThumbnail}
                          viewerSrc={comment.authorProfileUrlViewer}
                          variant="thumbnail"
                          alt=""
                        />
                      ) : (
                        <img className="mp-post-profile-img" src={AUTHOR_FALLBACK} alt="" />
                      )}
                      <div className="mp-post-profile-txt">
                        <strong>{comment.authorNickname}</strong>
                        <span>{formatDate(comment.createdAt)}</span>
                      </div>
                    </div>

                    <div className="mp-post-txt">
                      <p className="mp-post-title">{(comment as unknown as Record<string, unknown>).postTitle as string || '게시글'}</p>
                      <p className="mp-post-body">{comment.content}</p>
                    </div>

                    <div className="mp-post-reactions">
                      <div className="mp-reaction-item">
                        <HeartIcon filled={comment.isLiked} />
                        <span>{comment.likeCount}</span>
                      </div>
                      <div className="mp-reaction-item">
                        <CommentIcon />
                        <span>{comment.replyCount}</span>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </SubPageLayout>
  );
}
