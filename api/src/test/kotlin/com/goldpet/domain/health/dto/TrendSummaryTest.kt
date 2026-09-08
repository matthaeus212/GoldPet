package com.goldpet.domain.health.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 변便 추세 요약 방향 판정(±0.5 데드존) 검증. */
class TrendSummaryTest {

    @Test
    fun `directionOf applies the half-point dead zone`() {
        assertThat(TrendSummary.directionOf(0.5)).isEqualTo("IMPROVING")
        assertThat(TrendSummary.directionOf(2.0)).isEqualTo("IMPROVING")
        assertThat(TrendSummary.directionOf(0.49)).isEqualTo("STABLE")
        assertThat(TrendSummary.directionOf(0.0)).isEqualTo("STABLE")
        assertThat(TrendSummary.directionOf(-0.49)).isEqualTo("STABLE")
        assertThat(TrendSummary.directionOf(-0.5)).isEqualTo("DECLINING")
        assertThat(TrendSummary.directionOf(-3.0)).isEqualTo("DECLINING")
    }
}
