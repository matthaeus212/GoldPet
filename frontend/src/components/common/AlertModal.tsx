
import ReactDOM from 'react-dom';
import { useOverlayColor } from '../../hooks/useOverlayColor';

interface AlertModalProps {
    isOpen: boolean;
    message: string;
    onConfirm: () => void;
    onCancel?: () => void;
    confirmText?: string;
    cancelText?: string;
}

export const AlertModal = ({
    isOpen,
    message,
    onConfirm,
    onCancel,
    confirmText = '확인',
    cancelText = '취소'
}: AlertModalProps) => {
    useOverlayColor(isOpen);

    if (!isOpen) return null;

    const content = (
        <div className="gp-alert-overlay">
            <div className="gp-alert-container">
                <p className="gp-alert-message">{message.replace(/\\n/g, '\n')}</p>
                <div className="gp-alert-buttons">
                    {onCancel ? (
                        <>
                            <button type="button" className="gp-btn-half-secondary" onClick={onCancel}>{cancelText}</button>
                            <button type="button" className="gp-btn-half-primary" onClick={onConfirm}>{confirmText}</button>
                        </>
                    ) : (
                        <button type="button" className="gp-btn-full" onClick={onConfirm}>{confirmText}</button>
                    )}
                </div>
            </div>
        </div>
    );

    return ReactDOM.createPortal(content, document.body);
};
