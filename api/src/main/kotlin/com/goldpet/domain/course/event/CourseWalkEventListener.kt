package com.goldpet.domain.course.event

import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.walk.event.WalkCompletedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CourseWalkEventListener(
    private val walkCourseRepository: WalkCourseRepository
) {
    // AFTER_COMMIT 단계에서는 원본 트랜잭션이 이미 종료되어 있어, @Modifying 쿼리 실행 시
    // 새 트랜잭션이 필요. Spring 은 @TransactionalEventListener 와 @Transactional 결합 시
    // REQUIRES_NEW / NOT_SUPPORTED 만 허용.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWalkCompleted(event: WalkCompletedEvent) {
        val courseId = event.followedCourseId ?: return
        walkCourseRepository.incrementWalkCount(courseId)
    }
}
