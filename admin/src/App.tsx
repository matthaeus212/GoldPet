import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from '@/components/ui/sonner';
import { AuthProvider } from './context/AuthContext';
import ErrorBoundary from './components/ErrorBoundary';
import RequireAuth from './components/auth/RequireAuth';
import DashboardLayout from './layouts/DashboardLayout';
import LoginPage from './pages/auth/LoginPage';
import ChangePasswordPage from './pages/auth/ChangePasswordPage';
import ForbiddenPage from './pages/auth/ForbiddenPage';
import ProfilePage from './pages/settings/ProfilePage';
import DashboardPage from './pages/dashboard/DashboardPage';
import UsersPage from './pages/users/UsersPage';
import UserDetailPage from './pages/users/UserDetailPage';
import CommunityPage from './pages/community/CommunityPage';
import CommunityDetailPage from './pages/community/CommunityDetailPage';
import ChatManagementPage from './pages/chat/ChatManagementPage';
import ReportsPage from './pages/reports/ReportsPage';
import ReportDetailPage from './pages/reports/ReportDetailPage';
import EconomyPage from './pages/economy/EconomyPage';
import GamificationPage from './pages/gamification/GamificationPage';
import LBSPage from './pages/lbs/LBSPage';
import MarketingPage from './pages/marketing/MarketingPage';
import DataPage from './pages/data/DataPage';
import BreedsPage from './pages/data/BreedsPage';
import AttributesPage from './pages/data/AttributesPage';
import UserAttributesPage from './pages/data/UserAttributesPage';
import SystemPage from './pages/system/SystemPage';
import AIProfilePage from './pages/aiprofile/AIProfilePage';
import AIProfileLoadingTipsPage from './pages/aiprofile/AIProfileLoadingTipsPage';
import NoticeManagementPage from './pages/notice/NoticeManagementPage';
import EmoticonManagementPage from './pages/emoticon/EmoticonManagementPage';
import WalkRankingManagementPage from './pages/walk/WalkRankingManagementPage';
import CoursePage from './pages/course/CoursePage';
import CourseDetailPage from './pages/course/CourseDetailPage';
import StoolAnalysisStatsPage from './pages/stool-analysis/StoolAnalysisStatsPage';


function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/change-password" element={
            <RequireAuth>
              <ChangePasswordPage />
            </RequireAuth>
          } />
          <Route path="/forbidden" element={
            <RequireAuth>
              <ForbiddenPage />
            </RequireAuth>
          } />

          <Route path="/" element={
            <RequireAuth>
              <DashboardLayout />
            </RequireAuth>
          }>
            <Route index element={<DashboardPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="chat" element={<ChatManagementPage />} />
            <Route path="community" element={<CommunityPage />} />
            <Route path="marketing" element={<MarketingPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="economy" element={<EconomyPage />} />
            <Route path="lbs" element={<LBSPage />} />
            <Route path="gamification" element={<GamificationPage />} />
            <Route path="data" element={<DataPage />} />
            <Route path="data/breeds" element={<BreedsPage />} />
            <Route path="data/attributes" element={<AttributesPage />} />
            <Route path="data/user-attributes" element={<UserAttributesPage />} />
            <Route path="ai-profile" element={<AIProfilePage />} />
            <Route path="ai-profile/loading-tips" element={<AIProfileLoadingTipsPage />} />
            <Route path="notices" element={<NoticeManagementPage />} />
            <Route path="emoticons" element={<EmoticonManagementPage />} />
            <Route path="walk-ranking" element={<WalkRankingManagementPage />} />
            <Route path="system" element={<RequireAuth requiredRoles={['SUPER_ADMIN']}><SystemPage /></RequireAuth>} />
            <Route path="users/:userId" element={<UserDetailPage />} />
            <Route path="community/:postId" element={<CommunityDetailPage />} />
            <Route path="reports/:reportId" element={<ReportDetailPage />} />
            <Route path="courses" element={<CoursePage />} />
            <Route path="courses/:courseId" element={<CourseDetailPage />} />
            <Route path="stool-analysis" element={<StoolAnalysisStatsPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </BrowserRouter>
        <Toaster position="top-right" richColors />
      </AuthProvider>
    </ErrorBoundary>
  );
}

export default App;
