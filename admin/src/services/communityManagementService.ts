import { typedClient } from './typedClient';
import type {
  PageResponse,
  PostListItemResponse,
  PostDetailAdminResponse,
  CommentAdminResponse,
} from '../types/api';

// Generated aliases (Phase 3.11.3).
export type PostListItem = PostListItemResponse;
export type PostDetail = PostDetailAdminResponse;
export type CommentItem = CommentAdminResponse;

export const communityManagementService = {
  async getPosts(params: {
    page?: number;
    size?: number;
    search?: string;
    categoryId?: number;
  }): Promise<PageResponse<PostListItem>> {
    const response = await typedClient.get('/api/v1/admin/community/posts', { params });
    return response.data as unknown as PageResponse<PostListItem>;
  },

  async getPostDetail(postId: number): Promise<PostDetail> {
    const response = await typedClient.getPath(
      '/api/v1/admin/community/posts/{postId}',
      { postId },
    );
    return response.data as PostDetail;
  },

  async updatePost(postId: number, payload: { title?: string; content?: string }): Promise<PostDetail> {
    const body = payload as unknown as Parameters<typeof typedClient.patchPath<'/api/v1/admin/community/posts/{postId}'>>[2];
    const response = await typedClient.patchPath(
      '/api/v1/admin/community/posts/{postId}',
      { postId },
      body,
    );
    return response.data as PostDetail;
  },

  async updateComment(commentId: number, content: string): Promise<CommentItem> {
    const body = { content } as unknown as Parameters<typeof typedClient.patchPath<'/api/v1/admin/community/comments/{commentId}'>>[2];
    const response = await typedClient.patchPath(
      '/api/v1/admin/community/comments/{commentId}',
      { commentId },
      body,
    );
    return response.data as CommentItem;
  },

  async hidePost(postId: number, reason?: string): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/community/posts/{postId}/hide',
      { postId },
      { reason } as unknown as Parameters<typeof typedClient.postPath<'/api/v1/admin/community/posts/{postId}/hide'>>[2],
    );
  },

  async unhidePost(postId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/community/posts/{postId}/unhide',
      { postId },
      undefined,
    );
  },

  async deletePost(postId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/community/posts/{postId}',
      { postId },
    );
  },

  async hideComment(commentId: number, reason?: string): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/community/comments/{commentId}/hide',
      { commentId },
      { reason } as unknown as Parameters<typeof typedClient.postPath<'/api/v1/admin/community/comments/{commentId}/hide'>>[2],
    );
  },

  async deleteComment(commentId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/community/comments/{commentId}',
      { commentId },
    );
  },
};
