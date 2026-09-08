import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WalkPhotoGalleryViewer, WalkPhotoViewerSlide } from '../WalkPhotoGalleryViewer';
import { makePhotosWithVariants } from './fixtures';

// CSS — suppress module resolution in jsdom
vi.mock('../WalkPhotoDetail.css', () => ({}));
vi.mock('swiper/css', () => ({}));
vi.mock('swiper/css/navigation', () => ({}));
vi.mock('swiper/css/zoom', () => ({}));

// Swiper — render children directly; no DOM API requirements in jsdom.
// initialSlide 를 노출하고 마운트 횟수를 세어, 피드 하이드레이션 시 key 변경으로 remount 되며
// 올바른 initialSlide 가 적용되는지(검정화면 회귀) 검증할 수 있게 한다.
const { swiperMounts } = vi.hoisted(() => ({ swiperMounts: { count: 0 } }));
vi.mock('swiper/react', async () => {
  const React = await import('react');
  return {
    Swiper: ({
      children,
      initialSlide,
      virtual,
    }: {
      children: React.ReactNode;
      initialSlide?: number;
      virtual?: { enabled?: boolean };
    }) => {
      React.useEffect(() => {
        swiperMounts.count += 1;
      }, []);
      return (
        <div
          data-testid="swiper"
          data-initial-slide={String(initialSlide ?? 0)}
          data-virtual={String(virtual?.enabled === true)}
        >
          {children}
        </div>
      );
    },
    SwiperSlide: ({
      children,
      virtualIndex,
    }: {
      children: React.ReactNode;
      virtualIndex?: number;
    }) => (
      <div data-testid="swiper-slide" data-virtual-index={String(virtualIndex ?? '')}>
        {children}
      </div>
    ),
  };
});
vi.mock('swiper/modules', () => ({ Navigation: {}, Keyboard: {}, Zoom: {}, Virtual: {} }));

// useDeepLinkPhoto — vi.hoisted so the mock fn is available in the factory
const { mockUseDeepLinkPhoto } = vi.hoisted(() => ({
  mockUseDeepLinkPhoto: vi.fn(),
}));
// Path resolved from THIS file (__tests__/): ../../hooks = src/features/walk/hooks
vi.mock('../../hooks/useDeepLinkPhoto', () => ({
  useDeepLinkPhoto: mockUseDeepLinkPhoto,
}));

// Remaining dependencies — paths resolved from __tests__/ directory
vi.mock('../../../../contexts/ToastContext', () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));
vi.mock('../../../share/useShare', () => ({
  useShare: () => ({ share: vi.fn() }),
}));
vi.mock('../../../../stores/authStore', () => ({
  useAuthStore: () => null,
}));
vi.mock('../../../../services/reportService', () => ({
  reportService: { createReport: vi.fn() },
}));
vi.mock('../../utils/photoQueryCache', () => ({
  mapArrayRemoveItemById: vi.fn((d: unknown) => d),
  mapArrayUpdateItemById: vi.fn((d: unknown) => d),
  mapPagesRemoveItemById: vi.fn((d: unknown) => d),
  mapPagesUpdateItemById: vi.fn((d: unknown) => d),
}));

function renderViewer(initialIndex = 10) {
  const photos = makePhotosWithVariants(50);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <WalkPhotoGalleryViewer
          variant="page"
          mode="owned"
          walkId={1}
          initialSpotId={photos[initialIndex].id}
        />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('WalkPhotoGalleryViewer', () => {
  beforeEach(() => {
    const photos = makePhotosWithVariants(50);
    mockUseDeepLinkPhoto.mockReturnValue({
      initialPhoto: photos[10],
      photos,
      initialIndex: 10,
      allowSwipe: true,
      empty: false,
      loading: false,
      fetchNextPage: vi.fn(),
      hasNextPage: false,
    });
  });

  // 회귀(검정화면): 갤러리를 아래로 스크롤해 오래된 사진(피드 끝)을 열면, 뷰어는 처음 1장
  // ([photoFromState]) 로 마운트됐다가 피드가 하이드레이션되며 photos 가 전체로 바뀐다.
  // Swiper 의 initialSlide 는 최초 1회만 적용되므로 remount 없이는 내부 index 가 0 에 머물러
  // React activeIndex(=피드 위치)와 어긋나고, 실제 표시 슬라이드가 윈도우 밖이라 placeholder
  // (검정)로 렌더됐다. key 를 하이드레이션 시점에 한 번 바꿔 remount 시키는 것으로 고쳤다.
  it('remounts Swiper with the deep initialSlide when the feed hydrates (black-screen regression)', async () => {
    const photos = makePhotosWithVariants(50);
    const deepIndex = 49; // 갤러리 맨 아래(가장 오래된) 사진

    // 1) 하이드레이션 전: 클릭한 사진 1장만
    mockUseDeepLinkPhoto.mockReturnValue({
      initialPhoto: photos[deepIndex],
      photos: [photos[deepIndex]],
      initialIndex: 0,
      allowSwipe: true,
      empty: false,
      loading: false,
      fetchNextPage: vi.fn(),
      hasNextPage: false,
    });
    swiperMounts.count = 0;
    const { rerender, container } = render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <WalkPhotoGalleryViewer variant="page" mode="owned" walkId={1} initialSpotId={photos[deepIndex].id} />
        </QueryClientProvider>
      </MemoryRouter>,
    );
    expect(container.querySelector('[data-testid="swiper"]')?.getAttribute('data-initial-slide')).toBe('0');
    const mountsBefore = swiperMounts.count;

    // 2) 하이드레이션: 전체 피드 + 클릭한 사진은 맨 끝(49)
    mockUseDeepLinkPhoto.mockReturnValue({
      initialPhoto: photos[deepIndex],
      photos,
      initialIndex: deepIndex,
      allowSwipe: true,
      empty: false,
      loading: false,
      fetchNextPage: vi.fn(),
      hasNextPage: false,
    });
    rerender(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <WalkPhotoGalleryViewer variant="page" mode="owned" walkId={1} initialSpotId={photos[deepIndex].id} />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      // key 변경 → Swiper remount, 그리고 initialSlide 가 피드 내 실제 위치(49)로 적용돼야 한다.
      expect(swiperMounts.count).toBeGreaterThan(mountsBefore);
      expect(
        container.querySelector('[data-testid="swiper"]')?.getAttribute('data-initial-slide'),
      ).toBe(String(deepIndex));
      // 표시 슬라이드가 윈도우 안이어야 하므로 img 가 렌더된다(placeholder=검정 아님).
      expect(container.querySelectorAll('img.wpv_slide_img').length).toBeGreaterThan(0);
    });
  });

  // 회귀(iOS 렌더러 강제종료): 사진 수만큼 SwiperSlide 를 전부 DOM 에 만들면, Swiper 가
  // translate3d 를 거는 .swiper-wrapper 폭이 슬라이드 수에 정비례해 커진다(74장 ≈ CSS 3만px).
  // 실기기 계측에서 이 지점을 넘는 순간 WKWebView 의 WebContent 렌더러가 죽으며 화면이 통째로
  // 검게 멈췄다(이미지 디코딩 메모리는 11MB로 평평 → 메모리 부족이 아니라 거대 합성 레이어).
  // Virtual Slides 가 DOM 슬라이드를 활성 주변 몇 장으로 묶어주므로, 이 계약이 깨지면 안 된다.
  it('enables Swiper virtual slides and tags every slide with its virtualIndex', async () => {
    const { container } = renderViewer();
    await waitFor(() => {
      expect(container.querySelector('[data-testid="swiper"]')?.getAttribute('data-virtual')).toBe('true');
    });
    const slides = container.querySelectorAll('[data-testid="swiper-slide"]');
    expect(slides.length).toBeGreaterThan(0);
    slides.forEach((slide, idx) => {
      expect(slide.getAttribute('data-virtual-index')).toBe(String(idx));
    });
  });

  // EXACTLY-3 regression guard: WINDOW_HALF=1 means only slides [idx-1, idx, idx+1]
  // are in the active window regardless of total photo count → exactly 3 <img> mount.
  it('mounts exactly 3 img elements regardless of photo count', async () => {
    const { container } = renderViewer();
    await waitFor(() => {
      const imgs = container.querySelectorAll('img.wpv_slide_img');
      expect(imgs).toHaveLength(3);
    });
  });

  // Viewer src policy (iOS 메모리 누적 방어): 기본 표시는 600px medium 변형을 사용한다.
  // 줌하지 않은 슬라이드는 절대 1600px viewer 나 원본을 로드하지 않는다.
  it('uses medium URL by default (not viewer/original) when imageUrlMedium is present', async () => {
    const { container } = renderViewer();
    await waitFor(() => {
      expect(container.querySelectorAll('img.wpv_slide_img').length).toBeGreaterThan(0);
    });
    const imgs = container.querySelectorAll('img.wpv_slide_img');
    imgs.forEach((img) => {
      const src = img.getAttribute('src') ?? '';
      expect(src).toMatch(/_medium\.jpg/);
      expect(src).not.toMatch(/_viewer\.jpg/);
    });
  });

  // Share always uses full-resolution original — guard the share button is present.
  it('renders share button when imageUrl is present', async () => {
    const { container } = renderViewer();
    await waitFor(() => {
      expect(container.querySelector('button[aria-label="공유"]')).not.toBeNull();
    });
  });
});

describe('WalkPhotoViewerSlide zoom swap', () => {
  const photo = makePhotosWithVariants(1)[0];

  it('loads medium src when not zoomed', () => {
    const { container } = render(<WalkPhotoViewerSlide photo={photo} active zoomed={false} />);
    const src = container.querySelector('img.wpv_slide_img')?.getAttribute('src') ?? '';
    expect(src).toMatch(/_medium\.jpg/);
    expect(src).not.toMatch(/_viewer\.jpg/);
  });

  it('swaps to 1600px viewer src when zoomed', () => {
    const { container } = render(<WalkPhotoViewerSlide photo={photo} active zoomed />);
    const src = container.querySelector('img.wpv_slide_img')?.getAttribute('src') ?? '';
    expect(src).toMatch(/_viewer\.jpg/);
    expect(src).not.toMatch(/_medium\.jpg/);
  });

  it('falls back to viewer then original when medium is absent', () => {
    const noMedium = { ...photo, imageUrlMedium: undefined };
    const { container } = render(<WalkPhotoViewerSlide photo={noMedium} active zoomed={false} />);
    const src = container.querySelector('img.wpv_slide_img')?.getAttribute('src') ?? '';
    expect(src).toMatch(/_viewer\.jpg/);
  });

  // 회귀: medium 변형이 없는 구 사진은 파생 `_medium` presigned URL 이 404 난다(2026-02 이전 업로드).
  // URL 자체는 non-null 이라 `?? viewer` 폴백이 안 걸려 큰 사진이 blank 로 남던 버그.
  it('falls back to viewer when a present medium URL fails to load (404 on legacy photo)', () => {
    const { container } = render(<WalkPhotoViewerSlide photo={photo} active zoomed={false} />);
    const img = container.querySelector('img.wpv_slide_img') as HTMLImageElement;
    expect(img.getAttribute('src')).toMatch(/_medium\.jpg/);

    // medium 404 시뮬레이션
    fireEvent.error(img);

    const after = container.querySelector('img.wpv_slide_img')?.getAttribute('src') ?? '';
    expect(after).toMatch(/_viewer\.jpg/);
    expect(after).not.toMatch(/_medium\.jpg/);
  });

  it('does not invalidate the feed when falling back from a failed medium', () => {
    const onExpired = vi.fn();
    const { container } = render(
      <WalkPhotoViewerSlide photo={photo} active zoomed={false} onPresignedUrlExpired={onExpired} />,
    );
    fireEvent.error(container.querySelector('img.wpv_slide_img') as HTMLImageElement);
    // medium→viewer 폴백은 만료가 아니므로 쿼리 무효화(재요청 루프)를 트리거하지 않아야 한다.
    expect(onExpired).not.toHaveBeenCalled();
  });
});
