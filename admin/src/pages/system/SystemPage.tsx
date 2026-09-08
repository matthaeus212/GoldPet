import { useMemo, useState } from 'react';
import axios from 'axios';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { systemService } from '../../services/systemService';
import type { AdminUser, AdminCreateRequest, AdminUpdateRequest } from '../../services/systemService';
import { cacheService } from '../../services/cacheService';
import { Button } from '../../components/common/Button';
import { DataTable } from '../../components/common/DataTable';
import type { ColumnDef } from '../../components/common/DataTable';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button as UIButton } from '@/components/ui/button';
import AdminFormDialog from './AdminFormDialog';
import MatchingConfigCard from './MatchingConfigCard';
import ExperimentConfigCard from './ExperimentConfigCard';
import { DevLoginIpCard } from './DevLoginIpCard';

export default function SystemPage() {
  const queryClient = useQueryClient();
  const { confirm: confirmDialog, ConfirmDialog } = useConfirm()

  const [adminFormOpen, setAdminFormOpen] = useState(false);
  const [editingAdmin, setEditingAdmin] = useState<AdminUser | null>(null);
  const [tempPassword, setTempPassword] = useState<string | null>(null);
  const [tempPasswordTarget, setTempPasswordTarget] = useState<string>('');

  const { data: systemInfo, isLoading: loadingSystem } = useQuery({
    queryKey: ['systemInfo'],
    queryFn: () => systemService.getSystemInfo(),
  });

  const { data: admins = [], isLoading: loadingAdmins } = useQuery({
    queryKey: ['admins'],
    queryFn: () => systemService.getAdmins(),
  });

  const { data: maintenanceStatus, isLoading: loadingMaintenance } = useQuery({
    queryKey: ['maintenanceStatus'],
    queryFn: () => systemService.getMaintenanceStatus(),
  });
  const maintenanceEnabled = maintenanceStatus?.enabled ?? false;

  const { data: configs = [], isLoading: loadingConfigs } = useQuery({
    queryKey: ['configs'],
    queryFn: () => systemService.getConfigs(),
  });

  const { data: caches = [], isLoading: loadingCaches } = useQuery({
    queryKey: ['caches'],
    queryFn: () => cacheService.getStatus(),
  });

  const updateConfigMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      systemService.updateConfig(key, value),
    onSuccess: () => {
      toast.success('설정이 저장되었습니다');
      queryClient.invalidateQueries({ queryKey: ['configs'] });
    },
    onError: () => {
      toast.error('설정 변경에 실패했습니다');
    },
  });

  const clearCacheMutation = useMutation({
    mutationFn: (cacheName: string) => cacheService.clearCache(cacheName),
    onSuccess: (result) => {
      if (result.success) {
        toast.success(`캐시 초기화 완료: ${result.message}`);
      } else {
        toast.error(`캐시 초기화 실패: ${result.message}`);
      }
      queryClient.invalidateQueries({ queryKey: ['caches'] });
    },
    onError: () => {
      toast.error('캐시 초기화 실패');
    },
  });

  const signupEnabled = useMemo(() => {
    if (configs.length > 0) {
      const signupConfig = configs.find(c => c.key === 'allow_signups' || c.key === 'signup.enabled');
      if (signupConfig) return signupConfig.value === 'true';
    }
    return true;
  }, [configs]);

  const maintenanceMutation = useMutation({
    mutationFn: (enabled: boolean) => systemService.setMaintenanceMode(enabled),
    onSuccess: (_data, enabled) => {
      toast.info(enabled ? '유지보수 모드가 활성화되었습니다.' : '유지보수 모드가 해제되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['maintenanceStatus'] });
      queryClient.invalidateQueries({ queryKey: ['configs'] });
      queryClient.invalidateQueries({ queryKey: ['notices'] });
    },
    onError: () => {
      toast.error('유지보수 모드 변경에 실패했습니다.');
    },
  });

  const signupMutation = useMutation({
    mutationFn: (enabled: boolean) => systemService.updateConfig('allow_signups', String(enabled)),
    onSuccess: (_data, enabled) => {
      toast.info(enabled ? '신규 가입이 허용되었습니다.' : '신규 가입이 차단되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['configs'] });
    },
    onError: () => {
      toast.error('설정 변경에 실패했습니다.');
    },
  });

  const clearAllCachesMutation = useMutation({
    mutationFn: () => cacheService.clearAll(),
    onSuccess: (result) => {
      if (result.success) {
        toast.success(`전체 캐시 초기화 완료: ${result.message}`);
      } else {
        toast.error(`전체 캐시 초기화 실패: ${result.message}`);
      }
      queryClient.invalidateQueries({ queryKey: ['caches'] });
    },
    onError: () => {
      toast.error('전체 캐시 초기화 실패');
    },
  });

  const deleteAdminMutation = useMutation({
    mutationFn: (id: number) => systemService.deleteAdmin(id),
    onSuccess: () => {
      toast.success('관리자가 삭제되었습니다.');
      queryClient.invalidateQueries({ queryKey: ['admins'] });
    },
    onError: () => {
      toast.error('삭제에 실패했습니다.');
    },
  });

  const resetPasswordMutation = useMutation({
    mutationFn: (id: number) => systemService.resetAdminPassword(id),
    onSuccess: (data, id) => {
      const admin = admins.find(a => a.id === id);
      setTempPasswordTarget(admin?.email ?? '');
      setTempPassword(data.temporaryPassword);
    },
    onError: () => {
      toast.error('비밀번호 재발급에 실패했습니다.');
    },
  });

  const { data: backupJobs = [] } = useQuery({
    queryKey: ['backupJobs'],
    queryFn: () => systemService.getBackupJobs(5),
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
  });

  const lastJob = backupJobs[0];
  const isRunning = lastJob?.status === 'PENDING' || lastJob?.status === 'RUNNING';

  const triggerMutation = useMutation({
    mutationFn: () => systemService.triggerBackup(),
    onSuccess: () => {
      toast.success('백업이 시작되었습니다');
      queryClient.invalidateQueries({ queryKey: ['backupJobs'] });
    },
    onError: (err) => {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        toast.error('이미 진행 중인 백업이 있습니다');
      } else {
        toast.error('백업 트리거 실패');
      }
    },
  });

  const handleUpdateConfig = (key: string, value: string) => {
    updateConfigMutation.mutate({ key, value });
  };

  const handleClearCache = async (cacheName: string) => {
    if (!(await confirmDialog({ description: `${cacheName} 캐시를 초기화하시겠습니까?` }))) return;
    clearCacheMutation.mutate(cacheName);
  };

  const handleClearAllCaches = async () => {
    if (!(await confirmDialog({ description: '전체 캐시를 초기화하시겠습니까? 일시적으로 시스템 성능에 영향을 줄 수 있습니다.' }))) return;
    clearAllCachesMutation.mutate();
  };

  const handleToggleMaintenance = async () => {
    const next = !maintenanceEnabled;
    const msg = next
      ? '유지보수 모드를 활성화하시겠습니까? 서비스가 중단됩니다.'
      : '유지보수 모드를 해제하시겠습니까?';
    if (await confirmDialog({ description: msg })) maintenanceMutation.mutate(next);
  };

  const handleToggleSignup = async () => {
    const next = !signupEnabled;
    const msg = next
      ? '신규 가입을 허용하시겠습니까?'
      : '신규 가입을 차단하시겠습니까?';
    if (await confirmDialog({ description: msg })) signupMutation.mutate(next);
  };

  const handleDeleteAdmin = async (admin: AdminUser) => {
    if (!(await confirmDialog({ description: `${admin.email} 관리자를 삭제하시겠습니까?`, variant: 'destructive' }))) return;
    deleteAdminMutation.mutate(admin.id);
  };

  const handleResetPassword = async (admin: AdminUser) => {
    if (!(await confirmDialog({ description: `${admin.email}의 비밀번호를 재발급하시겠습니까?` }))) return;
    resetPasswordMutation.mutate(admin.id);
  };

  const openCreateDialog = () => {
    setEditingAdmin(null);
    setAdminFormOpen(true);
  };

  const openEditDialog = (admin: AdminUser) => {
    setEditingAdmin(admin);
    setAdminFormOpen(true);
  };

  const handleAdminFormSubmit = async (data: AdminCreateRequest | AdminUpdateRequest) => {
    try {
      if (editingAdmin) {
        await systemService.updateAdmin(editingAdmin.id, data as AdminUpdateRequest);
        toast.success('관리자 정보가 수정되었습니다.');
        setAdminFormOpen(false);
        queryClient.invalidateQueries({ queryKey: ['admins'] });
      } else {
        const result = await systemService.createAdmin(data as AdminCreateRequest);
        setAdminFormOpen(false);
        queryClient.invalidateQueries({ queryKey: ['admins'] });
        if (result.temporaryPassword) {
          setTempPasswordTarget(result.email);
          setTempPassword(result.temporaryPassword);
        } else {
          toast.success('관리자가 추가되었습니다.');
        }
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      toast.error(msg ?? '저장에 실패했습니다.');
      throw err;
    }
  };

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      OK: 'bg-green-100 text-green-800',
      WARNING: 'bg-yellow-100 text-yellow-800',
      ERROR: 'bg-red-100 text-red-800',
    };
    return <span className={`px-2 py-1 text-xs rounded-full ${styles[status] || 'bg-gray-100'}`}>{status}</span>;
  };

  const loading = loadingSystem || loadingAdmins || loadingConfigs || loadingCaches || loadingMaintenance;

  if (loading) {
    return <div className="flex items-center justify-center h-64">로딩 중...</div>;
  }

  const adminColumns: ColumnDef<AdminUser>[] = [
    { key: 'email', header: '이메일' },
    { key: 'name', header: '이름' },
    {
      key: 'role',
      header: '역할',
      render: (_value, row) => (
        <span className={`px-2 py-1 text-xs rounded ${row.role === 'SUPER_ADMIN' ? 'bg-red-100 text-red-800' : 'bg-blue-100 text-blue-800'}`}>
          {row.role === 'SUPER_ADMIN' ? '최고 관리자' : '운영자'}
        </span>
      ),
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
    { key: 'lastLogin', header: '최근 로그인' },
    {
      key: 'id',
      header: '관리',
      align: 'right',
      render: (_value, row) => (
        <span className="inline-flex gap-3">
          <Button variant="link" size="sm" onClick={() => openEditDialog(row)}>수정</Button>
          <Button variant="link" size="sm" onClick={() => handleResetPassword(row)} disabled={resetPasswordMutation.isPending}>재발급</Button>
          <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDeleteAdmin(row)}>삭제</Button>
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">시스템 관리</h1>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h2 className="font-semibold mb-4">시스템 상태</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div>
            <p className="text-sm text-gray-500">버전</p>
            <p className="text-lg font-semibold">{systemInfo?.version}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">환경</p>
            <p className="text-lg font-semibold">{systemInfo?.environment}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">서버 시간</p>
            <p className="text-lg font-semibold">{systemInfo?.serverTime}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">가동 시간</p>
            <p className="text-lg font-semibold">{systemInfo?.uptime}</p>
          </div>
        </div>
        <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <span>데이터베이스</span>
            {getStatusBadge(systemInfo?.dbStatus || 'OK')}
          </div>
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <span>캐시 서버</span>
            {getStatusBadge(systemInfo?.cacheStatus || 'OK')}
          </div>
          <div className="p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-2">
              <span>스토리지</span>
              <span className="text-sm">{systemInfo?.storageUsed}GB / {systemInfo?.storageTotal}GB</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div
                className="bg-indigo-600 h-2 rounded-full"
                style={{ width: `${((systemInfo?.storageUsed || 0) / (systemInfo?.storageTotal || 100)) * 100}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <div className="flex justify-between items-center mb-4">
          <h2 className="font-semibold">캐시 관리</h2>
          <Button variant="danger" onClick={handleClearAllCaches}>
            전체 초기화
          </Button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {caches.map(cache => (
            <div key={cache.name} className="p-4 bg-gray-50 rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <span className="font-medium text-sm">{cache.name}</span>
                <span className={`px-2 py-0.5 text-xs rounded ${cache.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
                  {cache.isActive ? '활성' : '비활성'}
                </span>
              </div>
              <p className="text-xs text-gray-500 mb-2">{cache.description}</p>
              <div className="flex items-center justify-between">
                <span className="text-xs text-gray-400">TTL: {cache.ttlSeconds}s</span>
                <Button
                  variant="link"
                  size="sm"
                  className="text-red-600 hover:text-red-900"
                  onClick={() => handleClearCache(cache.name)}
                >
                  초기화
                </Button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <MatchingConfigCard />

      <ExperimentConfigCard />

      <DevLoginIpCard />

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h2 className="font-semibold mb-4">앱 설정</h2>
        <div className="space-y-4">
          {[...configs].filter(c => c.key !== 'maintenance.mode').sort((a, b) => a.key.localeCompare(b.key)).map(config => {
            const isBooleanValue = config.value === 'true' || config.value === 'false';

            return (
              <div key={config.key} className="flex items-center justify-between py-3 border-b">
                <div>
                  <p className="font-medium">{config.key}</p>
                  <p className="text-sm text-gray-500">{config.description}</p>
                </div>
                <div className="flex items-center gap-2">
                  {isBooleanValue ? (
                    <select
                      key={`${config.key}-${config.value}`}
                      defaultValue={config.value}
                      className="border rounded px-2 py-1 w-32 text-right bg-white"
                      onChange={(e) => {
                        if (e.target.value !== config.value) {
                          handleUpdateConfig(config.key, e.target.value);
                        }
                      }}
                    >
                      <option value="true">활성 (true)</option>
                      <option value="false">비활성 (false)</option>
                    </select>
                  ) : (
                    <input
                      key={`${config.key}-${config.value}`}
                      type="text"
                      defaultValue={config.value}
                      className="border rounded px-2 py-1 w-32 text-right"
                      onBlur={(e) => {
                        if (e.target.value !== config.value) {
                          handleUpdateConfig(config.key, e.target.value);
                        }
                      }}
                    />
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200">
        <div className="p-4 border-b flex justify-between items-center">
          <h2 className="font-semibold">관리자 계정</h2>
          <Button variant="primary" onClick={openCreateDialog}>+ 관리자 추가</Button>
        </div>
        <DataTable<AdminUser>
          columns={adminColumns}
          data={admins}
          emptyMessage="관리자가 없습니다."
        />
      </div>

      <div className="bg-white shadow-sm rounded-lg border border-gray-200 p-6">
        <h2 className="font-semibold mb-4">유지보수</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between py-3 border-b">
            <div>
              <p className="font-medium">유지보수 모드</p>
              <p className="text-sm text-gray-500">서비스를 중단하고 유지보수 페이지를 표시합니다</p>
            </div>
            <Button
              variant={maintenanceEnabled ? 'danger' : 'ghost'}
              disabled={maintenanceMutation.isPending}
              onClick={handleToggleMaintenance}
            >
              {maintenanceMutation.isPending ? '변경 중...' : maintenanceEnabled ? '활성' : '비활성'}
            </Button>
          </div>
          <div className="flex items-center justify-between py-3 border-b">
            <div>
              <p className="font-medium">신규 가입 허용</p>
              <p className="text-sm text-gray-500">신규 회원 가입 허용 여부</p>
            </div>
            <Button
              variant={signupEnabled ? 'success' : 'ghost'}
              disabled={signupMutation.isPending}
              onClick={handleToggleSignup}
            >
              {signupMutation.isPending ? '변경 중...' : signupEnabled ? '활성' : '비활성'}
            </Button>
          </div>
          <div className="py-3">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="font-medium">데이터 백업</p>
                <p className="text-sm text-gray-500">수동 또는 자동(systemd timer)으로 실행됩니다</p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => queryClient.invalidateQueries({ queryKey: ['backupJobs'] })}
                >
                  지금 새로고침
                </Button>
                <Button
                  variant="primary"
                  disabled={isRunning || triggerMutation.isPending}
                  onClick={() => triggerMutation.mutate()}
                >
                  {triggerMutation.isPending ? '요청 중...' : isRunning ? '진행 중...' : '백업 실행'}
                </Button>
              </div>
            </div>
            {lastJob && (
              <div className="bg-gray-50 rounded-lg p-4 space-y-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-gray-500">상태</span>
                  <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                    lastJob.status === 'SUCCEEDED' ? 'bg-green-100 text-green-800' :
                    lastJob.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                    lastJob.status === 'RUNNING' ? 'bg-blue-100 text-blue-800' :
                    'bg-yellow-100 text-yellow-800'
                  }`}>{lastJob.status}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-gray-500">트리거</span>
                  <span>{lastJob.triggerType === 'MANUAL' ? `수동 (${lastJob.triggeredByAdminEmail ?? '-'})` : '자동 (cron)'}</span>
                </div>
                {lastJob.startedAt && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">시작</span>
                    <span>{lastJob.startedAt}</span>
                  </div>
                )}
                {lastJob.finishedAt && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">완료</span>
                    <span>{lastJob.finishedAt}</span>
                  </div>
                )}
                {lastJob.fileSizeBytes != null && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">파일 크기</span>
                    <span>{(lastJob.fileSizeBytes / 1024 / 1024).toFixed(2)} MB</span>
                  </div>
                )}
                {lastJob.filePath && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">경로</span>
                    <span className="text-xs text-gray-600 truncate max-w-xs">{lastJob.filePath}</span>
                  </div>
                )}
                {lastJob.errorMessage && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">오류</span>
                    <span className="text-red-600 text-xs">{lastJob.errorMessage}</span>
                  </div>
                )}
              </div>
            )}
            {backupJobs.length > 1 && (
              <div className="mt-3 overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead>
                    <tr className="text-gray-500 border-b">
                      <th className="pb-1 pr-3">ID</th>
                      <th className="pb-1 pr-3">상태</th>
                      <th className="pb-1 pr-3">트리거</th>
                      <th className="pb-1 pr-3">시작</th>
                      <th className="pb-1">크기</th>
                    </tr>
                  </thead>
                  <tbody>
                    {backupJobs.map(job => (
                      <tr key={job.id} className="border-b last:border-0">
                        <td className="py-1 pr-3 text-gray-400">{job.id}</td>
                        <td className="py-1 pr-3">
                          <span className={`px-1.5 py-0.5 rounded text-xs ${
                            job.status === 'SUCCEEDED' ? 'bg-green-100 text-green-800' :
                            job.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                            job.status === 'RUNNING' ? 'bg-blue-100 text-blue-800' :
                            'bg-yellow-100 text-yellow-800'
                          }`}>{job.status}</span>
                        </td>
                        <td className="py-1 pr-3">{job.triggerType === 'MANUAL' ? '수동' : '자동'}</td>
                        <td className="py-1 pr-3 text-gray-500">{job.startedAt ?? '-'}</td>
                        <td className="py-1 text-gray-500">
                          {job.fileSizeBytes != null ? `${(job.fileSizeBytes / 1024 / 1024).toFixed(1)} MB` : '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      <AdminFormDialog
        open={adminFormOpen}
        onOpenChange={setAdminFormOpen}
        editingAdmin={editingAdmin}
        onSubmit={handleAdminFormSubmit}
      />

      <Dialog open={tempPassword !== null} onOpenChange={(open) => { if (!open) setTempPassword(null); }}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>임시 비밀번호</DialogTitle>
            <DialogDescription>
              {tempPasswordTarget}의 임시 비밀번호입니다. 이 창을 닫으면 다시 확인할 수 없습니다.
            </DialogDescription>
          </DialogHeader>
          <div className="my-4 p-4 bg-gray-50 rounded-lg text-center">
            <p className="text-sm text-gray-500 mb-1">임시 비밀번호</p>
            <p className="text-xl font-mono font-bold tracking-widest text-gray-900 select-all">{tempPassword}</p>
          </div>
          <DialogFooter>
            <UIButton onClick={() => setTempPassword(null)}>확인</UIButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>

    {ConfirmDialog}
    </div>
  );
}
