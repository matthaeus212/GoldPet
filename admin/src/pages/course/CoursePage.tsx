import { useNavigate } from 'react-router-dom';
import { useQueryClient, useMutation } from '@tanstack/react-query';
import { courseManagementService } from '../../services/courseManagementService';
import type { CourseListItem } from '../../services/courseManagementService';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { SearchFilters } from '../../components/common/SearchFilters';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { useListParams } from '../../hooks/useListParams';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const DIFFICULTY_BADGE: Record<string, { label: string; className: string }> = {
  EASY: { label: '초급', className: 'bg-green-100 text-green-800' },
  MODERATE: { label: '중급', className: 'bg-amber-100 text-amber-800' },
  HARD: { label: '상급', className: 'bg-red-100 text-red-800' },
};

export default function CoursePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const { page, size, search, filters, setPage, setSize, setSearch, setFilter } = useListParams({ defaultSize: 20 });

  const { data, isLoading } = usePagedQuery<CourseListItem>({
    queryKey: 'courses',
    fetchFn: (params) => courseManagementService.getCourses({
      page: params.page,
      size: params.size,
      search: params.search || undefined,
      difficulty: params.difficulty || undefined,
      isPublished: params.isPublished !== undefined ? params.isPublished === 'true' : undefined,
    }),
    page,
    size,
    filters: { search, ...filters },
  });

  const hideMutation = useMutation({
    mutationFn: (courseId: number) => courseManagementService.hideCourse(courseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 숨김에 실패했습니다.'),
  });

  const unhideMutation = useMutation({
    mutationFn: (courseId: number) => courseManagementService.unhideCourse(courseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 숨김 해제에 실패했습니다.'),
  });

  const deleteMutation = useMutation({
    mutationFn: (courseId: number) => courseManagementService.deleteCourse(courseId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['courses'] }),
    onError: () => toast.error('코스 삭제에 실패했습니다.'),
  });

  const handleHide = async (courseId: number) => {
    if (!(await confirmDialog({ description: '이 코스를 숨기시겠습니까?' }))) return;
    hideMutation.mutate(courseId);
  };

  const handleUnhide = async (courseId: number) => {
    if (!(await confirmDialog({ description: '이 코스의 숨김을 해제하시겠습니까?' }))) return;
    unhideMutation.mutate(courseId);
  };

  const handleDelete = async (courseId: number) => {
    if (!(await confirmDialog({ description: '정말 이 코스를 삭제하시겠습니까? 이 작업은 취소할 수 없습니다.', variant: 'destructive' }))) return;
    deleteMutation.mutate(courseId);
  };

  const columns: ColumnDef<CourseListItem>[] = [
    {
      key: 'title',
      header: '제목',
      render: (_val, row) => (
        <div
          className="text-sm font-medium text-blue-600 hover:text-blue-800 cursor-pointer max-w-xs truncate"
          onClick={(e) => { e.stopPropagation(); navigate(`/courses/${row.id}`); }}
        >
          {row.title}
        </div>
      ),
    },
    { key: 'authorNickname', header: '작성자' },
    {
      key: 'difficulty',
      header: '난이도',
      render: (val) => {
        const badge = DIFFICULTY_BADGE[val as string] ?? { label: val, className: 'bg-gray-100 text-gray-800' };
        return (
          <span className={`px-2 py-1 text-xs font-semibold rounded-full ${badge.className}`}>
            {badge.label}
          </span>
        );
      },
    },
    { key: 'region', header: '지역' },
    { key: 'likeCount', header: '좋아요', align: 'center' },
    { key: 'commentCount', header: '댓글', align: 'center' },
    { key: 'walkCount', header: '산책수', align: 'center' },
    {
      key: 'isHidden',
      header: '상태',
      render: (val) => val ? (
        <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-red-100 text-red-800">
          숨김
        </span>
      ) : null,
    },
    {
      key: 'id',
      header: '액션',
      align: 'right',
      render: (_val, row) => (
        <div className="flex justify-end gap-2">
          <Button variant="link" size="sm" onClick={(e) => { e.stopPropagation(); navigate(`/courses/${row.id}`); }}>
            상세
          </Button>
          {!row.isHidden ? (
            <Button variant="link" size="sm" className="text-orange-600 hover:text-orange-900"
              onClick={(e) => { e.stopPropagation(); handleHide(row.id); }}>
              숨김
            </Button>
          ) : (
            <Button variant="link" size="sm" className="text-green-600 hover:text-green-900"
              onClick={(e) => { e.stopPropagation(); handleUnhide(row.id); }}>
              숨김 해제
            </Button>
          )}
          <Button variant="link" size="sm" className="text-red-600 hover:text-red-900"
            onClick={(e) => { e.stopPropagation(); handleDelete(row.id); }}>
            삭제
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">코스 관리</h1>
      </div>

      <SearchFilters
        fields={[
          { type: 'text', key: 'search', placeholder: '제목으로 검색...' },
          {
            type: 'button-group',
            key: 'difficulty',
            options: [
              { value: 'EASY', label: '쉬움' },
              { value: 'MODERATE', label: '보통' },
              { value: 'HARD', label: '어려움' },
            ],
          },
          {
            type: 'button-group',
            key: 'isPublished',
            options: [
              { value: 'true', label: '공개' },
              { value: 'false', label: '숨김' },
            ],
          },
        ]}
        values={{ search, ...filters }}
        onChange={(key, value) => {
          if (key === 'search') setSearch(value);
          else setFilter(key, value || null);
        }}
      />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
        <DataTable<CourseListItem>
          columns={columns}
          data={data?.content ?? []}
          loading={isLoading}
          emptyMessage="코스가 없습니다."
          onRowClick={(row) => navigate(`/courses/${row.id}`)}
          rowClassName={(row) => row.isHidden ? 'bg-gray-100 opacity-60' : ''}
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
