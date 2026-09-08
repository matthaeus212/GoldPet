package com.goldpet.domain.pet.service

import com.goldpet.domain.pet.dto.CreatePetRequest
import com.goldpet.domain.pet.dto.UpdatePetRequest
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.pet.entity.PetBreed
import com.goldpet.domain.pet.entity.PetSpecies
import com.goldpet.domain.pet.repository.PetAttributeRepository
import com.goldpet.domain.pet.repository.PetBreedRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.pet.repository.PetSpeciesRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class PetServiceTest {

    @Mock
    private lateinit var petRepository: PetRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var petSpeciesRepository: PetSpeciesRepository

    @Mock
    private lateinit var petBreedRepository: PetBreedRepository

    @Mock
    private lateinit var petAttributeRepository: PetAttributeRepository

    @Mock
    private lateinit var badgeAwardService: com.goldpet.domain.gamification.service.BadgeAwardService

    @Mock
    private lateinit var fileAttachmentLookupService: com.goldpet.domain.common.service.FileAttachmentLookupService

    private lateinit var petService: PetService

    private lateinit var testUser: User
    private lateinit var testSpecies: PetSpecies
    private lateinit var testBreed: PetBreed

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        petService = PetService(
            petRepository,
            userRepository,
            petSpeciesRepository,
            petBreedRepository,
            petAttributeRepository,
            badgeAwardService,
            fileAttachmentLookupService
        )

        testUser = createTestUser(1L, "testuser", "테스트유저")

        testSpecies = PetSpecies(
            id = 1,
            code = "DOG",
            name = "강아지",
            description = "개과 동물"
        )

        testBreed = PetBreed(
            id = 1,
            petSpecies = testSpecies,
            name = "푸들",
            description = "곱슬 털을 가진 견종",
            category = "소형견"
        )
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    @Test
    fun `createPet should create pet with all fields`() {
        // Given
        val request = CreatePetRequest(
            name = "뽀삐",
            speciesId = 1,
            breedId = 1,
            gender = "M",
            birthDate = LocalDate.of(2020, 5, 15),
            weightKg = 5.5,
            isNeutered = true,
            profileImageUrl = "https://example.com/pet.jpg",
            profileImageUrls = listOf("https://example.com/pet1.jpg", "https://example.com/pet2.jpg"),
            temperamentTags = "활발함,친근함"
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(petSpeciesRepository.findById(1)).thenReturn(Optional.of(testSpecies))
        whenever(petBreedRepository.findById(1)).thenReturn(Optional.of(testBreed))
        whenever(petRepository.save(any<Pet>())).thenAnswer { invocation ->
            val pet = invocation.getArgument<Pet>(0)
            Pet(
                id = 1L,
                owner = pet.owner,
                name = pet.name,
                species = pet.species,
                breed = pet.breed,
                gender = pet.gender,
                birthDate = pet.birthDate,
                weightKg = pet.weightKg,
                isNeutered = pet.isNeutered,
                profileImageUrl = pet.profileImageUrl,
                temperamentTags = pet.temperamentTags
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = petService.createPet(1L, request)

        // Then
        assertNotNull(result)
        assertEquals("뽀삐", result.name)
        assertEquals(1, result.speciesId)
        assertEquals("강아지", result.species)
        assertEquals(1, result.breedId)
        assertEquals("푸들", result.breed)
        assertEquals("M", result.gender)
        assertEquals(5.5, result.weightKg)
        assertTrue(result.isNeutered == true)
    }

    @Test
    fun `createPet should throw exception when user not found`() {
        // Given
        val request = CreatePetRequest(
            name = "뽀삐",
            speciesId = 1,
            breedId = null,
            gender = null,
            birthDate = null,
            weightKg = null,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        )
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            petService.createPet(999L, request)
        }
    }

    @Test
    fun `createPet should throw exception when species not found`() {
        // Given
        val request = CreatePetRequest(
            name = "뽀삐",
            speciesId = 999,
            breedId = null,
            gender = null,
            birthDate = null,
            weightKg = null,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(petSpeciesRepository.findById(999)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            petService.createPet(1L, request)
        }
    }

    @Test
    fun `getPet should return pet when exists`() {
        // Given
        val pet = Pet(
            id = 1L,
            owner = testUser,
            name = "뽀삐",
            species = testSpecies,
            breed = testBreed,
            gender = "M",
            birthDate = LocalDate.of(2020, 5, 15),
            weightKg = 5.5,
            isNeutered = true,
            profileImageUrl = "https://example.com/pet.jpg",
            temperamentTags = "활발함"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(petRepository.findById(1L)).thenReturn(Optional.of(pet))

        // When
        val result = petService.getPet(1L)

        // Then
        assertNotNull(result)
        assertEquals("뽀삐", result.name)
        assertEquals(1L, result.id)
    }

    @Test
    fun `getPet should throw exception when pet not found`() {
        // Given
        whenever(petRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            petService.getPet(999L)
        }
    }

    @Test
    fun `getPetsByOwner should return list of pets`() {
        // Given
        val pet1 = Pet(
            id = 1L,
            owner = testUser,
            name = "뽀삐",
            species = testSpecies,
            breed = testBreed,
            gender = "M",
            birthDate = null,
            weightKg = null,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val pet2 = Pet(
            id = 2L,
            owner = testUser,
            name = "초코",
            species = testSpecies,
            breed = testBreed,
            gender = "F",
            birthDate = null,
            weightKg = null,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(petRepository.findByOwnerId(1L)).thenReturn(listOf(pet1, pet2))

        // When
        val result = petService.getPetsByOwner(1L)

        // Then
        assertEquals(2, result.size)
        assertEquals("뽀삐", result[0].name)
        assertEquals("초코", result[1].name)
    }

    @Test
    fun `updatePet should update pet fields`() {
        // Given
        val pet = Pet(
            id = 1L,
            owner = testUser,
            name = "뽀삐",
            species = testSpecies,
            breed = testBreed,
            gender = "M",
            birthDate = null,
            weightKg = 5.5,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        val request = UpdatePetRequest(
            name = "뽀삐수정",
            speciesId = null,
            breedId = null,
            gender = null,
            birthDate = null,
            weightKg = 6.0,
            isNeutered = true,
            profileImageUrl = null,
            temperamentTags = null
        )

        whenever(petRepository.findById(1L)).thenReturn(Optional.of(pet))
        whenever(petRepository.save(any<Pet>())).thenReturn(pet)

        // When
        val result = petService.updatePet(1L, 1L, request)

        // Then
        assertEquals("뽀삐수정", pet.name)
        assertEquals(6.0, pet.weightKg)
        assertTrue(pet.isNeutered == true)
    }

    @Test
    fun `deletePet should delete pet`() {
        // Given
        val pet = Pet(
            id = 1L,
            owner = testUser,
            name = "뽀삐",
            species = testSpecies,
            breed = testBreed,
            gender = "M",
            birthDate = null,
            weightKg = null,
            isNeutered = null,
            profileImageUrl = null,
            temperamentTags = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(petRepository.findById(1L)).thenReturn(Optional.of(pet))

        // When & Then (no exception should be thrown)
        assertDoesNotThrow {
            petService.deletePet(1L, 1L)
        }
    }

    @Test
    fun `getAllSpecies should return list`() {
        // Given
        val species1 = PetSpecies(id = 1, code = "DOG", name = "강아지", description = null)
        val species2 = PetSpecies(id = 2, code = "CAT", name = "고양이", description = null)
        whenever(petSpeciesRepository.findAll()).thenReturn(listOf(species1, species2))

        // When
        val result = petService.getAllSpecies()

        // Then
        assertEquals(2, result.size)
        assertEquals("DOG", result[0].code)
        assertEquals("CAT", result[1].code)
    }

    @Test
    fun `getBreedsBySpecies should return breeds for species`() {
        // Given
        val breed1 = PetBreed(id = 1, petSpecies = testSpecies, name = "푸들", description = null)
        val breed2 = PetBreed(id = 2, petSpecies = testSpecies, name = "말티즈", description = null)
        whenever(petBreedRepository.findAllByPetSpeciesId(1)).thenReturn(listOf(breed1, breed2))

        // When
        val result = petService.getBreedsBySpecies(1)

        // Then
        assertEquals(2, result.size)
        assertEquals("푸들", result[0].name)
        assertEquals("말티즈", result[1].name)
    }
}
