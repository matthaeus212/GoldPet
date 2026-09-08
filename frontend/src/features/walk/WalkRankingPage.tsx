import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { walkService } from '../../services/walkService';
import type { WalkRankingEntry } from '../../services/walkService';
import { BackButton } from '../../components/common/BackButton';
import './WalkRankingPage.css';

function getCurrentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

const WalkRankingPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialYearMonth = searchParams.get('yearMonth') || getCurrentYearMonth();
  const [yearMonth, setYearMonth] = useState<string>(initialYearMonth);
  const currentYearMonth = getCurrentYearMonth();

  const { data: rankingData, isLoading } = useQuery({
    queryKey: ['walk', 'ranking', 'calendar', yearMonth],
    queryFn: () => walkService.getMonthlyRanking(yearMonth),
  });

  function prevMonth() {
    const [y, m] = yearMonth.split('-').map(Number);
    const d = new Date(y, m - 2, 1);
    setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }

  function nextMonth() {
    const [y, m] = yearMonth.split('-').map(Number);
    const d = new Date(y, m, 1);
    setYearMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }

  const bestCouple = rankingData?.bestCouple ?? null;
  const rankings = rankingData?.rankings ?? [];

  return (
    <div className="walk-ranking-page">
      {/* Header */}
      <div className="walk-ranking-header">
        <BackButton onClick={() => navigate('/walk')} />
        <h1>산책 랭킹</h1>
        <div style={{ width: 32 }} />
      </div>

      {/* Month Selector */}
      <div className="walk-ranking-month-selector">
        <button type="button" className="walk-ranking-month-arrow" onClick={prevMonth}>
          <svg width="8" height="14" viewBox="0 0 8 14" fill="none">
            <path d="M7 1L1 7L7 13" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <span className="walk-ranking-month-label">
          {yearMonth.replace('-', '.')}
        </span>
        <button
          type="button"
          className="walk-ranking-month-arrow"
          onClick={nextMonth}
          disabled={yearMonth >= currentYearMonth}
          style={{ opacity: yearMonth >= currentYearMonth ? 0.3 : 1 }}
        >
          <svg width="8" height="14" viewBox="0 0 8 14" fill="none">
            <path d="M1 1L7 7L1 13" stroke="#614108" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
      </div>

      {isLoading ? (
        <p className="walk-ranking-empty">불러오는 중...</p>
      ) : rankings.length === 0 ? (
        <p className="walk-ranking-empty">이 달의 산책 기록이 없습니다.</p>
      ) : (
        <div className="record_wrap">
          {/* Best Couple Card */}
          {bestCouple && (
            <div className="best_rank">
              <div className="best_couple">
                <p className="tit">
                  <img src="/assets/images/walk/best_icon.svg" alt="" />
                  BEST 산책 커플
                </p>
                <div className="couple_img">
                  <div className="blob_avatar">
                    <img
                      src={bestCouple.petProfileImageUrl || '/assets/images/common/pet_none_img.svg'}
                      alt={bestCouple.petName || ''}
                    />
                  </div>
                  <div className="blob_avatar">
                    <img
                      src={bestCouple.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
                      alt={bestCouple.nickname}
                    />
                  </div>
                </div>
                <button type="button" className="couple_btn">
                  {bestCouple.petName || '반려동물'}
                  <img src="/assets/images/walk/best_icon02.svg" alt="" />
                  {bestCouple.nickname}
                </button>
                <div className="couple_detail">
                  <dl>
                    <dt>총 거리(km)</dt>
                    <dd>{bestCouple.totalDistanceKm.toFixed(1)}</dd>
                  </dl>
                  <dl>
                    <dt>총 시간(분)</dt>
                    <dd>{bestCouple.totalMinutes.toLocaleString()}</dd>
                  </dl>
                  <dl>
                    <dt>총 적립(Gold)</dt>
                    <dd>{bestCouple.totalGold}</dd>
                  </dl>
                </div>
              </div>
            </div>
          )}

          {/* Ranking List */}
          <ul className="ranking_list">
            {rankings
              .filter(e => !bestCouple || e.userId !== bestCouple.userId)
              .map((entry) => (
                <RankingListItem key={entry.userId} entry={entry} />
              ))}
          </ul>
        </div>
      )}
    </div>
  );
};

function RankingListItem({ entry }: { entry: WalkRankingEntry }) {
  return (
    <li>
      <div className="ranking_img">
        <div className="blob_avatar">
          <img
            src={entry.petProfileImageUrl || '/assets/images/common/pet_none_img.svg'}
            alt={entry.petName || ''}
          />
        </div>
        <div className="blob_avatar">
          <img
            src={entry.profileImageUrl || '/assets/images/common/profile_none_img.svg'}
            alt={entry.nickname}
          />
        </div>
        <p className="count_txt">{entry.rank}</p>
      </div>
      <div className="ranking_txt">
        <strong>{entry.petName ? `${entry.petName}와 ${entry.nickname}` : entry.nickname}</strong>
        <div className="txt_wrap">
          <div className="txt01">
            <p>{entry.totalDistanceKm.toFixed(1)} Km</p>
            <p>총 {entry.totalMinutes}분</p>
          </div>
          <p>{entry.totalGold}G 적립</p>
        </div>
      </div>
    </li>
  );
}

export default WalkRankingPage;
