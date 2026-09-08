import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { LazyMotion, domAnimation } from 'motion/react';
import { queryClient } from './lib/queryClient';
import ErrorBoundary from './components/common/ErrorBoundary';
// import { useAuthStore } from './stores/authStore';
import './App.css';

// Non-page imports (static)
import { AlertProvider } from './contexts/AlertContext';
import { SplashPage } from './features/splash/SplashPage';
import ScrollToTop from './components/common/ScrollToTop';
import { PullToRefresh } from './components/common/PullToRefresh';
import { PushNotificationHandler } from './components/common/PushNotificationHandler';
import { AuthExpiredHandler } from './components/common/AuthExpiredHandler';
import { WalkCompletedHandler } from './components/common/WalkCompletedHandler';
import { TypeALayout } from './components/layout/TypeALayout';
import { TypeBLayout } from './components/layout/TypeBLayout';
import { LoadingProvider } from './contexts/LoadingContext';
import { ToastProvider } from './contexts/ToastContext';
import { useAppHeight } from './hooks/useAppHeight';
import MaintenanceGuard from './components/common/MaintenancePage';
import { ShareRedirectBridge } from './features/share/ShareRedirect';
import { PAYMENT_ENABLED } from './config/featureFlags';

// Named-export pages (adapter pattern)
const OnboardingPage = lazy(() => import('./features/onboarding/OnboardingPage').then(m => ({ default: m.OnboardingPage })));
const PermissionsPage = lazy(() => import('./features/permissions/PermissionsPage').then(m => ({ default: m.PermissionsPage })));
const BenefitsPage = lazy(() => import('./features/benefits/BenefitsPage').then(m => ({ default: m.BenefitsPage })));
const LoginPage = lazy(() => import('./features/auth/LoginPage').then(m => ({ default: m.LoginPage })));
const TermsAgreementPage = lazy(() => import('./features/auth/TermsAgreementPage').then(m => ({ default: m.TermsAgreementPage })));
const TermsDetailPage = lazy(() => import('./features/auth/TermsDetailPage').then(m => ({ default: m.TermsDetailPage })));
const SignupPage = lazy(() => import('./features/auth/SignupPage').then(m => ({ default: m.SignupPage })));
const FindUsernamePage = lazy(() => import('./features/auth/FindUsernamePage').then(m => ({ default: m.FindUsernamePage })));
const ResetPasswordPage = lazy(() => import('./features/auth/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })));
const SignupCompletePage = lazy(() => import('./features/auth/SignupCompletePage').then(m => ({ default: m.SignupCompletePage })));
const SnsSignupPage = lazy(() => import('./features/auth/SnsSignupPage').then(m => ({ default: m.SnsSignupPage })));
const LoginSuccessPage = lazy(() => import('./features/auth/LoginSuccessPage').then(m => ({ default: m.LoginSuccessPage })));
const FriendFindPage = lazy(() => import('./features/friend/FriendFindPage').then(m => ({ default: m.FriendFindPage })));
const FriendsListPage = lazy(() => import('./features/friend/FriendsListPage').then(m => ({ default: m.FriendsListPage })));
const ProfileTermsPage = lazy(() => import('./features/profile/ProfileTermsPage').then(m => ({ default: m.ProfileTermsPage })));
const CoursePage = lazy(() => import('./features/course/CoursePage').then(m => ({ default: m.CoursePage })));
const CourseCreatePage = lazy(() => import('./features/course/CourseCreatePage').then(m => ({ default: m.CourseCreatePage })));

// Default-export pages (standard lazy)
const HomePage = lazy(() => import('./features/home/HomePage'));
const ChatListPage = lazy(() => import('./features/chat/ChatListPage'));
const ChatDetailPage = lazy(() => import('./features/chat/ChatDetailPage'));
const WalkPage = lazy(() => import('./features/walk/WalkPage'));
const WalkMapPage = lazy(() => import('./features/walk/WalkMapPage'));
const WalkDetailPage = lazy(() => import('./features/walk/WalkDetailPage'));
const WalkCompletePage = lazy(() => import('./features/walk/WalkCompletePage'));
const WalkRankingPage = lazy(() => import('./features/walk/WalkRankingPage'));
const WalkMapExpandPage = lazy(() => import('./features/walk/WalkMapExpandPage'));
const CommunityListPage = lazy(() => import('./features/community/CommunityListPage'));
const CommunityDetailPage = lazy(() => import('./features/community/CommunityDetailPage'));
const CommunityWritePage = lazy(() => import('./features/community/CommunityWritePage'));
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'));
const ProfileOwnerWritePage = lazy(() => import('./features/profile/ProfileOwnerWritePage'));
const ProfilePetWritePage = lazy(() => import('./features/profile/ProfilePetWritePage'));
const PetRegisterPage = lazy(() => import('./features/pet/PetRegisterPage'));
const GamificationTabPage = lazy(() => import('./features/gamification/GamificationTabPage'));
const NotificationPage = lazy(() => import('./features/notification/NotificationPage'));
const NoticeDetailPage = lazy(() => import('./features/notification/NoticeDetailPage'));
const AIProfilePage = lazy(() => import('./features/aiprofile/AIProfilePage'));
const AIProfileStyleSelectPage = lazy(() => import('./features/aiprofile/AIProfileStyleSelectPage'));
const AIProfileResultPage = lazy(() => import('./features/aiprofile/AIProfileResultPage'));
const AIProfileSimplePage = lazy(() => import('./features/aiprofile/AIProfileSimplePage'));
const AIProfileCustomPage = lazy(() => import('./features/aiprofile/AIProfileCustomPage'));
const SettingsPage = lazy(() => import('./features/settings/SettingsPage'));
const PasswordChangePage = lazy(() => import('./features/settings/PasswordChangePage'));
const MyPage = lazy(() => import('./features/mypage/MyPage'));
const MyPostsPage = lazy(() => import('./features/mypage/MyPostsPage'));
const BlockManagementPage = lazy(() => import('./features/mypage/BlockManagementPage'));
const MyPagePetEditPage = lazy(() => import('./features/mypage/MyPagePetEditPage'));
const MyWalkPage = lazy(() => import('./features/mypage/MyWalkPage'));
const DormantAccountPage = lazy(() => import('./features/auth/DormantAccountPage'));
const WithdrawnAccountPage = lazy(() => import('./features/auth/WithdrawnAccountPage'));
const SuspendedAccountPage = lazy(() => import('./features/auth/SuspendedAccountPage'));
const PlaceListPage = lazy(() => import('./features/checkin/PlaceListPage'));
const PlaceDetailPage = lazy(() => import('./features/checkin/PlaceDetailPage'));
const GoldMainPage = lazy(() => import('./features/gold/GoldMainPage'));
const GoldPurchasePage = lazy(() => import('./features/gold/GoldPurchasePage'));
const GoldHistoryPage = lazy(() => import('./features/gold/GoldHistoryPage'));
const LocationSettingPage = lazy(() => import('./features/location/LocationSettingPage'));
const CourseDetailPage = lazy(() => import('./features/course/CourseDetailPage'));
const HealthHistoryPage = lazy(() => import('./features/health/HealthHistoryPage'));
const WalkPhotoGalleryPage = lazy(() => import('./features/walk/WalkPhotoGalleryPage'));
const WalkPhotoDetailPage = lazy(() => import('./features/walk/WalkPhotoDetailPage'));
const WalkSharedPhotoListPage = lazy(() => import('./features/walk/WalkSharedPhotoListPage'));
const WalkSharedPhotoDetailPage = lazy(() => import('./features/walk/WalkSharedPhotoDetailPage'));
const WalkPublicListPage = lazy(() => import('./features/walk/WalkPublicListPage'));
const UserProfilePage = lazy(() => import('./features/profile/UserProfilePage'));

// queryClient is imported from './lib/queryClient'

function App() {
  useAppHeight();
  // const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  return (
    <ErrorBoundary>
    <LazyMotion features={domAnimation}>
    <QueryClientProvider client={queryClient}>
      <MaintenanceGuard>
      <AlertProvider>
        <LoadingProvider>
          <ToastProvider>
           <BrowserRouter>
            <ScrollToTop />
            <PushNotificationHandler />
            <AuthExpiredHandler />
            <WalkCompletedHandler />
            <PullToRefresh />
            <Routes>
              {/* Entry point - stays static to avoid blank screen */}
              <Route path="/splash" element={<SplashPage />} />

              {/* Login - standalone */}
              <Route path="/login" element={<Suspense fallback={null}><LoginPage /></Suspense>} />

              {/* Type A Layout (Logo Only) */}
              <Route element={<TypeALayout variant="logo-only" />}>
                <Route path="/onboarding" element={<Suspense fallback={null}><OnboardingPage /></Suspense>} />
                <Route path="/permissions" element={<Suspense fallback={null}><PermissionsPage /></Suspense>} />
                <Route path="/benefits" element={<Suspense fallback={null}><BenefitsPage /></Suspense>} />
                <Route path="/signup/complete" element={<Suspense fallback={null}><SignupCompletePage /></Suspense>} />
              </Route>

              {/* Type A Layout (Back Only) */}
              <Route element={<TypeALayout variant="back-only" />}>
                <Route path="/signup" element={<Suspense fallback={null}><TermsAgreementPage /></Suspense>} />
                <Route path="/signup/form" element={<Suspense fallback={null}><SignupPage /></Suspense>} />
                <Route path="/signup/sns" element={<Suspense fallback={null}><SnsSignupPage /></Suspense>} />
                <Route path="/loginSuccess" element={<Suspense fallback={null}><LoginSuccessPage /></Suspense>} />
              </Route>

              {/* No Layout: Find Account */}
              <Route path="/find-username" element={<Suspense fallback={null}><FindUsernamePage /></Suspense>} />
              <Route path="/reset-password" element={<Suspense fallback={null}><ResetPasswordPage /></Suspense>} />

              {/* Type A Layout (Close Only) */}
              <Route element={<TypeALayout variant="close-only" />}>
                <Route path="/account/dormant" element={<Suspense fallback={null}><DormantAccountPage /></Suspense>} />
                <Route path="/account/withdrawn" element={<Suspense fallback={null}><WithdrawnAccountPage /></Suspense>} />
                <Route path="/account/suspended" element={<Suspense fallback={null}><SuspendedAccountPage /></Suspense>} />
                <Route path="/terms/:type" element={<Suspense fallback={null}><TermsDetailPage /></Suspense>} />
              </Route>

              {/* No Layout: Walk pages */}
              <Route path="/walk/ranking" element={<Suspense fallback={null}><WalkRankingPage /></Suspense>} />
              <Route path="/walk/map" element={<Suspense fallback={null}><WalkMapPage /></Suspense>} />
              <Route path="/walk/detail/:walkId" element={<Suspense fallback={null}><WalkDetailPage /></Suspense>} />
              <Route path="/walk/complete/:walkId" element={<Suspense fallback={null}><WalkCompletePage /></Suspense>} />
              <Route path="/walk/map-expand/:walkId" element={<Suspense fallback={null}><WalkMapExpandPage /></Suspense>} />

              {/* No Layout: Gold pages */}
              <Route path="/gold" element={<Suspense fallback={null}><GoldMainPage /></Suspense>} />
              <Route path="/gold/purchase" element={PAYMENT_ENABLED ? <Suspense fallback={null}><GoldPurchasePage /></Suspense> : <Navigate to="/gold" replace />} />
              <Route path="/gold/history" element={<Suspense fallback={null}><GoldHistoryPage /></Suspense>} />

              {/* Health */}
              <Route path="/health" element={<Suspense fallback={null}><HealthHistoryPage /></Suspense>} />

              {/* Walk Photos */}
              <Route path="/walk-photos" element={<Suspense fallback={null}><WalkPhotoGalleryPage /></Suspense>} />
              <Route path="/walk-photos/:walkId/:spotId" element={<Suspense fallback={null}><WalkPhotoDetailPage /></Suspense>} />
              <Route path="/walks" element={<Suspense fallback={null}><WalkPublicListPage /></Suspense>} />
              <Route path="/walk-shared-photos" element={<Suspense fallback={null}><WalkSharedPhotoListPage /></Suspense>} />
              <Route path="/walk-shared-photos/:walkId/:spotId" element={<Suspense fallback={null}><WalkSharedPhotoDetailPage /></Suspense>} />

              {/* No Layout: Custom header pages */}
              <Route path="/location/setting" element={<Suspense fallback={null}><LocationSettingPage /></Suspense>} />

              {/* Type B Layout (Logo + Icons): Home, Main Features */}
              <Route element={<TypeBLayout variant="logo-with-icons" />}>
                <Route path="/home" element={<Suspense fallback={null}><HomePage /></Suspense>} />
                <Route path="/friend-find" element={<Suspense fallback={null}><FriendFindPage /></Suspense>} />
                <Route path="/friend-list" element={<Suspense fallback={null}><FriendsListPage /></Suspense>} />
                <Route path="/chat" element={<Suspense fallback={null}><ChatListPage /></Suspense>} />
                <Route path="/walk" element={<Suspense fallback={null}><WalkPage /></Suspense>} />
                <Route path="/community" element={<Suspense fallback={null}><CommunityListPage /></Suspense>} />
                <Route path="/places" element={<Suspense fallback={null}><PlaceListPage /></Suspense>} />
                <Route path="/places/:placeId" element={<Suspense fallback={null}><PlaceDetailPage /></Suspense>} />
                <Route path="/ai-profile" element={<Suspense fallback={null}><AIProfilePage /></Suspense>} />
              </Route>

              {/* No Layout: Community */}
              <Route path="/community/:postId" element={<Suspense fallback={null}><CommunityDetailPage /></Suspense>} />
              <Route path="/community/write" element={<Suspense fallback={null}><CommunityWritePage /></Suspense>} />

              {/* Course routes */}
              <Route path="/courses" element={<Suspense fallback={null}><CoursePage /></Suspense>} />
              <Route path="/courses/create" element={<Suspense fallback={null}><CourseCreatePage /></Suspense>} />
              <Route path="/courses/create/from-walk/:walkId" element={<Suspense fallback={null}><CourseCreatePage /></Suspense>} />
              <Route path="/courses/:courseId/edit" element={<Suspense fallback={null}><CourseCreatePage /></Suspense>} />
              <Route path="/courses/:courseId" element={<Suspense fallback={null}><CourseDetailPage /></Suspense>} />

              {/* User Public Profile */}
              <Route path="/users/:userId" element={<Suspense fallback={null}><UserProfilePage /></Suspense>} />

              {/* MyPage / Profile / Settings */}
              <Route path="/mypage" element={<Suspense fallback={null}><MyPage /></Suspense>} />
              <Route path="/mypage/pet/edit" element={<Suspense fallback={null}><MyPagePetEditPage /></Suspense>} />
              <Route path="/mypage/posts" element={<Suspense fallback={null}><MyPostsPage /></Suspense>} />
              <Route path="/my-walks" element={<Suspense fallback={null}><MyWalkPage /></Suspense>} />
              <Route path="/block-management" element={<Suspense fallback={null}><BlockManagementPage /></Suspense>} />
              <Route path="/profile" element={<Suspense fallback={null}><ProfilePage /></Suspense>} />
              <Route path="/profile/owner/edit" element={<Suspense fallback={null}><ProfileOwnerWritePage /></Suspense>} />
              <Route path="/profile/pet/edit" element={<Suspense fallback={null}><ProfilePetWritePage /></Suspense>} />
              <Route path="/profile/terms" element={<Suspense fallback={null}><ProfileTermsPage /></Suspense>} />
              <Route path="/settings" element={<Suspense fallback={null}><SettingsPage /></Suspense>} />
              <Route path="/settings/password" element={<Suspense fallback={null}><PasswordChangePage /></Suspense>} />

              {/* Notifications */}
              <Route path="/notifications" element={<Suspense fallback={null}><NotificationPage /></Suspense>} />
              <Route path="/notices/:id" element={<Suspense fallback={null}><NoticeDetailPage /></Suspense>} />

              {/* AI Profile Routes */}
              <Route path="/ai-profile/styles" element={<Suspense fallback={null}><AIProfileStyleSelectPage /></Suspense>} />
              <Route path="/ai-profile/simple" element={<Suspense fallback={null}><AIProfileSimplePage /></Suspense>} />
              <Route path="/ai-profile/custom" element={<Suspense fallback={null}><AIProfileCustomPage /></Suspense>} />
              <Route path="/ai-profile/result/:requestId" element={<Suspense fallback={null}><AIProfileResultPage /></Suspense>} />

              {/* Chat - No Layout */}
              <Route path="/chat/:chatId" element={<Suspense fallback={null}><ChatDetailPage /></Suspense>} />
              <Route path="/chat/group/:chatId" element={<Suspense fallback={null}><ChatDetailPage /></Suspense>} />

              {/* Misc */}
              <Route path="/pet/register" element={<Suspense fallback={null}><PetRegisterPage /></Suspense>} />
              <Route path="/pet/edit/:petId" element={<Suspense fallback={null}><PetRegisterPage /></Suspense>} />
              <Route path="/my-badges" element={<Suspense fallback={null}><GamificationTabPage /></Suspense>} />
              <Route path="/missions" element={<Suspense fallback={null}><GamificationTabPage /></Suspense>} />

              {/* Share short-link routes */}
              <Route
                path="/w/:walkId"
                element={
                  <ShareRedirectBridge
                    to={(p, search) => {
                      const spotId = search?.get('spot');
                      if (spotId) return `/walk-shared-photos/${p.walkId}/${spotId}`;
                      return `/walk/detail/${p.walkId}`;
                    }}
                  />
                }
              />
              <Route path="/c/:courseId" element={<ShareRedirectBridge to={(p) => `/courses/${p.courseId}`} />} />
              <Route path="/pet/:petId/health" element={<ShareRedirectBridge to={() => '/health'} searchFn={(p) => `?pet=${p.petId}`} />} />
              <Route path="/health/:healthId" element={<ShareRedirectBridge to={() => '/health'} searchFn={(p) => `?focus=${p.healthId}`} />} />
              <Route path="/post/:postId" element={<ShareRedirectBridge to={(p) => `/community/${p.postId}`} />} />

              <Route path="/" element={<Navigate to="/splash" replace />} />
            </Routes>
          </BrowserRouter>
          </ToastProvider>
        </LoadingProvider>
      </AlertProvider>
      </MaintenanceGuard>
    </QueryClientProvider>
    </LazyMotion>
    </ErrorBoundary>
  );
}

export default App;
