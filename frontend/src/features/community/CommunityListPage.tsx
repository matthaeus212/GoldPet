import { useState, Fragment } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { communityService } from '../../services/communityService';
import { systemService } from '../../services/systemService';
import { CACHE_TIME } from '../../config/queryConfig';
import { GpImage } from '../../components/common/GpImage';
import { PlacementBanner } from '../../components/common/PlacementBanner';
import { useHandleProfileTap } from '../../hooks/useHandleProfileTap';
import './CommunityPage.css';

const AUTHOR_FALLBACK = '/assets/images/common/profile_none_img.svg';

export default function CommunityListPage() {
  const navigate = useNavigate();
  const handleProfileTap = useHandleProfileTap();
  const [selectedCategory, setSelectedCategory] = useState<number | undefined>(undefined);
  const [sortMode, setSortMode] = useState<'latest' | 'trending'>('latest');

  const { data: categories } = useQuery({
    queryKey: ['community', 'categories'],
    queryFn: communityService.getCategories,
    staleTime: 1000 * 60 * 60, // 1 hour - categories rarely change
  });

  const { data: postsData, isLoading } = useQuery({
    queryKey: ['community', 'posts', selectedCategory, sortMode],
    queryFn: () => communityService.getPosts(selectedCategory, 0, 20, sortMode),
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: totalCountData } = useQuery({
    queryKey: ['community', 'totalCount'],
    queryFn: () => communityService.getPosts(undefined, 0, 1),
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: publicSettings } = useQuery({
      queryKey: ['system', 'publicSettings'],
      queryFn: systemService.getPublicSettings,
      staleTime: 1000 * 60 * 5, // Cache for 5 minutes
  });

  const truncateContent = (content: string, maxLength: number) => {
      if (content.length <= maxLength) return content;
      return content.substring(0, maxLength) + '...';
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const hours = Math.floor(diff / (1000 * 60 * 60));
    
    if (hours < 1) return '방금 전';
    if (hours < 24) return `${hours}시간 전`;
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
  };

  /* Mutation for Like in List */
  const queryClient = useQueryClient();
  const likeMutation = useMutation({
    mutationFn: (postId: number) => communityService.toggleLike(postId),
    onSuccess: () => {
      // Invalidate posts list query to refresh like status/count
      queryClient.invalidateQueries({ queryKey: ['community', 'posts'] });
    },
  });

  const handleLike = (e: React.MouseEvent, postId: number) => {
    e.stopPropagation();
    likeMutation.mutate(postId);
  };

  return (
    <div id="communityContainer">
      {/* Tabs */}
      <div className="community_tab">
        <ul>
          <li>
            <button 
              type="button" 
              className={selectedCategory === undefined ? 'active' : ''}
              onClick={() => setSelectedCategory(undefined)}
            >
              전체 <span>{totalCountData?.totalCount || 0}</span>
            </button>
          </li>
          {categories?.map((cat) => (
            <li key={cat.id}>
              <button 
                type="button"
                className={selectedCategory === cat.id ? 'active' : ''}
                onClick={() => setSelectedCategory(cat.id)}
              >
                {cat.name} <span>{cat.postCount || 0}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>

      {/* Sort toggle: 최신순 / 인기순(트렌딩) */}
      <div style={{ display: 'flex', gap: 8, padding: '10px 16px 4px' }}>
        {([['latest', '최신순'], ['trending', '인기순']] as const).map(([mode, label]) => {
          const active = sortMode === mode;
          return (
            <button
              key={mode}
              type="button"
              onClick={() => setSortMode(mode)}
              style={{
                padding: '5px 14px',
                borderRadius: 16,
                fontSize: 13,
                cursor: 'pointer',
                border: 'none',
                background: active ? '#2b2b2b' : '#f2f2f2',
                color: active ? '#fff' : '#888',
                fontWeight: active ? 700 : 400,
              }}
            >
              {label}
            </button>
          );
        })}
      </div>

      {/* Post List */}
      {/* Post List */}
      <div className="post_list">
        {isLoading ? (
          <div style={{ padding: '20px', textAlign: 'center' }}>로딩 중...</div>
        ) : postsData?.posts.length === 0 ? (
          <div className="comment_none">
             <img src="/assets/images/common/comment_icon03.svg" alt="" />
             등록된 게시글이 없어요.
          </div>
        ) : (
          <ul className="community_list">
            {postsData?.posts.map((post, idx) => {
              const allPosts = postsData.posts;
              const insertIdx = allPosts.length > 3 ? 2 : allPosts.length - 1;
              return (
              <Fragment key={post.id}>
              <li onClick={() => navigate(`/community/${post.id}`)}>
                <div
                  className="list_profile"
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
                              decoding="async"
                          />
                      ) : (
                          <img src={AUTHOR_FALLBACK} alt="" decoding="async" />
                      )}
                  </div>
                  <div className="txt_wrap">
                      <strong>{post.authorNickname}</strong>
                      <p>{formatDate(post.createdAt)}</p>
                  </div>
                </div>
                
                <strong className="list_tit">{post.title}</strong>
                <p className="list_txt">
                    {truncateContent(post.content, publicSettings?.communityPostPreviewLength || 100)}
                </p>
                
                {post.imageUrls && post.imageUrls.length > 0 && (() => {
                    const rawUrl = post.imageUrls[0];
                    const rawThumb = post.imageUrlsThumbnail?.[0];
                    const normalize = (u: string | undefined | null) =>
                        !u || u.startsWith('http') ? u : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'}${u}`;
                    const fullUrl = normalize(rawUrl)!;
                    const thumbUrl = normalize(rawThumb);
                    return (
                        <div className="list_img">
                            <GpImage
                                src={fullUrl}
                                thumbnailSrc={thumbUrl}
                                variant="thumbnail"
                                alt=""
                                loading="lazy"
                                decoding="async"
                            />
                        </div>
                    );
                })()}

                {/* Like & Comment Counts */}
                <div className="like_comment_count">
                    <div className="count_wrap">
                        <div 
                          className={`icon_img like_icon ${post.isLiked ? 'active' : ''}`}
                          onClick={(e) => handleLike(e, post.id)}
                        ></div> 
                        {post.likeCount}
                    </div>
                    <div className="count_wrap">
                        <div 
                          className={`icon_img comment_icon ${post.isCommentedByMe ? 'active' : ''}`}
                          onClick={(e) => e.stopPropagation()}
                        ></div> 
                        {post.commentCount}
                    </div>
                </div>
              </li>
              {idx === insertIdx && (
                <li className="community_ad_slot">
                  <PlacementBanner placement="COMMUNITY" />
                </li>
              )}
              </Fragment>
              );
            })}
          </ul>
        )}
      </div>

      {/* Write Button */}
      {/* Write Button */}
      <button type="button" id="writeBtn" onClick={() => navigate('/community/write', { state: { initialCategoryId: selectedCategory } })}>글쓰기</button>
    </div>
  );
}
