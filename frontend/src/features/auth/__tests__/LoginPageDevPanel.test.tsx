// 개발자 패널이 서버 판정(허용 IP)에서만 노출되는지 고정 — 계정탈취 폼 노출 회귀 방지
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LoginPage } from '../LoginPage';

vi.mock('../LoginPage.css', () => ({}));

const { mockIsDevLoginAvailable, mockIsBridgeAvailable } = vi.hoisted(() => ({
  mockIsDevLoginAvailable: vi.fn(),
  mockIsBridgeAvailable: vi.fn(),
}));

vi.mock('../../../services/authService', () => ({
  authService: {
    isDevLoginAvailable: mockIsDevLoginAvailable,
    openLoginPopup: vi.fn(),
    login: vi.fn(),
  },
}));

vi.mock('../../../bridge/nativeBridge', () => ({
  nativeBridge: {
    isAvailable: mockIsBridgeAvailable,
    callMethod: vi.fn(),
    onEvent: vi.fn(() => () => {}),
  },
}));

vi.mock('../../../services/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../../../services/userService', () => ({ userService: {} }));
vi.mock('../../../contexts/AlertContext', () => ({ useAlert: () => ({ showAlert: vi.fn() }) }));
vi.mock('../../../hooks/useKeyboardDismiss', () => ({ useKeyboardDismiss: () => {} }));
vi.mock('../../../components/auth/AccountLinkModal', () => ({ AccountLinkModal: () => null }));
vi.mock('../../../stores/authStore', () => ({
  useAuthStore: Object.assign(() => null, { getState: () => ({ login: vi.fn() }) }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <LoginPage />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage 개발자 패널 노출 (계정탈취 폼 회귀 방지)', () => {
  beforeEach(() => {
    mockIsDevLoginAvailable.mockReset();
    mockIsBridgeAvailable.mockReset();
    mockIsBridgeAvailable.mockReturnValue(false); // 브라우저
  });

  // 회귀: 예전에는 `import.meta.env.MODE === 'dev'` 로 렌더했는데, 라이브 프론트가
  // `vite build --mode dev` 로 빌드되므로 **모든 사용자에게 이 입력칸이 보였다.**
  // 거기에 남의 이메일을 넣으면 그 계정으로 로그인됐다.
  it('서버가 불허하면 개발자 패널을 렌더하지 않는다', async () => {
    mockIsDevLoginAvailable.mockResolvedValue(false);

    const { container } = renderPage();

    await waitFor(() => expect(mockIsDevLoginAvailable).toHaveBeenCalled());
    expect(container.textContent).not.toContain('DEVELOPER ONLY');
    expect(container.querySelector('input[name="testEmail"]')).toBeNull();
  });

  it('서버가 허용하면(허용 IP) 개발자 패널을 렌더한다', async () => {
    mockIsDevLoginAvailable.mockResolvedValue(true);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/DEVELOPER ONLY/)).toBeTruthy();
    });
  });

  // 앱 WebView 에서는 패널이 필요 없고, 가용성 조회도 하지 않아야 한다.
  it('앱 WebView 에서는 패널을 렌더하지 않고 서버에 묻지도 않는다', async () => {
    mockIsBridgeAvailable.mockReturnValue(true);
    mockIsDevLoginAvailable.mockResolvedValue(true);

    const { container } = renderPage();

    await waitFor(() => expect(container.textContent).not.toContain('DEVELOPER ONLY'));
    expect(mockIsDevLoginAvailable).not.toHaveBeenCalled();
  });
});
