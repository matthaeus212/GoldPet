import React from 'react';
import type { StoolAnalysisResponse } from '../../services/stoolAnalysisService';
import { useShare } from '../share';

interface Props {
  analysis: StoolAnalysisResponse;
  petName: string;
  petImageUrl: string | null;
  onClose: () => void;
}

function parseJsonArray(val: string | null | undefined): string[] {
  if (!val) return [];
  try {
    const parsed = JSON.parse(val);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
}

function scoreColor(score: number | null | undefined): string {
  if (score == null) return '#AAAAAA';
  if (score === 1) return '#ff0909';
  if (score === 2) return '#ff7f0f';
  if (score === 3) return '#ffe30f';
  if (score === 4) return '#0f97ff';
  return '#12dd00';
}

function scoreColorTinted(score: number | null | undefined): string {
  if (score == null) return 'rgba(170,170,170,0.1)';
  if (score === 1) return 'rgba(255,9,9,0.1)';
  if (score === 2) return 'rgba(255,127,15,0.1)';
  if (score === 3) return 'rgba(255,227,15,0.1)';
  if (score === 4) return 'rgba(15,151,255,0.1)';
  return 'rgba(18,221,0,0.1)';
}

function scoreLabel(score: number | null | undefined): string {
  if (score == null) return '-';
  if (score === 1) return '위험';
  if (score === 2) return '주의';
  if (score === 3) return '보통';
  if (score === 4) return '건강';
  return '매우 건강';
}

const ScoreData: React.FC<{ score: number | null | undefined }> = ({ score }) => {
  const pct = score != null ? (score / 5) * 100 : 0;
  const numSize = score === 1 ? 32 : 20;
  const unitSize = score === 1 ? 16 : 12;
  return (
    <div className="hd_4c_score">
      <div style={{ display: 'flex', alignItems: 'baseline' }}>
        <span style={{ fontSize: numSize, fontWeight: 900, color: '#614108' }}>
          {score ?? '-'}
        </span>
        <span style={{ fontSize: unitSize, fontWeight: 500, color: '#614108' }}>/5</span>
      </div>
      <div className="hd_4c_bar_bg">
        <div
          className="hd_4c_bar_fill"
          style={{ width: `${pct}%`, backgroundColor: scoreColor(score) }}
        />
      </div>
    </div>
  );
};

const FourCCard: React.FC<{
  label: string;
  score: number | null | undefined;
  description: string | null | undefined;
}> = ({ label, score, description }) => (
  <div className="hd_4c_cell">
    <p className="hd_4c_label">{label}</p>
    <div className="hd_4c_card">
      <ScoreData score={score} />
      {description && <p className="hd_4c_desc">{description}</p>}
    </div>
  </div>
);

export const HealthDetailModal: React.FC<Props> = ({
  analysis,
  petName,
  petImageUrl,
  onClose,
}) => {
  const { share } = useShare();
  const tips = parseJsonArray(analysis.healthTips);
  const warnings = parseJsonArray(analysis.warnings);
  const overallPct =
    analysis.overallScore != null ? (analysis.overallScore / 5) * 100 : 0;
  const overallNumSize = analysis.overallScore === 1 ? 32 : 20;
  const overallUnitSize = analysis.overallScore === 1 ? 16 : 12;

  return (
    <div className="hd_overlay" onClick={onClose}>
      <div className="hd_modal" onClick={e => e.stopPropagation()}>
        <div className="hd_drag_handle" />

        {/* Pet info row */}
        <div className="hd_pet_row">
          <div className="hd_pet_info">
            {petImageUrl ? (
              <img src={petImageUrl} alt={petName} className="hd_pet_img" />
            ) : (
              <div className="hd_pet_img hd_pet_img--placeholder">
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
            <span className="hd_pet_name">{petName}</span>
          </div>
          <div className="hd_header_right">
            <span className="hd_date">{formatDate(analysis.createdAt)}</span>
            <button
              type="button"
              className="hd_share_btn"
              aria-label="공유"
              onClick={() => void share({
                kind: 'health-detail',
                healthId: analysis.id,
                petName,
                summary: analysis.healthSummary ?? '',
              })}
            >
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path
                  d="M6 8V1M3 3.5L6 1l3 2.5M2 9.5h8"
                  stroke="#8A497D"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>
          </div>
        </div>

        <div className="hd_modal_body">
          {/* Stool photo */}
          <div className="hd_photo_wrap">
            <img src={analysis.imageUrl} alt="분석 이미지" className="hd_photo" />
          </div>

          {/* Result section */}
          <div>
            <p className="hd_result_section_label">건강 분석 결과</p>
            <div className="hd_result_card">
              {/* Overall score */}
              <div className="hd_overall_section">
                <div className="hd_overall_title_row">
                  <span className="hd_overall_title">종합점수</span>
                  {analysis.overallScore != null && (
                    <span
                      className="hd_overall_badge"
                      style={{
                        backgroundColor: scoreColor(analysis.overallScore),
                        color: analysis.overallScore === 3 ? '#614108' : '#fff',
                      }}
                    >
                      {scoreLabel(analysis.overallScore)}
                    </span>
                  )}
                </div>
                <div
                  className="hd_overall_score_box"
                  style={{ backgroundColor: scoreColorTinted(analysis.overallScore) }}
                >
                  <div className="hd_score_widget">
                    <div style={{ display: 'flex', alignItems: 'baseline' }}>
                      <span
                        style={{
                          fontSize: overallNumSize,
                          fontWeight: 900,
                          color: '#614108',
                        }}
                      >
                        {analysis.overallScore ?? '-'}
                      </span>
                      <span style={{ fontSize: overallUnitSize, fontWeight: 500, color: '#614108' }}>
                        /5
                      </span>
                    </div>
                    <div className="hd_4c_bar_bg">
                      <div
                        className="hd_4c_bar_fill"
                        style={{
                          width: `${overallPct}%`,
                          backgroundColor: scoreColor(analysis.overallScore),
                        }}
                      />
                    </div>
                  </div>
                  {analysis.healthSummary && (
                    <p className="hd_overall_summary">{analysis.healthSummary}</p>
                  )}
                </div>
              </div>

              {/* 4C Grid */}
              <div className="hd_4c_grid">
                <FourCCard
                  label="색상(Color)"
                  score={analysis.colorScore}
                  description={analysis.colorAssessment}
                />
                <FourCCard
                  label="농도(Consistency)"
                  score={analysis.consistencyScore}
                  description={analysis.consistencyAssessment}
                />
                <FourCCard
                  label="코팅(Coating)"
                  score={analysis.coatingScore}
                  description={analysis.coatingAssessment}
                />
                <FourCCard
                  label="내용물(Contents)"
                  score={analysis.contentsScore}
                  description={analysis.contentsAssessment}
                />
              </div>

              {/* Warnings */}
              {warnings.length > 0 && (
                <div className="hd_warnings_section">
                  <p className="hd_warnings_title">주의사항</p>
                  <p className="hd_warnings_text">{warnings.join(' ')}</p>
                </div>
              )}

              {/* Tips */}
              {tips.length > 0 && (
                <div className="hd_tips_section">
                  <p className="hd_tips_title">건강 팁</p>
                  <div className="hd_tips_card">
                    <p className="hd_tips_text">{tips.join(' ')}</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Bottom disclaimer */}
          <p className="hd_disclaimer_text">
            AI 분석은 참고용이며, 정확한 진단은 수의사와 상담하세요.
          </p>
        </div>
      </div>
    </div>
  );
};
