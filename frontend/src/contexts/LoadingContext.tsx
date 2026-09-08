import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { Loading } from '../components/common/Loading';

interface LoadingContextType {
  showLoading: (message?: string) => void;
  hideLoading: () => void;
  isLoading: boolean;
}

const LoadingContext = createContext<LoadingContextType | undefined>(undefined);

export const LoadingProvider = ({ children }: { children: ReactNode }) => {
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<string | undefined>(undefined);

  const showLoading = useCallback((msg?: string) => {
    setMessage(msg);
    setIsLoading(true);
  }, []);

  const hideLoading = useCallback(() => {
    setIsLoading(false);
    setMessage(undefined);
  }, []);

  return (
    <LoadingContext.Provider value={{ showLoading, hideLoading, isLoading }}>
      {children}
      {isLoading && <Loading fullScreen description={message} />}
    </LoadingContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components -- context files conventionally co-locate the Provider and its hook
export const useLoading = () => {
  const context = useContext(LoadingContext);
  if (context === undefined) {
    throw new Error('useLoading must be used within a LoadingProvider');
  }
  return context;
};
