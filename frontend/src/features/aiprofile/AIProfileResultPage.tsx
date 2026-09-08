import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { aiProfileService } from '../../services/aiProfileService';
import { useToast } from '../../contexts/ToastContext';
import { useAlert } from '../../contexts/AlertContext';
import { useOverlayColor } from '../../hooks/useOverlayColor';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import pawIcon from './assets/paw-loading.svg';
import './AIProfilePage.css';

const DEFAULT_TIPS = [
  '강아지는 사람의 감정을 읽을 수 있어요 🐶',
  '고양이는 하루 16시간 이상 잠을 자요 😸',
  '반려동물과 산책하면 스트레스가 줄어들어요 🌿',
  'AI가 최고의 결과를 만들고 있어요 ✨',
  '강아지의 코 무늬는 사람의 지문처럼 고유해요 🐾',
  '고양이는 약 100가지 소리를 낼 수 있어요 🎵',
  '반려동물은 주인의 목소리를 기억해요 💕',
];

export default function AIProfileResultPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { showConfirm } = useAlert();
  const queryClient = useQueryClient();

  const [showApplySheet, setShowApplySheet] = useState(false);
  useOverlayColor(showApplySheet, '#000000', '#EFE1C4');
  const [elapsed, setElapsed] = useState(0);
  const [tipIndex, setTipIndex] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { data: serverTips } = useQuery({
    queryKey: ['ai-loading-tips'],
    queryFn: () => aiProfileService.getLoadingTips(),
    staleTime: 1000 * 60 * 60, // 1시간
  });

  const tips = serverTips && serverTips.length > 0 ? serverTips : DEFAULT_TIPS;

  const { data: request } = useQuery({
    queryKey: ['ai-profile', 'request', requestId],
    queryFn: () => aiProfileService.getRequest(Number(requestId)),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'PROCESSING' ? 3000 : false;
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => aiProfileService.cancelRequest(Number(requestId)),
    onSuccess: () => {
      showToast('요청이 취소되었습니다.');
      navigate('/ai-profile', { replace: true });
    },
    onError: () => {
      showToast('취소에 실패했습니다.', 'error');
    },
  });

  const applyMutation = useMutation({
    mutationFn: ({ applyAs, forceReplace }: { applyAs: 'MAIN' | 'ADDITIONAL'; forceReplace?: boolean }) =>
      aiProfileService.applyToProfile(Number(requestId), {
        applyAs,
        petId: request!.petId!,
        forceReplace,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pets'] });
      showToast('프로필에 적용되었습니다!', 'success');
      setShowApplySheet(false);
      navigate('/profile?tab=pet', { replace: true });
    },
    onError: (error: unknown, variables) => {
      const err = error as { response?: { data?: { message?: string; errorCode?: string } } };
      const errorCode = err?.response?.data?.errorCode;
      const message = err?.response?.data?.message || '적용에 실패했습니다.';
      if (
        errorCode === 'PROFILE_IMAGE_LIMIT_EXCEEDED' &&
        variables.applyAs === 'MAIN' &&
        !variables.forceReplace
      ) {
        showConfirm(
          '이미 10장의 이미지가 등록되어 있어요.\n기존 대표 이미지를 새 이미지로 교체할까요?',
          () => applyMutation.mutate({ applyAs: 'MAIN', forceReplace: true }),
          undefined,
          { confirmText: '교체하기', cancelText: '취소' },
        );
        return;
      }
      showToast(message, 'error');
    },
  });

  const handleOpenApplySheet = () => {
    if (!request?.petId) {
      showToast('등록된 반려동물이 없어 프로필에 적용할 수 없습니다.', 'error');
      return;
    }
    setShowApplySheet(true);
  };

  const handleApply = (applyAs: 'MAIN' | 'ADDITIONAL') => {
    if (applyMutation.isPending) return;
    setShowApplySheet(false);
    applyMutation.mutate({ applyAs });
  };

  const isProcessing = request?.status === 'PENDING' || request?.status === 'PROCESSING';

  useEffect(() => {
    if (isProcessing) {
      timerRef.current = setInterval(() => setElapsed((s) => s + 1), 1000);
      const tipTimer = setInterval(() => setTipIndex((i) => (i + 1) % tips.length), 4000);
      return () => {
        if (timerRef.current) clearInterval(timerRef.current);
        clearInterval(tipTimer);
        setElapsed(0);
      };
    } else {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- timer should only start/stop based on isProcessing; adding tips.length would reset the interval when tips change
  }, [isProcessing]);

  const getProgressStep = () => {
    if (elapsed < 10) return 0;
    if (elapsed < 40) return 1;
    return 2;
  };

  const progressSteps = ['프롬프트 분석 중', '이미지 생성 중', '마무리 중'];
  const progressStep = getProgressStep();

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m > 0 ? `${m}분 ${s.toString().padStart(2, '0')}초` : `${s}초`;
  };

  if (!request) {
    return (
      <SubPageLayout title="">
        <div className="ai-result-processing">
          <div className="ai-result-paw-icon">
            <img src={pawIcon} alt="" />
          </div>
          <div className="ai-result-processing-title">불러오는 중...</div>
        </div>
      </SubPageLayout>
    );
  }

  const isCompleted = request.status === 'COMPLETED';
  const isFailed = request.status === 'FAILED';
  const isRefunded = request.status === 'REFUNDED';

  return (
    <SubPageLayout title="">
      <div id="aiMake">
        {/* PENDING / PROCESSING state */}
        {isProcessing && (
          <div className="ai-result-processing">
            <div className="ai-result-processing-title">AI가 프로필을 생성하고 있어요...</div>
            <div className="ai-result-processing-subtitle">
              잠시만 기다려 주세요.<br />완료되면 자동으로 결과가 표시됩니다.
            </div>

            <div className="ai-result-paw-icon">
              <img src={pawIcon} alt="" />
            </div>

            <div className="ai-result-timer">{formatTime(elapsed)}</div>

            <div className="ai-result-steps">
              {progressSteps.map((label, i) => (
                <div key={label} className={`ai-result-step ${i <= progressStep ? 'active' : ''} ${i === progressStep ? 'current' : ''}`}>
                  <div className="ai-result-step-dot" />
                  <span>{label}</span>
                </div>
              ))}
            </div>

            <div className="ai-result-tip" key={tipIndex}>
              {tips[tipIndex]}
            </div>

            <button
              type="button"
              className="ai-result-cancel-full-btn"
              onClick={() => cancelMutation.mutate()}
              disabled={cancelMutation.isPending}
            >
              {cancelMutation.isPending ? '취소 중...' : '생성 취소하기'}
            </button>
          </div>
        )}

        {/* COMPLETED state */}
        {isCompleted && request.resultUrl && (
          <div className="ai-result-completed">
            <div className="ai-result-completed-body">
              <div className="ai-result-completed-title">이미지 생성이 완료되었어요!</div>
              <div className="ai-result-image-wrap">
                <img
                  src={request.resultUrl}
                  alt="AI 생성 이미지"
                  className="ai-result-image"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = '/assets/images/common/pet_none_img.svg';
                  }}
                />
              </div>
              {request.stylePrompt && (
                <div className="ai-result-prompt">{request.stylePrompt}</div>
              )}
            </div>
            <div className="ai-result-btn-row">
              <button
                type="button"
                className="ai-result-remake-btn"
                onClick={() => navigate('/ai-profile', { replace: true })}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 2v6h-6" /><path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
                  <path d="M3 22v-6h6" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
                </svg>
                다시 만들기
              </button>
              <button
                type="button"
                className="ai-result-apply-btn"
                onClick={handleOpenApplySheet}
              >
                프로필 적용하기
              </button>
            </div>
          </div>
        )}

        {/* FAILED state */}
        {isFailed && (
          <div className="ai-result-status-wrap">
            <div className="ai_txt_wrap">
              <strong style={{ color: 'var(--color-text-secondary)' }}>프로필 생성에 실패했어요</strong>
              <div className="ai-result-processing-subtitle" style={{ marginTop: 16 }}>
                프로필 생성 중 오류가 발생했습니다.<br />
                골드가 환불되었습니다.
              </div>
            </div>
            <button
              type="button"
              className="submit_btn"
              onClick={() => navigate('/ai-profile', { replace: true })}
            >
              다시 시도하기
            </button>
          </div>
        )}

        {/* REFUNDED state */}
        {isRefunded && (
          <div className="ai-result-status-wrap">
            <div className="ai_txt_wrap">
              <strong style={{ color: 'var(--color-text-secondary)' }}>환불 처리됨</strong>
              <div className="ai-result-processing-subtitle" style={{ marginTop: 16 }}>
                요청이 취소되어 골드가 환불되었습니다.
              </div>
            </div>
            <button
              type="button"
              className="submit_btn"
              onClick={() => navigate('/ai-profile', { replace: true })}
            >
              다시 만들기
            </button>
          </div>
        )}
      </div>

      {/* Apply Bottom Sheet */}
      {showApplySheet && (
        <div className="ai-apply-sheet-overlay" onClick={() => setShowApplySheet(false)}>
          <div className="ai-apply-sheet" onClick={(e) => e.stopPropagation()}>
            <div className="ai-apply-sheet-menu">
              <button
                type="button"
                className="ai-apply-sheet-item"
                onClick={() => handleApply('MAIN')}
                disabled={applyMutation.isPending}
              >
                대표 이미지로 등록
              </button>
              <hr className="ai-apply-sheet-divider" />
              <button
                type="button"
                className="ai-apply-sheet-item"
                onClick={() => handleApply('ADDITIONAL')}
                disabled={applyMutation.isPending}
              >
                추가 이미지로 등록
              </button>
              <hr className="ai-apply-sheet-divider" />
              <button
                type="button"
                className="ai-apply-sheet-item"
                onClick={() => setShowApplySheet(false)}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </SubPageLayout>
  );
}
