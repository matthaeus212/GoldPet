import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../stores/authStore';
import { systemService } from '../services/systemService';

/**
 * 커뮤니티 프로필 블록 탭 핸들러.
 *
 * - feature flag `authorProfileLinkEnabled` off → 탭 무시 (기존 li 네비만)
 * - 자기 자신 탭 → /mypage
 * - 타인 탭 → /users/{authorId}
 */
export function useHandleProfileTap() {
  const navigate = useNavigate();
  const myId = useAuthStore((s) => s.user?.id);

  const { data: publicSettings } = useQuery({
    queryKey: ['system', 'publicSettings'],
    queryFn: systemService.getPublicSettings,
    staleTime: 1000 * 60 * 5,
  });

  const flagEnabled = publicSettings?.authorProfileLinkEnabled ?? false;

  return useCallback(
    (authorId: number | undefined, e?: React.MouseEvent) => {
      if (!flagEnabled) return;
      e?.stopPropagation();
      if (!authorId) return;
      if (authorId === myId) {
        navigate('/mypage');
      } else {
        navigate(`/users/${authorId}`);
      }
    },
    [flagEnabled, myId, navigate],
  );
}
