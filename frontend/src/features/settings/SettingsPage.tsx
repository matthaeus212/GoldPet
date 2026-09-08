import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { userService } from '../../services/userService';
import { useAlert } from '../../contexts/AlertContext';
import { useToast } from '../../contexts/ToastContext';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { settingsService } from '../../services/settingsService';
import { profileService } from '../../services/profileService';
import type { NotificationSettings, PrivacySettings } from '../../services/settingsService';
import './SettingsPage.css';

const ChevronRight = () => (
  <svg className="settings-chevron" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M9 6l6 6-6 6" />
  </svg>
);

const SettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, updateUser } = useAuthStore();
  const { showConfirm } = useAlert();
  const { showToast } = useToast();

  const [notificationSettings, setNotificationSettings] = useState<NotificationSettings>({
    pushAlert: true,
    chatAlert: true,
    communityAlert: true,
    marketingAlert: false,
    reengagementAlert: true, // default on; matches backend isReengagementAlertEnabled default
  });

  const [privacySettings, setPrivacySettings] = useState<PrivacySettings>({
    locationSharing: true,
    profilePublic: true,
  });

  // Extended privacy state for Figma design fields
  const [activityPublic, setActivityPublic] = useState(true);

  useEffect(() => {
    const loadSettings = async () => {
      try {
        const [notifications, privacy] = await Promise.all([
          settingsService.getNotificationSettings(),
          settingsService.getPrivacySettings(),
        ]);
        setNotificationSettings(notifications);
        setPrivacySettings(privacy);
      } catch {
        showToast('설정을 불러오는데 실패했습니다.', 'error');
      }
    };
    loadSettings();
    // 로그인 응답(UserInfo)엔 oauthProvider가 없어 '연동 계정'이 비므로, /users/me로 보완해 스토어 갱신
    userService.getMe().then(updateUser).catch(() => { /* 실패 시 기존 user 유지 */ });
  // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only effect; showToast from context is stable but effect is intentionally run once on mount
  }, []);

  const handleNotificationToggle = async (key: keyof NotificationSettings) => {
    const newSettings = { ...notificationSettings, [key]: !notificationSettings[key] };
    setNotificationSettings(newSettings);
    try {
      await settingsService.updateNotificationSettings(newSettings);
    } catch {
      setNotificationSettings(notificationSettings);
      showToast('알림 설정 변경에 실패했습니다.', 'error');
    }
  };

  const handlePrivacyToggle = async (key: keyof PrivacySettings) => {
    const newSettings = { ...privacySettings, [key]: !privacySettings[key] };
    setPrivacySettings(newSettings);
    try {
      await settingsService.updatePrivacySettings(newSettings);
    } catch {
      setPrivacySettings(privacySettings);
      showToast('개인정보 설정 변경에 실패했습니다.', 'error');
    }
  };

  const handleLogout = () => {
    showConfirm('로그아웃 하시겠습니까?', async () => {
      await profileService.logout();
      navigate('/login', { replace: true });
      showToast('로그아웃 되었습니다.', 'success');
    });
  };

  const handleDeleteAccount = () => {
    showConfirm(
      '정말로 회원탈퇴 하시겠습니까?\n모든 데이터가 삭제되며 복구할 수 없습니다.',
      async () => {
        try {
          await profileService.deleteAccount();
          showToast('회원탈퇴가 처리되었습니다.', 'success');
          navigate('/login', { replace: true });
        } catch {
          showToast('회원탈퇴에 실패했습니다. 다시 시도해주세요.', 'error');
        }
      },
      undefined,
      { confirmText: '탈퇴하기', cancelText: '취소' }
    );
  };

  const handleDataExport = async () => {
    try {
      await settingsService.exportMyData();
      showToast('데이터 다운로드가 시작되었습니다.', 'success');
    } catch {
      showToast('데이터 다운로드에 실패했습니다.', 'error');
    }
  };

  return (
    <SubPageLayout title="설정" onBack={() => navigate('/mypage')}>
      <div className="settings-container">
        {/* 알림 설정 */}
        <div className="settings-group">
          <div className="settings-group-title">알림 설정</div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">푸시 알림</span>
              <div className="toggle-switch">
                <input type="checkbox" id="push-alert" checked={notificationSettings.pushAlert} onChange={() => handleNotificationToggle('pushAlert')} />
                <label htmlFor="push-alert"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">채팅 알림</span>
              <div className="toggle-switch">
                <input type="checkbox" id="chat-alert" checked={notificationSettings.chatAlert} onChange={() => handleNotificationToggle('chatAlert')} />
                <label htmlFor="chat-alert"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">커뮤니티 알림</span>
              <div className="toggle-switch">
                <input type="checkbox" id="community-alert" checked={notificationSettings.communityAlert} onChange={() => handleNotificationToggle('communityAlert')} />
                <label htmlFor="community-alert"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">마케팅 알림</span>
              <div className="toggle-switch">
                <input type="checkbox" id="marketing-alert" checked={notificationSettings.marketingAlert} onChange={() => handleNotificationToggle('marketingAlert')} />
                <label htmlFor="marketing-alert"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">산책 리마인더</span>
              <div className="toggle-switch">
                <input type="checkbox" id="reengagement-alert" checked={notificationSettings.reengagementAlert} onChange={() => handleNotificationToggle('reengagementAlert')} />
                <label htmlFor="reengagement-alert"></label>
              </div>
            </div>
          </div>
        </div>

        {/* 공개 설정 */}
        <div className="settings-group">
          <div className="settings-group-title">공개 설정</div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">프로필 공개</span>
              <div className="toggle-switch">
                <input type="checkbox" id="profile-public" checked={privacySettings.profilePublic} onChange={() => handlePrivacyToggle('profilePublic')} />
                <label htmlFor="profile-public"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">위치 표시</span>
              <div className="toggle-switch">
                <input type="checkbox" id="location-sharing" checked={privacySettings.locationSharing} onChange={() => handlePrivacyToggle('locationSharing')} />
                <label htmlFor="location-sharing"></label>
              </div>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">활동 공개</span>
              <div className="toggle-switch">
                <input type="checkbox" id="activity-public" checked={activityPublic} onChange={() => setActivityPublic(!activityPublic)} />
                <label htmlFor="activity-public"></label>
              </div>
            </div>
          </div>
        </div>

        {/* 계정 */}
        <div className="settings-group">
          <div className="settings-group-title">계정</div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">이메일</span>
              <span className="settings-value">{user?.email || '-'}</span>
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">연동 계정</span>
              <div className="settings-social-icons">
                {user?.oauthProvider && user.oauthProvider !== 'LOCAL' && (
                  <img
                    src={`/assets/images/auth/${user.oauthProvider}_logo.svg`}
                    alt={user.oauthProvider}
                    className="settings-social-icon"
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                  />
                )}
              </div>
            </div>
          </div>
          {user?.oauthProvider === 'LOCAL' && (
            <div className="settings-card settings-card-link" onClick={() => navigate('/settings/password')}>
              <div className="settings-row">
                <span className="settings-label">비밀번호 변경</span>
                <ChevronRight />
              </div>
            </div>
          )}
        </div>

        {/* 내 데이터 */}
        <div className="settings-group">
          <div className="settings-group-title">내 데이터</div>
          <div className="settings-card settings-card-link" onClick={handleDataExport}>
            <div className="settings-row">
              <span className="settings-label">내 데이터 다운로드</span>
              <ChevronRight />
            </div>
          </div>
        </div>

        {/* 고객지원 */}
        <div className="settings-group">
          <div className="settings-group-title">고객지원</div>
          <div className="settings-card settings-card-link" onClick={() => navigate('/terms/service')}>
            <div className="settings-row">
              <span className="settings-label">서비스 이용약관</span>
              <ChevronRight />
            </div>
          </div>
          <div className="settings-card settings-card-link" onClick={() => navigate('/terms/privacy')}>
            <div className="settings-row">
              <span className="settings-label">개인정보 처리방침</span>
              <ChevronRight />
            </div>
          </div>
          <div className="settings-card">
            <div className="settings-row">
              <span className="settings-label">버전 정보</span>
              <span className="settings-value">1.0.0</span>
            </div>
          </div>
        </div>

        {/* 로그아웃 / 회원탈퇴 */}
        <div className="settings-group">
          <div className="settings-card settings-card-link" onClick={handleLogout}>
            <div className="settings-row">
              <span className="settings-label settings-label-primary">로그아웃</span>
            </div>
          </div>
          <div className="settings-card settings-card-link" onClick={handleDeleteAccount}>
            <div className="settings-row">
              <span className="settings-label settings-label-danger">회원 탈퇴</span>
            </div>
          </div>
        </div>
      </div>
    </SubPageLayout>
  );
};

export default SettingsPage;
