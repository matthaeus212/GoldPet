package com.goldpet.domain.walk.event

import org.springframework.context.ApplicationEvent

class WalkCompletedEvent(
    source: Any,
    val walkId: Long,
    val userId: Long,
    val followedCourseId: Long?,
    val distanceKm: Double,
    val goldReward: Int
) : ApplicationEvent(source)
