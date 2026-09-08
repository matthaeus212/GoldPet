/**
 * Centralized re-export of OpenAPI-generated types with frontend-specific refinements.
 * Source of truth is the backend Kotlin DTOs — see `openapi-typegen-migration.md`.
 *
 * Add more re-exports here as Phase 3 domains migrate. Keep refinements narrow and
 * documented: each override should name the backend constraint it's narrowing.
 */
import type { components } from '../api/schema.d';

export type Schemas = components['schemas'];

/**
 * Open-union wrapper: accepts known values at compile time and tolerates unknown
 * backend values during rolling deploys. Pair with explicit switch+default at the
 * consumption site (or Zod) to detect unknown values at runtime.
 */
export type OpenEnum<T> = T | (string & {});

// --- Auth domain (Phase 3.1) ---

/**
 * Backend declares `gender: String?`, but frontend UI only produces "MALE" | "FEMALE".
 * Narrowing here protects call sites; if backend starts returning other values the
 * consumer will catch it at the boundary. Gender enum upgrade tombstoned for a later
 * phase (requires DB migration of existing user.gender values).
 */
type Gender = 'MALE' | 'FEMALE';

export type AuthResponse = Schemas['AuthResponse'];
export type LoginRequest = Schemas['LoginRequest'];
export type LinkSuggestionInfo = Schemas['LinkSuggestionInfo'];
export type UserInfo = Schemas['UserInfo'];
export type EmailAvailabilityResponse = Schemas['EmailAvailabilityResponse'];
export type RefreshTokenRequest = Schemas['RefreshTokenRequest'];
export type ConfirmLinkRequest = Schemas['ConfirmLinkRequest'];

export type SignupRequest = Omit<Schemas['SignupRequest'], 'gender'> & {
  gender?: Gender;
};

export type SnsSignupRequest = Omit<Schemas['SnsSignupRequest'], 'gender' | 'name'> & {
  name?: string;
  gender?: Gender;
};

// --- Chat domain (Phase 3.3) ---

export type ChatRoomType = Schemas['ChatRoomResponse']['roomType']; // 'DIRECT' | 'GROUP' | 'AI_PET'
export type ChatRoomResponse = Schemas['ChatRoomResponse'];
export type ChatMessageResponse = Schemas['ChatMessageResponse'];
export type MessageType = Schemas['ChatMessageResponse']['messageType']; // uppercase backend enum
export type ParticipantInfo = Schemas['ParticipantInfo'];
export type FileInfo = Schemas['FileInfo'];

// --- Stool Analysis domain (Phase 3.4) ---

export type AnalysisStatus = Schemas['StoolAnalysisResponse']['status']; // 'PENDING'|'ANALYZING'|'COMPLETED'|'FAILED'
export type StoolAnalysisResponse = Schemas['StoolAnalysisResponse'];
export type MonthlyScore = Schemas['MonthlyScore'];
export type HealthTrendResponse = Schemas['HealthTrendResponse'];

// --- Notification domain (Phase 3.5) ---

export type NotificationType = Schemas['NotificationResponse']['type'];
// e.g. 'MATCH'|'MESSAGE'|'LIKE'|'COMMENT'|'FOLLOW'|'WALK'|'NOTICE'|'EVENT'|'SYSTEM'|'REPORT_RESOLVED'
export type NotificationResponse = Schemas['NotificationResponse'];
export type PageNotificationResponse = Schemas['PageNotificationResponse'];
export type UnreadCountResponse = Schemas['UnreadCountResponse'];

// --- Report domain (Phase 3.6) ---

export type ReportType = Schemas['CreateReportRequest']['type']; // 'POST'|'COMMENT'|'USER'|'CHAT'|'COURSE'|'WALK_SPOT'
export type ReportStatus = Schemas['ReportResponse']['status']; // 'PENDING'|'RESOLVED'|'DISMISSED'
export type CreateReportRequest = Schemas['CreateReportRequest'];
export type ReportResponse = Schemas['ReportResponse'];

// --- Course domain (Phase 3.7, completed in Phase 4 sweep) ---

export type CourseDifficulty = Schemas['CourseResponse']['difficulty']; // 'EASY'|'MODERATE'|'HARD'
export type CourseSpotType = Schemas['CourseSpotResponse']['type']; // 10-value enum
export type CourseResponse = Schemas['CourseResponse'];
export type CourseSpotResponse = Schemas['CourseSpotResponse'];
export type CourseSpotDto = Schemas['CourseSpotDto'];
export type CourseCommentResponse = Schemas['CourseCommentResponse'];
export type CourseLikeStatusResponse = Schemas['CourseLikeStatusResponse'];
export type CreateCourseRequest = Schemas['CreateCourseRequest'];
export type UpdateCourseRequest = Schemas['UpdateCourseRequest'];
export type CreateCourseCommentRequest = Schemas['CreateCourseCommentRequest'];
export type UpdateCourseCommentRequest = Schemas['UpdateCourseCommentRequest'];

// --- Notice domain (Phase 4 sweep) ---
export type AppNoticeResponse = Schemas['AppNoticeResponse'];
export type NoticeType = Schemas['AppNoticeResponse']['type']; // POPUP_MODAL|MAINTENANCE|EVENT_BANNER|NOTICE

// --- User domain (Phase 3.8, frontend-side /users/me family) ---

export type UserResponse = Schemas['UserResponse'];
// Backend schema name is `UserStatsResponse`; expose as `UserStats` for local ergonomics.
export type UserStats = Schemas['UserStatsResponse'];
// Note: UpdateProfileRequest hand-rolled in userService.ts (frontend adds imageUrls,
// backend UserProfileUpdateRequest adds mainLocation*; they're not a pure mirror).

// --- Walk domain (Phase 3.9) ---

export type WalkSpotType = Schemas['WalkSpotDto']['type']; // 'PEE'|'POOP'|'PHOTO'|'PROBLEM'|'OTHER'
export type WalkSpotDto = Schemas['WalkSpotDto'];
export type WalkResponse = Schemas['WalkResponse'];
export type WalkPhotoResponse = Schemas['WalkPhotoResponse'];
export type WalkStatsResponse = Schemas['WalkStatsResponse'];
export type CreateWalkRequest = Schemas['CreateWalkRequest'];
export type PhotoMintResponse = Schemas['PhotoMintResponse'];
export type UpdateSpotNoteRequest = Schemas['UpdateSpotNoteRequest'];
export type UpdateSpotVisibilityRequest = Schemas['UpdateSpotVisibilityRequest'];
export type PageWalkResponse = Schemas['PageWalkResponse'];
export type PageWalkPhotoResponse = Schemas['PageWalkPhotoResponse'];

// Walk ranking — endpoint split (`/walks/ranking` + `/walks/ranking/calendar`)
// resolved the prior `ResponseEntity<Any>` gap.
export type WalkRankingResponse = Schemas['WalkRankingResponse'];
export type WalkCoupleRankingResponse = Schemas['WalkCoupleRankingResponse'];

// --- Gamification domain (Phase 3.10) ---

export type BadgeResponse = Schemas['BadgeResponse'];

// --- Emoticon domain (Phase 3.10) ---

export type EmoticonResponse = Schemas['EmoticonResponse'];

// --- Home domain (Phase 3.10, partial) ---
// Only the recent-walk slice maps cleanly. `HomeResponse.recommendations` is a
// `FriendResponse[]` that the frontend reshapes into its own `HomeRecommendation`
// display model — keep that conversion local to homeService.ts.
export type HomeRecentWalkResponse = Schemas['HomeRecentWalkResponse'];
export type HomeTodayWalkResponse = Schemas['HomeTodayWalkResponse'];
export type HomeResponse = Schemas['HomeResponse'];

// --- Friend domain (Phase 3.10) ---
// Backend exposes `petGender`/`status` as plain `string`; the frontend narrows to
// known unions at the call site (FriendCard etc.). Keep alias + cast as needed.
export type FriendResponse = Schemas['FriendResponse'];
// SpeciesResponse re-export moved next to Pet domain (used by both friend selector
// and pet management).

// --- Gold domain (Phase 3.10) ---
// Backend types are named `ChargeRequest`/`SpendRequest` (not `ChargeGoldRequest`).
export type GoldBalanceResponse = Schemas['GoldBalanceResponse'];
export type GoldProductResponse = Schemas['GoldProductResponse'];
export type TransactionResponse = Schemas['TransactionResponse'];
export type ChargeRequest = Schemas['ChargeRequest'];
export type SpendRequest = Schemas['SpendRequest'];
export type GoldTransactionType = Schemas['TransactionResponse']['type']; // CHARGE|SPEND|REWARD|REFUND|ADMIN_ADJUST
export type GoldTransactionStatus = Schemas['TransactionResponse']['status']; // PENDING|COMPLETED|FAILED|CANCELLED

// --- Pet domain (Phase 3.10) ---
export type PetResponse = Schemas['PetResponse'];
export type SpeciesResponse = Schemas['SpeciesResponse'];
export type BreedResponse = Schemas['BreedResponse'];
export type CreatePetRequest = Schemas['CreatePetRequest'];
export type UpdatePetRequest = Schemas['UpdatePetRequest'];
export type AttributeOption = Schemas['AttributeOption'];
export type PetAttributeResponse = Schemas['PetAttributeResponse'];

// --- Checkin domain (Phase 3.10) ---
export type PlaceResponse = Schemas['PlaceResponse'];
export type CheckInResponse = Schemas['CheckInResponse'];
export type CreateCheckInRequest = Schemas['CreateCheckInRequest'];
export type CreatePlaceRequest = Schemas['CreatePlaceRequest'];
export type PlaceCategory = Schemas['PlaceResponse']['category']; // PARK|CAFE|RESTAURANT|...

// --- AI Profile domain (Phase 3.10) ---
export type AIStyleOption = Schemas['AIStyleOption'];
export type CreateAIProfileRequest = Schemas['CreateAIProfileRequest'];
export type AIProfileRequestResponse = Schemas['AIProfileRequestResponse'];
export type AIRequestType = Schemas['CreateAIProfileRequest']['type']; // IMAGE|VIDEO|AVATAR
export type AIRequestStatus = Schemas['AIProfileRequestResponse']['status']; // PENDING|PROCESSING|COMPLETED|FAILED|REFUNDED
export type ApplyProfileRequest = Schemas['ApplyProfileRequest'];
