import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import axios from 'axios';
import { systemService } from '../../services/systemService';
import type { MatchingConfig } from '../../services/systemService';
import { Button } from '../../components/common/Button';

const WEIGHT_FIELDS: { key: keyof MatchingConfig; label: string; hint: string }[] = [
  { key: 'weightDistance', label: '거리 근접도', hint: '가까울수록 높은 점수' },
  { key: 'weightInterest', label: '관심사 일치', hint: '공통 관심사 비율' },
  { key: 'weightHobby', label: '취미 일치', hint: '공통 취미 비율' },
  { key: 'weightTemperament', label: '기질 일치', hint: '반려동물 성향 겹침' },
];

function CardShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
      <h2 className="font-semibold mb-1">매칭 궁합 점수 설정</h2>
      <p className="text-sm text-gray-500 mb-4">
        궁합순 정렬의 on/off와 점수 가중치를 조정합니다. (즉시 반영, 재배포 불필요)
      </p>
      {children}
    </div>
  );
}

/** 폼 상태를 `initial` 로 시드 (effect-free). 저장 성공 후 부모가 refetch → key 변경으로 remount. */
function MatchingConfigForm({ initial }: { initial: MatchingConfig }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<MatchingConfig>(initial);

  const mutation = useMutation({
    mutationFn: (cfg: MatchingConfig) => systemService.updateMatchingConfig(cfg),
    onSuccess: () => {
      toast.success('매칭 궁합 설정이 저장되었습니다');
      queryClient.invalidateQueries({ queryKey: ['matchingConfig'] });
    },
    onError: (err) => {
      const msg = axios.isAxiosError(err)
        ? (err.response?.data as { message?: string })?.message
        : undefined;
      toast.error(msg ?? '설정 저장에 실패했습니다');
    },
  });

  const weightSum = form.weightDistance + form.weightInterest + form.weightHobby + form.weightTemperament;
  const sumValid = Math.abs(weightSum - 1) <= 0.01;
  const inRange = (v: number) => !Number.isNaN(v) && v >= 0 && v <= 1;
  const allInRange =
    WEIGHT_FIELDS.every((f) => inRange(form[f.key] as number)) && inRange(form.boostRankBonus);
  const canSave = sumValid && allInRange && !mutation.isPending;

  const setNum = (key: keyof MatchingConfig, raw: string) =>
    setForm((p) => ({ ...p, [key]: raw === '' ? 0 : Number(raw) }));

  return (
    <CardShell>
      {/* on/off */}
      <div className="flex items-center justify-between py-3 border-b">
        <div>
          <p className="font-medium">궁합순 정렬 사용</p>
          <p className="text-sm text-gray-500">OFF 시 궁합순 요청은 거리순으로 폴백</p>
        </div>
        <button
          type="button"
          onClick={() => setForm((p) => ({ ...p, enabled: !p.enabled }))}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
            form.enabled ? 'bg-green-500' : 'bg-gray-300'
          }`}
          aria-pressed={form.enabled}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
              form.enabled ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </div>

      {/* weights */}
      <div className="mt-4 space-y-3">
        {WEIGHT_FIELDS.map((f) => {
          const v = form[f.key] as number;
          return (
            <div key={f.key} className="flex items-center justify-between">
              <div>
                <p className="font-medium">{f.label}</p>
                <p className="text-xs text-gray-400">{f.hint}</p>
              </div>
              <div className="flex items-center gap-3">
                <div className="hidden sm:block h-2 w-28 rounded bg-gray-100 overflow-hidden">
                  <div
                    className="h-full bg-blue-400"
                    style={{ width: `${Math.min(Math.max(v, 0), 1) * 100}%` }}
                  />
                </div>
                <input
                  type="number"
                  step="0.05"
                  min="0"
                  max="1"
                  value={v}
                  onChange={(e) => setNum(f.key, e.target.value)}
                  className={`border rounded px-2 py-1 w-20 text-right ${
                    inRange(v) ? '' : 'border-red-400 bg-red-50'
                  }`}
                />
              </div>
            </div>
          );
        })}
      </div>

      {/* sum indicator */}
      <div className="mt-3 flex items-center justify-between border-t pt-3">
        <span className="text-sm text-gray-500">가중치 합계 (거리+관심사+취미+기질)</span>
        <span className={`text-sm font-semibold ${sumValid ? 'text-green-600' : 'text-red-500'}`}>
          {weightSum.toFixed(2)} {sumValid ? '✓' : '— 1.00 이어야 함'}
        </span>
      </div>

      {/* boost bonus */}
      <div className="mt-4 flex items-center justify-between">
        <div>
          <p className="font-medium">부스트 가산점</p>
          <p className="text-xs text-gray-400">프로필 부스트 구매자 궁합 점수 가산 (최종 0~1 클램프)</p>
        </div>
        <input
          type="number"
          step="0.05"
          min="0"
          max="1"
          value={form.boostRankBonus}
          onChange={(e) => setNum('boostRankBonus', e.target.value)}
          className={`border rounded px-2 py-1 w-20 text-right ${
            inRange(form.boostRankBonus) ? '' : 'border-red-400 bg-red-50'
          }`}
        />
      </div>

      <div className="mt-5 flex items-center justify-end gap-3">
        <Button variant="secondary" onClick={() => setForm(initial)} disabled={mutation.isPending}>
          되돌리기
        </Button>
        <Button variant="primary" onClick={() => mutation.mutate(form)} disabled={!canSave}>
          {mutation.isPending ? '저장 중…' : '저장'}
        </Button>
      </div>
    </CardShell>
  );
}

export default function MatchingConfigCard() {
  const { data, isLoading } = useQuery({
    queryKey: ['matchingConfig'],
    queryFn: () => systemService.getMatchingConfig(),
  });

  if (isLoading || !data) {
    return (
      <CardShell>
        <p className="text-sm text-gray-500">불러오는 중…</p>
      </CardShell>
    );
  }

  // key on values → 저장 후 refetch 로 값이 바뀌면 폼이 새 initial 로 remount.
  const k = `${data.enabled}-${data.weightDistance}-${data.weightInterest}-${data.weightHobby}-${data.weightTemperament}-${data.boostRankBonus}`;
  return <MatchingConfigForm key={k} initial={data} />;
}
