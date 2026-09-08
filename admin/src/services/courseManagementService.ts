import { typedClient } from './typedClient';
import type {
  PageResponse,
  CourseListItemResponse,
  CourseDetailAdminResponse,
  CourseSpotAdminResponse,
  CourseCommentAdminResponse,
} from '../types/api';

// Generated aliases (Phase 3.11.3).
export type CourseListItem = CourseListItemResponse;
export type CourseSpotItem = CourseSpotAdminResponse;
export type CourseCommentItem = CourseCommentAdminResponse;
export type CourseDetail = CourseDetailAdminResponse;

export const courseManagementService = {
  async getCourses(params: {
    page: number;
    size: number;
    search?: string;
    difficulty?: string;
    isPublished?: boolean;
  }): Promise<PageResponse<CourseListItem>> {
    const { data } = await typedClient.get('/api/v1/admin/courses', { params });
    return data as unknown as PageResponse<CourseListItem>;
  },

  async getCourseDetail(courseId: number): Promise<CourseDetail> {
    const { data } = await typedClient.getPath(
      '/api/v1/admin/courses/{courseId}',
      { courseId },
    );
    return data as CourseDetail;
  },

  async hideCourse(courseId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/courses/{courseId}/hide',
      { courseId },
      undefined,
    );
  },

  async unhideCourse(courseId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/courses/{courseId}/unhide',
      { courseId },
      undefined,
    );
  },

  async deleteCourse(courseId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/courses/{courseId}',
      { courseId },
    );
  },

  async hideComment(commentId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/courses/comments/{commentId}/hide',
      { commentId },
      undefined,
    );
  },

  async unhideComment(commentId: number): Promise<void> {
    await typedClient.postPath(
      '/api/v1/admin/courses/comments/{commentId}/unhide',
      { commentId },
      undefined,
    );
  },

  async deleteComment(commentId: number): Promise<void> {
    await typedClient.deletePath(
      '/api/v1/admin/courses/comments/{commentId}',
      { commentId },
    );
  },
};
