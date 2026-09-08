import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Activity, BarChart2, CheckCircle, DollarSign } from 'lucide-react';
import { stoolAnalysisAdminService } from '../../services/stoolAnalysisAdminService';
import type { AdminStoolAnalysisItem } from '../../services/stoolAnalysisAdminService';
import { Tabs } from '../../components/common/Tabs';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { StatCard } from '../../components/common/StatCard';
import { Button } from '../../components/common/Button';
import { useListParams } from '../../hooks/useListParams';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { toast } from 'sonner'

const TABS = [
  { key: 'overview', label: '통계 개요' },
  { key: 'list', label: '분석 목록' },
  { key: 'settings', label: '설정' },
];

const getStatusLabel = (status: string) => {
  const labels: Record<string, { text: string; color: string }> = {
    PENDING: { text: '대기', color: 'bg-gray-100 text-gray-800' },
    ANALYZING: { text: '분석중', color: 'bg-blue-100 text-blue-800' },
    COMPLETED: { text: '완료', color: 'bg-green-100 text-green-800' },
    FAILED: { text: '실패', color: 'bg-red-100 text-red-800' },
  };
  const label = labels[status] || { text: status, color: 'bg-gray-100' };
  return <span className={`px-2 py-1 text-xs rounded ${label.color}`}>{label.text}</span>;
};

export default function StoolAnalysisStatsPage() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('overview');
  const [limitInput, setLimitInput] = useState('');

  const { page, size, filters, setPage, setSize, setFilter } = useListParams({ defaultSize: 20 });
  const statusFilter = filters.status || '';

  const { data: stats } = useQuery({
    queryKey: ['stool-analysis-stats'],
    queryFn: () => stoolAnalysisAdminService.getStats(),
  });

  const { data: dailyLimit } = useQuery({
    queryKey: ['stool-daily-limit'],
    queryFn: () => stoolAnalysisAdminService.getDailyLimit(),
  });

  const { data: listData, isLoading: listLoading } = usePagedQuery<AdminStoolAnalysisItem>({
    queryKey: 'stool-analysis-list',
    fetchFn: (params) =>
      stoolAnalysisAdminService.getAnalyses({ page: params.page, status: params.status || undefined }),
    page,
    size,
    filters: { status: statusFilter || undefined },
    enabled: activeTab === 'list',
  });

  const updateLimitMutation = useMutation({
    mutationFn: (value: number) => stoolAnalysisAdminService.updateDailyLimit(value),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stool-daily-limit'] });
      setLimitInput('');
      toast.info('일일 제한이 업데이트되었습니다.');
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e?.response?.data?.message || '업데이트 실패'),
  });

  const handleUpdateLimit = () => {
    const value = parseInt(limitInput, 10);
    if (isNaN(value) || value < 0) {
      toast.info('유효한 숫자를 입력해주세요.');
      return;
    }
    updateLimitMutation.mutate(value);
  };

  const completedCount = stats?.statusDistribution?.COMPLETED ?? 0;
  const totalCount = stats?.totalCount ?? 0;
  const completedRate = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  const maxDailyCount = Math.max(...(stats?.dailyCounts?.map((d) => d.count) ?? [1]), 1);

  const columns: ColumnDef<AdminStoolAnalysisItem>[] = [
    { key: 'id', header: 'ID', align: 'right' },
    { key: 'petName', header: '반려동물' },
    { key: 'userNickname', header: '보호자' },
    {
      key: 'overallScore',
      header: '종합 점수',
      align: 'center',
      render: (_v, row) =>
        row.overallScore != null ? (
          <span className={`font-medium ${row.overallScore >= 70 ? 'text-green-600' : row.overallScore >= 40 ? 'text-yellow-600' : 'text-red-600'}`}>
            {row.overallScore}
          </span>
        ) : (
          <span className="text-gray-400">-</span>
        ),
    },
    { key: 'status', header: '상태', align: 'center', render: (_v, row) => getStatusLabel(row.status) },
    { key: 'createdAt', header: '요청일시', render: (_v, row) => row.createdAt ?? '-' },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">배변 건강 분석 관리</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="총 분석 건수" value={totalCount} icon={Activity} />
        <StatCard
          title="평균 건강 점수"
          value={stats?.avgOverallScore != null ? stats.avgOverallScore.toFixed(1) : '-'}
          icon={BarChart2}
        />
        <StatCard title="완료율" value={`${completedRate}%`} icon={CheckCircle} />
        <StatCard
          title="예상 AI 비용"
          value={`₩${(stats?.estimatedCostKrw ?? 0).toLocaleString()}`}
          icon={DollarSign}
        />
      </div>

      <Tabs tabs={TABS} activeTab={activeTab} onTabChange={setActiveTab} />

      {/* Overview Tab */}
      {activeTab === 'overview' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Daily count chart */}
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="font-semibold mb-4">최근 30일 일별 분석 건수</h2>
            {stats?.dailyCounts && stats.dailyCounts.length > 0 ? (
              <div className="space-y-2">
                {[...stats.dailyCounts].reverse().slice(-14).map((d) => (
                  <div key={d.date} className="flex items-center gap-3">
                    <span className="text-xs text-gray-500 w-24 shrink-0">{d.date}</span>
                    <div className="flex-1 h-5 bg-gray-100 rounded overflow-hidden">
                      <div
                        className="h-full bg-amber-400 rounded"
                        style={{ width: `${(d.count / maxDailyCount) * 100}%` }}
                      />
                    </div>
                    <span className="text-xs text-gray-700 w-6 text-right">{d.count}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-500">데이터가 없습니다.</p>
            )}
          </div>

          {/* Status distribution */}
          <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
            <h2 className="font-semibold mb-4">상태 분포</h2>
            <div className="space-y-3">
              {['COMPLETED', 'ANALYZING', 'PENDING', 'FAILED'].map((status) => {
                const count = stats?.statusDistribution?.[status] ?? 0;
                const pct = totalCount > 0 ? Math.round((count / totalCount) * 100) : 0;
                return (
                  <div key={status} className="flex items-center gap-3">
                    <span className="w-20 shrink-0">{getStatusLabel(status)}</span>
                    <div className="flex-1 h-4 bg-gray-100 rounded overflow-hidden">
                      <div
                        className={`h-full rounded ${
                          status === 'COMPLETED'
                            ? 'bg-green-400'
                            : status === 'ANALYZING'
                            ? 'bg-blue-400'
                            : status === 'PENDING'
                            ? 'bg-gray-400'
                            : 'bg-red-400'
                        }`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <span className="text-sm text-gray-700 w-20 text-right">
                      {count.toLocaleString()} ({pct}%)
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* List Tab */}
      {activeTab === 'list' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">분석 목록</h2>
            <select
              value={statusFilter}
              onChange={(e) => setFilter('status', e.target.value || null)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">전체</option>
              <option value="PENDING">대기</option>
              <option value="ANALYZING">분석중</option>
              <option value="COMPLETED">완료</option>
              <option value="FAILED">실패</option>
            </select>
          </div>
          <DataTable
            columns={columns}
            data={listData?.content ?? []}
            loading={listLoading}
            emptyMessage="분석 내역이 없습니다."
          />
          <div className="border-t border-gray-100">
            <Pagination
              page={page}
              totalPages={listData?.totalPages ?? 1}
              totalElements={listData?.totalElements ?? 0}
              size={size}
              onPageChange={setPage}
              onSizeChange={setSize}
            />
          </div>
        </div>
      )}

      {/* Settings Tab */}
      {activeTab === 'settings' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6 max-w-lg">
          <h2 className="font-semibold mb-4">분석 설정</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                일일 분석 제한 (stool.analysis.daily_limit)
              </label>
              <p className="text-sm text-gray-500 mb-2">
                현재 값: <span className="font-medium text-gray-900">{dailyLimit ?? 10}</span>건/일
              </p>
              <div className="flex gap-2">
                <input
                  type="number"
                  value={limitInput}
                  onChange={(e) => setLimitInput(e.target.value)}
                  placeholder={String(dailyLimit ?? 10)}
                  min="0"
                  className="flex-1 px-3 py-2 border rounded-lg text-sm"
                />
                <Button
                  variant="primary"
                  loading={updateLimitMutation.isPending}
                  onClick={handleUpdateLimit}
                >
                  저장
                </Button>
              </div>
              <p className="text-xs text-gray-400 mt-1">
                반려동물 1마리당 하루 최대 분석 가능 횟수입니다.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
