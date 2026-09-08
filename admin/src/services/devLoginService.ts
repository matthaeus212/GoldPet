// dev-login 허용 IP 관리 API 클라이언트
import { typedClient } from './typedClient';

export interface DevLoginIpEntry {
  id: number;
  ipPattern: string;
  label: string;
  enabled: boolean;
  expiresAt: string | null;
  /** enabled && 미만료 — 지금 실제로 통과시키는지 */
  active: boolean;
  createdAt: string;
}

export interface DevLoginWhitelistView {
  /** 마스터 킬스위치. false 면 IP 와 무관하게 dev-login 전면 차단 */
  enabled: boolean;
  allowedEmails: string[];
  /** 지금 이 화면을 보는 관리자의 IP(X-Real-IP 기준). 등록 시 오타·추측을 막으려고 서버가 알려준다 */
  requesterIp: string | null;
  entries: DevLoginIpEntry[];
}

export interface CreateDevLoginIpRequest {
  ipPattern: string;
  label: string;
  /** 생략 = 무기한. 임시 QA IP 는 만료를 걸어 방치되지 않게 한다. */
  expiresAt?: string;
}

export interface UpdateDevLoginIpRequest {
  label?: string;
  enabled?: boolean;
  expiresAt?: string;
  /** 만료를 없앨 때 true (expiresAt 을 null 로) */
  clearExpiry?: boolean;
  /** 이 변경으로 본인이 잠기는 걸 알고도 진행할 때만 true */
  confirmSelfLockout?: boolean;
}

export const devLoginService = {
  getWhitelist: async (): Promise<DevLoginWhitelistView> => {
    const res = await typedClient.get('/api/v1/admin/dev-login/whitelist');
    return res.data as unknown as DevLoginWhitelistView;
  },

  create: async (req: CreateDevLoginIpRequest): Promise<DevLoginIpEntry> => {
    const res = await typedClient.post('/api/v1/admin/dev-login/whitelist', req);
    return res.data as unknown as DevLoginIpEntry;
  },

  update: async (id: number, req: UpdateDevLoginIpRequest): Promise<DevLoginIpEntry> => {
    const res = await typedClient.putPath(
      '/api/v1/admin/dev-login/whitelist/{id}',
      { id },
      { clearExpiry: false, confirmSelfLockout: false, ...req },
    );
    return res.data as unknown as DevLoginIpEntry;
  },

  remove: async (id: number, confirmSelfLockout = false): Promise<void> => {
    await typedClient.deletePath(
      '/api/v1/admin/dev-login/whitelist/{id}',
      { id },
      { params: { confirmSelfLockout } },
    );
  },
};
