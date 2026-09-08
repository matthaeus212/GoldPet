/**
 * home-dashboard: 홈 대시보드 4개 핵심 컴포넌트 존재 검증
 *
 * dev-login → /home → 4개 섹션 data-testid / CSS class 검증
 *
 * MISSING data-testid — 아래 모두 PR template에 추가 요청:
 *   - 친구 추천 섹션  → h2 text + .home_friends__scroll / .home_friends__empty
 *   - 오늘 산책 카드  → .today_walk__card
 *   - 빠른 메뉴      → .home_quickmenu
 *   - AI 배너        → .home_ai_banner  (사용자가 닫으면 사라짐 — 초기 방문 시만 표시)
 *
 * 권장 추가 testid:
 *   data-testid="home-friend-section"   on <section className="home_section"> (첫 번째)
 *   data-testid="home-walk-card"        on <div className="today_walk__card">
 *   data-testid="home-quick-menu"       on <section className="home_quickmenu">
 *   data-testid="home-ai-banner"        on <div className="home_ai_banner">
 */
import { test, expect } from '@playwright/test';
import { devLogin, loginAndGoTo } from './helpers';

test.describe('home-dashboard: 홈 4대 핵심 컴포넌트', () => {
  test.beforeEach(async ({ page, request }) => {
    const user = await devLogin(request, 'test-home-dash@goldpet.com', '홈대시테스터');
    await loginAndGoTo(page, user, '/home');
    await page.waitForLoadState('networkidle');
  });

  test('① 친구 추천 섹션이 표시된다', async ({ page }) => {
    // heading
    await expect(
      page.locator('h2', { hasText: '새로운 친구 추천' }),
    ).toBeVisible({ timeout: 10_000 });

    // 카드 또는 빈 상태 중 하나 이상
    const cards  = await page.locator('.friend_card').count();
    const empty  = await page.locator('.home_friends__empty').count();
    expect(cards + empty, '친구 카드 또는 빈 상태가 있어야 함').toBeGreaterThan(0);
  });

  test('② 오늘의 산책 카드가 표시된다', async ({ page }) => {
    await expect(page.getByTestId('home-today-walk-card')).toBeVisible({ timeout: 10_000 });
    // 산책 시작 버튼 포함 확인
    await expect(page.getByTestId('walk-start-button')).toBeVisible();
  });

  test('③ 빠른 메뉴(QuickMenu) 4항목이 표시된다', async ({ page }) => {
    const quickMenu = page.getByTestId('home-quick-menu');
    await expect(quickMenu).toBeVisible({ timeout: 10_000 });

    // 4개 항목: 산책 코스, 건강 체크, 랭킹, 미션
    const items = quickMenu.locator('.home_quickmenu__item');
    await expect(items).toHaveCount(4);
  });

  test('④ AI 배너가 최초 진입 시 표시된다', async ({ page }) => {
    // AI 배너는 showAiBanner state가 true일 때만 렌더. 초기값 true이므로 표시되어야 함.
    await expect(page.getByTestId('home-ai-banner')).toBeVisible({ timeout: 10_000 });
    // 배너 내 안내 문구
    await expect(
      page.locator('text=아직 AI 프로필이 없으시네요?'),
    ).toBeVisible();
  });
});
