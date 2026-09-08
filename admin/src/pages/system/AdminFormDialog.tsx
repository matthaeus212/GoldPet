import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button as UIButton } from '@/components/ui/button';
import type { AdminUser, AdminCreateRequest, AdminUpdateRequest } from '../../services/systemService';

interface AdminFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editingAdmin: AdminUser | null;
  onSubmit: (data: AdminCreateRequest | AdminUpdateRequest) => Promise<void>;
}

const createSchema = z.object({
  email: z.string().trim().email('올바른 이메일을 입력해주세요.'),
  name: z.string().trim().min(1, '이름을 입력해주세요.'),
  role: z.enum(['SUPER_ADMIN', 'OPERATOR', 'VIEWER']),
  temporaryPassword: z.string().optional(),
});

const editSchema = z.object({
  name: z.string().trim().min(1, '이름을 입력해주세요.'),
  role: z.enum(['SUPER_ADMIN', 'OPERATOR', 'VIEWER']),
  isActive: z.boolean(),
});

type CreateFormValues = z.infer<typeof createSchema>;
type EditFormValues = z.infer<typeof editSchema>;

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '최고 관리자',
  OPERATOR: '운영자',
  VIEWER: '뷰어',
};

export default function AdminFormDialog({ open, onOpenChange, editingAdmin, onSubmit }: AdminFormDialogProps) {
  const isEdit = editingAdmin !== null;

  const createForm = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { email: '', name: '', role: 'OPERATOR', temporaryPassword: '' },
  });

  const editForm = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { name: '', role: 'OPERATOR', isActive: true },
  });

  useEffect(() => {
    if (open) {
      if (editingAdmin) {
        editForm.reset({
          name: editingAdmin.name,
          role: editingAdmin.role as 'SUPER_ADMIN' | 'OPERATOR' | 'VIEWER',
          isActive: editingAdmin.isActive,
        });
      } else {
        createForm.reset({ email: '', name: '', role: 'OPERATOR', temporaryPassword: '' });
      }
    }
  }, [open, editingAdmin, createForm, editForm]);

  const handleClose = () => onOpenChange(false);

  const handleCreateSubmit = async (values: CreateFormValues) => {
    const payload: AdminCreateRequest = {
      email: values.email,
      name: values.name,
      role: values.role,
    };
    if (values.temporaryPassword) {
      payload.temporaryPassword = values.temporaryPassword;
    }
    await onSubmit(payload);
  };

  const handleEditSubmit = async (values: EditFormValues) => {
    const payload: AdminUpdateRequest = {
      name: values.name,
      role: values.role,
      isActive: values.isActive,
    };
    await onSubmit(payload);
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose(); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? '관리자 수정' : '관리자 추가'}</DialogTitle>
          <DialogDescription>
            {isEdit ? '관리자 정보를 수정합니다.' : '새 관리자 계정을 추가합니다.'}
          </DialogDescription>
        </DialogHeader>

        {isEdit ? (
          <Form {...editForm}>
            <form onSubmit={editForm.handleSubmit(handleEditSubmit)} className="space-y-4">
              <FormField name="name" control={editForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>이름 *</FormLabel>
                  <FormControl><Input {...field} placeholder="홍길동" /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField name="role" control={editForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>역할 *</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger><SelectValue placeholder="역할 선택" /></SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Object.entries(ROLE_LABELS).map(([value, label]) => (
                        <SelectItem key={value} value={value}>{label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField name="isActive" control={editForm.control} render={({ field }) => (
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
              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={handleClose}>취소</UIButton>
                <UIButton type="submit" disabled={editForm.formState.isSubmitting}>
                  {editForm.formState.isSubmitting ? '저장 중...' : '저장'}
                </UIButton>
              </DialogFooter>
            </form>
          </Form>
        ) : (
          <Form {...createForm}>
            <form onSubmit={createForm.handleSubmit(handleCreateSubmit)} className="space-y-4">
              <FormField name="email" control={createForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>이메일 *</FormLabel>
                  <FormControl><Input type="email" {...field} placeholder="admin@goldpet.com" /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField name="name" control={createForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>이름 *</FormLabel>
                  <FormControl><Input {...field} placeholder="홍길동" /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField name="role" control={createForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>역할 *</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger><SelectValue placeholder="역할 선택" /></SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Object.entries(ROLE_LABELS).map(([value, label]) => (
                        <SelectItem key={value} value={value}>{label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField name="temporaryPassword" control={createForm.control} render={({ field }) => (
                <FormItem>
                  <FormLabel>임시 비밀번호 (비워두면 자동 생성)</FormLabel>
                  <FormControl><Input type="password" {...field} placeholder="비워두면 자동 생성" /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={handleClose}>취소</UIButton>
                <UIButton type="submit" disabled={createForm.formState.isSubmitting}>
                  {createForm.formState.isSubmitting ? '저장 중...' : '추가'}
                </UIButton>
              </DialogFooter>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
