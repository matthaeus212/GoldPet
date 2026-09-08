import { useCallback, useMemo, useState, type ImgHTMLAttributes } from 'react';

/**
 * T1-2 / T2-D2 GpImage — 백엔드 variant 파이프라인이 공급하는 thumbnail/medium/viewer
 * URL 중 가장 적절한 variant 를 선택해 렌더링하는 공용 컴포넌트.
 *
 * WebP 지원: `webpThumbnailSrc` / `webpMediumSrc` / `webpViewerSrc` 중 하나라도 제공되면
 * `<picture><source type="image/webp">` 패턴으로 렌더. 미제공 시 plain `<img>` 유지.
 *
 * ## 사용 예시
 * ```tsx
 * // 카드/아바타 목록 (썸네일 우선)
 * <GpImage
 *   src={friend.profileImageUrl}
 *   thumbnailSrc={friend.profileImageUrlThumbnail}
 *   webpThumbnailSrc={friend.profileImageUrlThumbnailWebp}
 *   variant="thumbnail"
 *   alt={friend.nickname}
 *   className="avatar"
 * />
 *
 * // 프로필 상세 (viewer)
 * <GpImage
 *   src={user.profileImageUrl}
 *   viewerSrc={user.profileImageUrlViewer}
 *   thumbnailSrc={user.profileImageUrlThumbnail}
 *   variant="viewer"
 *   loading="eager"
 *   fetchPriority="high"
 * />
 * ```
 *
 * ## fallback 체인
 * - variant="thumbnail" → thumbnailSrc → mediumSrc → viewerSrc → src
 * - variant="medium"    → mediumSrc    → thumbnailSrc → viewerSrc → src
 * - variant="viewer"    → viewerSrc    → mediumSrc → thumbnailSrc → src
 * - variant="auto"(def) → viewerSrc    → mediumSrc → thumbnailSrc → src
 *
 * variant 로드 실패(onError)시 체인의 다음 후보로 자동 전환. 모든 후보 소진 시 빈 img.
 *
 * ## 기본 성능 힌트
 * - `loading="lazy"` + `decoding="async"` 기본값.
 * - LCP 후보(첫 slide, hero)는 `loading="eager" decoding="sync" fetchPriority="high"` 명시.
 */

type Variant = 'thumbnail' | 'medium' | 'viewer' | 'auto';

/** jpg + 대응 webp(없으면 null) 쌍. chain 각 entry 는 중복 제거된 후보. */
type ChainEntry = { jpg: string; webp: string | null };

export interface GpImageProps
  extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'loading' | 'decoding' | 'fetchPriority'> {
  /** 원본 URL — 항상 fallback 맨 끝. null/undefined 허용. */
  src: string | null | undefined;
  thumbnailSrc?: string | null;
  mediumSrc?: string | null;
  viewerSrc?: string | null;
  /** T2-D2: WebP thumbnail variant. 제공 시 <picture><source> 패턴 활성화. */
  webpThumbnailSrc?: string | null;
  /** T2-D2: WebP medium variant. */
  webpMediumSrc?: string | null;
  /** T2-D2: WebP viewer variant. */
  webpViewerSrc?: string | null;
  variant?: Variant;
  loading?: ImgHTMLAttributes<HTMLImageElement>['loading'];
  decoding?: ImgHTMLAttributes<HTMLImageElement>['decoding'];
  /** React 19 camelCase. `fetchpriority` 가 아닌 `fetchPriority` 사용. */
  fetchPriority?: 'high' | 'low' | 'auto';
  /** intrinsic 너비 — CLS 방지용. */
  width?: number | string;
  /** intrinsic 높이 — CLS 방지용. */
  height?: number | string;
}

function buildChain(
  variant: Variant,
  src: string | null | undefined,
  thumbnailSrc: string | null | undefined,
  mediumSrc: string | null | undefined,
  viewerSrc: string | null | undefined,
  webpThumbnailSrc: string | null | undefined,
  webpMediumSrc: string | null | undefined,
  webpViewerSrc: string | null | undefined,
): ChainEntry[] {
  // jpg variant 와 대응 webp variant 를 쌍으로 묶어 순서 결정
  const ordered: [string | null | undefined, string | null | undefined][] = (() => {
    switch (variant) {
      case 'thumbnail':
        return [
          [thumbnailSrc, webpThumbnailSrc],
          [mediumSrc, webpMediumSrc],
          [viewerSrc, webpViewerSrc],
          [src, null],
        ];
      case 'medium':
        return [
          [mediumSrc, webpMediumSrc],
          [thumbnailSrc, webpThumbnailSrc],
          [viewerSrc, webpViewerSrc],
          [src, null],
        ];
      case 'viewer':
        return [
          [viewerSrc, webpViewerSrc],
          [mediumSrc, webpMediumSrc],
          [thumbnailSrc, webpThumbnailSrc],
          [src, null],
        ];
      case 'auto':
      default:
        return [
          [viewerSrc, webpViewerSrc],
          [mediumSrc, webpMediumSrc],
          [thumbnailSrc, webpThumbnailSrc],
          [src, null],
        ];
    }
  })();

  // 중복 jpg URL 제거 (variant 가 원본과 동일한 fallback 을 내릴 때)
  const seen = new Set<string>();
  const result: ChainEntry[] = [];
  for (const [jpg, webp] of ordered) {
    if (jpg && jpg.length > 0 && !seen.has(jpg)) {
      seen.add(jpg);
      result.push({ jpg, webp: webp && webp.length > 0 ? webp : null });
    }
  }
  return result;
}

export function GpImage({
  src,
  thumbnailSrc,
  mediumSrc,
  viewerSrc,
  webpThumbnailSrc,
  webpMediumSrc,
  webpViewerSrc,
  variant = 'auto',
  loading = 'lazy',
  decoding = 'async',
  fetchPriority,
  alt = '',
  onError,
  ...rest
}: GpImageProps) {
  const chain = useMemo(
    () => buildChain(variant, src, thumbnailSrc, mediumSrc, viewerSrc, webpThumbnailSrc, webpMediumSrc, webpViewerSrc),
    [variant, src, thumbnailSrc, mediumSrc, viewerSrc, webpThumbnailSrc, webpMediumSrc, webpViewerSrc],
  );
  const [index, setIndex] = useState(0);

  const handleError = useCallback<NonNullable<ImgHTMLAttributes<HTMLImageElement>['onError']>>(
    (event) => {
      setIndex((prev) => (prev < chain.length - 1 ? prev + 1 : prev));
      onError?.(event);
    },
    [chain.length, onError],
  );

  if (chain.length === 0) {
    return null;
  }

  const current = chain[Math.min(index, chain.length - 1)];

  const imgEl = (
    <img
      {...rest}
      src={current.jpg}
      alt={alt}
      loading={loading}
      decoding={decoding}
      fetchPriority={fetchPriority}
      onError={handleError}
    />
  );

  if (current.webp) {
    return (
      <picture>
        <source type="image/webp" srcSet={current.webp} />
        {imgEl}
      </picture>
    );
  }

  return imgEl;
}

export default GpImage;
