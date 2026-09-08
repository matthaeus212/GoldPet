package com.goldpet.domain.health.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.pet.entity.PetSpecies
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.entity.StoolAnalysis
import com.goldpet.domain.walk.repository.StoolAnalysisRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class StoolAnalysisServiceTest {

    @Mock private lateinit var stoolAnalysisRepository: StoolAnalysisRepository
    @Mock private lateinit var petRepository: PetRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var walkSpotRepository: WalkSpotRepository
    @Mock private lateinit var applicationEventPublisher: ApplicationEventPublisher
    @Mock private lateinit var systemSettingService: SystemSettingService
    @Mock private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var stoolAnalysisService: StoolAnalysisService

    @Test
    fun `getAnalysis_shouldReturnResponse_whenAnalysisBelongsToUser`() {
        val userId = 1L
        val user = User(
            id = userId, email = "health@test.com", oauthProvider = "GOOGLE", oauthId = "g2",
            username = null, password = null, nickname = "HealthUser",
            name = null, birthDate = null, phoneNumber = null,
            gender = null, birthYear = null, mainLocationText = null,
            mainLocationGeom = null, profileImageUrl = null
        )
        val species = PetSpecies(code = "DOG", name = "Dog", description = null)
        val pet = Pet(
            id = 1L, owner = user, name = "Buddy", species = species,
            breed = null, gender = null, birthDate = null, weightKg = null,
            isNeutered = null, profileImageUrl = null, temperamentTags = null
        )
        val analysis = StoolAnalysis(
            id = 10L, pet = pet, user = user,
            imageUrl = "https://s3.test/stool/img.jpg",
            status = AnalysisStatus.COMPLETED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        `when`(stoolAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis))

        val result = stoolAnalysisService.getAnalysis(10L, userId)

        assertThat(result).isNotNull
        assertThat(result.id).isEqualTo(10L)
        assertThat(result.status).isEqualTo(AnalysisStatus.COMPLETED)
        assertThat(result.imageUrl).isEqualTo("https://s3.test/stool/img.jpg")
        verify(stoolAnalysisRepository).findById(10L)
    }
}
