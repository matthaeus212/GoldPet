import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { noticeService } from '../../services/noticeService';
import type { AppNoticeResponse } from '../../services/noticeService';
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

const TYPE_LABELS: Record<string, string> = {
  POPUP_MODAL: '팝업 모달',
  MAINTENANCE: '점검 공지',
  EVENT_BANNER: '이벤트 배너',
  NOTICE: '공지사항',
};

const SCREEN_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'HOME', label: '홈' },
  { value: 'WALK', label: '산책' },
  { value: 'COMMUNITY', label: '커뮤니티' },
];

const FILTER_TABS = [
  { key: 'all', label: '전체', testId: 'notice-tab-all' },
  { key: 'active', label: '활성', testId: 'notice-tab-active' },
  { key: 'inactive', label: '비활성', testId: 'notice-tab-inactive' },
];

const noticeSchema = z.object({
  type: z.string().min(1),
  title: z.string().trim().min(1, '제목을 입력해주세요.'),
  content: z.string().default(''),
  linkUrl: z.string().default(''),
  targetScreen: z.string().default('ALL'),
  priority: z.number().int().min(0).default(0),
  isDismissible: z.boolean().default(true),
  isActive: z.boolean().default(true),
  startAt: z.string().min(1, '시작일시를 입력해주세요.'),
  endAt: z.string().default(''),
});

type NoticeFormValues = z.infer<typeof noticeSchema>;

function formatDateTime(dt: string) {
  if (!dt) return '-';
  return new Date(dt).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' });
}

export default function NoticeManagementPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm();
  const [activeFilter, setActiveFilter] = useState('all');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingNotice, setEditingNotice] = useState<AppNoticeResponse | null>(null);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [urlInput, setUrlInput] = useState('');

  const form = useForm<NoticeFormValues>({
    resolver: zodResolver(noticeSchema),
    defaultValues: {
      type: 'POPUP_MODAL', title: '', content: '',
      linkUrl: '', targetScreen: 'ALL', priority: 0,
      isDismissible: true, isActive: true, startAt: '', endAt: '',
    },
  });

  const { formState: { isSubmitting } } = form;

  const { data: notices = [], isLoading } = useQuery({
    queryKey: ['notices'],
    queryFn: noticeService.getAllNotices,
  });

  const filteredNotices = notices.filter((n) => {
    if (activeFilter === 'active') return n.isActive;
    if (activeFilter === 'inactive') return !n.isActive;
    return true;
  });

  const openCreateForm = () => {
    setEditingNotice(null);
    form.reset({
      type: 'POPUP_MODAL', title: '', content: '',
      linkUrl: '', targetScreen: 'ALL', priority: 0,
      isDismissible: true, isActive: true, startAt: '', endAt: '',
    });
    setImageUrls([]);
    setUrlInput('');
    setDialogOpen(true);
  };

  const openEditForm = (notice: AppNoticeResponse) => {
    setEditingNotice(notice);
    form.reset({
      type: notice.type,
      title: notice.title,
      content: notice.content || '',
      linkUrl: notice.linkUrl || '',
      targetScreen: notice.targetScreen || 'ALL',
      priority: notice.priority,
      isDismissible: notice.isDismissible,
      isActive: notice.isActive,
      startAt: notice.startAt ? notice.startAt.slice(0, 16) : '',
      endAt: notice.endAt ? notice.endAt.slice(0, 16) : '',
    });
    setImageUrls(notice.imageUrls || []);
    setUrlInput('');
    setDialogOpen(true);
  };

  const closeForm = () => {
    setDialogOpen(false);
    setEditingNotice(null);
    form.reset();
    setImageUrls([]);
    setUrlInput('');
  };

  const addImageUrl = () => {
    const url = urlInput.trim();
    if (!url) return;
    setImageUrls((prev) => [...prev, url]);
    setUrlInput('');
  };

  const removeImageUrl = (idx: number) => {
    setImageUrls((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    for (const file of Array.from(files)) {
      try {
        const url = await noticeService.uploadImage(file);
        setImageUrls((prev) => [...prev, url]);
      } catch {
        toast.error(`이미지 업로드 실패: ${file.name}`);
      }
    }
    e.target.value = '';
  };

  const onSubmit = async (values: NoticeFormValues) => {
    try {
      const payload = {
        ...values,
        content: values.content || undefined,
        imageUrls: imageUrls.filter((u) => u.trim()),
        linkUrl: values.linkUrl || undefined,
        endAt: values.endAt || undefined,
      };
      if (editingNotice) {
        await noticeService.updateNotice(editingNotice.id, payload as Parameters<typeof noticeService.updateNotice>[1]);
      } else {
        await noticeService.createNotice(payload as Parameters<typeof noticeService.createNotice>[0]);
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ['notices'] });
      queryClient.invalidateQueries({ queryKey: ['maintenanceStatus'] });
    } catch (error) {
      console.error('Failed to save notice:', error);
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleDelete = async (id: number) => {
    if (!(await confirmDialog({ description: '정말 삭제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await noticeService.deleteNotice(id);
      queryClient.invalidateQueries({ queryKey: ['notices'] });
      queryClient.invalidateQueries({ queryKey: ['maintenanceStatus'] });
    } catch (error) {
      console.error('Failed to delete notice:', error);
      toast.error('삭제에 실패했습니다.');
    }
  };

  const getTypeBadge = (type: string) => {
    const styles: Record<string, string> = {
      POPUP_MODAL: 'bg-purple-100 text-purple-800',
      MAINTENANCE: 'bg-orange-100 text-orange-800',
      EVENT_BANNER: 'bg-blue-100 text-blue-800',
      NOTICE: 'bg-green-100 text-green-800',
    };
    return (
      <span className={`px-2 py-1 text-xs rounded-full ${styles[type] || 'bg-gray-100 text-gray-800'}`}>
        {TYPE_LABELS[type] || type}
      </span>
    );
  };

  const columns: ColumnDef<AppNoticeResponse>[] = [
    { key: 'type', header: '유형', render: (value) => getTypeBadge(value) },
    { key: 'title', header: '제목', className: 'font-medium max-w-xs truncate' },
    {
      key: 'targetScreen',
      header: '대상',
      render: (value) => SCREEN_OPTIONS.find((o) => o.value === value)?.label || value || '-',
    },
    { key: 'priority', header: '우선순위', align: 'center' },
    {
      key: 'isActive',
      header: '상태',
      render: (value, row) => (
        <button
          type="button"
          data-testid="notice-toggle-active"
          className={`px-2 py-1 text-xs rounded-full cursor-pointer transition-colors ${value ? 'bg-green-100 text-green-800 hover:bg-green-200' : 'bg-gray-100 text-gray-800 hover:bg-gray-200'}`}
          onClick={async () => {
            const next = !value;
            const msg = next ? '활성화하시겠습니까?' : '비활성화하시겠습니까?';
            if (!(await confirmDialog({ description: `"${row.title}" 공지를 ${msg}` }))) return;
            try {
              await noticeService.updateNotice(row.id, { isActive: next });
              queryClient.invalidateQueries({ queryKey: ['notices'] });
              queryClient.invalidateQueries({ queryKey: ['maintenanceStatus'] });
            } catch {
              toast.error('상태 변경에 실패했습니다.');
            }
          }}
          title="클릭하여 상태 변경"
        >
          {value ? '활성' : '비활성'}
        </button>
      ),
    },
    { key: 'startAt', header: '시작일시', render: (value) => formatDateTime(value) },
    { key: 'endAt', header: '종료일시', render: (value) => (value ? formatDateTime(value) : '-') },
    {
      key: 'id',
      header: '액션',
      align: 'right',
      render: (_value, row) => (
        <div className="flex gap-3 justify-end">
          <Button variant="link" size="sm" onClick={() => openEditForm(row)}>수정</Button>
          <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDelete(row.id)}>삭제</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">공지/팝업 관리</h1>
        <Button variant="primary" data-testid="notice-add-button" onClick={openCreateForm}>+ 공지 추가</Button>
      </div>

      <Tabs tabs={FILTER_TABS} activeTab={activeFilter} onTabChange={setActiveFilter} />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <DataTable<AppNoticeResponse>
          columns={columns}
          data={filteredNotices}
          loading={isLoading}
          emptyMessage="공지가 없습니다."
          rowDataTestId={() => 'notice-row'}
        />
      </div>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingNotice ? '공지 수정' : '새 공지 추가'}</DialogTitle>
            <DialogDescription>유형, 제목, 일정 등을 입력하세요.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <FormField
                  name="type"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>유형</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger><SelectValue /></SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="POPUP_MODAL">팝업 모달</SelectItem>
                          <SelectItem value="MAINTENANCE">점검 공지</SelectItem>
                          <SelectItem value="EVENT_BANNER">이벤트 배너</SelectItem>
                          <SelectItem value="NOTICE">공지사항</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  name="targetScreen"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>대상 화면</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger><SelectValue /></SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {SCREEN_OPTIONS.map((opt) => (
                            <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                name="title"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>제목 *</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="제목을 입력하세요" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="content"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>내용</FormLabel>
                    <FormControl>
                      <textarea
                        {...field}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm resize-none"
                        rows={3}
                        placeholder="내용을 입력하세요"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {/* Image array — managed via form.watch + form.setValue */}
              <div className="space-y-2">
                <span className="text-sm font-medium">이미지</span>
                {imageUrls.length > 0 && (
                  <div className="flex flex-wrap gap-3">
                    {imageUrls.map((url, idx) => url && (
                      <div key={idx} className="relative group">
                        <img
                          src={url}
                          alt={`이미지 ${idx + 1}`}
                          className="w-24 h-24 object-cover rounded-lg border border-gray-200"
                        />
                        <button
                          type="button"
                          onClick={() => removeImageUrl(idx)}
                          className="absolute -top-2 -right-2 w-5 h-5 bg-red-500 text-white rounded-full text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                        >
                          &times;
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <label className="inline-flex items-center gap-1 px-3 py-2 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded-lg text-sm cursor-pointer hover:bg-indigo-100">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                    이미지 업로드
                    <input type="file" accept="image/*" multiple className="hidden" onChange={handleImageUpload} />
                  </label>
                  <span className="text-xs text-gray-400 self-center">JPG, PNG, GIF, WebP / 최대 10MB</span>
                </div>
                <div className="flex gap-2">
                  <Input
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    placeholder="https://..."
                    onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addImageUrl(); } }}
                    className="flex-1"
                  />
                  <UIButton type="button" variant="outline" onClick={addImageUrl}>추가</UIButton>
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <FormField
                  name="linkUrl"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>링크 URL</FormLabel>
                      <FormControl><Input {...field} placeholder="https://..." /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  name="priority"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>우선순위</FormLabel>
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
                <FormField
                  name="isActive"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>상태</FormLabel>
                      <Select onValueChange={(v) => field.onChange(v === 'true')} value={field.value ? 'true' : 'false'}>
                        <FormControl>
                          <SelectTrigger><SelectValue /></SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="true">활성</SelectItem>
                          <SelectItem value="false">비활성</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  name="isDismissible"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem className="flex flex-row items-center gap-2 space-y-0 pt-6">
                      <FormControl>
                        <input
                          type="checkbox"
                          id="isDismissible"
                          checked={field.value}
                          onChange={(e) => field.onChange(e.target.checked)}
                          className="w-4 h-4"
                        />
                      </FormControl>
                      <FormLabel htmlFor="isDismissible" className="cursor-pointer font-medium">
                        오늘 하루 그만 보기 허용
                      </FormLabel>
                    </FormItem>
                  )}
                />
                <FormField
                  name="startAt"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>시작일시 *</FormLabel>
                      <FormControl><Input type="datetime-local" {...field} /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  name="endAt"
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>종료일시 (선택)</FormLabel>
                      <FormControl><Input type="datetime-local" {...field} /></FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={closeForm}>취소</UIButton>
                <UIButton type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '저장 중...' : (editingNotice ? '수정' : '생성')}
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
