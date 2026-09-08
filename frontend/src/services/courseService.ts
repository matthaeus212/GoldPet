// Course Service - API integration for Walk Course Platform

import { typedClient } from './api/typedClient';
import type {
  CourseDifficulty,
  CourseSpotType,
  CourseSpotDto,
  CourseSpotResponse,
  CourseResponse,
  CreateCourseRequest,
  UpdateCourseRequest,
  CourseCommentResponse,
  CreateCourseCommentRequest,
  UpdateCourseCommentRequest,
  CourseLikeStatusResponse,
} from '../types/api';

// ── Types ── (Phase 4 sweep — generated re-exports)

export type {
  CourseDifficulty,
  CourseSpotType,
  CourseSpotDto,
  CourseSpotResponse,
  CourseResponse,
  CreateCourseRequest,
  UpdateCourseRequest,
  CourseCommentResponse,
  CreateCourseCommentRequest,
  UpdateCourseCommentRequest,
  CourseLikeStatusResponse,
};

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  last: boolean;
  first: boolean;
}

// ── Service ──

export const courseService = {
  // ── Course CRUD ──

  getCourseDetail: async (courseId: number): Promise<CourseResponse> => {
    const response = await typedClient.getPath('/api/v1/courses/{courseId}', { courseId });
    return response.data as CourseResponse;
  },

  createCourse: async (request: CreateCourseRequest): Promise<CourseResponse> => {
    const response = await typedClient.post('/api/v1/courses', request);
    return response.data as CourseResponse;
  },

  updateCourse: async (courseId: number, request: UpdateCourseRequest): Promise<CourseResponse> => {
    const response = await typedClient.putPath('/api/v1/courses/{courseId}', { courseId }, request);
    return response.data as CourseResponse;
  },

  deleteCourse: async (courseId: number): Promise<void> => {
    await typedClient.deletePath('/api/v1/courses/{courseId}', { courseId });
  },

  // ── Course Search & List ──

  searchNearbyCourses: async (params: {
    lat: number;
    lng: number;
    radiusMeters?: number;
    difficulty?: CourseDifficulty;
    page?: number;
    size?: number;
  }): Promise<PageResponse<CourseResponse>> => {
    const response = await typedClient.get('/api/v1/courses/search', { params });
    return response.data as PageResponse<CourseResponse>;
  },

  getPopularCourses: async (params?: {
    region?: string;
    difficulty?: CourseDifficulty;
    sortBy?: string;
    page?: number;
    size?: number;
  }): Promise<PageResponse<CourseResponse>> => {
    const response = await typedClient.get('/api/v1/courses/popular', { params });
    return response.data as PageResponse<CourseResponse>;
  },

  getMyCourses: async (params?: {
    page?: number;
    size?: number;
  }): Promise<PageResponse<CourseResponse>> => {
    const response = await typedClient.get('/api/v1/courses/my', { params });
    return response.data as PageResponse<CourseResponse>;
  },

  // ── Course from Walk ──

  createCourseFromWalk: async (walkId: number, request: CreateCourseRequest): Promise<CourseResponse> => {
    const response = await typedClient.postPath(
      '/api/v1/courses/from-walk/{walkId}',
      { walkId },
      request,
    );
    return response.data as CourseResponse;
  },

  // ── Like ──

  toggleCourseLike: async (courseId: number): Promise<CourseLikeStatusResponse> => {
    const response = await typedClient.postPath(
      '/api/v1/courses/{courseId}/likes',
      { courseId },
      undefined,
    );
    return response.data as CourseLikeStatusResponse;
  },

  getCourseLikeStatus: async (courseId: number): Promise<CourseLikeStatusResponse> => {
    const response = await typedClient.getPath(
      '/api/v1/courses/{courseId}/likes/status',
      { courseId },
    );
    return response.data as CourseLikeStatusResponse;
  },

  // ── Comments ──

  getCourseComments: async (courseId: number, params?: {
    page?: number;
    size?: number;
  }): Promise<PageResponse<CourseCommentResponse>> => {
    const response = await typedClient.getPath(
      '/api/v1/courses/{courseId}/comments',
      { courseId },
      { params },
    );
    return response.data as PageResponse<CourseCommentResponse>;
  },

  createCourseComment: async (courseId: number, request: CreateCourseCommentRequest): Promise<CourseCommentResponse> => {
    const response = await typedClient.postPath(
      '/api/v1/courses/{courseId}/comments',
      { courseId },
      request,
    );
    return response.data as CourseCommentResponse;
  },

  updateCourseComment: async (courseId: number, commentId: number, request: UpdateCourseCommentRequest): Promise<CourseCommentResponse> => {
    const response = await typedClient.putPath(
      '/api/v1/courses/{courseId}/comments/{commentId}',
      { courseId, commentId },
      request,
    );
    return response.data as CourseCommentResponse;
  },

  deleteCourseComment: async (courseId: number, commentId: number): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/courses/{courseId}/comments/{commentId}',
      { courseId, commentId },
    );
  },

  toggleCommentLike: async (courseId: number, commentId: number): Promise<boolean> => {
    const response = await typedClient.postPath(
      '/api/v1/courses/{courseId}/comments/{commentId}/like',
      { courseId, commentId },
      undefined,
    );
    return response.data as unknown as boolean;
  },
};
