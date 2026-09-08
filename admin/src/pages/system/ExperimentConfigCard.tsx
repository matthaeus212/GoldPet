import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import axios from 'axios';
import { systemService } from '../../services/systemService';
import type { ExperimentConfig } from '../../services/systemService';
import { Button } from '../../components/common/Button';

function CardShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
      <h2 className="font-semibold mb-1">A/B 실험 — 궁합 매칭</h2>
      <p className="text-sm text-gray-500 mb-4">
        궁합순 노출을 TREATMENT(궁합 스코어링)·CONTROL(거리순)으로 나눠 전환율을 비교합니다. (즉시 반영, 재배포 불필요)
      </p>
      {children}
    </div>
  );
}

/** 폼 상태를 `initial` 로 시드. 저장 성공 후 부모가 refetch → key 변경으로 remount. */
function ExperimentConfigForm({ initial }: { initial: ExperimentConfig }) {
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(initial.enabled);
  const [splitPct, setSplitPct] = useState(initial.splitPct);

  const mutation = useMutation({
    mutationFn: (cfg: { enabled: boolean; splitPct: number }) =>
      systemService.updateExperimentConfig(cfg),
    onSuccess: () => {
      toast.success('A/B 실험 설정이 저장되었습니다');
      queryClient.invalidateQueries({ queryKey: ['experimentConfig'] });
    },
    onError: (err) => {
      const msg = axios.isAxiosError(err)
        ? (err.response?.data as { message?: string })?.message
        : undefined;
      toast.error(msg ?? '설정 저장에 실패했습니다');
    },
  });

  const splitValid = Number.isInteger(splitPct) && splitPct >= 0 && splitPct <= 100;
  const canSave = splitValid && !mutation.isPending;

  return (
    <CardShell>
      {/* on/off */}
      <div className="flex items-center justify-between py-3 border-b">
        <div>
          <p className="font-medium">A/B 실험 사용</p>
          <p className="text-sm text-gray-500">
            OFF 시 코호트 미배정 — 궁합순 요청은 전원 궁합 스코어링(실험 도입 전 동작)
          </p>
        </div>
        <button
          type="button"
          onClick={() => setEnabled((v) => !v)}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
            enabled ? 'bg-green-500' : 'bg-gray-300'
          }`}
          aria-pressed={enabled}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
              enabled ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </div>

      {/* split_pct */}
      <div className="mt-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium">TREATMENT 분할 비율</p>
            <p className="text-xs text-gray-400">
              궁합 스코어링을 받는 유저 비율(나머지는 CONTROL=거리순)
            </p>
          </div>
          <div className="flex items-center gap-3">
            <input
              type="number"
              step="5"
              min="0"
              max="100"
              value={splitPct}
              onChange={(e) => setSplitPct(e.target.value === '' ? 0 : Math.round(Number(e.target.value)))}
              className={`border rounded px-2 py-1 w-20 text-right ${
                splitValid ? '' : 'border-red-400 bg-red-50'
              }`}
            />
            <span className="text-sm text-gray-500 w-24 text-right">
              T {splitPct}% / C {100 - splitPct}%
            </span>
          </div>
        </div>
        <input
          type="range"
          min="0"
          max="100"
          step="5"
          value={splitPct}
          onChange={(e) => setSplitPct(Math.round(Number(e.target.value)))}
          className="w-full mt-3 accent-green-500"
        />
      </div>

      {/* meta + 경고 */}
      <div className="mt-4 rounded-md bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-800">
        분할 비율·솔트 변경은 <b>아직 배정되지 않은 신규 유저에게만</b> 적용됩니다(이미 배정된 코호트는 불변 — 실험 무결성 보호).
        {' '}현재 솔트: {initial.hasSalt ? `설정됨 (v${initial.saltVersion})` : '미설정 — 활성화 시 자동 생성'}.
      </div>

      <div className="mt-5 flex items-center justify-end gap-3">
        <Button
          variant="secondary"
          onClick={() => {
            setEnabled(initial.enabled);
            setSplitPct(initial.splitPct);
          }}
          disabled={mutation.isPending}
        >
          되돌리기
        </Button>
        <Button
          variant="primary"
          onClick={() => mutation.mutate({ enabled, splitPct })}
          disabled={!canSave}
        >
          {mutation.isPending ? '저장 중…' : '저장'}
        </Button>
      </div>
    </CardShell>
  );
}

export default function ExperimentConfigCard() {
  const { data, isLoading } = useQuery({
    queryKey: ['experimentConfig'],
    queryFn: () => systemService.getExperimentConfig(),
  });

  if (isLoading || !data) {
    return (
      <CardShell>
        <p className="text-sm text-gray-500">불러오는 중…</p>
      </CardShell>
    );
  }

  // key on values → 저장 후 refetch 로 값이 바뀌면 폼이 새 initial 로 remount.
  const k = `${data.enabled}-${data.splitPct}-${data.saltVersion}-${data.hasSalt}`;
  return <ExperimentConfigForm key={k} initial={data} />;
}
