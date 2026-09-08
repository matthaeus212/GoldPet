import React from 'react';
import { twMerge } from 'tailwind-merge';
import clsx from 'clsx';

type ButtonVariant = 'primary' | 'danger' | 'warning' | 'success' | 'secondary' | 'ghost' | 'link';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  children: React.ReactNode;
}

const variantStyles: Record<ButtonVariant, string> = {
  // 표준 작업: 추가, 생성, 검색, 발송, 저장
  primary: 'bg-indigo-600 text-white hover:bg-indigo-700 focus:ring-indigo-500',
  // 삭제, 초기화
  danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500',
  // 환불, 정지, 숨김
  warning: 'bg-amber-500 text-white hover:bg-amber-600 focus:ring-amber-500',
  // 활성화, 조치, 승인
  success: 'bg-green-600 text-white hover:bg-green-700 focus:ring-green-500',
  // 취소, 닫기
  secondary: 'bg-gray-200 text-gray-800 hover:bg-gray-300 focus:ring-gray-500',
  // 비활성화, 낮은 우선순위 작업
  ghost: 'border border-gray-300 text-gray-700 hover:bg-gray-50 focus:ring-gray-500',
  // 테이블 인라인 작업: 상세, 수정, 삭제
  link: 'text-indigo-600 hover:text-indigo-900 underline-offset-2 hover:underline p-0',
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
};

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className,
  children,
  ...props
}) => {
  const isDisabled = disabled || loading;
  const isLink = variant === 'link';

  return (
    <button
      className={twMerge(
        clsx(
          'inline-flex items-center justify-center font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2',
          !isLink && 'rounded-lg',
          variantStyles[variant],
          !isLink && sizeStyles[size],
          isDisabled && 'opacity-50 cursor-not-allowed',
        ),
        className,
      )}
      disabled={isDisabled}
      {...props}
    >
      {loading && (
        <svg className="animate-spin -ml-1 mr-2 h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      )}
      {children}
    </button>
  );
};
