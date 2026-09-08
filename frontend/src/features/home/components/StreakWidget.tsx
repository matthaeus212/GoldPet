import type { StreakResponse } from '../../../services/streakService';

interface StreakWidgetProps {
  data: StreakResponse;
}

export function StreakWidget({ data }: StreakWidgetProps) {
  const { currentStreak, longestStreak, freezeCount, activeToday } = data;
  const isColdStart = currentStreak === 0 && !data.lastActiveDate;

  return (
    <div className="streak_widget">
      {isColdStart ? (
        <p className="streak_widget__empty">첫 산책으로 스트릭을 시작하세요 🐾</p>
      ) : (
        <>
          <div className="streak_widget__main">
            <div className="streak_widget__flame_wrap">
              <span className="streak_widget__flame_icon" aria-hidden="true">🔥</span>
              <span className="streak_widget__count">{currentStreak}</span>
              <span className="streak_widget__unit">일 연속</span>
            </div>
            {activeToday && (
              <span className="streak_widget__today_badge">오늘 완료 ✓</span>
            )}
          </div>
          <div className="streak_widget__divider" />
          <div className="streak_widget__stats">
            <div className="streak_widget__stat">
              <span className="streak_widget__stat_label">최장 스트릭</span>
              <span className="streak_widget__stat_value">{longestStreak}일</span>
            </div>
            <div className="streak_widget__stat">
              <span className="streak_widget__stat_label">프리즈</span>
              <span className="streak_widget__stat_value">{freezeCount}회 남음</span>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
