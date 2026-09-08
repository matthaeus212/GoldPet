import { useState } from 'react';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { Camera, Clock, CheckCircle, BarChart2, XCircle, RefreshCw } from 'lucide-react';
import { aiProfileAdminService } from '../../services/aiProfileAdminService';
import type {
  AIStyleAdminResponse,
  AIStyleAdminRequest,
} from '../../services/aiProfileAdminService';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { useListParams } from '../../hooks/useListParams';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { Tabs } from '../../components/common/Tabs';
import { StatCard } from '../../components/common/StatCard';
import { Button } from '../../components/common/Button';
import type { AIRequestAdminResponse } from '../../services/aiProfileAdminService';
import AIProfileLoadingTipsPage from './AIProfileLoadingTipsPage';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const AI_PROFILE_TABS = [
  { key: 'requests', label: '요청 관리' },
  { key: 'styles', label: '스타일 관리' },
  { key: 'loading-tips', label: '로딩 팁 관리' },
];

const getStatusBadge = (status: string) => {
  const styles: Record<string, string> = {
    PENDING: 'bg-yellow-100 text-yellow-800',
    PROCESSING: 'bg-blue-100 text-blue-800',
    COMPLETED: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    REFUNDED: 'bg-gray-100 text-gray-800',
  };
  const labels: Record<string, string> = {
    PENDING: '대기중',
    PROCESSING: '처리중',
    COMPLETED: '완료',
    FAILED: '실패',
    REFUNDED: '환불',
  };
  return (
    <span className={`px-2 py-1 text-xs rounded-full ${styles[status] || 'bg-gray-100 text-gray-800'}`}>
      {labels[status] || status}
    </span>
  );
};

export default function AIProfilePage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const [activeTab, setActiveTab] = useState<string>('requests');
  const [styles, setStyles] = useState<AIStyleAdminResponse[]>([]);
  const [stylesLoading, setStylesLoading] = useState(false);
  const [showStyleModal, setShowStyleModal] = useState(false);
  const [editingStyle, setEditingStyle] = useState<AIStyleAdminResponse | null>(null);
  const [styleForm, setStyleForm] = useState<AIStyleAdminRequest>({
    id: '',
    name: '',
    description: '',
    previewUrl: '',
    goldCost: 10,
    isActive: true,
    displayOrder: 0,
  });

  const [selectedRequest, setSelectedRequest] = useState<AIRequestAdminResponse | null>(null);

  const { page, size, filters, setPage, setSize, setFilter } = useListParams({ defaultSize: 20 });
  const statusFilter = filters.status || '';

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['ai-profile-stats'],
    queryFn: () => aiProfileAdminService.getStats(),
  });

  const { data: requests, isLoading: requestsLoading } = usePagedQuery<AIRequestAdminResponse>({
    queryKey: 'ai-profile-requests',
    fetchFn: (params) => aiProfileAdminService.getRequests(params.page, params.size, params.status || undefined),
    page,
    size,
    filters: { status: statusFilter || undefined },
    enabled: activeTab === 'requests',
  });

  const loadStyles = async () => {
    setStylesLoading(true);
    try {
      const data = await aiProfileAdminService.getStyles();
      setStyles(data);
    } catch (error) {
      console.error('Failed to load styles:', error);
    } finally {
      setStylesLoading(false);
    }
  };

  const handleTabChange = async (key: string) => {
    setActiveTab(key);
    if (key === 'styles') {
      loadStyles();
    }
  };

  const handleRetry = async (id: number) => {
    if (!(await confirmDialog({ description: '이 요청을 다시 시도하시겠습니까?' }))) return;
    try {
      await aiProfileAdminService.retryRequest(id);
      queryClient.invalidateQueries({ queryKey: ['ai-profile-requests'] });
      queryClient.invalidateQueries({ queryKey: ['ai-profile-stats'] });
    } catch (error) {
      console.error('Failed to retry request:', error);
      toast.error('재시도에 실패했습니다.');
    }
  };

  const handleRefund = async (id: number) => {
    if (!(await confirmDialog({ description: '이 요청을 환불 처리하시겠습니까?' }))) return;
    try {
      await aiProfileAdminService.refundRequest(id);
      queryClient.invalidateQueries({ queryKey: ['ai-profile-requests'] });
      queryClient.invalidateQueries({ queryKey: ['ai-profile-stats'] });
    } catch (error) {
      console.error('Failed to refund request:', error);
      toast.error('환불 처리에 실패했습니다.');
    }
  };

  const handleForceFail = async (id: number) => {
    if (!(await confirmDialog({ description: '이 요청을 강제 실패 처리하시겠습니까? 골드가 환불됩니다.' }))) return;
    try {
      await aiProfileAdminService.forceFailRequest(id);
      queryClient.invalidateQueries({ queryKey: ['ai-profile-requests'] });
      queryClient.invalidateQueries({ queryKey: ['ai-profile-stats'] });
    } catch (error) {
      console.error('Failed to force fail request:', error);
      toast.error('강제 실패 처리에 실패했습니다.');
    }
  };

  const handleViewRequestDetail = async (id: number) => {
    try {
      const detail = await aiProfileAdminService.getRequestDetail(id);
      setSelectedRequest(detail);
    } catch (error) {
      console.error('Failed to load request detail:', error);
      toast.error('요청 상세 정보를 불러올 수 없습니다.');
    }
  };

  const handleToggleStyle = async (id: string) => {
    try {
      await aiProfileAdminService.toggleStyle(id);
      loadStyles();
    } catch (error) {
      console.error('Failed to toggle style:', error);
      toast.error('스타일 상태 변경에 실패했습니다.');
    }
  };

  const handleDeleteStyle = async (id: string) => {
    if (!(await confirmDialog({ description: '이 스타일을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await aiProfileAdminService.deleteStyle(id);
      loadStyles();
    } catch (error) {
      console.error('Failed to delete style:', error);
      toast.error('스타일 삭제에 실패했습니다.');
    }
  };

  const handleOpenAddStyle = () => {
    setEditingStyle(null);
    setStyleForm({ id: '', name: '', description: '', previewUrl: '', goldCost: 10, isActive: true, displayOrder: 0 });
    setShowStyleModal(true);
  };

  const handleOpenEditStyle = (style: AIStyleAdminResponse) => {
    setEditingStyle(style);
    setStyleForm({
      id: style.id,
      name: style.name,
      description: style.description || '',
      previewUrl: style.previewUrl || '',
      goldCost: style.goldCost,
      isActive: style.isActive,
      displayOrder: style.displayOrder,
    });
    setShowStyleModal(true);
  };

  const handleSaveStyle = async () => {
    try {
      if (editingStyle) {
        await aiProfileAdminService.updateStyle(editingStyle.id, styleForm);
      } else {
        await aiProfileAdminService.createStyle(styleForm);
      }
      setShowStyleModal(false);
      loadStyles();
    } catch (error) {
      console.error('Failed to save style:', error);
      toast.error('스타일 저장에 실패했습니다.');
    }
  };

  const requestColumns: ColumnDef<AIRequestAdminResponse>[] = [
    { key: 'id', header: 'ID', width: '60px' },
    {
      key: 'userNickname',
      header: '사용자',
      render: (value, row) => value || `User ${row.userId}`,
    },
    { key: 'petName', header: '반려동물' },
    {
      key: 'stylePrompt',
      header: '스타일',
      render: (value, row) => value || row.type,
    },
    {
      key: 'status',
      header: '상태',
      render: (value) => getStatusBadge(value),
    },
    {
      key: 'goldCost',
      header: '비용(Gold)',
      align: 'right',
      render: (value) => value.toLocaleString(),
    },
    {
      key: 'createdAt',
      header: '요청일시',
      render: (value) => new Date(value).toLocaleString('ko-KR'),
    },
    {
      key: 'id',
      header: '액션',
      align: 'center',
      render: (_value, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button variant="link" size="sm" onClick={() => handleViewRequestDetail(row.id)}>상세</Button>
          {(row.status === 'PENDING' || row.status === 'PROCESSING') && (
            <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleForceFail(row.id)}>강제 실패</Button>
          )}
          {row.status === 'FAILED' && (
            <>
              <Button variant="link" size="sm" onClick={() => handleRetry(row.id)}>재시도</Button>
              <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleRefund(row.id)}>환불</Button>
            </>
          )}
        </div>
      ),
    },
  ];

  if (statsLoading) {
    return <div className="flex items-center justify-center h-64">로딩 중...</div>;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">AI 프로필 관리</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="총 요청" value={stats?.totalRequests || 0} icon={Camera} />
        <StatCard title="대기중" value={stats?.pendingRequests || 0} icon={Clock} />
        <StatCard title="완료" value={stats?.completedRequests || 0} icon={CheckCircle} />
        <StatCard title="성공률" value={`${stats?.successRate?.toFixed(1) || 0}%`} icon={BarChart2} />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <StatCard title="실패" value={stats?.failedRequests || 0} icon={XCircle} />
        <StatCard title="환불" value={stats?.refundedRequests || 0} icon={RefreshCw} />
      </div>

      <Tabs tabs={AI_PROFILE_TABS} activeTab={activeTab} onTabChange={handleTabChange} />

      {/* Requests Tab */}
      {activeTab === 'requests' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">AI 프로필 요청 목록</h2>
            <select
              value={statusFilter}
              onChange={(e) => {
                setFilter('status', e.target.value || null);
                setPage(0);
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">전체</option>
              <option value="PENDING">대기중</option>
              <option value="PROCESSING">처리중</option>
              <option value="COMPLETED">완료</option>
              <option value="FAILED">실패</option>
              <option value="REFUNDED">환불</option>
            </select>
          </div>
          <DataTable<AIRequestAdminResponse>
            columns={requestColumns}
            data={requests?.content || []}
            loading={requestsLoading}
            emptyMessage="요청이 없습니다."
          />
          {requests && requests.totalPages > 0 && (
            <div className="border-t">
              <Pagination
                page={page}
                totalPages={requests.totalPages}
                totalElements={requests.totalElements}
                size={size}
                onPageChange={setPage}
                onSizeChange={setSize}
              />
            </div>
          )}
        </div>
      )}

      {/* Styles Tab */}
      {activeTab === 'styles' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">AI 스타일 목록</h2>
            <Button variant="primary" onClick={handleOpenAddStyle}>+ 스타일 추가</Button>
          </div>
          <div className="p-6">
            {stylesLoading ? (
              <div className="text-center text-gray-500 py-8">로딩 중...</div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {styles.length === 0 ? (
                  <div className="col-span-full text-center text-gray-500 py-8">등록된 스타일이 없습니다.</div>
                ) : (
                  styles.map((style) => (
                    <div key={style.id} className="border border-gray-200 rounded-lg overflow-hidden">
                      {style.previewUrl && (
                        <img src={style.previewUrl} alt={style.name} className="w-full h-48 object-cover" />
                      )}
                      <div className="p-4">
                        <div className="flex items-start justify-between mb-2">
                          <h3 className="font-semibold text-lg">{style.name}</h3>
                          <span className={`px-2 py-1 text-xs rounded-full ${
                            style.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                          }`}>
                            {style.isActive ? '활성' : '비활성'}
                          </span>
                        </div>
                        {style.description && (
                          <p className="text-sm text-gray-600 mb-3">{style.description}</p>
                        )}
                        <div className="flex items-center justify-between text-sm mb-4">
                          <span className="text-gray-500">비용: {style.goldCost.toLocaleString()} Gold</span>
                          <span className="text-gray-500">순서: {style.displayOrder}</span>
                        </div>
                        <div className="flex gap-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="flex-1"
                            onClick={() => handleToggleStyle(style.id)}
                          >
                            {style.isActive ? '비활성화' : '활성화'}
                          </Button>
                          <Button
                            variant="primary"
                            size="sm"
                            className="flex-1"
                            onClick={() => handleOpenEditStyle(style)}
                          >
                            수정
                          </Button>
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => handleDeleteStyle(style.id)}
                          >
                            삭제
                          </Button>
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Loading Tips Tab */}
      {activeTab === 'loading-tips' && (
        <AIProfileLoadingTipsPage />
      )}

      {/* Style Create/Edit Modal */}
      {showStyleModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h3 className="text-lg font-semibold mb-4">
              {editingStyle ? '스타일 수정' : '새 스타일 추가'}
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">ID</label>
                <input
                  type="text"
                  value={styleForm.id}
                  onChange={(e) => setStyleForm({ ...styleForm, id: e.target.value })}
                  disabled={!!editingStyle}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm disabled:bg-gray-100"
                  placeholder="예: cartoon, watercolor"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">이름</label>
                <input
                  type="text"
                  value={styleForm.name}
                  onChange={(e) => setStyleForm({ ...styleForm, name: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="스타일 이름"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">설명</label>
                <textarea
                  value={styleForm.description}
                  onChange={(e) => setStyleForm({ ...styleForm, description: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  rows={2}
                  placeholder="스타일 설명"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">미리보기 URL</label>
                <input
                  type="text"
                  value={styleForm.previewUrl}
                  onChange={(e) => setStyleForm({ ...styleForm, previewUrl: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="/styles/example.jpg"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">비용 (Gold)</label>
                  <input
                    type="number"
                    value={styleForm.goldCost}
                    onChange={(e) => setStyleForm({ ...styleForm, goldCost: parseInt(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    min={0}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">표시 순서</label>
                  <input
                    type="number"
                    value={styleForm.displayOrder}
                    onChange={(e) => setStyleForm({ ...styleForm, displayOrder: parseInt(e.target.value) || 0 })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    min={0}
                  />
                </div>
              </div>
              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="styleActive"
                  checked={styleForm.isActive}
                  onChange={(e) => setStyleForm({ ...styleForm, isActive: e.target.checked })}
                  className="h-4 w-4 text-indigo-600 rounded"
                />
                <label htmlFor="styleActive" className="ml-2 text-sm text-gray-700">활성화</label>
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button variant="secondary" onClick={() => setShowStyleModal(false)}>취소</Button>
              <Button variant="primary" onClick={handleSaveStyle}>
                {editingStyle ? '수정' : '추가'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Request Detail Modal */}
      {selectedRequest && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
            <h3 className="text-lg font-semibold mb-4">AI 프로필 요청 상세</h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-gray-500">사용자</p>
                  <p className="font-medium">{selectedRequest.userNickname || `User ${selectedRequest.userId}`}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">반려동물</p>
                  <p className="font-medium">{selectedRequest.petName}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">스타일</p>
                  <p className="font-medium">{selectedRequest.stylePrompt || selectedRequest.type}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">상태</p>
                  {getStatusBadge(selectedRequest.status)}
                </div>
                <div>
                  <p className="text-sm text-gray-500">비용</p>
                  <p className="font-medium">{selectedRequest.goldCost.toLocaleString()} Gold</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500">요청일시</p>
                  <p className="font-medium">{selectedRequest.createdAt ? new Date(selectedRequest.createdAt).toLocaleString('ko-KR') : '-'}</p>
                </div>
              </div>
              {selectedRequest.sourceImageUrl && (
                <div>
                  <p className="text-sm text-gray-500 mb-1">소스 이미지</p>
                  <img src={selectedRequest.sourceImageUrl} alt="소스" className="w-32 h-32 object-cover rounded-lg border" />
                </div>
              )}
              {selectedRequest.resultUrl && (
                <div>
                  <p className="text-sm text-gray-500 mb-1">결과 이미지</p>
                  <img src={selectedRequest.resultUrl} alt="결과" className="w-32 h-32 object-cover rounded-lg border" />
                </div>
              )}
              {selectedRequest.errorMessage && (
                <div>
                  <p className="text-sm text-gray-500 mb-1">에러 메시지</p>
                  <p className="text-sm text-red-600 bg-red-50 p-2 rounded">{selectedRequest.errorMessage}</p>
                </div>
              )}
            </div>
            <div className="mt-6 flex justify-end">
              <Button variant="secondary" onClick={() => setSelectedRequest(null)}>닫기</Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </div>
  );
}
