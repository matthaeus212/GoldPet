# Runbook: Grafana 출시 대시보드 (4 패널 + KPI)

> 작성일: 2026-05-26
> 대상 Sprint: 0 (빈 대시보드 셋업) + Sprint 4 (실데이터 확인)
> 책임자: Ops Lead
> 관련 BLOCKER: §1-1 #9 (Sentry SDK) + §확장 테스트 계획 (Observability)

---

## 사전 조건

- Prometheus + Grafana 인스턴스 운영 중 (없다면 `deploy-dev/docker/grafana` 추가 필요).
- Spring Boot Actuator `/actuator/prometheus` 엔드포인트 노출 (`management.endpoints.web.exposure.include=prometheus` 추가).
- Loki (또는 CloudWatch Logs) 통한 로그 집계 (선택).

---

## 4 핵심 패널 (출시 게이트 KPI)

### Panel 1: `walks_complete_p95` — 산책 종료 API p95 latency
- **목표**: <800ms (closed test 합격선)
- **PromQL**:
  ```promql
  histogram_quantile(
    0.95,
    sum(rate(http_server_requests_seconds_bucket{uri="/api/v1/walks/{id}/complete",method="POST"}[5m])) by (le)
  ) * 1000
  ```
- **Alert**: >2000ms 5min 지속 → Slack #ops-alert.

### Panel 2: `gold_daily_reconciliation` — 골드 일별 정합성
- **목표**: SUM(gold_transactions) vs SUM(users.gold_balance) 차이 0
- **데이터 소스**: 일 1회 Quartz job이 `metrics.gold.reconciliation.diff` 메트릭 publish
- **PromQL**:
  ```promql
  metrics_gold_reconciliation_diff
  ```
- **Alert**: !=0 → Slack #payment-alert (immediate).

### Panel 3: `fcm_delivery_rate` — FCM 푸시 도달률
- **목표**: ≥95% (30초 내)
- **PromQL**:
  ```promql
  sum(rate(fcm_send_total{status="success"}[5m]))
  /
  sum(rate(fcm_send_total[5m])) * 100
  ```
- **Alert**: <90% 10min 지속 → Slack #ops-alert.

### Panel 4: `refresh_token_rotation_count` — JWT rotation 빈도 + reuse detection
- **목표**: reuse detection 발생 시 즉시 가시화
- **PromQL**:
  ```promql
  # 분당 rotation 횟수
  sum(rate(auth_refresh_rotation_total[1m]))
  # reuse detection 발생 횟수 (별도 카운터)
  sum(increase(auth_refresh_reuse_detected_total[5m]))
  ```
- **Alert**: reuse_detected > 0 (any) → Slack #security-alert (immediate, P0).

---

## 보조 패널 (운영 가시성)

| 패널명 | PromQL 또는 데이터 소스 | 목표 |
|---|---|---|
| RDS CPU | CloudWatch `AWS/RDS/CPUUtilization` | <70% |
| RDS Connections | CloudWatch `AWS/RDS/DatabaseConnections` | <80% of max |
| API 5xx rate | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100` | <0.5% |
| Sentry critical events | Sentry API integration plugin | 0건 |
| Crash-free sessions | Firebase Crashlytics export → Prometheus | ≥99.5% |
| ANR rate (Android) | Play Console Vitals export | <0.47% |
| 결제 성공률 (Sprint 4+) | `gold_transaction_total{status="success"} / gold_transaction_total * 100` | ≥98% |

---

## Sprint 0 작업 (오늘)

1. Grafana 인스턴스 접속 → 신규 Dashboard "GoldPet Launch KPI" 생성.
2. 위 4 핵심 패널 + 7 보조 패널을 **빈 PromQL placeholder**로 생성 (실데이터는 메트릭 publish 후 채워짐).
3. Dashboard JSON export → `deploy-dev/grafana/dashboards/launch-kpi.json` 으로 git commit.
4. 4 alert rule 등록 (#ops-alert / #payment-alert / #security-alert Slack 채널 webhook).

---

## Sprint 1 작업 (메트릭 publish 통합)

### Backend (`api/`) — Micrometer 카운터 추가

```kotlin
// api/src/main/kotlin/com/goldpet/common/metrics/AppMetrics.kt
@Component
class AppMetrics(private val registry: MeterRegistry) {
    val refreshRotationCounter: Counter = Counter.builder("auth_refresh_rotation_total").register(registry)
    val refreshReuseDetectedCounter: Counter = Counter.builder("auth_refresh_reuse_detected_total").register(registry)
    val fcmSendCounter: (status: String) -> Counter = { status ->
        Counter.builder("fcm_send_total").tag("status", status).register(registry)
    }
    val goldReconciliationDiffGauge: AtomicLong = AtomicLong(0).also {
        Gauge.builder("metrics_gold_reconciliation_diff", it, AtomicLong::get).register(registry)
    }
}
```

### Quartz job — gold reconciliation
```kotlin
// api/src/main/kotlin/com/goldpet/domain/gold/job/GoldReconciliationJob.kt
@Component
class GoldReconciliationJob(
    private val txRepo: GoldTransactionRepository,
    private val userRepo: UserRepository,
    private val metrics: AppMetrics,
) {
    @Scheduled(cron = "0 0 1 * * *")  // 매일 01:00 KST
    fun reconcile() {
        val txSum = txRepo.sumAllAmounts()
        val balanceSum = userRepo.sumAllGoldBalances()
        val diff = balanceSum - txSum
        metrics.goldReconciliationDiffGauge.set(diff)
        if (diff != 0L) log.error("Gold reconciliation diff: $diff")
    }
}
```

---

## Closed Test 기간 운영 (Sprint 5-6)

- **일일 09:00 KPI 리뷰 미팅**: Grafana 대시보드 + Sentry 콘솔 화면 공유.
- KPI 미달 (예: p95 >800ms 1일 이상 지속) → Hot-fix sprint 발동.
- KPI 2일 연속 미달 → 출시 연기 의사결정 (책임자: Backend Lead + Ops Lead 공동).

---

## 변경 이력
- 2026-05-26: 최초 작성 (Sprint 0 빈 셋업 + Sprint 1 메트릭 통합 runbook).
