# .omc/measurements — Gate 측정 결과 저장 규약

이 디렉토리는 Image Performance Overhaul (Tier 0~3) 의 Acceptance Gate 측정 결과를 저장합니다.

## 파일 명명 규칙

```
lcp-{YYYYMMDD}-{mode}.json          A0-5 Lighthouse LCP 측정 결과
lcp-baseline.json                   A0-5 최신 baseline (gate-a05-lcp.sh 가 자동 갱신)
lcp-after.json                      A0-5 최신 after    (gate-a05-lcp.sh 가 자동 갱신)
```

## Gate 스크립트 위치

| Gate | 스크립트 | 설명 |
|------|---------|------|
| A0-1 | `api/scripts/gate-a01-viewer-rate.sql` | viewer 생성률 ≥95% |
| A0-2 | `api/scripts/gate-a02-payload.js` | 평균 payload −60% |
| A0-3 | `api/scripts/gate-a03-error-rate.sh` | 에러율 변동 <2% |
| A0-4 | `api/scripts/gate-a04-cache-control.sh` | Cache-Control 헤더 검증 |
| A0-5 | `frontend/scripts/gate-a05-lcp.sh` | Lighthouse LCP 악화 금지 |
| A0-6 | `api/scripts/gate-a06-counters.sh` | Counter 3종 non-zero |

## 실행 순서 (Tier 0 배포 전/후)

```bash
# 0. 환경 변수 설정
export DB_URL="postgresql://goldpet:goldpet123@localhost:5432/goldpet"
export API_BASE_URL="http://localhost:8081"          # 또는 https://api.mannamsquare.com
export S3_PUBLIC_URL="https://s3.mannamsquare.com/goldpet-public/<uuid>.jpg"

# ── 배포 전 (baseline) ──────────────────────────────────────────────────────

# A0-3 baseline 저장
bash api/scripts/gate-a03-error-rate.sh "$API_BASE_URL"

# A0-5 baseline 3회 측정 (수동, 약 3분 소요)
bash frontend/scripts/gate-a05-lcp.sh https://app.mannamsquare.com baseline

# ── Tier 0 배포 (PR#1~PR#5) ─────────────────────────────────────────────────

# ── 배포 후 검증 ─────────────────────────────────────────────────────────────

# A0-1: 신규 업로드 viewer 생성률
psql "$DB_URL" -v ON_ERROR_STOP=1 -f api/scripts/gate-a01-viewer-rate.sql

# A0-2: payload 크기 baseline 저장 후 비교
DB_URL="$DB_URL" node api/scripts/gate-a02-payload.js baseline
# ... 업로드 실행 ...
DB_URL="$DB_URL" node api/scripts/gate-a02-payload.js after
node api/scripts/gate-a02-payload.js compare

# A0-3: 에러율 비교 (배포 후)
bash api/scripts/gate-a03-error-rate.sh "$API_BASE_URL"

# A0-4: Cache-Control 헤더 (신규 업로드 URL 필요)
DB_URL="$DB_URL" bash api/scripts/gate-a04-cache-control.sh

# A0-5: LCP after 3회 측정 + 비교
bash frontend/scripts/gate-a05-lcp.sh https://app.mannamsquare.com after
bash frontend/scripts/gate-a05-lcp.sh _ compare

# A0-6: Counter 3종 non-zero
bash api/scripts/gate-a06-counters.sh "$API_BASE_URL"
```

## 결과 판정 기준

| Gate | Pass 조건 | 실패 시 |
|------|----------|---------|
| A0-1 | viewer_rate ≥ 0.95 | T0-1 재검증, variantExecutor 큐 포화 확인 |
| A0-2 | 평균 크기 감소율 ≥ 60% | viewer_url LIKE '%_viewer.jpg' 샘플 확인 |
| A0-3 | delta < 2% | 즉시 롤백, 에러 로그 grep |
| A0-4 | immutable + public + max-age=31536000 | nginx proxy_hide_header / add_header 조치 |
| A0-5 | after LCP 중앙값 ≤ baseline × 1.05 | T0-3 롤백, Swiper[0] eager 확인 |
| A0-6 | 핵심 Counter 2종 non-zero | Counter 이름 오타, Micrometer 등록 확인 |

**6개 모두 PASS → Tier 1 착수 가능**

## JSON 스키마 (A0-5)

```json
{
  "mode": "baseline | after",
  "url": "https://...",
  "runs": 3,
  "values_ms": [1234.0, 1456.0, 1350.0],
  "median_ms": 1350.0,
  "measured_at": "2026-04-20T10:00:00Z"
}
```

## .gitignore

이 디렉토리의 JSON 측정 결과는 선택적으로 커밋해도 됩니다.  
`a02-baseline.json`, `a02-after.json`, `a03-baseline.txt` 는 로컬 임시 파일입니다.
