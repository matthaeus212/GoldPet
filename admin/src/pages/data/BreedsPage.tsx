import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { breedService } from '../../services/breedService';
import type { BreedResponse, SpeciesResponse } from '../../services/breedService';
import { Button } from '../../components/common/Button';
import { useConfirm } from '@/hooks/useConfirm';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Button as UIButton } from '@/components/ui/button';

const breedSchema = z.object({
  speciesId: z.number().int().min(1, '종을 선택해주세요.'),
  name: z.string().trim().min(1, '품종명을 입력해주세요.'),
  category: z.string(),
  description: z.string(),
});

type BreedFormValues = z.infer<typeof breedSchema>;

interface BreedsPageProps {
  embedded?: boolean;
}

const BreedsPage: React.FC<BreedsPageProps> = ({ embedded = false }) => {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm();
  const [selectedSpeciesId, setSelectedSpeciesId] = useState<number | undefined>();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingBreed, setEditingBreed] = useState<BreedResponse | null>(null);

  const form = useForm<BreedFormValues>({
    resolver: zodResolver(breedSchema),
    defaultValues: { speciesId: 0, name: '', category: '', description: '' },
  });

  const { formState: { isSubmitting } } = form;

  const { data: species = [] } = useQuery<SpeciesResponse[]>({
    queryKey: ['species'],
    queryFn: breedService.getSpecies,
  });

  const { data: breeds = [], isLoading } = useQuery<BreedResponse[]>({
    queryKey: ['breeds', selectedSpeciesId],
    queryFn: () => breedService.getBreeds(selectedSpeciesId),
  });

  const openCreateForm = () => {
    setEditingBreed(null);
    form.reset({ speciesId: selectedSpeciesId || 0, name: '', category: '', description: '' });
    setDialogOpen(true);
  };

  const openEditForm = (breed: BreedResponse) => {
    setEditingBreed(breed);
    form.reset({
      speciesId: breed.speciesId,
      name: breed.name,
      category: breed.category || '',
      description: breed.description || '',
    });
    setDialogOpen(true);
  };

  const closeForm = () => {
    setDialogOpen(false);
    setEditingBreed(null);
    form.reset();
  };

  const onSubmit = async (values: BreedFormValues) => {
    try {
      if (editingBreed) {
        await breedService.updateBreed(editingBreed.id, {
          name: values.name,
          category: values.category || undefined,
          description: values.description || undefined,
        });
      } else {
        await breedService.createBreed({
          speciesId: values.speciesId,
          name: values.name,
          category: values.category || undefined,
          description: values.description || undefined,
        });
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ['breeds'] });
    } catch (error) {
      console.error('Failed to save breed:', error);
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleDelete = async (breedId: number) => {
    if (!(await confirmDialog({ description: '정말 삭제하시겠습니까?', variant: 'destructive' }))) return;
    try {
      await breedService.deleteBreed(breedId);
      queryClient.invalidateQueries({ queryKey: ['breeds'] });
    } catch (error) {
      console.error('Failed to delete breed:', error);
      toast.error('삭제에 실패했습니다.');
    }
  };

  const getSpeciesName = (speciesId: number) => {
    return species.find((s) => s.id === speciesId)?.name || '-';
  };

  return (
    <div className={embedded ? '' : 'p-6'}>
      {!embedded && (
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold text-gray-800">품종 관리</h1>
        </div>
      )}

      <div className={`flex justify-between items-center ${embedded ? 'mb-4' : 'mb-6'}`}>
        <div></div>
        <Button variant="primary" onClick={openCreateForm}>+ 품종 추가</Button>
      </div>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">종 선택</label>
        <select
          value={selectedSpeciesId || ''}
          onChange={(e) => setSelectedSpeciesId(e.target.value ? Number(e.target.value) : undefined)}
          className="border border-gray-300 rounded-lg px-3 py-2 w-48"
        >
          <option value="">전체</option>
          {species.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">종</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">분류</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">품종명</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">설명</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">관리</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-6 py-4 text-center text-gray-500">로딩 중...</td>
              </tr>
            ) : breeds.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-4 text-center text-gray-500">등록된 품종이 없습니다.</td>
              </tr>
            ) : (
              breeds.map((breed) => (
                <tr key={breed.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{breed.id}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{getSpeciesName(breed.speciesId)}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{breed.category || '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{breed.name}</td>
                  <td className="px-6 py-4 text-sm text-gray-500">{breed.description || '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <Button variant="link" size="sm" onClick={() => openEditForm(breed)}>수정</Button>
                    <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDelete(breed.id)}>삭제</Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingBreed ? '품종 수정' : '품종 추가'}</DialogTitle>
            <DialogDescription>종, 분류, 품종명, 설명을 입력하세요.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                name="speciesId"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>종</FormLabel>
                    <Select
                      onValueChange={(v) => field.onChange(Number(v))}
                      value={field.value > 0 ? String(field.value) : ''}
                      disabled={!!editingBreed}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="선택해주세요" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {species.map((s) => (
                          <SelectItem key={s.id} value={String(s.id)}>{s.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="category"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>분류 (예: 소형견, 단모종)</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="분류를 입력하세요" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="name"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>품종명</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="품종명을 입력하세요" />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="description"
                control={form.control}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>설명 (선택)</FormLabel>
                    <FormControl>
                      <textarea
                        {...field}
                        rows={3}
                        placeholder="설명을 입력하세요"
                        className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <DialogFooter>
                <UIButton type="button" variant="outline" onClick={closeForm}>취소</UIButton>
                <UIButton type="submit" disabled={isSubmitting}>
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
};

export default BreedsPage;
