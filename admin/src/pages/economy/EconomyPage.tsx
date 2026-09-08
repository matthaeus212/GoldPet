import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Coins, TrendingUp, DollarSign, Star, ClipboardList } from 'lucide-react';
import { economyService } from '../../services/economyService';
import type { TransactionItem, GoldProductAdmin } from '../../services/economyService';
import { userManagementService } from '../../services/userManagementService';
import type { UserListItem } from '../../services/userManagementService';
import { Tabs } from '../../components/common/Tabs';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Pagination } from '../../components/common/Pagination';
import { StatCard } from '../../components/common/StatCard';
import { Button } from '../../components/common/Button';
import { useListParams } from '../../hooks/useListParams';
import { usePagedQuery } from '../../hooks/usePagedQuery';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const TABS = [
  { key: 'transactions', label: '거래 내역' },
  { key: 'products', label: '상품 관리' },
  { key: 'adjust', label: 'Gold 조정' },
];

const getTypeLabel = (type: string) => {
  const labels: Record<string, { text: string; color: string }> = {
    CHARGE: { text: '충전', color: 'bg-green-100 text-green-800' },
    SPEND: { text: '사용', color: 'bg-red-100 text-red-800' },
    REWARD: { text: '보상', color: 'bg-blue-100 text-blue-800' },
    REFUND: { text: '환불', color: 'bg-yellow-100 text-yellow-800' },
    ADMIN_ADJUST: { text: '관리자 조정', color: 'bg-purple-100 text-purple-800' },
  };
  const label = labels[type] || { text: type, color: 'bg-gray-100' };
  return <span className={`px-2 py-1 text-xs rounded ${label.color}`}>{label.text}</span>;
};

export default function EconomyPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const [activeTab, setActiveTab] = useState('transactions');

  // Transaction filter
  const { page, size, filters, setPage, setSize, setFilter } = useListParams({ defaultSize: 20 });
  const typeFilter = filters.type || '';

  // Refund modal state
  const [refundTarget, setRefundTarget] = useState<TransactionItem | null>(null);
  const [refundReason, setRefundReason] = useState('');

  // Adjust form state
  const [adjustUserId, setAdjustUserId] = useState('');
  const [adjustAmount, setAdjustAmount] = useState('');
  const [adjustReason, setAdjustReason] = useState('');
  // 회원 검색(숫자 ID 대신 닉네임/이메일로 선택)
  const [adjustQuery, setAdjustQuery] = useState('');
  const [adjustSelectedUser, setAdjustSelectedUser] = useState<UserListItem | null>(null);

  // Product form state
  const [showProductForm, setShowProductForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState<GoldProductAdmin | null>(null);
  const [productForm, setProductForm] = useState({
    productCode: '',
    name: '',
    goldAmount: '',
    price: '',
    bonus: '0',
    displayOrder: '1',
  });

  // Queries
  const { data: statsData } = useQuery({
    queryKey: ['economy-stats'],
    queryFn: () => economyService.getStats(),
  });

  const { data: txData, isLoading: txLoading } = usePagedQuery<TransactionItem>({
    queryKey: 'economy-transactions',
    fetchFn: (params) =>
      economyService.getTransactions({ page: params.page, type: params.type || undefined }),
    page,
    size,
    filters: { type: typeFilter || undefined },
    enabled: activeTab === 'transactions',
  });

  const { data: products = [], isLoading: productsLoading } = useQuery({
    queryKey: ['economy-products'],
    queryFn: () => economyService.getProducts(),
    enabled: activeTab === 'products',
  });

  // Mutations
  const refundMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      economyService.refundTransaction(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['economy-transactions'] });
      setRefundTarget(null);
      setRefundReason('');
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e?.response?.data?.message || '환불 처리 실패'),
  });

  const adjustMutation = useMutation({
    mutationFn: ({ userId, amount, reason }: { userId: number; amount: number; reason: string }) =>
      economyService.adjustGold(userId, amount, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['economy-transactions'] });
      queryClient.invalidateQueries({ queryKey: ['economy-stats'] });
      setAdjustUserId('');
      setAdjustAmount('');
      setAdjustReason('');
      setAdjustQuery('');
      setAdjustSelectedUser(null);
      toast.success('골드 조정이 완료되었습니다.');
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e?.response?.data?.message || '골드 조정 실패'),
  });

  // 회원 검색 결과(닉네임/이메일) — 선택 전에만 조회
  const { data: adjustSearchResults = [], isFetching: adjustSearching } = useQuery({
    queryKey: ['admin-user-search', adjustQuery],
    queryFn: () => userManagementService.searchUsers(adjustQuery.trim(), 8),
    enabled: adjustQuery.trim().length >= 1 && !adjustSelectedUser,
  });

  const createProductMutation = useMutation({
    mutationFn: () =>
      economyService.createProduct({
        productCode: productForm.productCode,
        name: productForm.name,
        goldAmount: parseInt(productForm.goldAmount),
        price: parseInt(productForm.price),
        bonus: parseInt(productForm.bonus) || 0,
        displayOrder: parseInt(productForm.displayOrder) || 1,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['economy-products'] });
      resetProductForm();
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e?.response?.data?.message || '상품 처리 실패'),
  });

  const updateProductMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Parameters<typeof economyService.updateProduct>[1] }) =>
      economyService.updateProduct(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['economy-products'] });
      resetProductForm();
    },
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e?.response?.data?.message || '상품 처리 실패'),
  });

  const deleteProductMutation = useMutation({
    mutationFn: (id: number) => economyService.deleteProduct(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['economy-products'] }),
    onError: (e: { response?: { data?: { message?: string } } }) => toast.error(e?.response?.data?.message || '상품 삭제에 실패했습니다.'),
  });

  const handleDeleteProduct = async (id: number) => {
    if (!(await confirmDialog({ description: '이 상품을 삭제하시겠습니까?', variant: 'destructive' }))) return;
    deleteProductMutation.mutate(id);
  };

  // Handlers
  const handleRefund = () => {
    if (!refundTarget || !refundReason.trim()) return;
    refundMutation.mutate({ id: refundTarget.id, reason: refundReason });
  };

  const handleAdjust = () => {
    const userId = parseInt(adjustUserId);
    const amount = parseInt(adjustAmount);
    if (!userId || !amount || !adjustReason.trim()) {
      toast.info('모든 항목을 입력해주세요.');
      return;
    }
    adjustMutation.mutate({ userId, amount, reason: adjustReason });
  };

  const handleProductSubmit = () => {
    if (editingProduct) {
      updateProductMutation.mutate({
        id: editingProduct.id,
        data: {
          name: productForm.name,
          goldAmount: parseInt(productForm.goldAmount),
          price: parseInt(productForm.price),
          bonus: parseInt(productForm.bonus),
          displayOrder: parseInt(productForm.displayOrder),
        },
      });
    } else {
      createProductMutation.mutate();
    }
  };

  const handleToggleProduct = (product: GoldProductAdmin) => {
    updateProductMutation.mutate({ id: product.id, data: { isActive: !product.isActive } });
  };

  const startEditProduct = (product: GoldProductAdmin) => {
    setEditingProduct(product);
    setProductForm({
      productCode: product.productCode,
      name: product.name,
      goldAmount: product.goldAmount.toString(),
      price: product.price.toString(),
      bonus: product.bonus.toString(),
      displayOrder: product.displayOrder.toString(),
    });
    setShowProductForm(true);
  };

  const resetProductForm = () => {
    setShowProductForm(false);
    setEditingProduct(null);
    setProductForm({ productCode: '', name: '', goldAmount: '', price: '', bonus: '0', displayOrder: '1' });
  };

  // Column defs
  const txColumns: ColumnDef<TransactionItem>[] = [
    { key: 'type', header: '유형', render: (_v, row) => getTypeLabel(row.type) },
    { key: 'userNickname', header: '회원' },
    { key: 'description', header: '설명' },
    {
      key: 'amount',
      header: '금액',
      align: 'right',
      render: (_v, row) => (
        <span className={`font-medium ${row.amount >= 0 ? 'text-green-600' : 'text-red-600'}`}>
          {row.amount >= 0 ? '+' : ''}{row.amount.toLocaleString()}
        </span>
      ),
    },
    { key: 'createdAt', header: '일시', render: (_v, row) => row.createdAt ?? '-' },
    {
      key: 'actions',
      header: '작업',
      align: 'center',
      render: (_v, row) =>
        row.type === 'SPEND' ? (
          <Button variant="warning" size="sm" onClick={() => setRefundTarget(row)}>
            환불
          </Button>
        ) : null,
    },
  ];

  const productColumns: ColumnDef<GoldProductAdmin>[] = [
    { key: 'productCode', header: '코드', render: (_v, row) => <span className="font-mono text-sm">{row.productCode}</span> },
    { key: 'name', header: '상품명' },
    { key: 'goldAmount', header: '골드', align: 'right', render: (_v, row) => row.goldAmount.toLocaleString() },
    { key: 'price', header: '가격', align: 'right', render: (_v, row) => `₩${row.price.toLocaleString()}` },
    { key: 'bonus', header: '보너스', align: 'right' },
    {
      key: 'isActive',
      header: '상태',
      align: 'center',
      render: (_v, row) => (
        <Button
          variant={row.isActive ? 'success' : 'ghost'}
          size="sm"
          onClick={() => handleToggleProduct(row)}
        >
          {row.isActive ? '활성' : '비활성'}
        </Button>
      ),
    },
    {
      key: 'actions',
      header: '작업',
      align: 'center',
      render: (_v, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button variant="link" size="sm" onClick={() => startEditProduct(row)}>
            수정
          </Button>
          <Button
            variant="link"
            size="sm"
            className="text-red-600 hover:text-red-900"
            onClick={() => handleDeleteProduct(row.id)}
          >
            삭제
          </Button>
        </div>
      ),
    },
  ];

  const stats = statsData;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Gold/결제 관리</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard title="총 Gold 유통량" value={stats?.totalGoldCirculation ?? 0} icon={Coins} />
        <StatCard title="총 매출" value={`₩${(stats?.totalRevenue ?? 0).toLocaleString()}`} icon={TrendingUp} />
        <StatCard title="오늘 매출" value={`₩${(stats?.todayRevenue ?? 0).toLocaleString()}`} icon={DollarSign} />
        <StatCard title="활성 구독" value={stats?.activeSubscriptions ?? 0} icon={Star} />
        <StatCard title="대기 정산" value={stats?.pendingPayouts ?? 0} icon={ClipboardList} />
      </div>

      <Tabs tabs={TABS} activeTab={activeTab} onTabChange={setActiveTab} />

      {/* Transactions Tab */}
      {activeTab === 'transactions' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">거래 내역</h2>
            <select
              value={typeFilter}
              onChange={(e) => setFilter('type', e.target.value || null)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="">전체</option>
              <option value="CHARGE">충전</option>
              <option value="SPEND">사용</option>
              <option value="REWARD">보상</option>
              <option value="REFUND">환불</option>
              <option value="ADMIN_ADJUST">관리자 조정</option>
            </select>
          </div>
          <DataTable
            columns={txColumns}
            data={txData?.content ?? []}
            loading={txLoading}
            emptyMessage="거래 내역이 없습니다."
          />
          <div className="border-t border-gray-100">
            <Pagination
              page={page}
              totalPages={txData?.totalPages ?? 1}
              totalElements={txData?.totalElements ?? 0}
              size={size}
              onPageChange={setPage}
              onSizeChange={setSize}
            />
          </div>
        </div>
      )}

      {/* Products Tab */}
      {activeTab === 'products' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200">
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">골드 상품</h2>
            <Button
              variant="primary"
              size="sm"
              onClick={() => { resetProductForm(); setShowProductForm(true); }}
            >
              상품 추가
            </Button>
          </div>

          {showProductForm && (
            <div className="p-4 border-b bg-gray-50">
              <h3 className="text-sm font-medium mb-3">{editingProduct ? '상품 수정' : '새 상품'}</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                {!editingProduct && (
                  <input
                    placeholder="상품 코드"
                    value={productForm.productCode}
                    onChange={(e) => setProductForm({ ...productForm, productCode: e.target.value })}
                    className="px-3 py-2 border rounded text-sm"
                  />
                )}
                <input
                  placeholder="상품명"
                  value={productForm.name}
                  onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                  className="px-3 py-2 border rounded text-sm"
                />
                <input
                  placeholder="골드량"
                  type="number"
                  value={productForm.goldAmount}
                  onChange={(e) => setProductForm({ ...productForm, goldAmount: e.target.value })}
                  className="px-3 py-2 border rounded text-sm"
                />
                <input
                  placeholder="가격 (원)"
                  type="number"
                  value={productForm.price}
                  onChange={(e) => setProductForm({ ...productForm, price: e.target.value })}
                  className="px-3 py-2 border rounded text-sm"
                />
                <input
                  placeholder="보너스"
                  type="number"
                  value={productForm.bonus}
                  onChange={(e) => setProductForm({ ...productForm, bonus: e.target.value })}
                  className="px-3 py-2 border rounded text-sm"
                />
                <input
                  placeholder="표시 순서"
                  type="number"
                  value={productForm.displayOrder}
                  onChange={(e) => setProductForm({ ...productForm, displayOrder: e.target.value })}
                  className="px-3 py-2 border rounded text-sm"
                />
              </div>
              <div className="mt-3 flex gap-2">
                <Button
                  variant="primary"
                  size="sm"
                  loading={createProductMutation.isPending || updateProductMutation.isPending}
                  onClick={handleProductSubmit}
                >
                  {editingProduct ? '수정' : '추가'}
                </Button>
                <Button variant="secondary" size="sm" onClick={resetProductForm}>
                  취소
                </Button>
              </div>
            </div>
          )}

          <DataTable
            columns={productColumns}
            data={products}
            loading={productsLoading}
            emptyMessage="상품이 없습니다."
          />
        </div>
      )}

      {/* Gold Adjust Tab */}
      {activeTab === 'adjust' && (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6 max-w-lg">
          <h2 className="font-semibold mb-4">골드 수동 조정</h2>
          <p className="text-sm text-gray-500 mb-4">양수: 지급, 음수: 차감. ADMIN_ADJUST 거래로 기록됩니다.</p>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">회원</label>
              {adjustSelectedUser ? (
                <div className="flex items-center justify-between px-3 py-2 border rounded-lg bg-gray-50">
                  <span className="text-sm">
                    <span className="font-medium">{adjustSelectedUser.nickname}</span>
                    <span className="text-gray-500 ml-2">
                      #{adjustSelectedUser.id}{adjustSelectedUser.email ? ` · ${adjustSelectedUser.email}` : ''}
                    </span>
                  </span>
                  <button
                    type="button"
                    className="text-xs text-gray-500 hover:text-gray-700"
                    onClick={() => { setAdjustSelectedUser(null); setAdjustUserId(''); setAdjustQuery(''); }}
                  >
                    변경
                  </button>
                </div>
              ) : (
                <div className="relative">
                  <input
                    value={adjustQuery}
                    onChange={(e) => setAdjustQuery(e.target.value)}
                    placeholder="닉네임 또는 이메일로 검색"
                    autoComplete="off"
                    className="w-full px-3 py-2 border rounded-lg text-sm"
                  />
                  {adjustQuery.trim().length >= 1 && (
                    <div className="absolute z-10 mt-1 w-full bg-white border rounded-lg shadow-sm max-h-60 overflow-auto">
                      {adjustSearching ? (
                        <div className="px-3 py-2 text-sm text-gray-400">검색 중…</div>
                      ) : adjustSearchResults.length === 0 ? (
                        <div className="px-3 py-2 text-sm text-gray-400">검색 결과 없음</div>
                      ) : (
                        adjustSearchResults.map((u) => (
                          <button
                            key={u.id}
                            type="button"
                            className="w-full text-left px-3 py-2 text-sm hover:bg-gray-50 flex items-center justify-between"
                            onClick={() => {
                              setAdjustSelectedUser(u);
                              setAdjustUserId(String(u.id));
                              setAdjustQuery('');
                            }}
                          >
                            <span>
                              <span className="font-medium">{u.nickname}</span>
                              <span className="text-gray-500 ml-2">#{u.id}{u.email ? ` · ${u.email}` : ''}</span>
                            </span>
                            {u.status !== 'ACTIVE' && (
                              <span className="text-xs text-gray-400">{u.status}</span>
                            )}
                          </button>
                        ))
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">금액</label>
              <input
                type="number"
                value={adjustAmount}
                onChange={(e) => setAdjustAmount(e.target.value)}
                placeholder="예: 100 또는 -50"
                className="w-full px-3 py-2 border rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">사유</label>
              <input
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value)}
                placeholder="조정 사유를 입력하세요"
                className="w-full px-3 py-2 border rounded-lg text-sm"
              />
            </div>
            <Button
              variant="primary"
              className="w-full"
              loading={adjustMutation.isPending}
              onClick={handleAdjust}
            >
              골드 조정
            </Button>
          </div>
        </div>
      )}

      {/* Refund Modal */}
      {refundTarget && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-96 shadow-xl">
            <h3 className="text-lg font-semibold mb-2">거래 환불</h3>
            <p className="text-sm text-gray-600 mb-1">
              거래 #{refundTarget.id} ({refundTarget.userNickname})
            </p>
            <p className="text-sm text-gray-600 mb-4">
              금액: {Math.abs(refundTarget.amount).toLocaleString()}G
            </p>
            <input
              value={refundReason}
              onChange={(e) => setRefundReason(e.target.value)}
              placeholder="환불 사유를 입력하세요"
              className="w-full px-3 py-2 border rounded-lg text-sm mb-4"
            />
            <div className="flex gap-2">
              <Button
                variant="warning"
                className="flex-1"
                disabled={!refundReason.trim()}
                loading={refundMutation.isPending}
                onClick={handleRefund}
              >
                환불 처리
              </Button>
              <Button
                variant="secondary"
                className="flex-1"
                onClick={() => { setRefundTarget(null); setRefundReason(''); }}
              >
                취소
              </Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </div>
  );
}
