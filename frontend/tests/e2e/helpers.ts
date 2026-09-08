/**
 * Playwright E2E 공용 헬퍼
 *
 * - devLogin()  : /api/v1/auth/dev-login 호출 → tokens + user 반환
 * - loginAndGoTo() : Zustand persist auth state → localStorage 주입 → 페이지 이동
 */
import type { APIRequestContext, Page } from '@playwright/test';

export const API_BASE = 'http://localhost:8081';

export interface DevUser {
  accessToken: string;
  refreshToken: string;
  user: Record<string, unknown> & { id: number; nickname: string };
}

/** dev-login endpoint 호출 */
export async function devLogin(
  request: APIRequestContext,
  email: string,
  nickname?: string,
): Promise<DevUser> {
  const resp = await request.post(`${API_BASE}/api/v1/auth/dev-login`, {
    data: { email, nickname: nickname ?? email.split('@')[0] },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!resp.ok()) {
    throw new Error(`dev-login failed [${resp.status()}]: ${await resp.text()}`);
  }
  return resp.json() as Promise<DevUser>;
}

/**
 * Zustand persist auth state를 localStorage에 주입 후 targetPath로 이동.
 * 페이지가 이미 origin 내에 있지 않으면 '/'로 먼저 이동하여 origin을 확립.
 */
export async function loginAndGoTo(
  page: Page,
  user: DevUser,
  targetPath: string,
): Promise<void> {
  const authState = {
    state: {
      token: user.accessToken,
      refreshToken: user.refreshToken,
      isAuthenticated: true,
      user: user.user,
    },
    version: 0,
  };
  // origin 확립 (localStorage는 동일 origin에서만 쓸 수 있음)
  await page.goto('/');
  await page.evaluate(
    (s) => localStorage.setItem('auth-storage', JSON.stringify(s)),
    authState,
  );
  await page.goto(targetPath);
}
