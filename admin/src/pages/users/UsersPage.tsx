import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { userManagementService } from '../../services/userManagementService';
import type { UserListItem } from '../../services/userManagementService';
import type { UserStatus } from '../../types/api';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { useListParams } from '../../hooks/useListParams';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { SearchFilters } from '../../components/common/SearchFilters';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '활성' },
  { value: 'SUSPENDED', label: '정지' },
  { value: 'DORMANT', label: '휴면' },
  { value: 'WITHDRAWN', label: '탈퇴' },
];

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '활성',
  SUSPENDED: '정지',
  DORMANT: '휴면',
  WITHDRAWN: '탈퇴',
};

const FILTER_FIELDS = [
  { type: 'text' as const, key: 'search', placeholder: '닉네임으로 검색...' },
  { type: 'select' as const, key: 'status', label: '전체 상태', options: STATUS_OPTIONS },
];

export default function UsersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const { page, size, search, filters, setPage, setSize, setFilter } = useListParams({ defaultSize: 20 });

  const { data, isLoading } = usePagedQuery<UserListItem>({
    queryKey: 'users',
    fetchFn: (params) => userManagementService.getUsers(params),
    page,
    size,
    filters: { search: search || undefined, ...filters },
  });

  const handleStatusChange = async (userId: number, newStatus: string) => {
    try {
      await userManagementService.updateUserStatus(userId, newStatus as UserStatus);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    } catch (error) {
      console.error('Failed to update status:', error);
      toast.error('상태 변경에 실패했습니다.');
    }
  };

  const handleDelete = async (userId: number) => {
    if (!(await confirmDialog({ description: '정말 이 회원을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await userManagementService.deleteUser(userId);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    } catch (error) {
      console.error('Failed to delete user:', error);
      toast.error('회원 삭제에 실패했습니다.');
    }
  };

  const columns: ColumnDef<UserListItem>[] = [
    {
      key: 'nickname',
      header: '회원',
      render: (_value, row) => (
        <div className="flex items-center">
          <div className="h-10 w-10 flex-shrink-0">
            {row.profileImageUrl ? (
              <img className="h-10 w-10 rounded-full" src={row.profileImageUrl} alt="" />
            ) : (
              <div className="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center">
                <span className="text-gray-500 text-sm">{row.nickname.charAt(0)}</span>
              </div>
            )}
          </div>
          <div className="ml-4">
            <div className="text-sm font-medium text-gray-900">{row.nickname}</div>
            <div className="text-sm text-gray-500">{row.email || '-'}</div>
          </div>
        </div>
      ),
    },
    {
      key: 'status',
      header: '상태',
      render: (_value, row) => (
        <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
          row.status === 'ACTIVE' ? 'bg-green-100 text-green-800' :
          row.status === 'SUSPENDED' ? 'bg-red-100 text-red-800' :
          row.status === 'DORMANT' ? 'bg-yellow-100 text-yellow-800' :
          'bg-gray-100 text-gray-800'
        }`}>
          {STATUS_LABELS[row.status] ?? row.status}
        </span>
      ),
    },
    { key: 'petCount', header: '펫', align: 'center' },
    { key: 'walkCount', header: '산책', align: 'center' },
    { key: 'postCount', header: '게시글', align: 'center' },
    { key: 'createdAt', header: '가입일' },
    {
      key: 'id',
      header: '액션',
      align: 'right',
      render: (_value, row) => (
        <div className="flex justify-end gap-2">
          <Button variant="link" size="sm" onClick={() => navigate(`/users/${row.id}`)}>
            상세
          </Button>
          {row.status === 'ACTIVE' ? (
            <Button variant="link" size="sm" className="text-orange-600 hover:text-orange-900" onClick={() => handleStatusChange(row.id, 'SUSPENDED')}>
              정지
            </Button>
          ) : (
            <Button variant="link" size="sm" className="text-green-600 hover:text-green-900" onClick={() => handleStatusChange(row.id, 'ACTIVE')}>
              활성화
            </Button>
          )}
          <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDelete(row.id)}>
            삭제
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">회원 관리</h1>
      </div>

      <SearchFilters
        fields={FILTER_FIELDS}
        values={{ search, ...filters }}
        onChange={(key, value) => {
          if (key === 'search') {
            setFilter('search', value || null);
          } else {
            setFilter(key, value || null);
          }
        }}
      />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <DataTable<UserListItem>
          columns={columns}
          data={data?.content ?? []}
          loading={isLoading}
          emptyMessage="회원이 없습니다."
        />
        {data && (
          <Pagination
            page={page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            size={size}
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        )}
      </div>
    {ConfirmDialog}
    </div>
  );
}
