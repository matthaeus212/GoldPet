import { useState } from 'react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { useConfirm } from '@/hooks/useConfirm';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { gamificationService } from '../../services/gamificationService';
import { fileService } from '../../services/fileService';
import type { Badge } from '../../services/gamificationService';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button as UIButton } from '@/components/ui/button';

function BadgeImage({ src, size }: { src: string; size: 'sm' | 'lg' }) {
  const [broken, setBroken] = useState(false);
  const box = size === 'sm' ? 'w-8 h-8 rounded text-sm' : 'w-16 h-16 rounded-lg text-2xl border';
  if (!src || broken) {
    return <div className={`${box} bg-gray-100 flex items-center justify-center`}>🏅</div>;
  }
  const imgCls = size === 'sm' ? 'w-8 h-8 rounded object-cover' : 'w-16 h-16 rounded-lg object-cover border';
  return <img src={src} alt="" className={imgCls} onError={() => setBroken(true)} loading="lazy" />;
}

const CONDITION_TYPE_LABELS: Record<string, string> = {
  PET_REGISTER: '반려동물 등록',
  WALK_COUNT: '산책 횟수',
  WALK_DISTANCE_TOTAL: '총 산책 거리(km)',
  COMMUNITY_POST: '게시글 작성',
  COMMUNITY_COMMENT: '댓글 작성',
  CHECK_IN: '체크인',
  FRIEND_MATCH: '친구 매칭',
};

const CONDITION_TYPES = Object.keys(CONDITION_TYPE_LABELS);

const REPEAT_CYCLES = [
  { value: 'DAILY', label: '매일' },
  { value: 'WEEKLY', label: '매주' },
  { value: 'MONTHLY', label: '매월' },
];

const badgeSchema = z.object({
  name: z.string().trim().min(1, '이름을 입력해주세요.'),
  description: z.string().trim().min(1, '설명을 입력해주세요.'),
  imageUrl: z.string(),
  conditionType: z.string(),
  conditionValue: z.string(),
  rewardGold: z.string(),
  startDate: z.string(),
  endDate: z.string(),
  isRepeatable: z.boolean(),
  repeatCycle: z.string(),
  isActive: z.boolean(),
});

type BadgeFormValues = z.infer<typeof badgeSchema>;

const defaultValues: BadgeFormValues = {
  name: '',
  description: '',
  imageUrl: '',
  conditionType: '',
  conditionValue: '',
  rewardGold: '',
  startDate: '',
  endDate: '',
  isRepeatable: false,
  repeatCycle: '',
  isActive: true,
};

const getMissionTypeLabel = (badge: Badge) => {
  if (badge.isRepeatable && badge.startDate) return '기간+반복';
  if (badge.isRepeatable) return '반복';
  if (badge.startDate) return '기간 한정';
  return '상시';
};

const getMissionTypeColor = (badge: Badge) => {
  if (badge.isRepeatable) return 'bg-purple-100 text-purple-700';
  if (badge.startDate) return 'bg-orange-100 text-orange-700';
  return 'bg-gray-100 text-gray-600';
};

export default function GamificationPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingBadge, setEditingBadge] = useState<Badge | null>(null);
  const [uploading, setUploading] = useState(false);

  const form = useForm<BadgeFormValues>({
    resolver: zodResolver(badgeSchema),
    defaultValues,
  });

  const { formState: { isSubmitting }, setValue, watch } = form;
  const imageUrl = watch('imageUrl');
  const isRepeatable = watch('isRepeatable');

  const { data: badges = [], isLoading } = useQuery({
    queryKey: ['badges'],
    queryFn: () => gamificationService.getBadges(),
  });

  const openCreateDialog = () => {
    setEditingBadge(null);
    form.reset(defaultValues);
    setDialogOpen(true);
  };

  const openEditDialog = (badge: Badge) => {
    setEditingBadge(badge);
    form.reset({
      name: badge.name,
      description: badge.description,
      imageUrl: badge.imageUrl || '',
      conditionType: badge.conditionType || '',
      conditionValue: badge.conditionValue?.toString() || '',
      rewardGold: badge.rewardGold?.toString() || '',
      startDate: badge.startDate || '',
      endDate: badge.endDate || '',
      isRepeatable: badge.isRepeatable,
      repeatCycle: badge.repeatCycle || '',
      isActive: badge.isActive,
    });
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setEditingBadge(null);
    form.reset();
  };

  const deleteBadgeMutation = useMutation({
    mutationFn: (id: number) => gamificationService.deleteBadge(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['badges'] }),
    onError: () => toast.error('삭제에 실패했습니다.'),
  });

  const handleDeleteBadge = async (id: number) => {
    if (!(await confirmDialog({ description: '이 뱃지를 삭제하시겠습니까? 획득자가 있으면 비활성화 처리됩니다', variant: 'destructive' }))) return;
    deleteBadgeMutation.mutate(id);
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const result = await fileService.uploadFile(file);
      setValue('imageUrl', result.url, { shouldValidate: true });
    } catch (error) {
      console.error('Failed to upload image:', error);
      toast.error('이미지 업로드에 실패했습니다.');
    } finally {
      setUploading(false);
    }
  };

  const onSubmit = async (values: BadgeFormValues) => {
    try {
      const payload = {
        name: values.name,
        description: values.description,
        imageUrl: values.imageUrl || undefined,
        conditionType: values.conditionType || undefined,
        conditionValue: values.conditionValue ? parseInt(values.conditionValue) : undefined,
        rewardGold: values.rewardGold ? parseInt(values.rewardGold) : undefined,
        startDate: values.startDate || undefined,
        endDate: values.endDate || undefined,
        isRepeatable: values.isRepeatable || undefined,
        repeatCycle: values.isRepeatable ? values.repeatCycle || undefined : undefined,
      };
      if (editingBadge) {
        await gamificationService.updateBadge(editingBadge.id, { ...payload, isActive: values.isActive });
      } else {
        await gamificationService.createBadge(payload as Parameters<typeof gamificationService.createBadge>[0]);
      }
      closeDialog();
      queryClient.invalidateQueries({ queryKey: ['badges'] });
    } catch (error) {
      console.error('Failed to save badge:', error);
      toast.error('저장에 실패했습니다.');
    }
  };

  const columns: ColumnDef<Badge>[] = [
    { key: 'id', header: 'ID', width: '60px', render: (value) => <span className="text-gray-400">{value}</span> },
    {
      key: 'imageUrl',
      header: '이미지',
      width: '60px',
      render: (value) => <BadgeImage src={value as string} size="sm" />,
    },
    {
      key: 'name',
      header: '이름',
      render: (value, row) => (
        <div>
          <div className="font-medium text-sm">{value}</div>
          <div className="text-xs text-gray-500 mt-0.5 max-w-[200px] truncate">{row.description}</div>
        </div>
      ),
    },
    {
      key: 'conditionType',
      header: '미션 조건',
      render: (value, row) => value ? (
        <span className="inline-flex items-center gap-1">
          <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded text-xs font-medium">
            {CONDITION_TYPE_LABELS[value] || value}
          </span>
          <span className="text-gray-600">{row.conditionValue}회</span>
        </span>
      ) : (
        <span className="text-gray-400">-</span>
      ),
    },
    {
      key: 'isRepeatable',
      header: '유형',
      align: 'center',
      render: (_value, row) => (
        <span className={`px-2 py-0.5 rounded text-xs font-medium ${getMissionTypeColor(row)}`}>
          {getMissionTypeLabel(row)}
        </span>
      ),
    },
    {
      key: 'rewardGold',
      header: '보상',
      align: 'right',
      render: (value) => value ? <span className="text-amber-600 font-medium">{value}G</span> : '-',
    },
    {
      key: 'isActive',
      header: '상태',
      align: 'center',
      render: (value) => (
        <span className={`px-2 py-0.5 rounded text-xs font-medium ${value ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
          {value ? '활성' : '비활성'}
        </span>
      ),
    },
    {
      key: 'earnedCount',
      header: '획득자',
      align: 'right',
      render: (value) => `${value.toLocaleString()}명`,
    },
    {
      key: 'id',
      header: '관리',
      align: 'center',
      render: (_value, row) => (
        <div className="flex gap-2 justify-center">
          <Button variant="link" size="sm" onClick={() => openEditDialog(row)}>수정</Button>
          <Button variant="link" size="sm" className="text-red-600" onClick={() => handleDeleteBadge(row.id)}>삭제</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">뱃지 미션 관리</h1>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <div className="p-4 border-b flex justify-between items-center">
          <h2 className="font-semibold">뱃지 목록 ({badges.length})</h2>
          <Button variant="primary" onClick={openCreateDialog}>+ 뱃지 추가</Button>
        </div>
        <DataTable<Badge>
          columns={columns}
          data={badges}
          loading={isLoading}
          emptyMessage="등록된 뱃지가 없습니다."
          rowClassName={(row) => !row.isActive ? 'opacity-50' : ''}
        />
      </div>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) closeDialog(); }}>
        <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingBadge ? '뱃지 수정' : '뱃지 추가'}</DialogTitle>
            <DialogDescription>뱃지 이름, 설명, 미션 조건을 입력하세요.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              {/* Image Upload */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">뱃지 이미지</label>
                <div className="flex items-center gap-4">
                  <BadgeImage src={imageUrl || ''} size="lg" />
                  <div className="flex items-center gap-2">
                    <label className={`inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-lg cursor-pointer transition-colors ${uploading ? 'bg-gray-100 text-gray-400' : 'bg-blue-50 text-blue-700 hover:bg-blue-100'}`}>
                      {uploading ? '업로드 중...' : '이미지 선택'}
                      <input type="file" accept="image/*" onChange={handleImageUpload} disabled={uploading} className="hidden" />
                    </label>
                    {imageUrl && (
                      <button type="button" onClick={() => setValue('imageUrl', '')} className="text-sm text-red-600 hover:text-red-800">
                        삭제
                      </button>
                    )}
                  </div>
                </div>
              </div>

              <FormField name="name" control={form.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>이름 *</FormLabel>
                  <FormControl><Input {...field} placeholder="뱃지 이름" /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />

              <FormField name="description" control={form.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>설명 *</FormLabel>
                  <FormControl>
                    <textarea
                      {...field}
                      rows={2}
                      placeholder="뱃지 설명"
                      className="flex min-h-[60px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )} />

              <div className="grid grid-cols-2 gap-4">
                <FormField name="conditionType" control={form.control} render={({ field }) => (
                  <FormItem>
                    <FormLabel>미션 조건 유형</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger><SelectValue placeholder="선택 안함" /></SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="">선택 안함</SelectItem>
                        {CONDITION_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>{CONDITION_TYPE_LABELS[type]}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField name="conditionValue" control={form.control} render={({ field }) => (
                  <FormItem>
                    <FormLabel>달성 기준값</FormLabel>
                    <FormControl><Input type="number" {...field} placeholder="1" min={1} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
              </div>

              <FormField name="rewardGold" control={form.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>보상 골드</FormLabel>
                  <FormControl><Input type="number" {...field} placeholder="0 (보상 없음)" min={0} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />

              <div className="grid grid-cols-2 gap-4">
                <FormField name="startDate" control={form.control} render={({ field }) => (
                  <FormItem>
                    <FormLabel>시작일 (비워두면 상시)</FormLabel>
                    <FormControl><Input type="date" {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField name="endDate" control={form.control} render={({ field }) => (
                  <FormItem>
                    <FormLabel>종료일</FormLabel>
                    <FormControl><Input type="date" {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
              </div>

              <div className="space-y-2">
                <FormField name="isRepeatable" control={form.control} render={({ field }) => (
                  <FormItem className="flex items-center gap-2 space-y-0">
                    <FormControl>
                      <input
                        type="checkbox"
                        id="isRepeatable"
                        checked={field.value}
                        onChange={(e) => {
                          field.onChange(e.target.checked);
                          if (!e.target.checked) setValue('repeatCycle', '');
                        }}
                        className="rounded"
                      />
                    </FormControl>
                    <FormLabel htmlFor="isRepeatable" className="font-normal cursor-pointer">반복 미션</FormLabel>
                  </FormItem>
                )} />
                {isRepeatable && (
                  <FormField name="repeatCycle" control={form.control} render={({ field }) => (
                    <FormItem>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger><SelectValue placeholder="주기 선택" /></SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {REPEAT_CYCLES.map((cycle) => (
                            <SelectItem key={cycle.value} value={cycle.value}>{cycle.label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )} />
                )}
              </div>

              {editingBadge && (
                <FormField name="isActive" control={form.control} render={({ field }) => (
                  <FormItem className="flex items-center gap-2 space-y-0">
                    <FormControl>
                      <input
                        type="checkbox"
                        id="isActive"
                        checked={field.value}
                        onChange={(e) => field.onChange(e.target.checked)}
                        className="rounded"
                      />
                    </FormControl>
                    <FormLabel htmlFor="isActive" className="font-normal cursor-pointer">활성 상태</FormLabel>
                  </FormItem>
                )} />
              )}

              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={closeDialog}>취소</UIButton>
                <UIButton type="submit" disabled={isSubmitting || uploading}>
                  {isSubmitting ? '저장 중...' : '저장'}
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
