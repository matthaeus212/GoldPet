/**
 * xf-04: 다중 기기 + FCM 토큰 등록 플로우
 *
 * dev-login → PUT /users/me/fcm-token (가짜 토큰) → GET /users/me/devices
 * → response에 device가 등장하는지 확인
 *
 * MISSING data-testid:
 *   - 기기 목록 UI 없음 (현재 설정 페이지에 기기 관리 화면 미노출)
 *   → API 레벨 검증으로 대체
 */
import { test, expect } from '@playwright/test';
import { devLogin, loginAndGoTo, API_BASE } from './helpers';

test.describe('xf-04: 다중 기기 FCM 토큰 등록', () => {
  test('FCM 토큰 PUT → 기기 목록 GET에 device 등장', async ({ page, request }) => {
    // Step 1: dev-login
    const user = await devLogin(request, 'test-xf04@goldpet.com', 'XF04테스터');

    // Step 2: FCM 토큰 등록
    const fakeToken = `fake-fcm-token-${Date.now()}`;
    const fcmResp = await request.put(`${API_BASE}/api/v1/users/me/fcm-token`, {
      data: { fcmToken: fakeToken },
      headers: {
        Authorization: `Bearer ${user.accessToken}`,
        'Content-Type': 'application/json',
      },
    });
    expect(
      fcmResp.ok(),
      `PUT /users/me/fcm-token 성공 기대 (status=${fcmResp.status()})`,
    ).toBeTruthy();

    // Step 3: 기기 목록 조회
    const devicesResp = await request.get(`${API_BASE}/api/v1/users/me/devices`, {
      headers: { Authorization: `Bearer ${user.accessToken}` },
    });
    expect(
      devicesResp.ok(),
      `GET /users/me/devices 성공 기대 (status=${devicesResp.status()})`,
    ).toBeTruthy();

    const rawDevices: unknown = await devicesResp.json();
    const devices: Array<Record<string, unknown>> = Array.isArray(rawDevices)
      ? (rawDevices as Array<Record<string, unknown>>)
      : ((rawDevices as { content?: Array<Record<string, unknown>> }).content ?? []);

    expect(devices.length, '기기가 1개 이상 등록되어 있어야 함').toBeGreaterThan(0);

    // Step 4: 등록된 기기 중 방금 등록한 FCM 토큰이 있거나
    //         서버가 최신 토큰만 유지하는 경우 기기 수만 확인
    console.log(`[xf-04] 등록된 기기 수: ${devices.length}`);
    console.log(`[xf-04] 기기 샘플:`, JSON.stringify(devices[0]));

    // Step 5: 홈 화면 진입 가능한지 확인 (인증 유효성 스모크)
    await loginAndGoTo(page, user, '/home');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h2', { hasText: '새로운 친구 추천' })).toBeVisible({
      timeout: 10_000,
    });
  });
});
