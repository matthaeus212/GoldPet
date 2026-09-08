import { useState } from 'react';

function getCurrentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

interface UseMonthNavigationResult {
  currentMonth: string;
  goToPrevMonth: () => void;
  goToNextMonth: () => void;
  isCurrentMonth: boolean;
  formatMonth: (yearMonth: string) => string;
}

export function useMonthNavigation(): UseMonthNavigationResult {
  const [currentMonth, setCurrentMonth] = useState<string>(getCurrentYearMonth());
  const todayYearMonth = getCurrentYearMonth();

  function goToPrevMonth() {
    const [y, m] = currentMonth.split('-').map(Number);
    const d = new Date(y, m - 2, 1);
    setCurrentMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }

  function goToNextMonth() {
    const [y, m] = currentMonth.split('-').map(Number);
    const d = new Date(y, m, 1);
    setCurrentMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }

  function formatMonth(yearMonth: string): string {
    return yearMonth.replace('-', '.');
  }

  return {
    currentMonth,
    goToPrevMonth,
    goToNextMonth,
    isCurrentMonth: currentMonth >= todayYearMonth,
    formatMonth,
  };
}
