import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/ToastContext';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { userService } from '../../services/userService';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import './SettingsPage.css';

const PASSWORD_REGEX = /^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{}|;:',.<>?/~`]).{8,}$/;

const PasswordChangePage: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<{ current?: string; new?: string; confirm?: string }>({});

  const validate = (): boolean => {
    const newErrors: typeof errors = {};

    if (!currentPassword) {
      newErrors.current = '현재 비밀번호를 입력해주세요.';
    }

    if (!newPassword) {
      newErrors.new = '새 비밀번호를 입력해주세요.';
    } else if (!PASSWORD_REGEX.test(newPassword)) {
      newErrors.new = '8자 이상, 영문/숫자/특수문자를 포함해야 합니다.';
    } else if (newPassword === currentPassword) {
      newErrors.new = '새 비밀번호는 현재 비밀번호와 달라야 합니다.';
    }

    if (!confirmPassword) {
      newErrors.confirm = '비밀번호 확인을 입력해주세요.';
    } else if (newPassword !== confirmPassword) {
      newErrors.confirm = '비밀번호가 일치하지 않습니다.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || isSubmitting) return;

    setIsSubmitting(true);
    try {
      await userService.changePassword(currentPassword, newPassword);
      showToast('비밀번호가 변경되었습니다.', 'success');
      navigate('/settings');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } }; message?: string };
      const message = err.response?.data?.message || '비밀번호 변경에 실패했습니다.';
      if (message.includes('현재 비밀번호')) {
        setErrors({ current: message });
      } else {
        showToast(message, 'error');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const keyboardDismiss = useKeyboardDismiss();

  return (
    <SubPageLayout title="비밀번호 변경" onBack={() => navigate('/settings')}>
      <div className="settings-container" {...keyboardDismiss}>
        <form onSubmit={handleSubmit}>
          <div className="settings-group">
            <div className="settings-group-title">비밀번호 변경</div>

            <div className="settings-card">
              <label className="settings-label" style={{ display: 'block', marginBottom: 8 }}>현재 비밀번호</label>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => { setCurrentPassword(e.target.value); setErrors((prev) => ({ ...prev, current: undefined })); }}
                placeholder="현재 비밀번호 입력"
                className="pw-input"
                autoComplete="current-password"
              />
              {errors.current && <p className="pw-error">{errors.current}</p>}
            </div>

            <div className="settings-card">
              <label className="settings-label" style={{ display: 'block', marginBottom: 8 }}>새 비밀번호</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => { setNewPassword(e.target.value); setErrors((prev) => ({ ...prev, new: undefined })); }}
                placeholder="8자 이상, 영문/숫자/특수문자 포함"
                className="pw-input"
                autoComplete="new-password"
              />
              {errors.new && <p className="pw-error">{errors.new}</p>}
            </div>

            <div className="settings-card">
              <label className="settings-label" style={{ display: 'block', marginBottom: 8 }}>비밀번호 확인</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => { setConfirmPassword(e.target.value); setErrors((prev) => ({ ...prev, confirm: undefined })); }}
                placeholder="새 비밀번호 다시 입력"
                className="pw-input"
                autoComplete="new-password"
              />
              {errors.confirm && <p className="pw-error">{errors.confirm}</p>}
            </div>
          </div>

          <div style={{ padding: '24px 0' }}>
            <button
              type="submit"
              disabled={isSubmitting}
              className="pw-submit-btn"
            >
              {isSubmitting ? '변경 중...' : '비밀번호 변경'}
            </button>
          </div>
        </form>
      </div>
    </SubPageLayout>
  );
};

export default PasswordChangePage;
