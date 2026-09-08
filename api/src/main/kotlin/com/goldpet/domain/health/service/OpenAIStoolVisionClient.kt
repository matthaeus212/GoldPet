package com.goldpet.domain.health.service

import com.goldpet.domain.common.util.ExternalJson
import com.goldpet.domain.common.util.ExternalJson.intAtOrNull
import com.goldpet.domain.common.util.ExternalJson.objectAtOrNull
import com.goldpet.domain.common.util.ExternalJson.objectListAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringListAtOrNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.domain.health.dto.PetContextDto
import com.goldpet.domain.health.dto.StoolAnalysisResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
@Profile("local", "dev", "prod")
class OpenAIStoolVisionClient(
    @Qualifier("openaiRestTemplate") private val restTemplate: RestTemplate,
    @Value("\${openai.api-key:}") private val apiKey: String,
    @Value("\${S3_PUBLIC_ENDPOINT:http://localhost:9100}") private val publicEndpoint: String,
    @Value("\${S3_PUBLIC_BUCKET_NAME:goldpet-public}") private val publicBucketName: String,
    private val promptBuilder: StoolAnalysisPromptBuilder,
    private val objectMapper: ObjectMapper
) : StoolVisionClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun analyze(imageUrl: String, petInfo: PetContextDto): StoolAnalysisResult {
        return try {
            callOpenAI(imageUrl, petInfo)
        } catch (e: RestClientException) {
            // DNS 실패(UnknownHostException) 등 일시적 네트워크 이슈 회복 시간 확보용 짧은 backoff.
            log.warn("OpenAI call failed, retrying once after 500ms: {}", e.message)
            try {
                Thread.sleep(500)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ie
            }
            callOpenAI(imageUrl, petInfo)
        }
    }

    private fun resolveImageUrl(imageUrl: String): String {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl
        return "$publicEndpoint/$publicBucketName/$imageUrl"
    }

    private fun callOpenAI(imageUrl: String, petInfo: PetContextDto): StoolAnalysisResult {
        val fullImageUrl = resolveImageUrl(imageUrl)
        val systemPrompt = promptBuilder.buildSystemPrompt(petInfo)
        val userPrompt = promptBuilder.buildUserPrompt()

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "max_tokens" to 1000,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to systemPrompt
                ),
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to userPrompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to fullImageUrl, "detail" to "high")
                        )
                    )
                )
            )
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(apiKey)
        }

        val entity = HttpEntity(requestBody, headers)
        val response = restTemplate.postForObject(
            "https://api.openai.com/v1/chat/completions",
            entity,
            Map::class.java
        ) ?: throw IllegalStateException("Empty response from OpenAI")

        // STYLE-002: 언체크드 캐스트 대신 안전 파서.
        val body = ExternalJson.asObject(response, "openai")
        val choices = body.objectListAtOrNull("choices")
            ?: throw IllegalStateException("No choices in OpenAI response")
        val message = choices.firstOrNull()?.objectAtOrNull("message")
            ?: throw IllegalStateException("No message in OpenAI response")
        val content = message.stringAtOrNull("content")
            ?: throw IllegalStateException("No content in OpenAI response")

        log.debug("OpenAI raw response: {}", content)

        return parseResult(content)
    }

    private fun parseResult(content: String): StoolAnalysisResult {
        val jsonContent = extractJson(content)
        val parsed = ExternalJson.asObject(objectMapper.readValue(jsonContent, Map::class.java), "openai")

        val colorScore = parsed.intAtOrNull("color_score") ?: 0
        val consistencyScore = parsed.intAtOrNull("consistency_score") ?: 0
        val coatingScore = parsed.intAtOrNull("coating_score") ?: 0
        val contentsScore = parsed.intAtOrNull("contents_score") ?: 0
        val overallScore = parsed.intAtOrNull("overall_score") ?: 0

        // Validate scores are in range 0-5
        listOf(colorScore, consistencyScore, coatingScore, contentsScore, overallScore).forEach { score ->
            if (score !in 0..5) throw IllegalStateException("Score out of range: $score")
        }

        val healthTips = parsed.stringListAtOrNull("health_tips") ?: emptyList()
        val warnings = parsed.stringListAtOrNull("warnings") ?: emptyList()

        return StoolAnalysisResult(
            colorScore = colorScore,
            consistencyScore = consistencyScore,
            coatingScore = coatingScore,
            contentsScore = contentsScore,
            overallScore = overallScore,
            colorAssessment = parsed["color_assessment"] as? String ?: "",
            consistencyAssessment = parsed["consistency_assessment"] as? String ?: "",
            coatingAssessment = parsed["coating_assessment"] as? String ?: "",
            contentsAssessment = parsed["contents_assessment"] as? String ?: "",
            healthSummary = parsed["health_summary"] as? String ?: "",
            healthTips = healthTips,
            warnings = warnings
        )
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1) throw IllegalStateException("No JSON found in response")
        return content.substring(start, end + 1)
    }
}
