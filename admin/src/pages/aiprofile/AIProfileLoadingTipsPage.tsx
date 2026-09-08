import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Button } from '../../components/common/Button';
import { Tabs } from '../../components/common/Tabs';
import { loadingTipService } from '../../services/loadingTipService';
import type { AILoadingTipResponse } from '../../services/loadingTipService';
import { useConfirm } from '@/hooks/useConfirm';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Button as UIButton } from '@/components/ui/button';

const tipSchema = z.object({
  content: z.string().trim().min(1, '내용을 입력해주세요.'),
  displayOrder: z.number().int().min(0).default(0),
});

type TipFormValues = z.infer<typeof tipSchema>;

const FILTER_TABS = [
  { key: 'all', label: '전체' },
  { key: 'active', label: '활성' },
  { key: 'inactive', label: '비활성' },
];

export default function AIProfileLoadingTipsPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm();
  const [activeFilter, setActiveFilter] = useState('all');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingTip, setEditingTip] = useState<AILoadingTipResponse | null>(null);

  const form = useForm<TipFormValues>({
    resolver: zodResolver(tipSchema),
    defaultValues: { content: '', displayOrder: 0 },
  });

  const { formState: { isSubmitting } } = form;

  const { data: tips = [], isLoading } = useQuery({
    queryKey: ['loading-tips'],
    queryFn: loadingTipService.getAllTips,
  });

  const filteredTips = tips.filter((t) => {
    if (activeFilter === 'active') return t.isActive;
    if (activeFilter === 'inactive') return !t.isActive;
    return true;
  });

  const openCreateForm = () => {
    setEditingTip(null);
    form.reset({ content: '', displayOrder: 0 });
    setDialogOpen(true);
  };

  const openEditForm = (tip: AILoadingTipResponse) => {
    setEditingTip(tip);
    form.reset({ content: tip.content, displayOrder: tip.displayOrder });
    setDialogOpen(true);
  };

  const closeForm = () => {
    setDialogOpen(false);
    setEditingTip(null);
    form.reset();
  };

  const onSubmit = async (values: TipFormValues) => {
    try {
      if (editingTip) {
        await loadingTipService.updateTip(editingTip.id, values);
      } else {
        await loadingTipService.createTip(values);
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ['loading-tips'] });
    } catch (error) {
      console.error('Failed to save tip:', error);
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleToggleActive = async (tip: AILoadingTipResponse) => {
    try {
      await loadingTipService.updateTip(tip.id, { isActive: !tip.isActive });
      queryClient.invalidateQueries({ queryKey: ['loading-tips'] });
    } catch (error) {
      console.error('Failed to toggle tip status:', error);
      toast.error('상태 변경에 실패했습니다.');
    }
  };

  const handleDelete = async (tip: AILoadingTipResponse) => {
    if (!(await confirmDialog({ description: `"${tip.content}" 팁을 삭제하시겠습니까?`, variant: 'destructive' }))) return;
    try {
      await loadingTipService.deleteTip(tip.id);
      queryClient.invalidateQueries({ queryKey: ['loading-tips'] });
    } catch (error) {
      console.error('Failed to delete tip:', error);
      toast.error('삭제에 실패했습니다.');
    }
  };

  const columns: ColumnDef<AILoadingTipResponse>[] = [
    { key: 'id', header: 'ID', width: '60px' },
    { key: 'content', header: '내용' },
    { key: 'displayOrder', header: '순서', width: '80px', align: 'center' },
    {
      key: 'isActive',
      header: '활성화',
      width: '100px',
      align: 'center',
      render: (_value, tip) => (
        <button
          type="button"
          onClick={() => handleToggleActive(tip)}
          className={`px-2 py-1 text-xs rounded-full cursor-pointer transition-colors ${
            tip.isActive ? 'bg-green-100 text-green-800 hover:bg-green-200' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
          }`}
        >
          {tip.isActive ? '활성' : '비활성'}
        </button>
      ),
    },
    {
      key: 'id',
      header: '관리',
      width: '120px',
      align: 'right',
      render: (_value, tip) => (
        <div className="flex gap-3 justify-end">
          <Button variant="link" size="sm" onClick={() => openEditForm(tip)}>수정</Button>
          <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDelete(tip)}>삭제</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">AI 로딩 팁 관리</h1>
        <Button variant="primary" onClick={openCreateForm}>+ 팁 추가</Button>
      </div>

      <Tabs tabs={FILTER_TABS} activeTab={activeFilter} onTabChange={setActiveFilter} />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <DataTable<AILoadingTipResponse>
          columns={columns}
          data={filteredTips}
          loading={isLoading}
          emptyMessage="등록된 팁이 없습니다."
        />
      </div>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingTip ? '팁 수정' : '새 팁 추가'}</DialogTitle>
            <DialogDescription>AI 프로필 생성 로딩 중 표시될 팁을 입력하세요.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                name="content"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>내용</FormLabel>
                    <FormControl>
                      <textarea
                        {...field}
                        rows={4}
                        placeholder="로딩 중 표시될 팁 내용을 입력하세요."
                        className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="displayOrder"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>표시 순서</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={0}
                        {...field}
                        onChange={(e) => field.onChange(parseInt(e.target.value) || 0)}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={closeForm}>취소</UIButton>
                <UIButton type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '저장 중...' : (editingTip ? '수정' : '추가')}
                </UIButton>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>

      {ConfirmDialog}
    </div>
  );
}
