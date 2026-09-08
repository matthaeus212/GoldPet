/**
 * ADM-02 UI: 장소 수정 — 409 Optimistic Lock 동시성 에러 처리 E2E
 *
 * 커버리지:
 *  - 장소 관리 탭 진입 + 시드 데이터 로드 확인
 *  - 장소 수정 폼 진입 + 기존 데이터 자동 채워짐 확인
 *  - API mock으로 PUT 409 응답 시뮬 → 에러 토스트 표시 검증
 *
 * 누락 data-testid (보고):
 *  - LBSPage: 장소 목록 행, 수정/삭제 버튼, 수정 폼 submit 버튼에 data-testid 없음
 *  - 수정 폼 컨테이너(.bg-gray-50)로 폼 버튼을 구분함
 */
import { test, expect, type Page } from '@playwright/test';

const API = 'http://localhost:8081/api/v1/admin';

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.getByPlaceholder('admin@goldpet.com').fill('e2eadmin@goldpet.com');
  await page.getByPlaceholder('••••••••').fill('Admin!e2e1');
  await page.getByRole('button', { name: /로그인/ }).click();

  try {
    await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 });
  } catch {
    if (await page.getByRole('heading', { name: '2단계 인증' }).isVisible()) {
      test.skip();
      return;
    }
    throw new Error('Login failed: still on /login after 10s');
  }
}

const SEED_PLACE = {
  id: 1,
  name: '서울숲',
  category: 'PARK',
  latitude: 37.5443,
  longitude: 127.0373,
  visits: 320,
  rating: 4.7,
};

test.describe('ADM-02 UI: 장소 수정 — 409 Optimistic Lock 에러', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);

    // LBS 관련 API 전체 mock
    await page.route(`${API}/lbs/**`, async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes('/lbs/stats')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            totalWalks: 100,
            todayWalks: 5,
            totalDistance: 500.0,
            avgDuration: 30,
            topSpots: [],
          }),
        });
      } else if (url.includes('/lbs/place-categories')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([{ value: 'PARK', displayName: '공원' }]),
        });
      } else if (/\/lbs\/places\/\d+/.test(url) && method === 'PUT') {
        // 동시 수정 시뮬: Optimistic Lock 실패 → 409
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Optimistic lock failure: resource modified by another session' }),
        });
      } else if (url.includes('/lbs/places') && !/\/lbs\/places\//.test(url) && method === 'GET') {
        // 장소 목록
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([SEED_PLACE]),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('장소 수정 PUT 409 → 에러 토스트 표시', async ({ page }) => {
    await page.goto('/lbs');
    await expect(page.getByRole('heading', { name: '산책/지도 관리' })).toBeVisible();

    // 장소 관리 탭으로 전환
    await page.getByRole('button', { name: '장소 관리' }).click();

    // 시드 장소 로드 확인
    await expect(page.getByText('서울숲')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('PARK')).toBeVisible();

    // 목록에서 수정 버튼 클릭
    await page.getByTestId('lbs-edit-button').first().click();

    // 수정 폼 열림 확인
    await expect(page.getByText('장소 수정')).toBeVisible();

    // 폼 submit 버튼
    const submitButton = page.getByTestId('lbs-form-submit');
    await expect(submitButton).toBeVisible();

    // 수정 제출 → PUT 409 응답
    await submitButton.click();

    // 에러 토스트 표시 검증
    await expect(page.getByText('장소 수정에 실패했습니다.')).toBeVisible({ timeout: 5000 });
  });

  test('장소 수정 폼 — 기존 데이터 자동 채워짐', async ({ page }) => {
    await page.goto('/lbs');
    await page.getByRole('button', { name: '장소 관리' }).click();
    await expect(page.getByText('서울숲')).toBeVisible({ timeout: 5000 });

    // 수정 버튼 클릭
    await page.getByTestId('lbs-edit-button').first().click();
    await expect(page.getByText('장소 수정')).toBeVisible();

    // 기존 이름 자동 채워짐 확인
    const nameInput = page.getByTestId('lbs-name-input');
    await expect(nameInput).toHaveValue('서울숲');

    // 취소 → 폼 닫힘
    await page.locator('div.bg-gray-50').getByRole('button', { name: '취소' }).click();
    await expect(page.getByText('장소 수정')).not.toBeVisible();
  });
});
