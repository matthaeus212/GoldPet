package com.goldpet.domain.aiprofile.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
@Profile("local", "dev", "prod")
class GoogleImagenApiClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${google.ai.api-key:}") private val apiKey: String,
    @Value("\${google.ai.model:gemini-3.1-flash-image}") private val model: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    private val skipImagePatterns = listOf(".svg", "pet_none_img", "placehold.co", "placeholder")
    private val maxImageBytes = 10 * 1024 * 1024 // 10MB

    private val imageDownloadTemplate = RestTemplate(
        org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5_000)
            setReadTimeout(15_000)
        }
    )

    private fun shouldSkipImage(url: String): Boolean =
        skipImagePatterns.any { url.contains(it, ignoreCase = true) }

    private fun downloadImageAsBase64(imageUrl: String): Pair<String, String>? {
        return try {
            val response = imageDownloadTemplate.getForEntity(imageUrl, ByteArray::class.java)
            val bytes = response.body ?: return null
            if (bytes.size > maxImageBytes) {
                log.warn("Reference image too large: {}MB, skipping", bytes.size / (1024 * 1024))
                return null
            }
            val contentType = response.headers.contentType?.toString() ?: "image/jpeg"
            val mimeType = if (contentType.startsWith("image/")) contentType else "image/jpeg"
            val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
            log.info("Downloaded reference image: {}KB, mimeType={}", bytes.size / 1024, mimeType)
            Pair(mimeType, base64)
        } catch (e: Exception) {
            log.warn("Failed to download reference image from {}: {}", imageUrl, e.message)
            null
        }
    }

    /**
     * Gemini 이미지 생성 (generateContent 방식 - base64 이미지 반환)
     * sourceImageUrl이 제공되면 참조 이미지로 함께 전송
     */
    fun generateImage(prompt: String, sourceImageUrl: String? = null): ByteArray {
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

        val requestParts = mutableListOf<Map<String, Any>>()

        // 참조 이미지가 있으면 inline_data로 추가
        val imageIncluded = if (!sourceImageUrl.isNullOrBlank() && !shouldSkipImage(sourceImageUrl)) {
            val imageData = downloadImageAsBase64(sourceImageUrl)
            if (imageData != null) {
                requestParts.add(mapOf(
                    "inline_data" to mapOf(
                        "mime_type" to imageData.first,
                        "data" to imageData.second
                    )
                ))
                true
            } else false
        } else false

        // 텍스트 프롬프트 추가
        val textPrompt = if (imageIncluded) {
            "Generate a new artistic portrait based on this reference photo of the pet. Style: $prompt"
        } else {
            "Generate an image: $prompt"
        }
        requestParts.add(mapOf("text" to textPrompt))

        val body = objectMapper.writeValueAsString(
            mapOf(
                "contents" to listOf(
                    mapOf("parts" to requestParts)
                ),
                "generationConfig" to mapOf(
                    "responseModalities" to listOf("IMAGE"),
                    "imageConfig" to mapOf(
                        "aspectRatio" to "1:1",
                        "imageSize" to "512px"
                    )
                )
            )
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity(body, headers)

        log.info("Requesting image generation with model={}, prompt length={}, imageIncluded={}", model, prompt.length, imageIncluded)

        val response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            String::class.java
        )

        val responseStr = response.body
            ?: throw IllegalStateException("Empty response from Google AI API")

        val root = objectMapper.readTree(responseStr)

        if (root.has("error")) {
            val errorMsg = root.path("error").path("message").asText("Unknown error")
            throw IllegalStateException("Google AI API error: $errorMsg")
        }

        val candidates = root.path("candidates")
        if (!candidates.isArray || candidates.isEmpty) {
            throw IllegalStateException("No candidates in response")
        }

        val parts = candidates[0].path("content").path("parts")
        if (!parts.isArray || parts.isEmpty) {
            throw IllegalStateException("No parts in response content")
        }

        for (part in parts) {
            val inlineData = part.path("inlineData")
            if (!inlineData.isMissingNode) {
                val base64Data = inlineData.path("data").asText(null)
                    ?: throw IllegalStateException("Missing data in inlineData")
                val mimeType = inlineData.path("mimeType").asText("image/png")

                val imageBytes = java.util.Base64.getDecoder().decode(base64Data)
                log.info("Image generation complete, mimeType={}, size={}KB", mimeType, imageBytes.size / 1024)
                return imageBytes
            }
        }

        throw IllegalStateException("No image data found in response parts")
    }
}
