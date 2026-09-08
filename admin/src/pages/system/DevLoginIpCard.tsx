// dev-login 허용 IP 관리 카드 (SUPER_ADMIN 전용)
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import axios from 'axios';
import { devLoginService } from '../../services/devLoginService';
import type { DevLoginIpEntry } from '../../services/devLoginService';
import { Button } from '../../components/common/Button';

/**
 * dev-login 은 비밀번호·SNS 인증 없이 토큰을 발급하는 QA 엔드포인트다.
 * 이 목록을 바꾸는 것은 **인증 통제를 바꾸는 것**이므로, 서버가 다음을 강제한다:
 *  - 변경 후에도 내 IP 가 통과하는지 확인(자기잠김 방지, 409)
 *  - 광역 CIDR 거부(IPv4 /24, IPv6 /48 보다 넓으면 400)
 *  - 마지막 활성 IP 삭제 금지(끄려면 킬스위치를 쓰라)
 * 변경은 캐시 없이 즉시 반영된다.
 */
function errorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    return (err.response?.data as { message?: string } | undefined)?.message ?? fallback;
  }
  return fallback;
}

export function DevLoginIpCard() {
  const queryClient = useQueryClient();
  const [ipPattern, setIpPattern] = useState('');
  const [label, setLabel] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['devLoginWhitelist'],
    queryFn: devLoginService.getWhitelist,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['devLoginWhitelist'] });

  const createMutation = useMutation({
    mutationFn: () => devLoginService.create({ ipPattern: ipPattern.trim(), label: label.trim() }),
    onSuccess: () => {
      toast.success('허용 IP를 등록했습니다 (즉시 반영)');
      setIpPattern('');
      setLabel('');
      void invalidate();
    },
    onError: (err) => toast.error(errorMessage(err, '등록에 실패했습니다')),
  });

  const toggleMutation = useMutation({
    mutationFn: (entry: DevLoginIpEntry) =>
      devLoginService.update(entry.id, { enabled: !entry.enabled }),
    onSuccess: () => {
      toast.success('변경했습니다 (즉시 반영)');
      void invalidate();
    },
    onError: (err) => toast.error(errorMessage(err, '변경에 실패했습니다')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => devLoginService.remove(id),
    onSuccess: () => {
      toast.success('삭제했습니다');
      void invalidate();
    },
    onError: (err) => toast.error(errorMessage(err, '삭제에 실패했습니다')),
  });

  const myIp = data?.requesterIp ?? null;
  const myIpRegistered =
    !!myIp && (data?.entries ?? []).some((e) => e.active && e.ipPattern === myIp);

  return (
    <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
      <h2 className="font-semibold mb-1">dev-login 허용 IP</h2>
      <p className="text-sm text-gray-500 mb-4">
        dev-login은 비밀번호 없이 시드 계정 토큰을 발급합니다. 여기 등록된 IP에서만 동작하며,
        허용 IP가 아니면 로그인 화면의 개발자 패널 자체가 보이지 않습니다. (즉시 반영, 재배포 불필요)
      </p>

      {data && !data.enabled && (
        <div className="mb-4 rounded border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          킬스위치가 꺼져 있어 <b>dev-login이 전면 차단</b>된 상태입니다. IP와 무관하게 동작하지 않습니다.
          (SystemSetting <code>devlogin.enabled</code>)
        </div>
      )}

      <div className="mb-4 rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm">
        <span className="text-gray-500">내 현재 IP: </span>
        <b className="font-mono">{myIp ?? '판정 불가'}</b>
        {myIp && !myIpRegistered && (
          <button
            type="button"
            className="ml-3 text-blue-600 underline"
            onClick={() => {
              setIpPattern(myIp);
              setLabel('내 접속 IP');
            }}
          >
            이 IP 등록하기
          </button>
        )}
        {myIpRegistered && <span className="ml-3 text-green-700">등록됨 ✓</span>}
        {data && data.allowedEmails.length > 0 && (
          <div className="mt-1 text-gray-500">
            허용 이메일: <span className="font-mono">{data.allowedEmails.join(', ')}</span>
          </div>
        )}
      </div>

      <form
        className="flex flex-wrap gap-2 items-end mb-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (!ipPattern.trim() || !label.trim()) {
            toast.error('IP와 라벨을 입력하세요');
            return;
          }
          createMutation.mutate();
        }}
      >
        <div>
          <label className="block text-xs text-gray-500 mb-1">IP 또는 CIDR</label>
          <input
            className="border rounded px-2 py-1 font-mono text-sm w-52"
            placeholder="115.79.198.72 또는 10.1.2.0/24"
            value={ipPattern}
            onChange={(e) => setIpPattern(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">라벨</label>
          <input
            className="border rounded px-2 py-1 text-sm w-56"
            placeholder="오너 접속 IP (베트남)"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={createMutation.isPending}>
          등록
        </Button>
      </form>

      {isLoading ? (
        <p className="text-sm text-gray-400">불러오는 중…</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500 border-b">
              <th className="py-2">IP / CIDR</th>
              <th>라벨</th>
              <th>상태</th>
              <th>만료</th>
              <th className="text-right">작업</th>
            </tr>
          </thead>
          <tbody>
            {(data?.entries ?? []).map((e) => (
              <tr key={e.id} className="border-b last:border-0">
                <td className="py-2 font-mono">
                  {e.ipPattern}
                  {myIp === e.ipPattern && <span className="ml-2 text-xs text-blue-600">(내 IP)</span>}
                </td>
                <td>{e.label}</td>
                <td>
                  {e.active ? (
                    <span className="text-green-700">활성</span>
                  ) : (
                    <span className="text-gray-400">비활성</span>
                  )}
                </td>
                <td className="text-gray-500">
                  {e.expiresAt ? new Date(e.expiresAt).toLocaleString() : '—'}
                </td>
                <td className="text-right space-x-2">
                  <button
                    type="button"
                    className="text-blue-600 underline disabled:opacity-40"
                    disabled={toggleMutation.isPending}
                    onClick={() => toggleMutation.mutate(e)}
                  >
                    {e.enabled ? '비활성화' : '활성화'}
                  </button>
                  <button
                    type="button"
                    className="text-red-600 underline disabled:opacity-40"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(e.id)}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
            {(data?.entries ?? []).length === 0 && (
              <tr>
                <td colSpan={5} className="py-4 text-center text-gray-400">
                  등록된 IP가 없습니다. 이 상태에서는 dev-login이 전면 차단됩니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
