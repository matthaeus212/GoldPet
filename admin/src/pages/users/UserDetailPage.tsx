import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userManagementService } from '../../services/userManagementService';
import type { UserDetail, UserGoldTransaction } from '../../services/userManagementService';
import { economyService } from '../../services/economyService';
import { DetailPageLayout } from '../../components/common/DetailPageLayout';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const GOLD_TYPE_LABELS: Record<string, { text: string; color: string }> = {
  CHARGE: { text: '충전', color: 'bg-green-100 text-green-800' },
  SPEND: { text: '사용', color: 'bg-red-100 text-red-800' },
  REWARD: { text: '보상', color: 'bg-blue-100 text-blue-800' },
  REFUND: { text: '환불', color: 'bg-yellow-100 text-yellow-800' },
  ADMIN_ADJUST: { text: '관리자 조정', color: 'bg-purple-100 text-purple-800' },
};

export default function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()

  const [showGrantDialog, setShowGrantDialog] = useState(false);
  const [grantAmount, setGrantAmount] = useState('');
  const [grantReason, setGrantReason] = useState('');

  const { data: user, isLoading, isError } = useQuery<UserDetail>({
    queryKey: ['users', 'detail', userId],
    queryFn: () => userManagementService.getUserDetail(Number(userId)),
    enabled: !!userId,
  });

  const { data: goldTx, isLoading: goldTxLoading } = useQuery({
    queryKey: ['users', 'gold-transactions', userId],
    queryFn: () => userManagementService.getGoldTransactions(Number(userId), { page: 0, size: 20 }),
    enabled: !!userId,
  });

  const grantMutation = useMutation({
    mutationFn: ({ amount, reason }: { amount: number; reason: string }) =>
      economyService.adjustGold(Number(userId), amount, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'detail', userId] });
      queryClient.invalidateQueries({ queryKey: ['users', 'gold-transactions', userId] });
      setShowGrantDialog(false);
      setGrantAmount('');
      setGrantReason('');
      toast.success('골드가 지급되었습니다.');
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e?.response?.data?.message || '골드 지급에 실패했습니다.'),
  });

  const handleGrant = () => {
    const amount = parseInt(grantAmount, 10);
    if (!amount || amount <= 0) {
      toast.info('지급 금액은 1 이상이어야 합니다.');
      return;
    }
    if (!grantReason.trim()) {
      toast.info('지급 사유를 입력해주세요.');
      return;
    }
    grantMutation.mutate({ amount, reason: grantReason });
  };

  const handleStatusChange = async () => {
    if (!user) return;
    const newStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    try {
      await userManagementService.updateUserStatus(user.id, newStatus);
      queryClient.invalidateQueries({ queryKey: ['users'] });
      navigate('/users');
    } catch (error) {
      console.error('Failed to update status:', error);
      toast.error('상태 변경에 실패했습니다.');
    }
  };

  const handleDelete = async () => {
    if (!user) return;
    if (!(await confirmDialog({ description: '정말 이 회원을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await userManagementService.deleteUser(user.id);
      queryClient.invalidateQueries({ queryKey: ['users'] });
      navigate('/users');
    } catch (error) {
      console.error('Failed to delete user:', error);
      toast.error('회원 삭제에 실패했습니다.');
    }
  };

  const handleUnlockProfile = async () => {
    if (!user) return;
    if (!(await confirmDialog({ description: '프로필 잠금을 해제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await userManagementService.unlockUserProfile(user.id);
      queryClient.invalidateQueries({ queryKey: ['users', 'detail', userId] });
      toast.success('프로필 잠금이 해제되었습니다.');
    } catch (error) {
      console.error('Failed to unlock profile:', error);
      toast.error('프로필 잠금 해제에 실패했습니다.');
    }
  };

  const actions = user ? (
    <>
      {user.profileLockedAt != null && (
        <Button variant="warning" onClick={handleUnlockProfile}>
          프로필 잠금 해제
        </Button>
      )}
      {user.status === 'ACTIVE' ? (
        <Button variant="warning" onClick={handleStatusChange}>
          정지
        </Button>
      ) : (
        <Button variant="success" onClick={handleStatusChange}>
          활성화
        </Button>
      )}
      <Button variant="danger" onClick={handleDelete}>
        삭제
      </Button>
    </>
  ) : undefined;

  if (isLoading) {
    return (
      <DetailPageLayout title="회원 상세" backPath="/users">
        <div className="py-12 text-center text-gray-500">로딩 중...</div>
      </DetailPageLayout>
    );
  }

  if (isError || !user) {
    return (
      <DetailPageLayout title="회원 상세" backPath="/users">
        <div className="py-12 text-center text-gray-500">회원 정보를 불러올 수 없습니다.</div>
      </DetailPageLayout>
    );
  }

  return (
    <DetailPageLayout title="회원 상세" backPath="/users" actions={actions}>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Profile Info */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">프로필 정보</h2>
            <div className="flex items-center gap-4 mb-6">
              {user.profileImageUrl ? (
                <img className="h-16 w-16 rounded-full object-cover" src={user.profileImageUrl} alt="" />
              ) : (
                <div className="h-16 w-16 rounded-full bg-gray-200 flex items-center justify-center">
                  <span className="text-gray-500 text-xl">{user.nickname.charAt(0)}</span>
                </div>
              )}
              <div>
                <div className="text-xl font-bold text-gray-900">{user.nickname}</div>
                <span className={`mt-1 px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                  user.status === 'ACTIVE' ? 'bg-green-100 text-green-800' :
                  user.status === 'SUSPENDED' ? 'bg-red-100 text-red-800' :
                  user.status === 'DORMANT' ? 'bg-yellow-100 text-yellow-800' :
                  user.status === 'WITHDRAWN' ? 'bg-gray-100 text-gray-800' :
                  'bg-gray-100 text-gray-800'
                }`}>
                  {user.status === 'ACTIVE' ? '활성' :
                   user.status === 'SUSPENDED' ? '정지' :
                   user.status === 'DORMANT' ? '휴면' :
                   user.status === 'WITHDRAWN' ? '탈퇴' :
                   user.status}
                </span>
              </div>
            </div>
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-sm font-medium text-gray-500">이메일</dt>
                <dd className="mt-1 text-sm text-gray-900">{user.email || '-'}</dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500">로그인 제공자</dt>
                <dd className="mt-1 text-sm text-gray-900">{user.oauthProvider || '-'}</dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500">골드 잔액</dt>
                <dd className="mt-1 flex items-center gap-2 text-sm text-gray-900">
                  <span>{user.goldBalance.toLocaleString()} G</span>
                  <Button variant="primary" size="sm" onClick={() => setShowGrantDialog(true)}>
                    골드 지급
                  </Button>
                </dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500">가입일</dt>
                <dd className="mt-1 text-sm text-gray-900">{user.createdAt || '-'}</dd>
              </div>
              <div>
                <dt className="text-sm font-medium text-gray-500">마지막 로그인</dt>
                <dd className="mt-1 text-sm text-gray-900">{user.lastLoginAt || '-'}</dd>
              </div>
              {user.intro && (
                <div className="col-span-2">
                  <dt className="text-sm font-medium text-gray-500">소개</dt>
                  <dd className="mt-1 text-sm text-gray-900">{user.intro}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Pets */}
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">반려동물 ({user.pets.length}마리)</h2>
            {user.pets.length === 0 ? (
              <p className="text-sm text-gray-500">등록된 반려동물이 없습니다.</p>
            ) : (
              <ul className="divide-y divide-gray-100">
                {user.pets.map((pet) => (
                  <li key={pet.id} className="py-3 flex items-center justify-between">
                    <div>
                      <span className="text-sm font-medium text-gray-900">{pet.name}</span>
                      <span className="ml-2 text-sm text-gray-500">{pet.species}</span>
                      {pet.breed && <span className="ml-1 text-sm text-gray-400">/ {pet.breed}</span>}
                    </div>
                    <span className="text-xs text-gray-400">ID: {pet.id}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Gold Transactions */}
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">골드 이력</h2>
            {goldTxLoading ? (
              <p className="text-sm text-gray-500">로딩 중...</p>
            ) : (goldTx?.content.length ?? 0) === 0 ? (
              <p className="text-sm text-gray-500">골드 거래 내역이 없습니다.</p>
            ) : (
              <ul className="divide-y divide-gray-100">
                {goldTx?.content.map((tx: UserGoldTransaction) => {
                  const label = GOLD_TYPE_LABELS[tx.type] ?? { text: tx.type, color: 'bg-gray-100 text-gray-800' };
                  return (
                    <li key={tx.id} className="py-3 flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <span className={`px-2 py-1 text-xs rounded ${label.color}`}>{label.text}</span>
                        <span className="ml-2 text-sm text-gray-600">{tx.description || '-'}</span>
                        <div className="mt-1 text-xs text-gray-400">{tx.createdAt ?? '-'}</div>
                      </div>
                      <span className={`text-sm font-medium whitespace-nowrap ${tx.amount >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                        {tx.amount >= 0 ? '+' : ''}{tx.amount.toLocaleString()} G
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>

        {/* Stats */}
        <div className="space-y-6">
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">활동 통계</h2>
            <dl className="space-y-3">
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">총 산책 횟수</dt>
                <dd className="text-sm font-medium text-gray-900">{user.stats.totalWalks.toLocaleString()}회</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">총 산책 거리</dt>
                <dd className="text-sm font-medium text-gray-900">{user.stats.totalDistance.toLocaleString()}m</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">게시글 수</dt>
                <dd className="text-sm font-medium text-gray-900">{user.stats.totalPosts.toLocaleString()}개</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">댓글 수</dt>
                <dd className="text-sm font-medium text-gray-900">{user.stats.totalComments.toLocaleString()}개</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-sm text-gray-500">획득 뱃지</dt>
                <dd className="text-sm font-medium text-gray-900">{user.stats.badgeCount.toLocaleString()}개</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      {/* Grant Gold Dialog (지급 전용 — 금액 양수만 허용) */}
      {showGrantDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-96 shadow-xl">
            <h3 className="text-lg font-semibold mb-2">골드 지급</h3>
            <p className="text-sm text-gray-600 mb-4">{user.nickname} 님에게 골드를 지급합니다. ADMIN_ADJUST 거래로 기록됩니다.</p>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">지급 금액</label>
                <input
                  type="number"
                  min={1}
                  value={grantAmount}
                  onChange={(e) => setGrantAmount(e.target.value)}
                  placeholder="예: 100"
                  className="w-full px-3 py-2 border rounded-lg text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">사유</label>
                <input
                  value={grantReason}
                  onChange={(e) => setGrantReason(e.target.value)}
                  placeholder="지급 사유를 입력하세요"
                  className="w-full px-3 py-2 border rounded-lg text-sm"
                />
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Button
                variant="primary"
                className="flex-1"
                loading={grantMutation.isPending}
                onClick={handleGrant}
              >
                지급
              </Button>
              <Button
                variant="secondary"
                className="flex-1"
                onClick={() => { setShowGrantDialog(false); setGrantAmount(''); setGrantReason(''); }}
              >
                취소
              </Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </DetailPageLayout>
  );
}
