import { useCallback } from 'react';
import { useAlert } from '../../contexts/AlertContext';
import { useToast } from '../../contexts/ToastContext';
import { nativeBridge } from '../../bridge/nativeBridge';
import { shouldWatermark } from './shouldWatermark';
import { composeImage } from './composeImage';
import { logShareAttempt, logShareSuccess, logShareFailure } from './shareAnalytics';
import type { ShareTarget, ShareResult } from './types';

const APP_BASE_URL = (import.meta.env.VITE_APP_URL as string | undefined) ?? 'https://app.goldpet.com';
const MAX_PAYLOAD_CHARS = 1.5 * 1024 * 1024; // 1.5 MB (base64 char count ≈ byte size)

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

interface ShareText {
  text: string;
  url: string;
  subject: string;
}

function buildShareText(target: ShareTarget): ShareText {
  switch (target.kind) {
    case 'walk-summary': {
      const petNames = target.session.petNames?.join(', ') ?? '반려동물';
      const distanceKm = (target.session.distance / 1000).toFixed(2);
      return {
        text: `오늘 ${petNames}와 ${distanceKm}km 산책했어요 🐾`,
        url: `${APP_BASE_URL}/w/${target.walkId}`,
        subject: `${petNames} 산책 기록`,
      };
    }
    case 'walk-photo': {
      const base = target.walkId ? `${APP_BASE_URL}/w/${target.walkId}` : APP_BASE_URL;
      const qs = target.walkId && target.spotId ? `?spot=${target.spotId}` : '';
      return {
        text: '산책 순간 📸',
        url: `${base}${qs}`,
        subject: '산책 사진',
      };
    }
    case 'course':
      return {
        text: `산책 코스 '${target.title}'`,
        url: `${APP_BASE_URL}/c/${target.courseId}`,
        subject: target.title,
      };
    case 'health-history':
      return {
        text: `${target.petName}의 건강 기록을 확인해 보세요`,
        url: `${APP_BASE_URL}/pet/${target.petId}/health`,
        subject: `${target.petName} 건강 기록`,
      };
    case 'health-detail':
      return {
        text: `${target.petName} 건강 검진 결과: ${target.summary}`,
        url: `${APP_BASE_URL}/health/${target.healthId}`,
        subject: `${target.petName} 건강 검진 결과`,
      };
    case 'community-post':
      return {
        text: target.title,
        url: `${APP_BASE_URL}/post/${target.postId}`,
        subject: target.title,
      };
  }
}

async function tryComposeImage(target: ShareTarget): Promise<string | undefined> {
  try {
    const blob = await composeImage(target);
    const base64 = await blobToBase64(blob);
    if (base64.length <= MAX_PAYLOAD_CHARS) {
      return base64;
    }
    // Payload too large — retry at lower quality (§4 guardrail)
    const retryBlob = await composeImage(target, 0.7);
    const retryBase64 = await blobToBase64(retryBlob);
    return retryBase64.length <= MAX_PAYLOAD_CHARS ? retryBase64 : undefined;
  } catch {
    return undefined;
  }
}

export function useShare() {
  const { showAlert } = useAlert();
  const { showToast } = useToast();

  const share = useCallback(
    async (target: ShareTarget): Promise<ShareResult> => {
      try {
        logShareAttempt(target);
        const { text, url, subject } = buildShareText(target);
        const fullText = `${text}\n${url}`;

        let imageBase64: string | undefined;
        if (shouldWatermark(target)) {
          imageBase64 = await tryComposeImage(target);
          if (!imageBase64) {
            if (target.kind === 'walk-photo') {
              // walk-photo 합성 최종 실패 — 무음 텍스트 전용 폴백 금지 (Pre-mortem Scenario 2).
              // 사용자가 직접 재시도할 수 있도록 명시적 토스트를 노출하고 공유를 중단한다.
              showToast('사진을 불러오지 못했어요. 다시 시도해주세요.', 'error');
              logShareFailure(target, 'photo-load-failed');
              return { status: 'failed' };
            }
            showToast('이미지 생성 실패, 링크만 공유합니다', 'error');
          }
        }

        if (!nativeBridge.isAvailable()) {
          await navigator.clipboard.writeText(fullText);
          showToast('링크가 복사되었습니다');
          const fallbackResult: ShareResult = { status: 'fallback-copied' };
          logShareSuccess(target, fallbackResult);
          return fallbackResult;
        }

        const raw = (await nativeBridge.callMethod('shareContent', {
          imageBase64,
          text: fullText,
          subject,
        })) as { status: string; channel?: string };

        const status: ShareResult['status'] =
          raw.status === 'shared' || raw.status === 'dismissed' || raw.status === 'fallback-copied'
            ? raw.status
            : 'failed';

        const result: ShareResult = { status, channel: raw.channel };
        if (status === 'failed') {
          logShareFailure(target, 'bridge-status-failed');
        } else {
          logShareSuccess(target, result);
        }
        return result;
      } catch (err) {
        const message = err instanceof Error ? err.message : '';
        if (message.includes('METHOD_NOT_FOUND')) {
          showToast('앱을 최신 버전으로 업데이트해 주세요', 'error');
          try {
            const { text, url } = buildShareText(target);
            await navigator.clipboard.writeText(`${text}\n${url}`);
            showToast('링크가 복사되었습니다');
            const fallbackResult: ShareResult = { status: 'fallback-copied' };
            logShareSuccess(target, fallbackResult);
            return fallbackResult;
          } catch {
            // clipboard also unavailable — fall through to showAlert
          }
        }
        logShareFailure(target, message || 'unknown');
        showAlert('공유할 수 없습니다. 다시 시도해 주세요.');
        return { status: 'failed' };
      }
    },
    [showAlert, showToast],
  );

  return { share };
}
