/**
 * ADM-05: 공지/팝업 관리 — 신규 공지 등록 E2E
 *
 * 커버리지:
 *  - 공지 추가 다이얼로그(폼 모달) 표시 검증
 *  - isDismissible("오늘 하루 그만 보기 허용") 옵션 확인
 *  - 게시(생성) 후 목록 반영 확인
 *  - 활성/비활성 탭 필터
 *
 * 누락 data-testid (보고):
 *  - NoticeManagementPage: "+ 공지 추가" 버튼, 공지 목록 행, 상태 토글 버튼에 data-testid 없음
 *  - isDismissible 체크박스는 id="isDismissible" 로 식별 가능
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
      // 2FA 활성화된 계정 — 자동화 불가, 스킵
      test.skip();
      return;
    }
    throw new Error('Login failed: still on /login after 10s');
  }
}

const BASE_NOTICE = {
  id: 99,
  type: 'POPUP_MODAL',
  title: '테스트 팝업 공지',
  content: '자동화 테스트용 공지입니다.',
  linkUrl: null,
  targetScreen: 'ALL',
  priority: 0,
  isDismissible: true,
  isActive: true,
  imageUrls: [],
  startAt: '2099-12-31T00:00:00',
  endAt: null,
};

test.describe('ADM-05: 공지/팝업 관리', () => {
  test('신규 공지 등록 → 폼 다이얼로그 검증 → isDismissible 확인 → 게시 → 목록 반영', async ({ page }) => {
    await loginAsAdmin(page);

    // POST 후 GET 재조회 시 생성된 공지 반환
    let noticeCreated = false;

    await page.route(`${API}/notices`, async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(noticeCreated ? [BASE_NOTICE] : []),
        });
      } else if (method === 'POST') {
        noticeCreated = true;
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(BASE_NOTICE),
        });
      } else {
        await route.continue();
      }
    });

    // 1. 공지 관리 페이지 진입
    await page.goto('/notices');
    await expect(page.getByRole('heading', { name: '공지/팝업 관리' })).toBeVisible();
    await expect(page.getByText('공지가 없습니다.')).toBeVisible();

    // 2. "+ 공지 추가" 클릭 → 폼 다이얼로그(preview 모달) 열림
    await page.getByTestId('notice-add-button').click();

    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('heading', { name: '새 공지 추가' })).toBeVisible();

    // 3. 유형 기본값 = POPUP_MODAL 확인 (trigger + option 2개 → first()로 한정)
    await expect(dialog.getByText('팝업 모달').first()).toBeVisible();

    // 4. 제목 입력
    await dialog.getByPlaceholder('제목을 입력하세요').fill('테스트 팝업 공지');

    // 5. 시작일시 입력 (필수 필드)
    await dialog.locator('input[type="datetime-local"]').first().fill('2099-12-31T00:00');

    // 6. "오늘 하루 그만 보기 허용"(isDismissible) 체크박스 표시 + 기본 체크 확인
    const dismissibleCheckbox = dialog.locator('#isDismissible');
    await expect(dismissibleCheckbox).toBeVisible();
    await expect(dismissibleCheckbox).toBeChecked();
    await expect(dialog.getByText('오늘 하루 그만 보기 허용')).toBeVisible();

    // 7. 게시(생성) 버튼 클릭
    await dialog.getByRole('button', { name: '생성' }).click();

    // 8. 다이얼로그 닫힘 확인
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    // 9. 목록에 새 공지 반영
    await expect(page.getByText('테스트 팝업 공지')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('팝업 모달').first()).toBeVisible();
  });

  test('활성 탭 필터 — 활성 공지만 표시', async ({ page }) => {
    await loginAsAdmin(page);

    await page.route(`${API}/notices`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { ...BASE_NOTICE, id: 1, isActive: true,  title: '활성 공지' },
            { ...BASE_NOTICE, id: 2, isActive: false, title: '비활성 공지' },
          ]),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto('/notices');

    // 전체 탭: 두 공지 모두 표시 (exact:true — '비활성 공지'가 '활성 공지' substring 포함)
    await expect(page.getByText('활성 공지', { exact: true })).toBeVisible();
    await expect(page.getByText('비활성 공지', { exact: true })).toBeVisible();

    // 활성 탭 클릭
    await page.getByTestId('notice-tab-active').click();
    await expect(page.getByText('활성 공지', { exact: true })).toBeVisible();
    await expect(page.getByText('비활성 공지', { exact: true })).not.toBeVisible();
  });
});
