package com.goldpet.domain.pet.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.pet.entity.Pet
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreatePetRequest(
    @field:Size(max = 20, message = "반려동물 이름은 20자 이내여야 합니다")
    val name: String,
    val speciesId: Int,
    val breedId: Int?,
    val gender: String?,
    val birthDate: LocalDate?,
    @field:DecimalMin(value = "0.1", message = "Weight must be at least 0.1kg")
    @field:DecimalMax(value = "200.0", message = "Weight must be at most 200kg")
    val weightKg: Double?,
    val isNeutered: Boolean?,
    val profileImageUrl: String?,
    val profileImageUrls: List<String>? = emptyList(),
    val temperamentTags: String?,
)

data class UpdatePetRequest(
    @field:Size(max = 20, message = "반려동물 이름은 20자 이내여야 합니다")
    val name: String?,
    val speciesId: Int?,
    val breedId: Int?,
    val gender: String?,
    val birthDate: LocalDate?,
    @field:DecimalMin(value = "0.1", message = "Weight must be at least 0.1kg")
    @field:DecimalMax(value = "200.0", message = "Weight must be at most 200kg")
    val weightKg: Double?,
    val isNeutered: Boolean?,
    val profileImageUrl: String?,
    val profileImageUrls: List<String>? = emptyList(),
    val temperamentTags: String?,
)

data class PetResponse(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val speciesId: Int,
    val breedId: Int?,
    val species: String,
    val breed: String?,
    val gender: String?,
    val birthDate: LocalDate?,
    val weightKg: Double?,
    @get:JsonProperty("isNeutered")
    val isNeutered: Boolean?,
    val profileImageUrl: String?,
    val profileImageUrls: List<String>,
    /** T1-1.2: viewer variant (1600px). */
    val profileImageUrlViewer: String? = null,
    /** T1-1.2: thumbnail variant (200px). */
    val profileImageUrlThumbnail: String? = null,
    /** T1-1.2: viewer variant list matching profileImageUrls order. */
    val profileImageUrlsViewer: List<String>? = null,
    /** T1-1.2: thumbnail variant list matching profileImageUrls order. */
    val profileImageUrlsThumbnail: List<String>? = null,
    val temperamentTags: List<String>,
) {
    companion object {
        /**
         * @param variantResolver T1-1.1 FileAttachmentLookupService. 리스트 경로에서는 호출 전에
         *        `variantResolver.batchLookup(allUrls)` 로 Caffeine warm 필수.
         */
        fun from(pet: Pet, variantResolver: FileAttachmentLookupService? = null): PetResponse {
            val primary = pet.profileImageUrl
            val urls = pet.profileImages.map { it.imageUrl }
            return PetResponse(
                id = pet.id,
                ownerId = pet.owner.id,
                name = pet.name,
                speciesId = pet.species.id,
                breedId = pet.breed?.id,
                species = pet.species.name,
                breed = pet.breed?.name,
                gender = pet.gender,
                birthDate = pet.birthDate,
                weightKg = pet.weightKg,
                isNeutered = pet.isNeutered,
                profileImageUrl = primary,
                profileImageUrls = urls,
                profileImageUrlViewer = variantResolver?.viewerUrlFor(primary),
                profileImageUrlThumbnail = variantResolver?.thumbnailUrlFor(primary),
                profileImageUrlsViewer = variantResolver?.let { r -> urls.map { r.viewerUrlFor(it) ?: it } },
                profileImageUrlsThumbnail = variantResolver?.let { r -> urls.map { r.thumbnailUrlFor(it) ?: it } },
                temperamentTags = pet.temperamentTags?.split(",") ?: emptyList()
            )
        }
    }
}

data class SimplePetResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    /** T1-1.2: thumbnail variant (200px). 리스트 카드에서 우선 사용. */
    val profileImageUrlThumbnail: String? = null,
) {
    companion object {
        fun from(pet: Pet, variantResolver: FileAttachmentLookupService? = null): SimplePetResponse {
            return SimplePetResponse(
                id = pet.id,
                name = pet.name,
                profileImageUrl = pet.profileImageUrl,
                profileImageUrlThumbnail = variantResolver?.thumbnailUrlFor(pet.profileImageUrl)
            )
        }
    }
}
