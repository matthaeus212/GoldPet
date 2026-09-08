import type { components } from '../api/schema.d';

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

/**
 * Open-union wrapper: accepts known values at compile time and tolerates unknown
 * backend values during rolling deploys. Pair with explicit switch+default at the
 * consumption site (or Zod) to detect unknown values at runtime.
 */
export type OpenEnum<T> = T | (string & {});

export type Schemas = components['schemas'];

// OpenAPI-derived enum types. Source of truth is backend Kotlin enums.
export type UserStatus = Schemas['UserListItemResponse']['status'];

// --- Report domain (Phase 3.6) ---
export type ReportType = Schemas['CreateReportRequest']['type']; // POST|COMMENT|USER|CHAT|COURSE|WALK_SPOT
export type ReportStatus = Schemas['ReportAdminResponse']['status']; // PENDING|RESOLVED|DISMISSED
export type ReportActionType = Schemas['ResolveReportRequest']['actionType']; // DELETE_POST|HIDE_POST|...
export type ReportAdminResponse = Schemas['ReportAdminResponse'];

// --- Walk admin domain (Phase 3.9) ---
export type WalkAdminResponse = Schemas['WalkAdminResponse'];
export type WalkDetailResponse = Schemas['WalkDetailResponse'];
export type WalkSpotAdminDto = Schemas['WalkSpotAdminDto'];
export type WalkStatsDetailResponse = Schemas['WalkStatsDetailResponse'];
export type WalkRankingAdminResponse = Schemas['WalkRankingAdminResponse'];
export type BestWalkCoupleResponse = Schemas['BestWalkCoupleResponse'];
export type BestWalkCoupleRequest = Schemas['BestWalkCoupleRequest'];

// --- Content domain (Phase 3.11.1) — admin content management ---

// AI Profile admin
export type AIRequestAdminResponse = Schemas['AIRequestAdminResponse'];
export type AIStyleAdminResponse = Schemas['AIStyleAdminResponse'];
export type AIStyleAdminRequest = Schemas['AIStyleAdminRequest'];
export type AIProfileStatsResponse = Schemas['AIProfileStatsResponse'];

// Emoticon admin
export type EmoticonResponse = Schemas['EmoticonResponse'];
export type EmoticonPackResponse = Schemas['EmoticonPackResponse'];
export type CreateEmoticonRequest = Schemas['CreateEmoticonRequest'];
export type UpdateEmoticonRequest = Schemas['UpdateEmoticonRequest'];

// Gamification (admin uses same Badge as frontend)
export type BadgeResponse = Schemas['BadgeResponse'];

// AI loading tip
export type AILoadingTipResponse = Schemas['AILoadingTipResponse'];
export type CreateLoadingTipRequest = Schemas['CreateLoadingTipRequest'];
export type UpdateLoadingTipRequest = Schemas['UpdateLoadingTipRequest'];

// Pet breed
export type BreedResponse = Schemas['BreedResponse'];
export type SpeciesResponse = Schemas['SpeciesResponse'];
export type CreateBreedRequest = Schemas['CreateBreedRequest'];
export type UpdateBreedRequest = Schemas['UpdateBreedRequest'];

// Pet attribute (backend uses single PetAttributeRequest for both create + update)
export type PetAttributeResponse = Schemas['PetAttributeResponse'];
export type AttributeOption = Schemas['AttributeOption'];
export type PetAttributeRequest = Schemas['PetAttributeRequest'];

// --- User & Auth & Report admin (Phase 3.11.2) ---

// User management
export type UserListItemResponse = Schemas['UserListItemResponse'];
export type UserDetailResponse = Schemas['UserDetailResponse'];

// Admin auth
export type AdminUserDto = Schemas['AdminUserDto'];
export type AdminLoginResponse = Schemas['AdminLoginResponse'];
export type Setup2faResponse = Schemas['Setup2faResponse'];

// --- Community / Chat / Course admin (Phase 3.11.3) ---

// Community management
export type PostListItemResponse = Schemas['PostListItemResponse'];
export type PostDetailAdminResponse = Schemas['PostDetailAdminResponse'];
export type CommentAdminResponse = Schemas['CommentAdminResponse'];

// Chat management
export type ChatRoomAdminResponse = Schemas['ChatRoomAdminResponse'];
export type ChatMessageAdminResponse = Schemas['ChatMessageAdminResponse'];

// Course management
export type CourseListItemResponse = Schemas['CourseListItemResponse'];
export type CourseDetailAdminResponse = Schemas['CourseDetailAdminResponse'];
export type CourseSpotAdminResponse = Schemas['CourseSpotAdminResponse'];
export type CourseCommentAdminResponse = Schemas['CourseCommentAdminResponse'];

// --- System / Operations admin (Phase 3.11.4) ---

// System
export type SystemInfoResponse = Schemas['SystemInfoResponse'];
export type AppConfigResponse = Schemas['AppConfigResponse'];

// Cache
export type CacheInfoResponse = Schemas['CacheInfoResponse'];
export type CacheClearResult = Schemas['CacheClearResult'];
export type CacheClearLogResponse = Schemas['CacheClearLogResponse'];

// Dashboard
export type DashboardStatsResponse = Schemas['DashboardStatsResponse'];
export type DashboardChartsResponse = Schemas['DashboardChartsResponse'];
export type RecentActivityItem = Schemas['RecentActivityItem'];

// LBS
export type LBSStatsResponse = Schemas['LBSStatsResponse'];

// Notice
export type AppNoticeResponse = Schemas['AppNoticeResponse'];
export type CreateAppNoticeRequest = Schemas['CreateAppNoticeRequest'];
export type UpdateAppNoticeRequest = Schemas['UpdateAppNoticeRequest'];

// Economy
export type EconomyStatsResponse = Schemas['EconomyStatsResponse'];
export type GoldProductAdminResponse = Schemas['GoldProductAdminResponse'];
export type GoldProductCreateRequest = Schemas['GoldProductCreateRequest'];
export type GoldProductUpdateRequest = Schemas['GoldProductUpdateRequest'];
export type TransactionResponse = Schemas['TransactionResponse'];
export type TransactionAdminResponse = Schemas['TransactionAdminResponse'];

// Stool analysis admin
export type StoolAnalysisStatsResponse = Schemas['StoolAnalysisStatsResponse'];
export type DailyCount = Schemas['DailyCount'];
export type AdminStoolAnalysisItem = Schemas['AdminStoolAnalysisItem'];

// Note: AdminUser (system), Place/LBSStats.topSpots (lbs), DataItem (data),
// FileUploadResponse (file), NotificationTemplate / SendNotificationRequest /
// DeliveryLog / Banner / Campaign (marketing), ChartDataPoint (dashboard) are
// not in the spec — kept as manual interfaces in their service files.
