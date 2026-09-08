import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { blockService } from '../../services/blockService';
import type { BlockedUser } from '../../services/blockService';
import { useAlert } from '../../contexts/AlertContext';
import { CACHE_TIME } from '../../config/queryConfig';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { EmptyState } from '../../components/common/EmptyState';
import './BlockManagementPage.css';

const ProhibitionIcon = () => (
  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary)" strokeWidth="1.5">
    <circle cx="12" cy="12" r="10" />
    <path d="M4.93 4.93l14.14 14.14" />
  </svg>
);

const BlockManagementPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showConfirm } = useAlert();

  const { data: blockedUsers = [], isLoading } = useQuery({
    queryKey: ['blocks'],
    queryFn: blockService.getBlockList,
    ...CACHE_TIME.DYNAMIC,
  });

  const unblockMutation = useMutation({
    mutationFn: (blockedUserId: number) => blockService.unblockUser(blockedUserId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['blocks'] });
    },
  });

  const handleUnblock = (user: BlockedUser) => {
    showConfirm(
      `${user.blockedNickname || '사용자'}님의 차단을 해제하시겠어요?`,
      () => {
        unblockMutation.mutate(user.blockedUserId);
      }
    );
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '';
    return dateStr.substring(0, 10).replace(/-/g, '.');
  };

  return (
    <SubPageLayout title="차단 관리" onBack={() => navigate('/mypage')}>
      <div className="block-management">
        {isLoading ? (
          <div className="block-loading">로딩 중...</div>
        ) : blockedUsers.length === 0 ? (
          <EmptyState icon={<ProhibitionIcon />} text="차단한 사용자가 없어요" />
        ) : (
          <ul className="block-list">
            {blockedUsers.map((user) => (
              <li key={user.blockId} className="block-item">
                <img
                  className="block-item-photo"
                  src={user.blockedProfileImageUrl || '/assets/images/common/profile_none_img.svg'}
                  alt={user.blockedNickname || '사용자'}
                  onError={(e) => { (e.target as HTMLImageElement).src = '/assets/images/common/profile_none_img.svg'; }}
                />
                <div className="block-item-info">
                  <div className="block-item-nickname">{user.blockedNickname || '사용자'}</div>
                  {user.blockedPetName && (
                    <div className="block-item-pet">{user.blockedPetName}</div>
                  )}
                </div>
                <div className="block-item-right">
                  <button
                    className="block-item-unblock"
                    onClick={() => handleUnblock(user)}
                    disabled={unblockMutation.isPending}
                  >
                    해제
                  </button>
                  {user.blockedAt && (
                    <span className="block-item-date">{formatDate(user.blockedAt)}</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </SubPageLayout>
  );
};

export default BlockManagementPage;
