-- walk_spots 에 600px medium 변형 키 컬럼 추가.
--
-- ## 배경
-- iOS WKWebView 메모리 누적 완화를 위해 산책사진 스와이프 표시를 600px `_medium` 변형으로 낮췄다.
-- 그런데 WalkSpot 은 viewer/thumb 키만 저장하고 medium 키가 없어, viewer 키에서 `_viewer`→`_medium`
-- 으로 **파생**했다. 신규 사진은 업로드 시 FileService 가 medium 변형을 생성하므로 파생 키가 유효하나,
-- 구 사진(2026-02 이전)은 `WalkSpotBackfillWorker` 가 `_thumb`/`_viewer` 만 생성해 `_medium` 객체가
-- 없다 → 파생 URL 이 404 → 큰 사진 blank.
--
-- ## 조치
-- medium 키를 **저장**한다(파생 금지). 미생성 사진은 NULL 로 남아 응답의 imageUrlMedium 이 null 이 되고,
-- 클라이언트는 기존 `medium ?? viewer` 폴백으로 자연스럽게 viewer 를 쓴다(404 요청 자체가 사라짐).
-- 이후 `WalkSpotBackfillWorker`(+ WalkSpotVariantSyncScheduler, 5분 주기)가 medium 변형을 생성하며
-- 이 컬럼을 채운다 → 전 사진에 메모리 이득이 확대된다.
--
-- ## 안전성
-- 단순 ADD COLUMN(nullable, 기본값 없음) — 즉시 완료되며 테이블 rewrite 없음. CONCURRENTLY 미사용
-- (V87 에서 Flyway 스키마-히스토리 트랜잭션과 CONCURRENTLY 가 자기 데드락을 일으킨 사고 이후 원칙).

ALTER TABLE walk_spots
    ADD COLUMN IF NOT EXISTS image_key_medium VARCHAR(512);
