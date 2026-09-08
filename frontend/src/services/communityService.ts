// Community Service - API integration for Community features

import apiClient from './api/client';
import { typedClient } from './api/typedClient';

export interface CommunityPost {
  id: number;
  authorId: number;
  categoryId: number;
  categoryName: string;
  title: string;
  content: string;
  authorNickname: string;
  authorProfileUrl?: string;
  authorProfileUrlThumbnail?: string;
  authorProfileUrlViewer?: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  postType: 'GENERAL' | 'QUESTION' | 'INFO';
  visibility: 'PUBLIC' | 'FRIENDS';
  isAnswered?: boolean;
  isLiked: boolean;
  isCommentedByMe: boolean;
  isMine: boolean;
  thumbnailUrl?: string;
  imageUrls: string[];
  imageUrlsThumbnail?: string[];
  imageUrlsMedium?: string[];
  imageUrlsViewer?: string[];
  youtubeUrl?: string | null;
  comments?: CommunityComment[];
  createdAt: string;
}

export interface CommunityCategory {
  id: number;
  name: string;
  slug: string;
  postCount?: number;
}

export interface CommunityComment {
  id: number;
  postId: number;
  authorId?: number;
  content: string;
  authorNickname: string;
  authorProfileUrl?: string;
  authorProfileUrlThumbnail?: string;
  authorProfileUrlViewer?: string;
  isAdopted: boolean;
  createdAt: string;
  parentCommentId: number | null;
  replyTo?: number;
  replies?: CommunityComment[];
  likeCount: number;
  isLiked: boolean;
  isRepliedByMe: boolean;
  depth: number;
  replyCount: number;
  isMine: boolean;
  postTitle?: string;
}

export interface CreatePostRequest {
  categoryId: number;
  title: string;
  content: string;
  postType: 'GENERAL' | 'QUESTION' | 'INFO';
  visibility?: 'PUBLIC' | 'FRIENDS';
  images?: File[];
}

export const communityService = {
  getCategories: async (): Promise<CommunityCategory[]> => {
    try {
      const response = await typedClient.get('/api/v1/community/categories');
      return response.data as unknown as CommunityCategory[];
    } catch (error: unknown) {
      console.error('Failed to fetch categories:', error);
      throw error;
    }
  },

  getPosts: async (categoryId?: number, page = 0, size = 20, sort?: 'latest' | 'trending'): Promise<{ posts: CommunityPost[]; hasMore: boolean; totalCount?: number }> => {
    try {
      const params: Record<string, number | string> = { page, size };
      if (categoryId) params.categoryId = categoryId;
      if (sort === 'trending') params.sort = 'trending';

      const response = await typedClient.get('/api/v1/community/posts', { params });
      const data = response.data as {
        content?: unknown[];
        last?: boolean;
        totalElements?: number;
      };
      const rawPosts = data.content ?? data;

      // Backend DTO (Phase 3 community rename) already emits authorId/authorNickname/
      // authorProfileUrl/isLiked directly — no remap needed.
      const posts = Array.isArray(rawPosts) ? (rawPosts as CommunityPost[]) : [];

      return {
        posts,
        hasMore: !data.last,
        totalCount: data.totalElements,
      };
    } catch (error: unknown) {
      console.error('Failed to fetch posts:', error);
      throw error;
    }
  },

  getPostDetail: async (postId: number): Promise<CommunityPost & { comments: CommunityComment[] }> => {
    try {
      const response = await typedClient.getPath(
        '/api/v1/community/posts/{postId}',
        { postId },
      );
      // Backend DTO (Phase 3 community rename) already emits author*/isLiked directly.
      return response.data as unknown as CommunityPost & { comments: CommunityComment[] };
    } catch {
      throw new Error('게시글을 불러올 수 없습니다.');
    }
  },

  createPost: async (data: CreatePostRequest): Promise<{ success: boolean; postId: number }> => {
    try {
      // 1. Upload images first if any
      const imageUrls: string[] = [];
      if (data.images && data.images.length > 0) {
        // Upload concurrently
        const uploadPromises = data.images.map(file => communityService.uploadFile(file));
        const results = await Promise.all(uploadPromises);
        imageUrls.push(...results);
      }

      // 2. Create post with image URLs
      const response = await typedClient.post('/api/v1/community/posts', {
        categoryId: data.categoryId,
        title: data.title,
        content: data.content,
        postType: data.postType,
        imageUrls: imageUrls,
      } as unknown as Parameters<typeof typedClient.post<'/api/v1/community/posts'>>[1]);
      return { success: true, postId: (response.data as { id: number }).id };
    } catch (error: unknown) {
      console.error('Create post error:', error);
      throw new Error('게시글 등록에 실패했습니다.');
    }
  },

  uploadFile: async (file: File): Promise<string> => {
    try {
      if (file.type.startsWith('image/')) {
        const { compressImage } = await import('../utils/imageCompression');
        file = await compressImage(file);
      }
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', 'community');

      // Multipart — stays on apiClient; typedClient is JSON-only.
      const response = await apiClient.post('/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      return response.data.url;
    } catch (error: unknown) {
      console.error('File upload error:', error);
      throw new Error('이미지 업로드에 실패했습니다.');
    }
  },

  toggleLike: async (postId: number): Promise<{ isLiked: boolean; likeCount: number }> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/community/posts/{postId}/like',
        { postId },
        undefined,
      );
      return response.data as unknown as { isLiked: boolean; likeCount: number };
    } catch {
      throw new Error('좋아요 처리에 실패했습니다.');
    }
  },

  createComment: async (postId: number, content: string, parentId?: number): Promise<CommunityComment> => {
    try {
      const response = await typedClient.postPath(
        '/api/v1/community/posts/{postId}/comments',
        { postId },
        { content, parentCommentId: parentId } as unknown as Parameters<typeof typedClient.postPath<'/api/v1/community/posts/{postId}/comments'>>[2],
      );
      return response.data as unknown as CommunityComment;
    } catch {
      throw new Error('댓글 등록에 실패했습니다.');
    }
  },

  toggleCommentLike: async (commentId: number): Promise<void> => {
    try {
      await typedClient.postPath(
        '/api/v1/community/comments/{commentId}/like',
        { commentId },
        undefined,
      );
    } catch {
      throw new Error('댓글 좋아요 처리에 실패했습니다.');
    }
  },

  deletePost: async (postId: number): Promise<void> => {
    try {
      await typedClient.deletePath('/api/v1/community/posts/{postId}', { postId });
    } catch {
      throw new Error('게시글 삭제에 실패했습니다.');
    }
  },

  deleteComment: async (commentId: number): Promise<void> => {
    try {
      await typedClient.deletePath('/api/v1/community/comments/{commentId}', { commentId });
    } catch {
      throw new Error('댓글 삭제에 실패했습니다.');
    }
  },

  updatePost: async (
    postId: number,
    data: { title: string; content: string; imageUrls?: string[]; categoryId?: number },
  ): Promise<void> => {
    try {
      await typedClient.putPath(
        '/api/v1/community/posts/{postId}',
        { postId },
        data as unknown as Parameters<typeof typedClient.putPath<'/api/v1/community/posts/{postId}'>>[2],
      );
    } catch {
      throw new Error('게시글 수정에 실패했습니다.');
    }
  },

  updateComment: async (commentId: number, content: string): Promise<void> => {
    try {
      await typedClient.putPath(
        '/api/v1/community/comments/{commentId}',
        { commentId },
        { content } as unknown as Parameters<typeof typedClient.putPath<'/api/v1/community/comments/{commentId}'>>[2],
      );
    } catch {
      throw new Error('댓글 수정에 실패했습니다.');
    }
  },

  getMyPosts: async (page = 0, size = 20): Promise<{ posts: CommunityPost[]; hasMore: boolean; totalCount: number }> => {
    try {
      const response = await typedClient.get('/api/v1/community/posts/me', {
        params: { page, size },
      });
      const data = response.data as {
        content?: unknown[];
        last?: boolean;
        totalElements?: number;
      };
      const rawPosts = data.content ?? [];

      // Backend DTO emits author*/isLiked directly (Phase 3 community rename).
      const posts = Array.isArray(rawPosts)
        ? rawPosts.map((p) => ({ ...(p as Record<string, unknown>), isMine: true }) as unknown as CommunityPost)
        : [];

      return {
        posts,
        hasMore: !data.last,
        totalCount: data.totalElements || 0,
      };
    } catch (error: unknown) {
      console.error('Failed to fetch my posts:', error);
      throw error;
    }
  },

  getMyComments: async (page = 0, size = 20): Promise<{ comments: CommunityComment[]; hasMore: boolean; totalCount: number }> => {
    try {
      const response = await typedClient.get('/api/v1/community/comments/me', {
        params: { page, size },
      });
      const data = response.data as {
        content?: unknown[];
        last?: boolean;
        totalElements?: number;
      };
      const rawComments = data.content ?? [];

      // Backend DTO emits author*/isLiked directly; only set UI-local isMine fallback.
      const comments = Array.isArray(rawComments)
        ? rawComments.map((c) => {
            const rec = c as Record<string, unknown>;
            return {
              ...rec,
              isMine: true,
              postTitle: rec.postTitle ?? '',
            } as unknown as CommunityComment;
          })
        : [];

      return {
        comments,
        hasMore: !data.last,
        totalCount: data.totalElements || 0,
      };
    } catch (error: unknown) {
      console.error('Failed to fetch my comments:', error);
      throw error;
    }
  },
};
