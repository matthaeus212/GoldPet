import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { breedService } from '../../services/breedService';
import { dataService } from '../../services/dataService';
import BreedsPage from './BreedsPage';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import { Tabs } from '../../components/common/Tabs';
import type { ColumnDef } from '../../components/common/DataTable';
import type { SpeciesResponse } from '../../types/api';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const DATA_TABS = [
  { key: 'species', label: '반려동물 종' },
  { key: 'breeds', label: '품종' },
  { key: 'categories', label: '커뮤니티 카테고리' },
];

type SpeciesItem = SpeciesResponse;

interface CategoryItem {
  id: number;
  name: string;
  code: string;
}

export default function DataPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
  const [activeTab, setActiveTab] = useState('species');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [newCode, setNewCode] = useState('');

  const { data: species = [], isLoading: isSpeciesLoading } = useQuery({
    queryKey: ['species'],
    queryFn: breedService.getSpecies,
  });

  const { data: categories = [], isLoading: isCategoriesLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: dataService.getCategories,
  });

  const deleteSpeciesMutation = useMutation({
    mutationFn: (id: number) => breedService.deleteSpecies(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['species'] }),
    onError: () => toast.error('종 삭제에 실패했습니다.'),
  });

  const deleteCategoryMutation = useMutation({
    mutationFn: (id: number) => dataService.deleteCategory(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
    onError: () => toast.error('카테고리 삭제에 실패했습니다.'),
  });

  const createSpeciesMutation = useMutation({
    mutationFn: (data: { name: string; code: string }) => breedService.createSpecies(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['species'] });
      closeModal();
    },
    onError: () => toast.error('종 추가에 실패했습니다.'),
  });

  const createCategoryMutation = useMutation({
    mutationFn: (name: string) => dataService.createCategory(name, newCode || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      closeModal();
    },
    onError: () => toast.error('카테고리 추가에 실패했습니다.'),
  });

  const handleDeleteSpecies = async (id: number) => {
    if (!(await confirmDialog({ description: '이 종을 삭제하시겠습니까? 연관된 품종이 있으면 삭제할 수 없습니다.', variant: 'destructive' }))) return;
    deleteSpeciesMutation.mutate(id);
  };

  const handleDeleteCategory = async (id: number) => {
    if (!(await confirmDialog({ description: '이 카테고리를 삭제하시겠습니까? 연관된 게시글이 있으면 삭제할 수 없습니다.', variant: 'destructive' }))) return;
    deleteCategoryMutation.mutate(id);
  };

  const openModal = () => {
    setNewName('');
    setNewCode('');
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setNewName('');
    setNewCode('');
  };

  const handleCreate = () => {
    if (!newName.trim()) {
      toast.info('이름을 입력해주세요.');
      return;
    }
    if (activeTab === 'species') {
      if (!newCode.trim()) {
        toast.info('코드를 입력해주세요.');
        return;
      }
      createSpeciesMutation.mutate({ name: newName, code: newCode });
    } else {
      createCategoryMutation.mutate(newName);
    }
  };

  const speciesColumns: ColumnDef<SpeciesItem>[] = [
    { key: 'code', header: '코드', className: 'font-mono' },
    { key: 'name', header: '이름', className: 'font-medium' },
    {
      key: 'id',
      header: '관리',
      align: 'right',
      render: (_v, row) => (
        <Button
          variant="link"
          size="sm"
          className="text-red-600 hover:text-red-900"
          onClick={() => handleDeleteSpecies(row.id)}
        >
          삭제
        </Button>
      ),
    },
  ];

  const categoryColumns: ColumnDef<CategoryItem>[] = [
    { key: 'name', header: '이름', className: 'font-medium' },
    { key: 'code', header: '코드', className: 'font-mono text-gray-500' },
    {
      key: 'id',
      header: '관리',
      align: 'right',
      render: (_v, row) => (
        <Button
          variant="link"
          size="sm"
          className="text-red-600 hover:text-red-900"
          onClick={() => handleDeleteCategory(row.id)}
        >
          삭제
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">코드/데이터 관리</h1>

      <Tabs tabs={DATA_TABS} activeTab={activeTab} onTabChange={setActiveTab} />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        {activeTab !== 'breeds' && (
          <div className="p-4 border-b flex justify-between items-center">
            <h2 className="font-semibold">
              {activeTab === 'species' ? '반려동물 종' : '커뮤니티 카테고리'}
            </h2>
            <Button variant="primary" onClick={openModal}>추가</Button>
          </div>
        )}
        {activeTab === 'species' && (
          <DataTable<SpeciesItem>
            columns={speciesColumns}
            data={species as SpeciesItem[]}
            loading={isSpeciesLoading}
            emptyMessage="등록된 종이 없습니다."
          />
        )}
        {activeTab === 'breeds' && (
          <BreedsPage embedded={true} />
        )}
        {activeTab === 'categories' && (
          <DataTable<CategoryItem>
            columns={categoryColumns}
            data={categories as CategoryItem[]}
            loading={isCategoriesLoading}
            emptyMessage="등록된 카테고리가 없습니다."
          />
        )}
      </div>

      {/* Create Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 className="text-lg font-semibold mb-4">
              {activeTab === 'species' ? '종 추가' : '카테고리 추가'}
            </h3>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">이름</label>
                <input
                  type="text"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="이름을 입력하세요"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">코드</label>
                <input
                  type="text"
                  value={newCode}
                  onChange={(e) => setNewCode(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder={activeTab === 'species' ? '예: DOG, CAT' : '예: free, qna (선택)'}
                />
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" size="sm" onClick={closeModal}>취소</Button>
              <Button
                variant="primary"
                size="sm"
                loading={createSpeciesMutation.isPending || createCategoryMutation.isPending}
                onClick={handleCreate}
              >
                추가
              </Button>
            </div>
          </div>
        </div>
      )}
    {ConfirmDialog}
    </div>
  );
}
