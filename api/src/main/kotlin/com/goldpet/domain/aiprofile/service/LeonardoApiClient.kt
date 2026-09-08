package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.common.util.ExternalJson
import com.goldpet.domain.common.util.ExternalJson.objectAtOrNull
import com.goldpet.domain.common.util.ExternalJson.objectListAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringAtOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class GenerationResult(
    val generationId: String,
    val status: String,          // PENDING, PROCESSING, COMPLETE, FAILED
    val imageUrl: String? = null
)

@Component
@Profile("local", "dev", "prod")
class LeonardoApiClient(
    private val restTemplate: RestTemplate,
    @Value("\${leonardo.api-key:}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl = "https://cloud.leonardo.ai/api/rest/v1"

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set("Authorization", "Bearer $apiKey")
        contentType = MediaType.APPLICATION_JSON
    }

    /**
     * Downloads the source image from imageUrl and uploads it to Leonardo as an init image.
     * Returns the initImageId to use in generation requests.
     */
    fun uploadInitImage(imageUrl: String): String {
        // Upload to Leonardo via URL-based init image endpoint
        val body = mapOf("url" to imageUrl)
        val entity = HttpEntity(body, authHeaders())

        val response = restTemplate.exchange(
            "$baseUrl/init-image",
            HttpMethod.POST,
            entity,
            Map::class.java
        )

        // STYLE-002: 언체크드 캐스트 대신 안전 파서.
        val responseBody = ExternalJson.objectOrNull(response.body)
            ?: throw IllegalStateException("Empty response from Leonardo init-image")
        val uploadInitImage = responseBody.objectAtOrNull("uploadInitImage")
            ?: throw IllegalStateException("Missing uploadInitImage in response: $responseBody")

        return uploadInitImage["id"] as? String
            ?: throw IllegalStateException("Missing id in uploadInitImage: $uploadInitImage")
    }

    /**
     * Creates an AI generation request.
     * Returns the generationId.
     */
    fun createGeneration(prompt: String, initImageId: String? = null, presetStyle: String? = null, negativePrompt: String? = null): String {
        val bodyMap = mutableMapOf<String, Any>(
            "height" to 512,
            "width" to 512,
            "num_images" to 1,
            "promptMagic" to false,
            "public" to false,
            "prompt" to prompt
        )

        if (initImageId != null) {
            bodyMap["initImageId"] = initImageId
            bodyMap["initStrength"] = 0.4
        }

        if (presetStyle != null) {
            bodyMap["presetStyle"] = presetStyle
        }

        if (!negativePrompt.isNullOrBlank()) {
            bodyMap["negative_prompt"] = negativePrompt
        }

        val entity = HttpEntity(bodyMap, authHeaders())
        val response = restTemplate.exchange(
            "$baseUrl/generations",
            HttpMethod.POST,
            entity,
            Map::class.java
        )

        val responseBody = ExternalJson.objectOrNull(response.body)
            ?: throw IllegalStateException("Empty response from Leonardo generations")
        val sdGenerationJob = responseBody.objectAtOrNull("sdGenerationJob")
            ?: throw IllegalStateException("Missing sdGenerationJob in response: $responseBody")

        return sdGenerationJob["generationId"] as? String
            ?: throw IllegalStateException("Missing generationId in sdGenerationJob: $sdGenerationJob")
    }

    /**
     * Polls for the status of a generation. Returns GenerationResult with status and imageUrl if complete.
     */
    fun getGeneration(generationId: String): GenerationResult {
        val entity = HttpEntity<Void>(HttpHeaders().apply {
            set("Authorization", "Bearer $apiKey")
        })

        val response = restTemplate.exchange(
            "$baseUrl/generations/$generationId",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        val body = ExternalJson.objectOrNull(response.body)
            ?: throw IllegalStateException("Empty response from Leonardo get generation")
        val generation = body.objectAtOrNull("generations_by_pk")
            ?: throw IllegalStateException("Missing generations_by_pk in response: $body")

        val status = generation.stringAtOrNull("status") ?: "UNKNOWN"

        val imageUrl = if (status == "COMPLETE") {
            generation.objectListAtOrNull("generated_images")
                ?.firstOrNull()
                ?.stringAtOrNull("url")
        } else null

        return GenerationResult(
            generationId = generationId,
            status = status,
            imageUrl = imageUrl
        )
    }
}
