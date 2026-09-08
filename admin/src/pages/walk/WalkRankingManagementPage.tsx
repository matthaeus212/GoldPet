import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { walkRankingService } from '../../services/walkRankingService';
import type { WalkRankingAdminEntry, BestWalkCoupleResponse } from '../../services/walkRankingService';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

function getCurrentYearMonth(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

function formatYearMonth(ym: string): string {
  const [y, m] = ym.split('-');
  return `${y}.${m}`;
}

function Avatar({ url, name, size = 32 }: { url: string | null; name: string | null; size?: number }) {
  const sizeClass = size === 32 ? 'w-8 h-8' : 'w-10 h-10';
  if (url) {
    return <img src={url} alt={name || ''} className={`${sizeClass} rounded-full object-cover border border-gray-200`} />;
  }
  return (
    <div className={`${sizeClass} rounded-full bg-gray-200 flex items-center justify-center text-xs text-gray-500 font-medium`}>
      {(name || '?')[0]}
    </div>
  );
}

export default function WalkRankingManagementPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const [yearMonth, setYearMonth] = useState(getCurrentYearMonth());
  const [confirmTarget, setConfirmTarget] = useState<WalkRankingAdminEntry | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [removing, setRemoving] = useState(false);

  const { data: rankings = [], isLoading: rankingsLoading } = useQuery({
    queryKey: ['admin', 'walkRanking', yearMonth],
    queryFn: () => walkRankingService.getRanking(yearMonth),
  });

  const { data: bestCouple, isLoading: bestLoading } = useQuery<BestWalkCoupleResponse | null>({
    queryKey: ['admin', 'bestCouple', yearMonth],
    queryFn: () => walkRankingService.getBestCouple(yearMonth),
  });

  const prevMonth = () => {
    const [y, m] = yearMonth.split('-').map(Number);
    const d = new Date(y, m - 2, 1);
    setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  };

  const nextMonth = async () => {
    const current = getCurrentYearMonth();
    if (yearMonth >= current) return;
    const [y, m] = yearMonth.split('-').map(Number);
    const d = new Date(y, m, 1);
    setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  };

  const handleSelect = async (entry: WalkRankingAdminEntry) => {
    if (!entry.petId) {
      toast.error('이 사용자는 해당 월에 반려동물 산책 기록이 없습니다.');
      return;
    }
    setConfirmTarget(entry);
  };

  const handleConfirmSelect = async () => {
    if (!confirmTarget || !confirmTarget.petId) return;
    setSubmitting(true);
    try {
      await walkRankingService.setBestCouple({
        yearMonth,
        userId: confirmTarget.userId,
        petId: confirmTarget.petId,
      });
      queryClient.invalidateQueries({ queryKey: ['admin', 'bestCouple', yearMonth] });
      setConfirmTarget(null);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e.response?.data?.message || '선정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async () => {
    if (!(await confirmDialog({ description: `${formatYearMonth(yearMonth)} 베스트 산책커플 선정을 해제하시겠습니까?` }))) return;
    setRemoving(true);
    try {
      await walkRankingService.deleteBestCouple(yearMonth);
      queryClient.invalidateQueries({ queryKey: ['admin', 'bestCouple', yearMonth] });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e.response?.data?.message || '해제에 실패했습니다.');
    } finally {
      setRemoving(false);
    }
  };

  const isBestCouple = (entry: WalkRankingAdminEntry) =>
    bestCouple != null && bestCouple.userId === entry.userId;

  const columns: ColumnDef<WalkRankingAdminEntry>[] = [
    {
      key: 'rank',
      header: '순위',
      width: '60px',
      align: 'center',
      render: (value, row) => (
        <span className={`font-bold ${isBestCouple(row) ? 'text-amber-500' : 'text-gray-700'}`}>
          #{value}
        </span>
      ),
    },
    {
      key: 'nickname',
      header: '사용자',
      render: (_value, row) => (
        <div className="flex items-center gap-2">
          <Avatar url={row.profileImageUrl ?? null} name={row.nickname ?? null} />
          <div>
            <div className="font-medium text-sm">{row.nickname || '-'}</div>
            <div className="text-xs text-gray-400">ID: {row.userId}</div>
          </div>
          {isBestCouple(row) && (
            <span className="ml-1 px-1.5 py-0.5 bg-amber-100 text-amber-700 text-xs rounded-full font-medium">
              BEST
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'petName',
      header: '반려동물',
      render: (_value, row) => (
        <div className="flex items-center gap-2">
          <Avatar url={row.petProfileImageUrl ?? null} name={row.petName ?? null} size={32} />
          <span className="text-sm">{row.petName || '-'}</span>
        </div>
      ),
    },
    {
      key: 'totalDistanceKm',
      header: '총 거리',
      align: 'right',
      render: (value) => <span className="font-medium">{Number(value).toFixed(2)} km</span>,
    },
    {
      key: 'totalMinutes',
      header: '총 시간',
      align: 'right',
      render: (value) => `${value}분`,
    },
    {
      key: 'totalGold',
      header: '총 적립',
      align: 'right',
      render: (value) => <span className="text-amber-600 font-medium">{value}G</span>,
    },
    {
      key: 'walkCount',
      header: '산책 횟수',
      align: 'right',
      render: (value) => `${value}회`,
    },
    {
      key: 'userId',
      header: '선정',
      align: 'center',
      render: (_value, row) => (
        isBestCouple(row) ? (
          <span className="px-2 py-1 text-xs bg-amber-100 text-amber-700 rounded-full font-medium">선정됨</span>
        ) : (
          <Button variant="secondary" size="sm" onClick={() => handleSelect(row)}>
            선정
          </Button>
        )
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">베스트 산책커플 관리</h1>
      </div>

      {/* Month Selector */}
      <div className="flex items-center gap-3">
        <button
          onClick={prevMonth}
          className="p-2 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          &lt;
        </button>
        <span className="text-lg font-semibold text-gray-800 min-w-[80px] text-center">
          {formatYearMonth(yearMonth)}
        </span>
        <button
          onClick={nextMonth}
          disabled={yearMonth >= getCurrentYearMonth()}
          className="p-2 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          &gt;
        </button>
      </div>

      {/* Best Couple Card */}
      {!bestLoading && bestCouple && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="px-2 py-0.5 bg-amber-500 text-white text-xs rounded-full font-bold">
                  BEST 산책 커플
                </span>
                <span className="text-sm text-amber-700 font-medium">{formatYearMonth(yearMonth)}</span>
              </div>
              <div className="flex items-center gap-3 mt-2">
                <div className="flex items-center gap-2">
                  <Avatar url={bestCouple.petProfileImageUrl ?? null} name={bestCouple.petName ?? null} size={40} />
                  <Avatar url={bestCouple.profileImageUrl ?? null} name={bestCouple.nickname ?? null} size={40} />
                </div>
                <div>
                  <div className="font-semibold text-gray-800">
                    {bestCouple.petName} &amp; {bestCouple.nickname}
                  </div>
                  <div className="text-xs text-gray-500">선정일: {new Date(bestCouple.createdAt).toLocaleDateString('ko-KR')}</div>
                </div>
              </div>
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleRemove}
              loading={removing}
              disabled={removing}
            >
              해제
            </Button>
          </div>
        </div>
      )}

      {!bestLoading && !bestCouple && (
        <div className="bg-gray-50 border border-dashed border-gray-300 rounded-lg p-4 text-center text-sm text-gray-500">
          {formatYearMonth(yearMonth)} 베스트 산책커플이 아직 선정되지 않았습니다. 아래 랭킹에서 선정하세요.
        </div>
      )}

      {/* Ranking Table */}
      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <div className="p-4 border-b">
          <h2 className="font-semibold text-gray-800">
            {formatYearMonth(yearMonth)} 산책 랭킹 ({rankings.length}명)
          </h2>
        </div>
        <DataTable<WalkRankingAdminEntry>
          columns={columns}
          data={rankings}
          loading={rankingsLoading}
          emptyMessage="해당 월에 산책 기록이 없습니다."
          rowClassName={(row) => isBestCouple(row) ? 'bg-amber-50' : ''}
        />
      </div>

      {/* Confirm Modal */}
      {confirmTarget && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
            <div className="p-6 border-b">
              <h3 className="text-lg font-semibold">베스트 산책커플 선정</h3>
            </div>
            <div className="p-6">
              <p className="text-gray-700">
                <span className="font-semibold">'{confirmTarget.petName || '(반려동물 없음)'} &amp; {confirmTarget.nickname}'</span>을(를){' '}
                <span className="font-semibold text-amber-600">{formatYearMonth(yearMonth)}</span> 베스트 산책커플로 선정하시겠습니까?
              </p>
              {bestCouple && (
                <p className="mt-2 text-sm text-orange-600">
                  기존 선정({bestCouple.petName} &amp; {bestCouple.nickname})이 해제됩니다.
                </p>
              )}
            </div>
            <div className="p-6 border-t flex justify-end gap-3">
              <Button variant="secondary" onClick={() => setConfirmTarget(null)} disabled={submitting}>
                취소
              </Button>
              <Button variant="primary" onClick={handleConfirmSelect} loading={submitting} disabled={submitting}>
                선정
              </Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </div>
  );
}
