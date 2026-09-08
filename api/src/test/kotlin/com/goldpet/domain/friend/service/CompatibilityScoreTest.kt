package com.goldpet.domain.friend.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * W1 Compatibility Scoring — 순수 스코어 함수 단위테스트 (플랜 §2.1 / §4).
 * 가중치는 기본값(0.40/0.25/0.20/0.15) 사용.
 */
class CompatibilityScoreTest {

    private val weights = CompatibilityWeights(
        distance = 0.40,
        interest = 0.25,
        hobby = 0.20,
        temperament = 0.15
    )

    @Test
    fun `identical distance - 100 percent interest match scores higher than 0 percent`() {
        val radius = 5000.0
        val distance = 1000.0 // 두 후보 동일 거리

        val fullMatch = computeCompatibilityScore(
            distanceMeters = distance,
            radiusMeters = radius,
            myInterests = setOf("camping", "coffee"),
            theirInterests = setOf("camping", "coffee"),
            myHobbies = emptySet(),
            theirHobbies = emptySet(),
            myTemperament = emptyList(),
            theirTemperament = emptyList(),
            weights = weights
        )
        val noMatch = computeCompatibilityScore(
            distanceMeters = distance,
            radiusMeters = radius,
            myInterests = setOf("camping", "coffee"),
            theirInterests = setOf("gaming", "movie"),
            myHobbies = emptySet(),
            theirHobbies = emptySet(),
            myTemperament = emptyList(),
            theirTemperament = emptyList(),
            weights = weights
        )

        assertThat(fullMatch).isGreaterThan(noMatch)
        // 동일거리이므로 차이는 interest 항(0.25 * 1.0)에서만 발생.
        assertThat(fullMatch - noMatch).isEqualTo(0.25)
    }

    @Test
    fun `empty interest and hobby sets are jaccard-safe (zero, no divide-by-zero)`() {
        val score = computeCompatibilityScore(
            distanceMeters = 0.0,
            radiusMeters = 5000.0,
            myInterests = emptySet(),
            theirInterests = emptySet(),
            myHobbies = emptySet(),
            theirHobbies = emptySet(),
            myTemperament = emptyList(),
            theirTemperament = emptyList(),
            weights = weights
        )
        // distance_norm = 1.0, 나머지 항 = 0 → 0.40
        assertThat(score).isEqualTo(0.40)
    }

    @Test
    fun `empty temperament tags do not divide by zero`() {
        assertThat(temperamentOverlap(emptyList(), emptyList())).isEqualTo(0.0)
        assertThat(temperamentOverlap(listOf("calm"), emptyList())).isEqualTo(0.0)
        assertThat(temperamentOverlap(emptyList(), listOf("calm"))).isEqualTo(0.0)
    }

    @Test
    fun `temperament overlap is matches over max tag count`() {
        // 일치 1개("calm"), max(2,3)=3 → 1/3
        val overlap = temperamentOverlap(
            listOf("calm", "shy"),
            listOf("calm", "active", "playful")
        )
        assertThat(overlap).isEqualTo(1.0 / 3.0)
    }

    @Test
    fun `jaccard of disjoint sets is zero and identical sets is one`() {
        assertThat(jaccard(setOf("a"), setOf("b"))).isEqualTo(0.0)
        assertThat(jaccard(setOf("a", "b"), setOf("a", "b"))).isEqualTo(1.0)
        assertThat(jaccard(emptySet(), emptySet())).isEqualTo(0.0)
        // 부분 일치: ∩={a}, ∪={a,b,c} → 1/3
        assertThat(jaccard(setOf("a", "b"), setOf("a", "c"))).isEqualTo(1.0 / 3.0)
    }

    @Test
    fun `boosted candidate outranks equal-score non-boosted candidate`() {
        val bonus = 0.15
        val base = 0.40

        val nonBoosted = applyBoostBonus(base, isBoosted = false, bonus = bonus)
        val boosted = applyBoostBonus(base, isBoosted = true, bonus = bonus)
        assertThat(boosted).isGreaterThan(nonBoosted)

        // 실제 getCompatibleFriends 정렬 경로 미러(동점 base → boosted 상위).
        val ranked = listOf(1L to base, 2L to base)
            .map { (id, b) -> id to applyBoostBonus(b, isBoosted = id == 2L, bonus = bonus) }
            .sortedByDescending { it.second }
        assertThat(ranked.first().first).isEqualTo(2L)
    }

    @Test
    fun `boost bonus clamps final score at 1 and is no-op when not boosted`() {
        assertThat(applyBoostBonus(0.95, isBoosted = true, bonus = 0.15)).isEqualTo(1.0)
        assertThat(applyBoostBonus(0.40, isBoosted = false, bonus = 0.15)).isEqualTo(0.40)
    }

    @Test
    fun `distance_norm clamps at radius and guards radius zero`() {
        // d >= radius → distance_norm = 0 → 모든 취향 0 이면 score 0
        val beyondRadius = computeCompatibilityScore(
            distanceMeters = 9999.0,
            radiusMeters = 5000.0,
            myInterests = emptySet(),
            theirInterests = emptySet(),
            myHobbies = emptySet(),
            theirHobbies = emptySet(),
            myTemperament = emptyList(),
            theirTemperament = emptyList(),
            weights = weights
        )
        assertThat(beyondRadius).isEqualTo(0.0)

        // radius<=0 가드: distance_norm = 0
        val zeroRadius = computeCompatibilityScore(
            distanceMeters = 100.0,
            radiusMeters = 0.0,
            myInterests = emptySet(),
            theirInterests = emptySet(),
            myHobbies = emptySet(),
            theirHobbies = emptySet(),
            myTemperament = emptyList(),
            theirTemperament = emptyList(),
            weights = weights
        )
        assertThat(zeroRadius).isEqualTo(0.0)
    }
}
