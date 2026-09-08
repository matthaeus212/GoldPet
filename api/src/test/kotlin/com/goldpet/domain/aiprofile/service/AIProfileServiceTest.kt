package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.repository.AIProfileRequestRepository
import com.goldpet.domain.aiprofile.repository.AIStyleRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AIProfileServiceTest {

    @Mock private lateinit var aiRequestRepository: AIProfileRequestRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var petRepository: PetRepository
    @Mock private lateinit var goldService: GoldService
    @Mock private lateinit var aiGenerationClient: AIGenerationClient
    @Mock private lateinit var aiStyleRepository: AIStyleRepository
    @Mock private lateinit var systemSettingService: SystemSettingService

    @InjectMocks
    private lateinit var aiProfileService: AIProfileService

    @Test
    fun `getPendingCount_shouldReturnSumOfPendingAndProcessing`() {
        `when`(aiRequestRepository.countByUserIdAndStatus(1L, AIRequestStatus.PENDING)).thenReturn(2L)
        `when`(aiRequestRepository.countByUserIdAndStatus(1L, AIRequestStatus.PROCESSING)).thenReturn(1L)

        val count = aiProfileService.getPendingCount(1L)

        assertThat(count).isEqualTo(3L)
        verify(aiRequestRepository).countByUserIdAndStatus(1L, AIRequestStatus.PENDING)
        verify(aiRequestRepository).countByUserIdAndStatus(1L, AIRequestStatus.PROCESSING)
    }
}
