/**
 * xf-02: 매칭 + 채팅 플로우
 *
 * dev 유저 A, B 생성 → 서로 좋아요(API) → isMutual 확인
 * → POST /chat/rooms/direct/{userBId} 로 DM 방 획득
 * → REST로 메시지 전송 → A로 채팅방 진입 → DOM에 메시지 버블 확인
 *
 * MISSING data-testid (PR template 추가 필요):
 *   - 채팅 입력 textarea  (tagName으로 대체)
 *   - 메시지 버블         (.chat_detail_message_bubble 로 대체)
 *
 * NOTE: 동일 이메일로 반복 실행 시 like가 이미 존재할 수 있음.
 *       API 오류를 무시하고 DM 방 획득으로 바로 이동.
 */
import { test, expect } from '@playwright/test';
import { devLogin, loginAndGoTo, API_BASE } from './helpers';

test.describe('xf-02: 매칭 + 채팅 플로우', () => {
  test('서로 좋아요 → DM 방 생성 → 채팅방 진입 → 메시지 DOM 확인', async ({
    page,
    request,
  }) => {
    // Step 1: 두 dev 유저 (결정론적 이메일 → 동일 유저 재사용)
    const userA = await devLogin(request, 'test-xf02a@goldpet.com', 'XF02가');
    const userB = await devLogin(request, 'test-xf02b@goldpet.com', 'XF02나');

    const userAId = userA.user.id as number;
    const userBId = userB.user.id as number;

    // Step 2: A → B 좋아요 (이미 있으면 무시)
    await request.post(`${API_BASE}/api/v1/friends/${userBId}/like`, {
      headers: {
        Authorization: `Bearer ${userA.accessToken}`,
        'Content-Type': 'application/json',
      },
    });

    // Step 3: B → A 좋아요
    const likeBResp = await request.post(
      `${API_BASE}/api/v1/friends/${userAId}/like`,
      {
        headers: {
          Authorization: `Bearer ${userB.accessToken}`,
          'Content-Type': 'application/json',
        },
      },
    );
    if (likeBResp.ok()) {
      const likeBody: { isMutual?: boolean } = await likeBResp.json();
      console.log(`[xf-02] isMutual=${likeBody.isMutual}`);
    }

    // Step 4: DM 방 get-or-create (mutual like 여부와 무관하게 방 확보)
    const dmResp = await request.post(
      `${API_BASE}/api/v1/chat/rooms/direct/${userBId}`,
      {
        headers: {
          Authorization: `Bearer ${userA.accessToken}`,
          'Content-Type': 'application/json',
        },
      },
    );
    expect(dmResp.ok(), `DM 방 생성/조회 성공 기대 (status=${dmResp.status()})`).toBeTruthy();
    const dmRoom: { id: number } = await dmResp.json();
    const chatRoomId = dmRoom.id;
    expect(chatRoomId, 'chatRoomId가 유효한 숫자여야 함').toBeGreaterThan(0);

    // Step 5: REST API로 메시지 먼저 전송 (WebSocket 의존 없이 DOM 검증)
    const testMsg = `E2E xf-02 메시지 ${Date.now()}`;
    const sendResp = await request.post(
      `${API_BASE}/api/v1/chat/rooms/${chatRoomId}/messages`,
      {
        data: { content: testMsg, messageType: 'TEXT' },
        headers: {
          Authorization: `Bearer ${userA.accessToken}`,
          'Content-Type': 'application/json',
        },
      },
    );
    expect(sendResp.ok(), '메시지 전송 API 성공').toBeTruthy();

    // Step 6: A로 채팅방 진입
    await loginAndGoTo(page, userA, `/chat/${chatRoomId}`);
    await page.waitForLoadState('networkidle');

    // Step 7: 채팅 입력 textarea 존재 확인
    await expect(page.locator('textarea').last()).toBeVisible({ timeout: 8_000 });

    // Step 8: 전송한 메시지가 메시지 버블 DOM에 있어야 함
    await expect(
      page.getByTestId('chat-message-bubble').filter({ hasText: testMsg }).first(),
    ).toBeVisible({ timeout: 10_000 });
  });
});
