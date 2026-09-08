// 휴면 안내 화면이 실데이터를 쓰고 해제 API 를 실제로 호출하는지 검증 (STYLE-001)
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DormantAccountPage from '../DormantAccountPage';
import { DORMANT_DETAILS_KEY } from '../../../services/api/client';

vi.mock('../DormantAccountPage.css', () => ({}));

const { mockActivate, mockNavigate } = vi.hoisted(() => ({
  mockActivate: vi.fn(),
  mockNavigate: vi.fn(),
}));

vi.mock('../../../services/authService', () => ({
  authService: { activateDormantAccount: mockActivate },
}));

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <DormantAccountPage />
    </MemoryRouter>,
  );
}

describe('DormantAccountPage (STYLE-001)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    mockActivate.mockReset();
    mockNavigate.mockReset();
  });

  // 회귀: 본문에 리터럴 'OOO' 와 하드코딩된 가짜 날짜(2021.01.29 / 2022.01.29)가 박혀 있었다.
  it('플레이스홀더 OOO 와 하드코딩된 가짜 날짜를 쓰지 않는다', () => {
    const { container } = renderPage();
    expect(container.textContent).not.toContain('OOO');
    expect(container.textContent).not.toContain('2021');
    expect(container.textContent).not.toContain('2022');
    expect(container.textContent).toContain('골드펫');
  });

  it('서버가 내려준 실제 날짜를 표시한다', () => {
    sessionStorage.setItem(
      DORMANT_DETAILS_KEY,
      JSON.stringify({
        activationToken: 'tok',
        lastLoginAt: '2026-01-15T10:00:00',
        dormantAt: '2027-01-15T10:00:00',
      }),
    );

    const { container } = renderPage();

    expect(container.textContent).toContain('2026. 01. 15');
    expect(container.textContent).toContain('2027. 01. 15');
  });

  it('날짜 정보가 없으면 정보 박스를 아예 그리지 않는다 (가짜 값 대신 생략)', () => {
    sessionStorage.setItem(DORMANT_DETAILS_KEY, JSON.stringify({ activationToken: 'tok' }));

    const { container } = renderPage();

    expect(container.textContent).not.toContain('최근 접속일');
    expect(container.textContent).not.toContain('휴면 전환일');
  });

  // 회귀: '휴면 해제' 버튼이 아무 API 도 부르지 않고 /login 으로만 이동했다(먹통 버튼).
  it('휴면 해제 버튼이 해제 API 를 호출하고 성공 시 로그인으로 보낸다', async () => {
    sessionStorage.setItem(DORMANT_DETAILS_KEY, JSON.stringify({ activationToken: 'tok-123' }));
    mockActivate.mockResolvedValue(undefined);

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '휴면 해제' }));

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalledWith('tok-123');
      expect(mockNavigate).toHaveBeenCalledWith('/login');
    });
    // 해제에 성공했으면 토큰을 남겨두지 않는다.
    expect(sessionStorage.getItem(DORMANT_DETAILS_KEY)).toBeNull();
  });

  it('해제 실패 시 안내를 띄우고 로그인으로 보내지 않는다', async () => {
    sessionStorage.setItem(DORMANT_DETAILS_KEY, JSON.stringify({ activationToken: 'tok' }));
    mockActivate.mockRejectedValue(new Error('boom'));

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '휴면 해제' }));

    await waitFor(() => {
      expect(screen.getByText(/휴면 해제에 실패/)).toBeTruthy();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // 해제 토큰 없이 직접 진입하면 본인 확인이 불가하므로 API 를 부르지 않고 재로그인시킨다.
  it('해제 토큰이 없으면 API 를 부르지 않고 로그인으로 보낸다', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '휴면 해제' }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/login');
    });
    expect(mockActivate).not.toHaveBeenCalled();
  });
});
