// 좋아요/매칭 FCM 알림을 트랜잭션 커밋 후 비동기로 발송하기 위한 도메인 이벤트
package com.goldpet.domain.friend.event

import com.goldpet.domain.notification.dto.CreateNotificationRequest

/**
 * PERF-006 — 좋아요/매칭 알림 발송을 DB 트랜잭션 경계 밖으로 분리하는 이벤트.
 *
 * `LikeService.likeUser`(@Transactional)가 저장 직후 블로킹 FCM send() 를 동기 호출하면
 * DB 쓰기 + Firebase 왕복이 한 트랜잭션에서 커넥션을 점유한다. 이 이벤트를 발행하면
 * [com.goldpet.domain.friend.event.LikeNotificationEventListener] 가 AFTER_COMMIT + @Async 로 받아
 * 커밋 이후 별도 스레드에서 알림을 저장/발송한다.
 *
 * 전달 시맨틱: 커밋 후 발송이므로 like 트랜잭션이 롤백되면 알림 미발송(올바름). 발송 실패는
 * 리스너에서 흡수되어 이미 커밋된 like 트랜잭션에 영향을 주지 않는다. 메시지 문구는 발행 시점에
 * 완성해 [request] 에 담으므로(발신자 닉네임 등 이미 로드됨) 타이밍만 바뀌고 내용은 불변이다.
 */
data class LikeNotificationEvent(
    val request: CreateNotificationRequest
)
