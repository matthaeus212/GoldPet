/**
 * Canvas-based watermark image composition (§5.5, §5.5.1)
 * - walk-summary: 1080×1920 fixed (9:16 Instagram Story format)
 * - walk-photo:   longest side 1920px resize, ratio preserved
 * Output: JPEG Blob (default quality 0.85)
 */

import type { ShareTarget } from './types';
import { formatAddressForShare } from './formatAddressForShare';
import apiClient from '../../services/api/client';

const CANVAS_W = 1080;
const CANVAS_H = 1920;
const DEFAULT_QUALITY = 0.85;

// ─── Image loading ────────────────────────────────────────────────────────────

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = url;
  });
}

// ─── Canvas helpers ───────────────────────────────────────────────────────────

function drawImageCover(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  destX: number,
  destY: number,
  destW: number,
  destH: number,
): void {
  const srcRatio = img.naturalWidth / img.naturalHeight;
  const dstRatio = destW / destH;
  let srcX = 0;
  let srcY = 0;
  let srcW = img.naturalWidth;
  let srcH = img.naturalHeight;
  if (srcRatio > dstRatio) {
    srcW = img.naturalHeight * dstRatio;
    srcX = (img.naturalWidth - srcW) / 2;
  } else {
    srcH = img.naturalWidth / dstRatio;
    srcY = (img.naturalHeight - srcH) / 2;
  }
  ctx.drawImage(img, srcX, srcY, srcW, srcH, destX, destY, destW, destH);
}

function drawGradientBackground(ctx: CanvasRenderingContext2D): void {
  const gradient = ctx.createLinearGradient(0, 0, 0, CANVAS_H);
  gradient.addColorStop(0, '#FF8C42');
  gradient.addColorStop(1, '#FFD166');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
}

// ─── Path polyline (§5.5.1) ───────────────────────────────────────────────────

function drawPathPolyline(
  ctx: CanvasRenderingContext2D,
  pathPoints: { lat: number; lng: number }[],
  areaX: number,
  areaY: number,
  areaW: number,
  areaH: number,
  padding: number,
): void {
  if (pathPoints.length < 2) return;

  const lats = pathPoints.map((p) => p.lat);
  const lngs = pathPoints.map((p) => p.lng);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const latRange = maxLat - minLat || 0.001;
  const lngRange = maxLng - minLng || 0.001;

  const drawW = areaW - padding * 2;
  const drawH = areaH - padding * 2;

  const toX = (lng: number): number => areaX + padding + ((lng - minLng) / lngRange) * drawW;
  // lat increases northward → invert Y
  const toY = (lat: number): number => areaY + padding + (1 - (lat - minLat) / latRange) * drawH;

  ctx.save();

  // Polyline
  ctx.strokeStyle = '#FF6B35';
  ctx.lineWidth = 8;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(toX(pathPoints[0].lng), toY(pathPoints[0].lat));
  for (let i = 1; i < pathPoints.length; i++) {
    ctx.lineTo(toX(pathPoints[i].lng), toY(pathPoints[i].lat));
  }
  ctx.stroke();

  // Start marker — green circle
  const sx = toX(pathPoints[0].lng);
  const sy = toY(pathPoints[0].lat);
  ctx.beginPath();
  ctx.arc(sx, sy, 16, 0, Math.PI * 2);
  ctx.fillStyle = '#22C55E';
  ctx.fill();
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 4;
  ctx.stroke();

  // End marker — red circle
  const last = pathPoints[pathPoints.length - 1];
  const ex = toX(last.lng);
  const ey = toY(last.lat);
  ctx.beginPath();
  ctx.arc(ex, ey, 16, 0, Math.PI * 2);
  ctx.fillStyle = '#EF4444';
  ctx.fill();
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 4;
  ctx.stroke();

  ctx.restore();
}

// ─── Formatters ───────────────────────────────────────────────────────────────

function formatDistance(meters: number): string {
  return (meters / 1000).toFixed(2);
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatCalories(kcal: number): string {
  return Math.round(kcal).toString();
}

function formatDate(isoString: string): string {
  const d = new Date(isoString);
  const y = d.getFullYear();
  const mo = String(d.getMonth() + 1).padStart(2, '0');
  const dy = String(d.getDate()).padStart(2, '0');
  return `${y}.${mo}.${dy}`;
}

// ─── Font loading ─────────────────────────────────────────────────────────────

async function waitForFonts(): Promise<void> {
  if (typeof document === 'undefined') return;
  try {
    await Promise.race([
      document.fonts.ready,
      new Promise<void>((_, reject) =>
        setTimeout(() => reject(new Error('font timeout')), 2000),
      ),
    ]);
  } catch {
    // fall through — system font will be used
  }
}

function resolveFontFamily(): string {
  if (typeof document === 'undefined') return 'sans-serif';
  const loaded = [...document.fonts].some(
    (f) => f.family.includes('Pretendard') && f.status === 'loaded',
  );
  return loaded ? 'Pretendard' : "-apple-system, 'Apple SD Gothic Neo', sans-serif";
}

// ─── canvas → Blob ────────────────────────────────────────────────────────────

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('canvas.toBlob returned null'));
      },
      'image/jpeg',
      quality,
    );
  });
}

// ─── walk-summary compositor ──────────────────────────────────────────────────

async function composeWalkSummary(
  target: Extract<ShareTarget, { kind: 'walk-summary' }>,
  quality: number,
): Promise<Blob> {
  await waitForFonts();
  const font = resolveFontFamily();

  const canvas = document.createElement('canvas');
  // Fix pixel dimensions — do NOT multiply by devicePixelRatio
  canvas.width = CANVAS_W;
  canvas.height = CANVAS_H;

  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D context unavailable');

  // ── Layout constants ──
  const IMG_H = 1620;         // full-bleed image area
  const POLY_Y = IMG_H;       // 1620
  const POLY_H = 40;          // 1620–1660
  const OVERLAY_Y = 1660;     // bottom overlay strip start
  const OVERLAY_H = 260;      // 1660–1920
  const PAD = 60;

  // ── Background image (full-bleed, top 1620px) ──
  const bgUrl = target.session.petProfileImageUrls?.[0];
  let bgLoaded = false;
  if (bgUrl) {
    try {
      const img = await loadImage(bgUrl);
      drawImageCover(ctx, img, 0, 0, CANVAS_W, IMG_H);
      bgLoaded = true;
    } catch {
      // fallback to gradient below
    }
  }
  if (!bgLoaded) {
    drawGradientBackground(ctx);
  }

  // ── Top header overlay (logo + date) ──
  // Semi-transparent gradient behind top text
  const topHeaderGrad = ctx.createLinearGradient(0, 0, 0, 140);
  topHeaderGrad.addColorStop(0, 'rgba(0,0,0,0.55)');
  topHeaderGrad.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = topHeaderGrad;
  ctx.fillRect(0, 0, CANVAS_W, 140);

  // GoldPet logo — top-left
  ctx.save();
  ctx.font = `bold 44px ${font}`;
  ctx.fillStyle = '#FFFFFF';
  ctx.textBaseline = 'top';
  ctx.textAlign = 'left';
  ctx.fillText('GoldPet', PAD, 48);

  // Date — top-right
  const dateStr = target.session.startTime ? formatDate(target.session.startTime) : '';
  ctx.font = `500 32px ${font}`;
  ctx.textAlign = 'right';
  ctx.fillStyle = 'rgba(255,255,255,0.9)';
  ctx.fillText(`${dateStr} · 산책 기록`, CANVAS_W - PAD, 56);
  ctx.restore();

  // ── Polyline strip (y=1620–1660, 40px height) ──
  if (target.session.pathPoints && target.session.pathPoints.length >= 2) {
    drawPathPolyline(
      ctx,
      target.session.pathPoints,
      PAD,
      POLY_Y,
      CANVAS_W - PAD * 2,
      POLY_H,
      4,
    );
  }

  // ── Bottom overlay strip (y=1660–1920) ──
  // Feather gradient (12px) at top of strip
  const featherGrad = ctx.createLinearGradient(0, OVERLAY_Y, 0, OVERLAY_Y + 12);
  featherGrad.addColorStop(0, 'rgba(0,0,0,0)');
  featherGrad.addColorStop(1, 'rgba(0,0,0,0.55)');
  ctx.fillStyle = featherGrad;
  ctx.fillRect(0, OVERLAY_Y, CANVAS_W, 12);

  // Solid overlay remainder
  ctx.fillStyle = 'rgba(0,0,0,0.55)';
  ctx.fillRect(0, OVERLAY_Y + 12, CANVAS_W, OVERLAY_H - 12);

  // ── Stats row (y≈1760) ──
  const statsBaseY = 1760;
  const colW = CANVAS_W / 3;
  const stats = [
    {
      value: formatDistance(target.session.distance),
      unit: 'km',
      label: '거리',
    },
    {
      value: target.session.durationSeconds != null
        ? formatDuration(target.session.durationSeconds)
        : '--:--',
      unit: '',
      label: '시간',
    },
    {
      value: formatCalories(target.session.calories),
      unit: 'kcal',
      label: '칼로리',
    },
  ];

  stats.forEach((stat, i) => {
    const cx = colW * i + colW / 2;
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';

    ctx.font = `bold 64px ${font}`;
    ctx.fillStyle = '#FFFFFF';
    ctx.fillText(stat.value, cx, statsBaseY);

    if (stat.unit) {
      ctx.font = `500 28px ${font}`;
      ctx.fillStyle = 'rgba(255,255,255,0.75)';
      ctx.fillText(stat.unit, cx, statsBaseY + 42);
    }

    ctx.font = `400 24px ${font}`;
    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.fillText(stat.label, cx, statsBaseY + (stat.unit ? 80 : 42));
    ctx.restore();
  });

  // ── Pet name + address (y≈1840) ──
  const petNames = target.session.petNames?.join(', ') ?? '';
  const address = target.session.startAddress
    ? formatAddressForShare(target.session.startAddress)
    : '';
  const petInfo = [petNames, address].filter(Boolean).join(' · ');

  ctx.save();
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  ctx.font = `500 26px ${font}`;
  ctx.fillStyle = 'rgba(255,255,255,0.85)';
  ctx.fillText(petInfo, CANVAS_W / 2, 1840);

  // ── Domain watermark (y≈1880) ──
  ctx.font = `400 22px ${font}`;
  ctx.fillStyle = 'rgba(255,255,255,0.45)';
  ctx.fillText('goldpet.com', CANVAS_W / 2, 1880);
  ctx.restore();

  return canvasToBlob(canvas, quality);
}

// ─── Per-spot URL mint (§3.7) ─────────────────────────────────────────────────

interface PhotoUrlMintResponse {
  spotId: number;
  imageUrl: string;
  imageKey: string;
  expiresAt: string;
}

/**
 * Re-authorize a single spot's presigned URL via the cold-path mint endpoint.
 * Called when the embedded feed URL is expired (403) or clock-skewed (400).
 * Applies the same auth/ownership check as the feed query — not a bypass.
 */
async function mintPhotoUrl(spotId: number): Promise<string> {
  const res = await apiClient.get<PhotoUrlMintResponse>(`/walks/photos/${spotId}/url`);
  return res.data.imageUrl;
}

// ─── walk-photo compositor ────────────────────────────────────────────────────

async function composeWalkPhoto(
  target: Extract<ShareTarget, { kind: 'walk-photo' }>,
  quality: number,
): Promise<Blob> {
  // Step 1: load the presigned URL embedded in the feed response.
  // loadImage already sets crossOrigin="anonymous" — required for canvas.toBlob.
  let img: HTMLImageElement;
  try {
    img = await loadImage(target.photoUrl);
  } catch {
    // Step 2: URL likely expired (403) or clock-skewed (400 RequestTimeTooSkewed).
    // Re-authorize via the per-spot mint endpoint if spotId is available.
    if (!target.spotId) {
      throw new Error('walk-photo load failed and no spotId for mint retry');
    }
    let freshUrl: string;
    try {
      freshUrl = await mintPhotoUrl(target.spotId);
    } catch {
      throw new Error('walk-photo load failed: mint endpoint unavailable');
    }
    // Step 3: retry with freshly signed URL.
    try {
      img = await loadImage(freshUrl);
    } catch {
      // Both attempts failed — throw so the caller (useShare) surfaces a user-visible toast.
      throw new Error('walk-photo load failed after mint retry');
    }
  }

  const isLandscape = img.naturalWidth >= img.naturalHeight;
  const scale = 1920 / (isLandscape ? img.naturalWidth : img.naturalHeight);
  const w = Math.round(img.naturalWidth * scale);
  const h = Math.round(img.naturalHeight * scale);

  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;

  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D context unavailable');

  ctx.drawImage(img, 0, 0, w, h);

  try {
    return await canvasToBlob(canvas, quality);
  } catch (err) {
    // SecurityError = canvas tainted despite crossOrigin="anonymous" — indicates a CORS
    // misconfiguration on the S3 bucket. Emit a metric event so the ops dashboard can alert
    // before user complaints accumulate (§4.4 walk_photo_canvas_taint_error_total).
    if (err instanceof DOMException && err.name === 'SecurityError') {
      console.warn('[goldpet] canvas SecurityError on walk-photo share — check S3 CORS policy', err);
      window.dispatchEvent(
        new CustomEvent('goldpet:metric', {
          detail: { name: 'walk_photo_canvas_taint_error_total', value: 1 },
        }),
      );
    }
    throw err;
  }
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Compose a shareable image from a ShareTarget.
 *
 * Fallback policy (§5.7):
 *   1. Background image load fails → drawGradientBackground (handled inline)
 *   2. Any other failure → throws → useShare.tryComposeImage retries at 0.7 quality
 *   3. Second failure → throws → useShare falls back to text-only sharing
 */
export async function composeImage(target: ShareTarget, quality?: number): Promise<Blob> {
  const q = quality ?? DEFAULT_QUALITY;
  switch (target.kind) {
    case 'walk-summary':
      return composeWalkSummary(target, q);
    case 'walk-photo':
      return composeWalkPhoto(target, q);
    default:
      // Phase 2+ kinds not yet implemented — throws so useShare falls back to text
      throw new Error(`composeImage: "${target.kind}" not yet implemented`);
  }
}
