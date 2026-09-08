import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useOverlayColor } from '../../hooks/useOverlayColor';

interface ReportModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (reason: string) => void;
    isSubmitting?: boolean;
}

const REPORT_REASONS = ['욕설/비방', '음란물', '스팸/광고', '사기/사칭', '기타'];

export function ReportModal({ isOpen, onClose, onSubmit, isSubmitting = false }: ReportModalProps) {
    const [selectedReason, setSelectedReason] = useState('');
    const [customReason, setCustomReason] = useState('');

    useOverlayColor(isOpen);

    if (!isOpen) return null;

    const isOther = selectedReason === '기타';
    const isDisabled =
        !selectedReason ||
        (isOther && customReason.trim() === '') ||
        isSubmitting;

    const handleSubmit = () => {
        if (isDisabled) return;
        onSubmit(isOther ? customReason.trim() : selectedReason);
    };

    return createPortal(
        <div className="report_modal_overlay" onClick={onClose}>
            <div className="report_modal" onClick={(e) => e.stopPropagation()}>
                <h3 className="report_modal_title">신고 사유 선택</h3>
                <div className="report_modal_options">
                    {REPORT_REASONS.map((reason) => (
                        <label key={reason} className="report_modal_option">
                            <input
                                type="radio"
                                name="report_reason"
                                value={reason}
                                checked={selectedReason === reason}
                                onChange={() => setSelectedReason(reason)}
                            />
                            <span>{reason}</span>
                        </label>
                    ))}
                </div>
                {isOther && (
                    <>
                        <textarea
                            className="report_modal_textarea"
                            placeholder="신고 사유를 입력해주세요"
                            maxLength={500}
                            value={customReason}
                            onChange={(e) => setCustomReason(e.target.value)}
                        />
                        <p className="report_modal_textarea_counter">{customReason.length} / 500</p>
                    </>
                )}
                <button
                    type="button"
                    className="report_modal_submit"
                    disabled={isDisabled}
                    onClick={handleSubmit}
                >
                    {isSubmitting ? '신고 중...' : '신고하기'}
                </button>
            </div>
        </div>,
        document.body
    );
}
