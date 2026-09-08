/**
 * xf-01: 첫 산책 진입 플로우
 *
 * dev-login → localStorage auth 주입 → 홈 진입
 * → 친구 추천 캐러셀 표시 검증 → "산책 시작하기" 클릭 → /walk 진입 확인
 *
 * MISSING data-testid (PR template 추가 필요):
 *   - 친구 추천 섹션 컨테이너  (.home_friends__scroll / .home_friends__empty 로 대체)
 *   - TodayWalkCard 산책 시작 버튼  (button text로 대체)
 *   - WalkPage 루트 컨테이너  (URL 변경으로 확인)
 */
import { test, expect } from '@playwright/test';
import { devLogin, loginAndGoTo } from './helpers';

test.describe('xf-01: 첫 산책 진입 플로우', () => {
  test('홈 진입 → 친구 추천 섹션 표시 → 산책 시작 → /walk 이동', async ({
    page,
    request,
  }) => {
    // 1. dev-login
    const user = await devLogin(request, 'test-xf01@goldpet.com', 'XF01테스터');

    // 2. localStorage auth 주입 후 /home으로 이동
    await loginAndGoTo(page, user, '/home');
    await page.waitForLoadState('networkidle');

    // 3. "새로운 친구 추천" 섹션 heading 확인
    const friendHeading = page.locator('h2', { hasText: '새로운 친구 추천' });
    await expect(friendHeading).toBeVisible({ timeout: 10_000 });

    // 4. 친구 카드 또는 빈 상태 메시지 중 하나가 렌더링
    //    (data-testid 없음 → CSS class 기반)
    const cardCount  = await page.locator('.friend_card').count();
    const emptyCount = await page.locator('.home_friends__empty').count();
    expect(
      cardCount + emptyCount,
      '친구 카드(.friend_card) 또는 빈 안내(.home_friends__empty)가 1개 이상 있어야 함',
    ).toBeGreaterThan(0);

    // 5. "산책 시작하기" 버튼 표시 확인
    const startWalkBtn = page.getByTestId('walk-start-button');
    await expect(startWalkBtn).toBeVisible({ timeout: 5_000 });

    // 6. 클릭 후 /walk로 라우팅
    await startWalkBtn.click();
    await expect(page).toHaveURL(/\/walk/, { timeout: 8_000 });
  });
});
