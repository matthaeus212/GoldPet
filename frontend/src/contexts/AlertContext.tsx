import { createContext, useContext, useState, useCallback } from 'react';
import type { ReactNode } from 'react';
import { AlertModal } from '../components/common/AlertModal';

interface AlertOptions {
  message: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  confirmText?: string;
  cancelText?: string;
}

interface ConfirmOptions {
  confirmText?: string;
  cancelText?: string;
}

interface AlertContextType {
  showAlert: (message: string, onConfirm?: () => void) => void;
  showConfirm: (message: string, onConfirm: () => void, onCancel?: () => void, options?: ConfirmOptions) => void;
  closeAlert: () => void;
}

const AlertContext = createContext<AlertContextType | undefined>(undefined);

export const AlertProvider = ({ children }: { children: ReactNode }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [alertState, setAlertState] = useState<AlertOptions>({ message: '' });

  const showAlert = useCallback((message: string, onConfirm?: () => void) => {
    setAlertState({ 
      message, 
      onConfirm: () => {
        setIsOpen(false);
        if (onConfirm) onConfirm();
      }
    });
    setIsOpen(true);
  }, []);

  const showConfirm = useCallback((message: string, onConfirm: () => void, onCancel?: () => void, options?: ConfirmOptions) => {
    setAlertState({
      message,
      confirmText: options?.confirmText,
      cancelText: options?.cancelText,
      onConfirm: () => {
        setIsOpen(false);
        onConfirm();
      },
      onCancel: () => {
        setIsOpen(false);
        if (onCancel) onCancel();
      }
    });
    setIsOpen(true);
  }, []);

  const closeAlert = useCallback(() => {
    setIsOpen(false);
  }, []);

  return (
    <AlertContext.Provider value={{ showAlert, showConfirm, closeAlert }}>
      {children}
      <AlertModal
        isOpen={isOpen}
        message={alertState.message}
        onConfirm={alertState.onConfirm || closeAlert}
        onCancel={alertState.onCancel}
        confirmText={alertState.confirmText}
        cancelText={alertState.cancelText}
      />
    </AlertContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components -- context files conventionally co-locate the Provider and its hook
export const useAlert = () => {
  const context = useContext(AlertContext);
  if (context === undefined) {
    throw new Error('useAlert must be used within an AlertProvider');
  }
  return context;
};
