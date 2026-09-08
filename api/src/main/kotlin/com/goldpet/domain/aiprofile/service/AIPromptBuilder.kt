package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.pet.entity.Pet
import org.springframework.stereotype.Component

@Component
class AIPromptBuilder(
    private val translationService: TranslationService
) {

    private val koreanRange = Regex("[\uAC00-\uD7A3]")

    private fun containsKorean(text: String): Boolean = koreanRange.containsMatchIn(text)

    private fun translateKorean(text: String): String =
        if (containsKorean(text)) translationService.translate(text) else text

    // Korean species to English
    private val speciesEnglish = mapOf(
        "강아지" to "dog", "개" to "dog",
        "고양이" to "cat",
        "DOG" to "dog", "CAT" to "cat"
    )

    // Common Korean breed names to English
    private val breedEnglish = mapOf(
        // Dogs
        "골든 리트리버" to "Golden Retriever",
        "래브라도 리트리버" to "Labrador Retriever",
        "푸들" to "Poodle",
        "토이 푸들" to "Toy Poodle",
        "미니어처 푸들" to "Miniature Poodle",
        "말티즈" to "Maltese",
        "시츄" to "Shih Tzu",
        "요크셔 테리어" to "Yorkshire Terrier",
        "포메라니안" to "Pomeranian",
        "치와와" to "Chihuahua",
        "비숑 프리제" to "Bichon Frise",
        "시바 이누" to "Shiba Inu",
        "시바견" to "Shiba Inu",
        "진돗개" to "Korean Jindo",
        "웰시 코기" to "Welsh Corgi",
        "코기" to "Welsh Corgi",
        "사모예드" to "Samoyed",
        "시베리안 허스키" to "Siberian Husky",
        "허스키" to "Siberian Husky",
        "보더 콜리" to "Border Collie",
        "불독" to "Bulldog",
        "프렌치 불독" to "French Bulldog",
        "잉글리시 불독" to "English Bulldog",
        "비글" to "Beagle",
        "닥스훈트" to "Dachshund",
        "슈나우저" to "Schnauzer",
        "미니어처 슈나우저" to "Miniature Schnauzer",
        "미니어처 핀셔" to "Miniature Pinscher",
        "도베르만" to "Doberman",
        "코카 스패니얼" to "Cocker Spaniel",
        "저먼 셰퍼드" to "German Shepherd",
        "셰퍼드" to "German Shepherd",
        "리트리버" to "Retriever",
        "스피츠" to "Spitz",
        "페키니즈" to "Pekingese",
        "달마시안" to "Dalmatian",
        "보스턴 테리어" to "Boston Terrier",
        "잭 러셀 테리어" to "Jack Russell Terrier",
        "웨스트 하이랜드 화이트 테리어" to "West Highland White Terrier",
        "삽살개" to "Korean Sapsali",
        "풍산개" to "Korean Pungsan",
        "믹스견" to "mixed breed dog",
        "혼합견" to "mixed breed dog",
        // Cats
        "러시안 블루" to "Russian Blue",
        "페르시안" to "Persian",
        "페르시안 고양이" to "Persian cat",
        "스코티시 폴드" to "Scottish Fold",
        "먼치킨" to "Munchkin",
        "랙돌" to "Ragdoll",
        "브리티시 숏헤어" to "British Shorthair",
        "아메리칸 숏헤어" to "American Shorthair",
        "벵갈" to "Bengal",
        "벵갈 고양이" to "Bengal cat",
        "샴" to "Siamese",
        "메인쿤" to "Maine Coon",
        "노르웨이 숲" to "Norwegian Forest Cat",
        "노르웨이 숲 고양이" to "Norwegian Forest Cat",
        "터키시 앙고라" to "Turkish Angora",
        "아비시니안" to "Abyssinian",
        "코리안 숏헤어" to "Korean Shorthair",
        "코숏" to "Korean Shorthair",
        "엑조틱 숏헤어" to "Exotic Shorthair",
        "버만" to "Birman",
        "소말리" to "Somali",
        "스핑크스" to "Sphynx",
        "혼합묘" to "mixed breed cat",
        "믹스묘" to "mixed breed cat"
    )

    // Temperament tag values to English personality descriptors
    private val temperamentEnglish = mapOf(
        // Activity
        "ENERGIZER" to "very energetic",
        "ACTIVE" to "active and playful",
        "NORMAL" to "moderately active",
        "CALM" to "calm and gentle",
        "STATIC" to "very calm and still",
        // Friendliness
        "SOCIAL" to "very friendly and social",
        "PEOPLE_ONLY" to "people-oriented",
        "SELECTIVE" to "selectively social",
        "SHY" to "shy and reserved",
        "INDEPENDENT" to "independent",
        // Training
        "PROFESSIONAL" to "professionally trained",
        "BASIC" to "well-mannered",
        "TRAINING" to "in training",
        "FREE_SPIRIT" to "free-spirited",
        // Barking
        "VOCAL" to "expressive",
        "NECESSARY" to "barks when needed",
        "QUIET" to "quiet"
    )

    /**
     * Build a detailed English prompt for Google Imagen based on pet info and style.
     */
    fun buildPrompt(pet: Pet?, stylePrompt: String?, presetId: String?, petType: String? = null): String {
        val parts = mutableListOf<String>()

        // Style mapping for Imagen
        val styleDescription = when (presetId) {
            "ILLUSTRATION" -> "warm cute storybook illustration style"
            "ANIME_GENERAL" -> "bright vivid anime style"
            "PHOTOGRAPHY" -> "professional studio pet photography, natural lighting"
            "CINEMATIC" -> "cinematic dramatic lighting, movie poster style"
            "WATERCOLOR" -> "soft delicate watercolor painting style"
            "OIL_PAINTING" -> "classical elegant oil painting style"
            else -> "Pixar 3D animation style"
        }

        if (pet != null) {
            val speciesEn = speciesEnglish[pet.species.name]
                ?: speciesEnglish[pet.species.code]
                ?: translateKorean(pet.species.name)
            val breedEn = pet.breed?.name?.let { breedEnglish[it] ?: translateKorean(it) }
            val subject = if (breedEn != null) "$breedEn $speciesEn" else speciesEn
            parts.add("A beautiful portrait of a cute $subject")

            pet.gender?.let { gender ->
                val genderStr = when (gender.uppercase()) {
                    "MALE", "M" -> "male"
                    "FEMALE", "F" -> "female"
                    else -> null
                }
                genderStr?.let { parts.add(it) }
            }

            pet.temperamentTags?.takeIf { it.isNotBlank() }?.let { tags ->
                val descriptors = tags.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapNotNull { tag ->
                        val colonParts = tag.split(":")
                        if (colonParts.size == 2) {
                            val value = colonParts[1].trim()
                            temperamentEnglish[value] ?: value.lowercase().replace("_", " ")
                        } else null
                    }
                if (descriptors.isNotEmpty()) {
                    parts.add("with a ${descriptors.joinToString(" and ")} personality")
                }
            }

            if (!stylePrompt.isNullOrBlank()) {
                parts.add(translateKorean(stylePrompt))
            }

            parts.add(styleDescription)
            parts.add("high quality, detailed, adorable expression")
        } else {
            val animalType = when (petType?.lowercase()) {
                "dog" -> "dog"
                "cat" -> "cat"
                else -> "pet"
            }

            if (!stylePrompt.isNullOrBlank()) {
                parts.add("A beautiful portrait of a cute $animalType, ${translateKorean(stylePrompt)}")
            } else {
                parts.add("A beautiful portrait of a cute $animalType")
            }

            parts.add(styleDescription)
            parts.add("high quality, detailed, adorable expression")
        }

        return parts.joinToString(", ")
    }
}
