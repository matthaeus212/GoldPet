import './WalkSaveAlertModal.css';

interface WalkSaveAlertModalProps {
  isOpen: boolean;
  minDuration: number;   // seconds
  minDistance: number;   // meters
  currentDuration: number;
  currentDistance: number;
  onCancel: () => void;
  onContinue: () => void;
}

export function WalkSaveAlertModal({
  isOpen,
  minDuration,
  minDistance,
  currentDuration,
  currentDistance,
  onCancel,
  onContinue,
}: WalkSaveAlertModalProps) {
  if (!isOpen) return null;

  return (
    <div className="walk-alert-backdrop">
      <div className="walk-alert-modal">
        <div className="walk-alert-content">
          <p className="walk-alert-title">산책 기록을 저장할 수 없습니다.</p>
          <div className="walk-alert-info-card">
            <p className="walk-alert-condition">
              최소 조건 : {minDuration}초이상, {minDistance}m 이상
            </p>
            <p className="walk-alert-current">
              현재 기록 : {currentDuration}초, {currentDistance}m
            </p>
          </div>
          <p className="walk-alert-question">산책을 계속하시겠습니까?</p>
        </div>
        <div className="walk-alert-buttons">
          <button type="button" className="walk-alert-btn-cancel" onClick={onCancel}>
            취소
          </button>
          <button type="button" className="walk-alert-btn-continue" onClick={onContinue}>
            계속
          </button>
        </div>
      </div>
    </div>
  );
}
