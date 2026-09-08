import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { goldService } from '../../services/goldService';
import { profileBoostService } from '../../services/profileBoostService';
import type { ProfileBoostDetail } from '../../services/profileBoostService';
import GoldBalanceCard from './components/GoldBalanceCard';
import TransactionItem from './components/TransactionItem';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { useAlert } from '../../contexts/AlertContext';
import { CACHE_TIME } from '../../config/queryConfig';
import { PAYMENT_ENABLED } from '../../config/featureFlags';
import './GoldPage.css';

type FilterType = 'ALL' | 'CHARGE' | 'SPEND' | 'REWARD' | 'REFUND';
type TabType = 'charge' | 'history' | 'boost';

function GoldMainPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showAlert, showConfirm } = useAlert();
  const [activeTab, setActiveTab] = useState<TabType>(PAYMENT_ENABLED ? 'charge' : 'history');
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [page, setPage] = useState(0);

  const { data: balanceData } = useQuery({
    queryKey: ['gold', 'balance'],
    queryFn: goldService.getBalance,
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: transactionsData } = useQuery({
    queryKey: ['gold', 'transactions', filter, page],
    queryFn: () => goldService.getTransactions(page, 20, filter === 'ALL' ? undefined : filter),
    ...CACHE_TIME.DYNAMIC,
    enabled: activeTab === 'history',
  });

  const { data: boostData } = useQuery({
    queryKey: ['profile-boost', 'active'],
    queryFn: profileBoostService.getActiveBoost,
    enabled: activeTab === 'boost',
    refetchInterval: (query) => (query.state.data?.active ? 30_000 : false),
  });

  const boostMutation = useMutation({
    mutationFn: () => profileBoostService.purchaseBoost(crypto.randomUUID()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-boost', 'active'] });
      queryClient.invalidateQueries({ queryKey: ['gold', 'balance'] });
      showAlert('프로필 부스트가 활성화되었습니다! 🚀');
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      showAlert(err.response?.data?.message ?? '부스트 구매에 실패했습니다.');
    },
  });

  const balance = balanceData?.balance ?? 0;
  const transactions = transactionsData?.content ?? [];
  const totalPages = transactionsData?.totalPages ?? 0;
  const hasMore = page < totalPages - 1;

  const handleFilterChange = (newFilter: FilterType) => {
    setFilter(newFilter);
    setPage(0);
  };

  return (
    <SubPageLayout title="골드">
    <div className="gold-container">
      <GoldBalanceCard balance={balance} />

      <div className="gold-tabs">
        {PAYMENT_ENABLED && (
          <button
            className={`gold-tab${activeTab === 'charge' ? ' active' : ''}`}
            onClick={() => setActiveTab('charge')}
          >
            충전하기
          </button>
        )}
        <button
          className={`gold-tab${activeTab === 'history' ? ' active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          사용내역
        </button>
        <button
          className={`gold-tab${activeTab === 'boost' ? ' active' : ''}`}
          onClick={() => setActiveTab('boost')}
        >
          프로필 부스트
        </button>
      </div>

      {PAYMENT_ENABLED && activeTab === 'charge' && (
        <div className="gold-charge-content">
          <button className="gold-charge-btn" onClick={() => navigate('/gold/purchase')}>
            골드 스토어로 이동
          </button>
        </div>
      )}

      {activeTab === 'history' && (
        <div>
          <div className="gold-filter-tabs">
            <button className={filter === 'ALL' ? 'active' : ''} onClick={() => handleFilterChange('ALL')}>전체</button>
            {PAYMENT_ENABLED && (
              <button className={filter === 'CHARGE' ? 'active' : ''} onClick={() => handleFilterChange('CHARGE')}>충전</button>
            )}
            <button className={filter === 'SPEND' ? 'active' : ''} onClick={() => handleFilterChange('SPEND')}>사용</button>
            <button className={filter === 'REWARD' ? 'active' : ''} onClick={() => handleFilterChange('REWARD')}>적립</button>
            {PAYMENT_ENABLED && (
              <button className={filter === 'REFUND' ? 'active' : ''} onClick={() => handleFilterChange('REFUND')}>환불</button>
            )}
          </div>

          <div>
            {transactions.length === 0 && (
              <p style={{ color: '#727272', fontSize: 14, textAlign: 'center', padding: '24px 0' }}>
                내역이 없습니다.
              </p>
            )}
            {transactions.map((transaction) => (
              <TransactionItem key={transaction.id} transaction={transaction} />
            ))}
          </div>

          {hasMore && (
            <div className="gold-load-more">
              <button onClick={() => setPage((prev) => prev + 1)} className="gold-load-more-btn">
                더 보기
              </button>
            </div>
          )}
        </div>
      )}

      {activeTab === 'boost' && (
        <BoostSection
          key={boostData?.boost?.id ?? 'no-boost'}
          boost={boostData ?? null}
          isPurchasing={boostMutation.isPending}
          onPurchase={() =>
            showConfirm(
              '골드를 사용하여 프로필을 부스트하시겠습니까?\n부스트 중에는 친구 추천에 더 자주 노출됩니다.',
              () => boostMutation.mutate(),
            )
          }
        />
      )}
    </div>
    </SubPageLayout>
  );
}

interface BoostSectionProps {
  boost: { active: boolean; boost: ProfileBoostDetail | null } | null;
  isPurchasing: boolean;
  onPurchase: () => void;
}

// Defined outside component so it is stable (not recreated each render).
function calcSecsLeft(expiresAt: string | null): number {
  if (!expiresAt) return 0;
  return Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
}

function formatRemaining(seconds: number): string {
  if (seconds <= 0) return '만료됨';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}시간 ${m}분 남음`;
  return `${m}분 남음`;
}

function BoostSection({ boost, isPurchasing, onPurchase }: BoostSectionProps) {
  const detail = boost?.boost ?? null;
  const isActive = !!(boost?.active && detail);
  const expiresAt = detail?.expiresAt ?? null;

  // Initialise from expiresAt; key prop on parent forces remount when boost changes.
  const [remaining, setRemaining] = useState(() => calcSecsLeft(expiresAt));

  useEffect(() => {
    if (!isActive || !expiresAt) return;
    // Only update inside the interval callback — no synchronous setState in effect body.
    const timer = setInterval(() => setRemaining(calcSecsLeft(expiresAt)), 1000);
    return () => clearInterval(timer);
  }, [isActive, expiresAt]);

  return (
    <div className="boost-section">
      {isActive && detail ? (
        <div className="boost-active-card">
          <div className="boost-active-icon" aria-hidden="true">🚀</div>
          <p className="boost-active-title">부스트 활성 중</p>
          <p className="boost-active-countdown">{formatRemaining(remaining)}</p>
          <p className="boost-active-expires">
            만료: {new Date(detail.expiresAt).toLocaleString('ko-KR', {
              month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
            })}
          </p>
        </div>
      ) : (
        <div className="boost-inactive-card">
          <div className="boost-inactive-icon" aria-hidden="true">🚀</div>
          <p className="boost-inactive-title">프로필 부스트</p>
          <p className="boost-inactive-desc">
            골드를 사용하여 친구 추천 목록에서 더 자주 노출되세요.
          </p>
          <button
            className="boost-purchase-btn"
            onClick={onPurchase}
            disabled={isPurchasing}
          >
            {isPurchasing ? '구매 중...' : '부스트 구매'}
          </button>
        </div>
      )}
    </div>
  );
}

export default GoldMainPage;
