import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { MainHeader } from '../../components/common/MainHeader';
import { usePetSelector } from '../../hooks/usePetSelector';
import type { Pet } from '../../services/petService';
import { stoolAnalysisService } from '../../services/stoolAnalysisService';
import type { StoolAnalysisResponse } from '../../services/stoolAnalysisService';
import { HealthDetailModal } from './HealthDetailModal';
import { useShare } from '../share';
import './HealthHistoryPage.css';

function formatDate(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
}

function monthLabel(yearMonth: string): string {
  const month = parseInt(yearMonth.split('-')[1], 10);
  return `${month}월`;
}

function scoreColor(score: number | null): string {
  if (score == null) return '#AAAAAA';
  if (score === 1) return '#ff0909';
  if (score === 2) return '#ff7f0f';
  if (score === 3) return '#ffe30f';
  if (score === 4) return '#0f97ff';
  return '#12dd00';
}

function scoreLabel(score: number | null): string {
  if (score == null) return '-';
  if (score === 1) return '위험';
  if (score === 2) return '주의';
  if (score === 3) return '보통';
  if (score === 4) return '건강';
  return '매우 건강';
}

const ShareBtn = ({ onClick }: { onClick: (e: React.MouseEvent) => void }) => (
  <button type="button" className="hh_share_btn" aria-label="공유" onClick={onClick}>
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
      <path d="M6 8V1M3 3.5L6 1l3 2.5M2 9.5h8" stroke="#8A497D" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  </button>
);

const ScoreWidget = ({ score }: { score: number | null }) => {
  const pct = score != null ? (score / 5) * 100 : 0;
  const numSize = score === 1 ? 32 : 20;
  const unitSize = score === 1 ? 16 : 12;
  return (
    <div className="hh_score_widget">
      <div className="hh_score_num">
        <span style={{ fontSize: numSize, fontWeight: 900, color: 'var(--color-text-primary, #614108)' }}>
          {score ?? '-'}
        </span>
        <span style={{ fontSize: unitSize, fontWeight: 500, color: 'var(--color-text-primary, #614108)' }}>
          /5
        </span>
      </div>
      <div className="hh_score_bar_bg">
        <div
          className="hh_score_bar_fill"
          style={{ width: `${pct}%`, backgroundColor: scoreColor(score) }}
        />
      </div>
    </div>
  );
};

const HealthHistoryPage = () => {
  const [searchParams] = useSearchParams();
  const paramPetId = searchParams.get('petId') ? Number(searchParams.get('petId')) : null;

  const { pets, selectedPet, setSelectedPet } = usePetSelector();
  const { share } = useShare();
  const [showPetDropdown, setShowPetDropdown] = useState(false);
  const [page, setPage] = useState(0);
  const [allAnalyses, setAllAnalyses] = useState<StoolAnalysisResponse[]>([]);
  const [selectedAnalysis, setSelectedAnalysis] = useState<StoolAnalysisResponse | null>(null);
  const [trendMonths, setTrendMonths] = useState<3 | 6 | 12>(6);
  const [show4C, setShow4C] = useState(false);

  useEffect(() => {
    if (paramPetId && pets.length > 0) {
      const found = pets.find(p => p.id === paramPetId);
      if (found) setSelectedPet(found);
    }
  }, [paramPetId, pets, setSelectedPet]);

  const activePetId = selectedPet?.id ?? null;

  const { data: historyPage, isFetching: historyLoading } = useQuery({
    queryKey: ['health', 'history', activePetId, page],
    queryFn: () => stoolAnalysisService.getAnalysisHistory(activePetId!, page),
    enabled: activePetId != null,
  });

  useEffect(() => {
    if (!historyPage) return;
    if (page === 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAllAnalyses(historyPage.content);
    } else {
      setAllAnalyses(prev => [...prev, ...historyPage.content]);
    }
  }, [historyPage, page]);

  const { data: trend } = useQuery({
    queryKey: ['health', 'trend', activePetId, trendMonths],
    queryFn: () => stoolAnalysisService.getHealthTrend(activePetId!, trendMonths),
    enabled: activePetId != null,
  });

  const handlePetChange = (pet: Pet) => {
    setSelectedPet(pet);
    setShowPetDropdown(false);
    setPage(0);
    setAllAnalyses([]);
  };

  const round1 = (v: number | null | undefined) => (v == null ? null : parseFloat(v.toFixed(1)));
  const trendData = (trend?.monthlyScores ?? []).map(m => ({
    name: monthLabel(m.yearMonth),
    score: parseFloat(m.averageScore.toFixed(1)),
    color: round1(m.colorAvg),
    consistency: round1(m.consistencyAvg),
    coating: round1(m.coatingAvg),
    contents: round1(m.contentsAvg),
  }));

  const summary = trend?.summary;
  const summaryView = summary
    ? {
        IMPROVING: { text: '개선되고 있어요 ↑', color: '#12a150' },
        DECLINING: { text: '주의가 필요해요 ↓', color: '#ff4d4f' },
        STABLE: { text: '안정적으로 유지 중 →', color: '#888888' },
      }[summary.direction] ?? { text: '안정적으로 유지 중 →', color: '#888888' }
    : null;

  const C4_LINES = [
    { key: 'color', label: '색', stroke: '#ff7f0f' },
    { key: 'consistency', label: '형태', stroke: '#0f97ff' },
    { key: 'coating', label: '코팅', stroke: '#12a150' },
    { key: 'contents', label: '내용물', stroke: '#8A497D' },
  ] as const;

  const hasMore = historyPage ? !historyPage.last : false;

  return (
    <div className="hh_page">
      <MainHeader variant="back-only" className="intro_header" />

      <div className="hh_container">
        <p className="hh_disclaimer_text">
          AI 분석은 참고용이며, 정확한 진단은 수의사와 상담하세요.
        </p>

        {/* Pet selector */}
        {selectedPet && (
          <div className="hh_pet_selector">
            <div className="hh_pet_photo_wrap">
              {selectedPet.profileImageUrl ? (
                <img
                  src={selectedPet.profileImageUrl}
                  alt={selectedPet.name}
                  className="hh_pet_photo"
                />
              ) : (
                <div className="hh_pet_photo hh_pet_photo--placeholder">
                  <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="8" r="4" stroke="#A58A54" strokeWidth="1.8" />
                    <path
                      d="M4 20c0-4 3.6-7 8-7s8 3 8 7"
                      stroke="#A58A54"
                      strokeWidth="1.8"
                      strokeLinecap="round"
                    />
                  </svg>
                </div>
              )}
            </div>
            <div
              className="hh_pet_name_row"
              onClick={() => setShowPetDropdown(v => !v)}
              role="button"
              tabIndex={0}
              onKeyDown={e => e.key === 'Enter' && setShowPetDropdown(v => !v)}
            >
              <span className="hh_pet_name">{selectedPet.name}</span>
              <svg
                width="12"
                height="8"
                viewBox="0 0 12 8"
                fill="none"
                style={{ transform: showPetDropdown ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}
              >
                <path
                  d="M1 1l5 5 5-5"
                  stroke="#614108"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            {showPetDropdown && pets.length > 1 && (
              <div className="hh_pet_dropdown">
                {pets.map(p => (
                  <button
                    key={p.id}
                    type="button"
                    className={`hh_pet_dropdown_item${p.id === selectedPet.id ? ' active' : ''}`}
                    onClick={() => handlePetChange(p)}
                  >
                    {p.name}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Trend chart */}
        {trendData.length > 0 && (
          <div className="hh_trend_card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
              <h2 className="hh_trend_title">최근 {trendMonths}개월 건강 추이</h2>
              <div style={{ display: 'flex', gap: 6 }}>
                {([3, 6, 12] as const).map(mo => {
                  const on = trendMonths === mo;
                  return (
                    <button key={mo} type="button" onClick={() => setTrendMonths(mo)}
                      style={{ padding: '3px 10px', borderRadius: 12, fontSize: 12, border: 'none', cursor: 'pointer',
                        background: on ? '#614108' : '#f2ece0', color: on ? '#fff' : '#A58A54', fontWeight: on ? 700 : 400 }}>
                      {mo}개월
                    </button>
                  );
                })}
              </div>
            </div>

            {summaryView && summary && (
              <p style={{ margin: '6px 0 0', fontSize: 13, fontWeight: 700, color: summaryView.color }}>
                {summaryView.text}
                <span style={{ marginLeft: 6, fontWeight: 400, color: '#aaa' }}>
                  ({summary.firstMonth?.split('-')[1]}월→{summary.lastMonth?.split('-')[1]}월{summary.direction !== 'STABLE' ? `, ${summary.deltaOverall > 0 ? '+' : ''}${summary.deltaOverall.toFixed(1)}점` : ''})
                </span>
              </p>
            )}

            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#A58A54', margin: '8px 0 2px' }}>
              <input type="checkbox" checked={show4C} onChange={e => setShow4C(e.target.checked)} />
              4C 세부(색·형태·코팅·내용물) 보기
            </label>

            <div className="hh_trend_chart">
              <ResponsiveContainer width="100%" height={show4C ? 200 : 160}>
                <LineChart data={trendData} margin={{ top: 8, right: 16, left: -20, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#EFE1C4" />
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#A58A54' }} />
                  <YAxis
                    domain={[0, 5]}
                    ticks={[1, 2, 3, 4, 5]}
                    tick={{ fontSize: 12, fill: '#A58A54' }}
                  />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, border: '1px solid #EFE1C4', fontSize: 13 }}
                    formatter={(value, name) => [`${value}점`, name as string]}
                  />
                  {show4C && <Legend wrapperStyle={{ fontSize: 11 }} />}
                  <Line
                    type="monotone"
                    dataKey="score"
                    name="종합"
                    stroke="#614108"
                    strokeWidth={2}
                    dot={{ fill: '#614108', r: 4 }}
                    activeDot={{ r: 6 }}
                    connectNulls
                  />
                  {show4C && C4_LINES.map(l => (
                    <Line key={l.key} type="monotone" dataKey={l.key} name={l.label}
                      stroke={l.stroke} strokeWidth={1.5} dot={{ r: 2 }} connectNulls />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {/* Analysis list */}
        <div className="hh_list_section">
          <h2 className="hh_list_title">분석 기록</h2>

          {!historyLoading && allAnalyses.length === 0 ? (
            <div className="hh_empty">
              <div className="hh_empty_icon">💩</div>
              <p className="hh_empty_text">아직 분석 기록이 없습니다</p>
              <p className="hh_empty_sub">
                산책 중 대변 사진을 촬영하면
                <br />
                AI가 건강 상태를 분석해 드려요
              </p>
            </div>
          ) : (
            <>
              <div className="hh_list_card">
                {allAnalyses.map((analysis, idx) => (
                  <div key={analysis.id}>
                    {idx > 0 && <div className="hh_list_divider" />}
                    <button
                      type="button"
                      className="hh_list_item"
                      onClick={() => setSelectedAnalysis(analysis)}
                    >
                      {/* Row 1: pet info + date + share */}
                      <div className="hh_list_item_header">
                        <div className="hh_list_item_pet">
                          {selectedPet?.profileImageUrl ? (
                            <img
                              src={selectedPet.profileImageUrl}
                              alt={selectedPet?.name}
                              className="hh_list_item_pet_img"
                            />
                          ) : (
                            <div className="hh_list_item_pet_img hh_list_item_pet_img--placeholder">
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="8" r="4" stroke="#614108" strokeWidth="1.8" />
                                <path
                                  d="M4 20c0-4 3.6-7 8-7s8 3 8 7"
                                  stroke="#614108"
                                  strokeWidth="1.8"
                                  strokeLinecap="round"
                                />
                              </svg>
                            </div>
                          )}
                          <span className="hh_list_item_pet_name">{selectedPet?.name}</span>
                          {analysis.status === 'COMPLETED' && analysis.overallScore != null && (
                            <span
                              className="hh_list_item_score_badge"
                              style={{ backgroundColor: scoreColor(analysis.overallScore) }}
                            >
                              {scoreLabel(analysis.overallScore)}
                            </span>
                          )}
                        </div>
                        <div className="hh_list_item_right">
                          <span className="hh_list_item_date">
                            {formatDate(analysis.createdAt ?? null)}
                          </span>
                          <ShareBtn onClick={e => {
                            e.stopPropagation();
                            void share({ kind: 'health-history', petId: selectedPet?.id ?? 0, petName: selectedPet?.name ?? '' });
                          }} />
                        </div>
                      </div>

                      {/* Row 2: stool image + score widget + summary */}
                      <div className="hh_list_item_body">
                        <div className="hh_list_item_thumb">
                          <img
                            src={analysis.imageUrl}
                            alt="분석 이미지"
                            className="hh_list_item_img"
                          />
                          {(analysis.status === 'PENDING' || analysis.status === 'ANALYZING') && (
                            <div className="hh_list_item_overlay">분석 중</div>
                          )}
                        </div>
                        {analysis.status === 'COMPLETED' && analysis.overallScore != null ? (
                          <>
                            <ScoreWidget score={analysis.overallScore} />
                            {analysis.healthSummary && (
                              <p className="hh_list_item_summary">{analysis.healthSummary}</p>
                            )}
                          </>
                        ) : analysis.status === 'PENDING' || analysis.status === 'ANALYZING' ? (
                          <span className="hh_list_item_status">분석 중...</span>
                        ) : analysis.status === 'FAILED' ? (
                          <span className="hh_list_item_status hh_list_item_status--fail">
                            분석 실패
                          </span>
                        ) : null}
                      </div>
                    </button>
                  </div>
                ))}
              </div>
              {hasMore && (
                <button
                  type="button"
                  className="hh_load_more_btn"
                  onClick={() => setPage(p => p + 1)}
                  disabled={historyLoading}
                >
                  {historyLoading ? '로딩 중...' : '더 보기'}
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {selectedAnalysis && (
        <HealthDetailModal
          analysis={selectedAnalysis}
          petName={selectedPet?.name ?? ''}
          petImageUrl={selectedPet?.profileImageUrl ?? null}
          onClose={() => setSelectedAnalysis(null)}
        />
      )}
    </div>
  );
};

export default HealthHistoryPage;
