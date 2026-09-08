import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { emoticonService } from '../../services/emoticonService';
import { fileService } from '../../services/fileService';
import type { EmoticonResponse } from '../../services/emoticonService';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import { Tabs } from '../../components/common/Tabs';
import type { ColumnDef } from '../../components/common/DataTable';
import { toast } from 'sonner';
import { useConfirm } from '@/hooks/useConfirm';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button as UIButton } from '@/components/ui/button';

const emoticonSchema = z.object({
  name: z.string().trim().min(1, '이름을 입력해주세요.'),
  packId: z.number().int().positive('팩을 선택해주세요.'),
  imageUrl: z.string().trim().min(1, '이미지 URL을 입력해주세요.').url('올바른 이미지 URL이 필요합니다.'),
  sortOrder: z.number().int().min(0).default(0),
});

type EmoticonFormValues = z.infer<typeof emoticonSchema>;

const FILTER_TABS = [
  { key: 'all', label: '전체' },
  { key: 'active', label: '활성' },
  { key: 'inactive', label: '비활성' },
];

export default function EmoticonManagementPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm();
  const [activeFilter, setActiveFilter] = useState('all');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingEmoticon, setEditingEmoticon] = useState<EmoticonResponse | null>(null);
  const [uploading, setUploading] = useState(false);

  const form = useForm<EmoticonFormValues>({
    resolver: zodResolver(emoticonSchema),
    defaultValues: { name: '', packId: 0, imageUrl: '', sortOrder: 0 },
  });

  const { formState: { isSubmitting } } = form;

  const { data: emoticons = [], isLoading } = useQuery({
    queryKey: ['emoticons'],
    queryFn: emoticonService.getAll,
  });

  const { data: packs = [] } = useQuery({
    queryKey: ['emoticon-packs'],
    queryFn: emoticonService.getPacks,
  });

  const filteredEmoticons = emoticons.filter((e) => {
    if (activeFilter === 'active') return e.isActive;
    if (activeFilter === 'inactive') return !e.isActive;
    return true;
  });

  const openCreateForm = () => {
    setEditingEmoticon(null);
    form.reset({ name: '', packId: packs[0]?.id ?? 0, imageUrl: '', sortOrder: 0 });
    setDialogOpen(true);
  };

  const openEditForm = (emoticon: EmoticonResponse) => {
    setEditingEmoticon(emoticon);
    form.reset({
      name: emoticon.name,
      packId: emoticon.packId,
      imageUrl: emoticon.imageUrl,
      sortOrder: emoticon.sortOrder,
    });
    setDialogOpen(true);
  };

  const closeForm = () => {
    setDialogOpen(false);
    setEditingEmoticon(null);
    form.reset();
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      toast.info('이미지 파일만 업로드 가능합니다.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.info('파일 크기는 10MB 이하여야 합니다.');
      return;
    }
    setUploading(true);
    try {
      const result = await fileService.uploadFile(file, 'emoticon');
      form.setValue('imageUrl', result.url, { shouldValidate: true });
    } catch (error) {
      console.error('Failed to upload image:', error);
      toast.error('이미지 업로드에 실패했습니다.');
    } finally {
      setUploading(false);
    }
  };

  const onSubmit = async (values: EmoticonFormValues) => {
    try {
      if (editingEmoticon) {
        await emoticonService.update(editingEmoticon.id, values);
      } else {
        await emoticonService.create(values);
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ['emoticons'] });
    } catch (error) {
      console.error('Failed to save emoticon:', error);
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleDelete = async (id: number, name: string) => {
    if (!(await confirmDialog({ description: `"${name}" 이모티콘을 삭제하시겠습니까?`, variant: 'destructive' }))) return;
    try {
      await emoticonService.delete(id);
      queryClient.invalidateQueries({ queryKey: ['emoticons'] });
    } catch (error) {
      console.error('Failed to delete emoticon:', error);
      toast.error('삭제에 실패했습니다.');
    }
  };

  const handleToggleActive = async (id: number, name: string, currentActive: boolean) => {
    const msg = currentActive ? '비활성화하시겠습니까?' : '활성화하시겠습니까?';
    if (!(await confirmDialog({ description: `"${name}" 이모티콘을 ${msg}` }))) return;
    try {
      await emoticonService.toggleActive(id);
      queryClient.invalidateQueries({ queryKey: ['emoticons'] });
    } catch (error) {
      console.error('Failed to toggle emoticon status:', error);
      toast.error('상태 변경에 실패했습니다.');
    }
  };

  const columns: ColumnDef<EmoticonResponse>[] = [
    {
      key: 'imageUrl',
      header: '이미지',
      width: '80px',
      align: 'center',
      render: (value, row) => (
        <img
          src={value}
          alt={row.name}
          style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 6, border: '1px solid #e5e7eb', margin: '0 auto' }}
          onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
        />
      ),
    },
    {
      key: 'name',
      header: '이름',
      className: 'font-medium',
    },
    {
      key: 'category',
      header: '팩 (카테고리)',
      render: (value) => (
        <span className="px-2 py-1 text-xs rounded-full bg-blue-100 text-blue-800">
          {value || '-'}
        </span>
      ),
    },
    {
      key: 'sortOrder',
      header: '정렬 순서',
      align: 'center',
    },
    {
      key: 'isActive',
      header: '상태',
      align: 'center',
      render: (value, row) => (
        <button
          type="button"
          className={`px-2 py-1 text-xs rounded-full cursor-pointer transition-colors ${value ? 'bg-green-100 text-green-800 hover:bg-green-200' : 'bg-gray-100 text-gray-800 hover:bg-gray-200'}`}
          onClick={() => handleToggleActive(row.id, row.name ?? '', value)}
          title="클릭하여 상태 변경"
        >
          {value ? '활성' : '비활성'}
        </button>
      ),
    },
    {
      key: 'id',
      header: '액션',
      align: 'right',
      render: (_value, row) => (
        <div className="flex gap-3 justify-end">
          <Button variant="link" size="sm" onClick={() => openEditForm(row)}>수정</Button>
          <Button
            variant="link"
            size="sm"
            className="text-red-600 hover:text-red-900"
            onClick={() => handleDelete(row.id, row.name ?? '')}
          >
            삭제
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">이모티콘 관리</h1>
        <Button variant="primary" onClick={openCreateForm}>+ 이모티콘 추가</Button>
      </div>

      <Tabs tabs={FILTER_TABS} activeTab={activeFilter} onTabChange={setActiveFilter} />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <DataTable<EmoticonResponse>
          columns={columns}
          data={filteredEmoticons}
          loading={isLoading}
          emptyMessage="이모티콘이 없습니다."
        />
      </div>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingEmoticon ? '이모티콘 수정' : '새 이모티콘 추가'}</DialogTitle>
            <DialogDescription>이름, 팩, 이미지, 정렬 순서를 입력하세요.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                name="name"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>이름</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="이모티콘 이름을 입력하세요" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="packId"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>팩</FormLabel>
                    <Select
                      onValueChange={(v) => field.onChange(Number(v))}
                      value={field.value > 0 ? String(field.value) : ''}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="팩을 선택하세요" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {packs.map((p) => (
                          <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="imageUrl"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>이미지</FormLabel>
                    <FormControl>
                      <div className="space-y-2">
                        <Input {...field} placeholder="https://..." />
                        <div className="flex items-center gap-2">
                          <label className={`inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-lg cursor-pointer transition-colors ${uploading ? 'bg-gray-100 text-gray-400' : 'bg-blue-50 text-blue-700 hover:bg-blue-100'}`}>
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                            {uploading ? '업로드 중...' : '이미지 업로드'}
                            <input
                              type="file"
                              accept="image/png,image/jpeg,image/gif,image/webp"
                              onChange={handleImageUpload}
                              disabled={uploading}
                              className="hidden"
                            />
                          </label>
                          <span className="text-xs text-gray-400">또는 URL 직접 입력</span>
                        </div>
                        {field.value && (
                          <img
                            src={field.value}
                            alt="미리보기"
                            style={{ width: 60, height: 60, objectFit: 'cover', borderRadius: 8, border: '1px solid #e5e7eb' }}
                            onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
                          />
                        )}
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="sortOrder"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>정렬 순서</FormLabel>
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
                <UIButton type="submit" disabled={isSubmitting || uploading}>
                  {isSubmitting ? '저장 중...' : (editingEmoticon ? '수정' : '생성')}
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
