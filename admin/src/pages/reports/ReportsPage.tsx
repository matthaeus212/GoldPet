import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { reportService } from '../../services/reportService';
import type { ReportItem } from '../../services/reportService';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { SearchFilters } from '../../components/common/SearchFilters';
import { Button } from '../../components/common/Button';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { useListParams } from '../../hooks/useListParams';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '대기' },
  { value: 'RESOLVED', label: '처리됨' },
  { value: 'DISMISSED', label: '기각' },
];

const TYPE_LABELS: Record<string, string> = {
  POST: '게시글',
  COMMENT: '댓글',
  USER: '회원',
  CHAT: '채팅',
};

const STATUS_BADGE_STYLES: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  RESOLVED: 'bg-green-100 text-green-800',
  DISMISSED: 'bg-gray-100 text-gray-800',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: '대기',
  RESOLVED: '처리됨',
  DISMISSED: '기각',
};

export default function ReportsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const { page, size, filters, setPage, setSize, setFilter } = useListParams({ defaultSize: 20 });

  const status = filters.status || 'PENDING';

  const { data, isLoading } = usePagedQuery<ReportItem>({
    queryKey: 'reports',
    fetchFn: ({ page, status }) =>
      reportService.getReports({ page, status }),
    page,
    size,
    filters: { status },
  });

  const handleDismiss = async (reportId: number) => {
    if (!(await confirmDialog({ description: '기각하시겠습니까?' }))) return;
    try {
      await reportService.dismissReport(reportId);
      queryClient.invalidateQueries({ queryKey: ['reports'] });
    } catch {
      toast.error('처리에 실패했습니다.');
    }
  };

  const columns: ColumnDef<ReportItem>[] = [
    {
      key: 'type',
      header: '유형',
      render: (value: string) => (
        <span className="px-2 py-1 text-xs bg-gray-100 rounded">{TYPE_LABELS[value] ?? value}</span>
      ),
    },
    {
      key: 'targetPreview',
      header: '대상',
      className: 'max-w-xs truncate',
    },
    {
      key: 'reason',
      header: '사유',
    },
    {
      key: 'reporterNickname',
      header: '신고자',
      render: (value: string) => <span className="text-gray-500">{value}</span>,
    },
    {
      key: 'status',
      header: '상태',
      render: (value: string) => (
        <span className={`px-2 py-1 text-xs rounded-full ${STATUS_BADGE_STYLES[value] ?? ''}`}>
          {STATUS_LABELS[value] ?? value}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: '신고일',
      render: (value: string | null) => <span className="text-gray-500">{value ?? '-'}</span>,
    },
    {
      key: 'id',
      header: '액션',
      align: 'right',
      render: (_value: number, row: ReportItem) => (
        <div className="flex justify-end gap-2">
          <Button variant="link" size="sm" onClick={(e) => { e.stopPropagation(); navigate(`/reports/${row.id}`); }}>
            상세
          </Button>
          {row.status === 'PENDING' && (
            <>
              <Button
                variant="link"
                size="sm"
                className="text-green-600 hover:text-green-900"
                onClick={(e) => { e.stopPropagation(); navigate(`/reports/${row.id}`); }}
              >
                조치
              </Button>
              <Button
                variant="link"
                size="sm"
                className="text-gray-600 hover:text-gray-900"
                onClick={(e) => { e.stopPropagation(); handleDismiss(row.id); }}
              >
                기각
              </Button>
            </>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">신고 관리</h1>
      </div>

      <SearchFilters
        fields={[
          {
            type: 'button-group',
            key: 'status',
            options: STATUS_OPTIONS,
          },
        ]}
        values={{ status }}
        onChange={(key, value) => setFilter(key, value || null)}
      />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <DataTable<ReportItem>
          columns={columns}
          data={data?.content ?? []}
          loading={isLoading}
          emptyMessage="신고가 없습니다."
          onRowClick={(row) => navigate(`/reports/${row.id}`)}
        />
        <div className="border-t border-gray-200">
          <Pagination
            page={page}
            totalPages={data?.totalPages ?? 0}
            totalElements={data?.totalElements ?? 0}
            size={size}
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        </div>
      </div>
    {ConfirmDialog}
    </div>
  );
}
