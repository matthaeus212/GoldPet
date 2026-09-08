/**
 * community-detail-keyboard: 게시글 상세 + 키보드 레이아웃 구조 검증
 *
 * dev-login → 게시글 생성(API) → /community/:postId 진입
 * → flex container 구조 확인 (iOS WebView 키보드 UX 보장)
 * → comment textarea focus → layout 검증
 *
 * iOS WebView 실제 키보드 스크롤은 데스크톱 Chromium에서 직접 검증 불가.
 * 구조(flex, overflow, position) 만 검증.
 *
 * MISSING data-testid (PR template 추가 필요):
 *   - #communityContainer  → id="communityContainer" 있음 (OK)
 *   - .detail_wrap         → class selector로 대체
 *   - .comment_submit      → class selector로 대체
 *   - comment textarea     → '.comment_submit textarea' 로 대체
 */
import { test, expect } from '@playwright/test';
import type { DevUser } from './helpers';
import { devLogin, loginAndGoTo, API_BASE } from './helpers';

// beforeAll에서 공유 — 개별 test가 devLogin을 중복 호출하면 동시 생성 500 발생
let postId = 0;           // 0 = 셋업 실패 → test.skip 트리거
let sharedUser: DevUser | null = null;

test.describe('community-detail-keyboard: flex container 구조 검증', () => {
  test.beforeAll(async ({ request }) => {
    try {
      sharedUser = await devLogin(
        request,
        'test-community-kb@goldpet.com',
        '커뮤KB테스터',
      );

      // 카테고리 조회 (첫 번째 카테고리 사용)
      const catResp = await request.get(`${API_BASE}/api/v1/community/categories`, {
        headers: { Authorization: `Bearer ${sharedUser.accessToken}` },
      });
      const cats: Array<{ id: number }> = catResp.ok() ? await catResp.json() : [];
      const categoryId = cats[0]?.id ?? 1;

      // 게시글 생성
      const createResp = await request.post(`${API_BASE}/api/v1/community/posts`, {
        data: {
          title: '[E2E] keyboard flex container test',
          content: '댓글 입력창 flex layout 검증용 게시글입니다.',
          categoryId,
        },
        headers: {
          Authorization: `Bearer ${sharedUser.accessToken}`,
          'Content-Type': 'application/json',
        },
      });

      if (createResp.ok()) {
        const body: { id?: number; postId?: number } = await createResp.json();
        postId = (body.id ?? body.postId) ?? 0;
      }

      // 생성 실패 시 기존 목록 첫 번째 사용
      if (postId === 0) {
        const listResp = await request.get(`${API_BASE}/api/v1/community/posts`, {
          headers: { Authorization: `Bearer ${sharedUser.accessToken}` },
        });
        if (listResp.ok()) {
          const lb: { posts?: Array<{ id: number }>; content?: Array<{ id: number }> } =
            await listResp.json();
          postId = (lb.posts ?? lb.content ?? [])[0]?.id ?? 0;
        }
      }

      console.log(`[community-kb] postId=${postId}`);
    } catch (e) {
      console.warn('[community-kb] beforeAll 셋업 실패 — 모든 테스트 스킵:', e);
    }
  });

  // 공통 guard: 셋업 실패 시 skip
  function guardSetup() {
    test.skip(!sharedUser || postId === 0, '셋업(게시글/유저) 실패로 스킵');
  }

  test('① #communityContainer에 community-detail-mode 클래스가 있다', async ({ page }) => {
    guardSetup();
    await loginAndGoTo(page, sharedUser!, `/community/${postId}`);
    await page.waitForLoadState('networkidle');

    const container = page.locator('#communityContainer');
    await expect(container).toBeVisible({ timeout: 10_000 });
    await expect(container).toHaveClass(/community-detail-mode/);
  });

  test('② .detail_wrap이 overflow-y: auto/scroll 컨테이너로 존재한다', async ({ page }) => {
    guardSetup();
    await loginAndGoTo(page, sharedUser!, `/community/${postId}`);
    await page.waitForLoadState('networkidle');

    const detailWrap = page.locator('.detail_wrap');
    await expect(detailWrap).toBeVisible({ timeout: 10_000 });

    const overflowY = await detailWrap.evaluate(
      (el) => getComputedStyle(el).overflowY,
    );
    expect(
      ['auto', 'scroll'],
      `.detail_wrap의 overflow-y는 auto 또는 scroll이어야 함 (실제: ${overflowY})`,
    ).toContain(overflowY);
  });

  test('③ .comment_submit이 .detail_wrap 바깥(형제) 요소로 존재한다', async ({ page }) => {
    guardSetup();
    await loginAndGoTo(page, sharedUser!, `/community/${postId}`);
    await page.waitForLoadState('networkidle');

    const isDirectChild = await page.evaluate(() => {
      const container     = document.getElementById('communityContainer');
      const commentSubmit = document.querySelector('[data-testid="comment-submit-area"]');
      if (!container || !commentSubmit) return false;
      return commentSubmit.parentElement === container;
    });
    expect(
      isDirectChild,
      '[data-testid="comment-submit-area"]은 #communityContainer의 직접 자식(형제 of .detail_wrap)이어야 함',
    ).toBe(true);
  });

  test('④ comment textarea focus 후 visible 유지된다', async ({ page }) => {
    guardSetup();
    await loginAndGoTo(page, sharedUser!, `/community/${postId}`);
    await page.waitForLoadState('networkidle');

    const textarea = page.getByTestId('comment-textarea');
    await expect(textarea).toBeVisible({ timeout: 10_000 });

    await textarea.focus();
    await expect(textarea).toBeFocused();
    // 포커스 후에도 화면에 있어야 함 (iOS 키보드 팝업 시 layout 보장 프록시)
    await expect(textarea).toBeVisible();
  });
});
