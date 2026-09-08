package com.goldpet.domain.health.service

import com.goldpet.domain.health.dto.PetContextDto
import org.springframework.stereotype.Component

@Component
class StoolAnalysisPromptBuilder {

    fun buildSystemPrompt(petContext: PetContextDto): String {
        val petInfo = buildString {
            append("반려동물 정보:\n")
            append("- 종: ${petContext.species}\n")
            petContext.breed?.let { append("- 품종: $it\n") }
            petContext.ageMonths?.let { append("- 나이: ${it}개월\n") }
            petContext.weightKg?.let { append("- 체중: ${it}kg\n") }
        }

        return """
당신은 반려동물 건강 전문가입니다. 반려동물의 대변 사진을 분석하여 건강 상태를 평가합니다.
4C 프레임워크(Color, Consistency, Coating, Contents)를 사용하여 분석하세요.

$petInfo

분석 지침:
1. 사진이 대변이 아닌 경우(음식, 물체, 사람 등) 모든 점수를 0으로 반환하고 health_summary에 "대변 이미지가 아닙니다"라고 명시하세요.
2. 각 항목은 1~5점으로 평가합니다 (1=매우 나쁨, 5=매우 좋음).
3. 반드시 아래 JSON 형식으로만 응답하세요.

JSON 응답 형식:
{
  "color_score": <1-5 또는 0>,
  "consistency_score": <1-5 또는 0>,
  "coating_score": <1-5 또는 0>,
  "contents_score": <1-5 또는 0>,
  "overall_score": <1-5 또는 0>,
  "color_assessment": "<색상 평가 설명>",
  "consistency_assessment": "<굳기/질감 평가 설명>",
  "coating_assessment": "<코팅/점액 평가 설명>",
  "contents_assessment": "<내용물 평가 설명>",
  "health_summary": "<전반적인 건강 상태 요약>",
  "health_tips": ["<건강 관리 팁1>", "<건강 관리 팁2>"],
  "warnings": ["<주의사항1>", "<주의사항2>"]
}

평가 기준:
- Color(색상): 황갈색~갈색이 정상(5점), 검정/빨강/흰색/회색은 이상(1~2점)
- Consistency(굳기): 소시지 형태로 약간 촉촉한 것이 정상(5점), 너무 딱딱하거나 묽으면 감점
- Coating(코팅): 점액이 없는 것이 정상(5점), 점액이 많으면 감점
- Contents(내용물): 소화되지 않은 음식물, 기생충, 이물질 없는 것이 정상(5점)
        """.trimIndent()
    }

    fun buildUserPrompt(): String {
        return "이 사진을 분석해주세요. 반드시 JSON 형식으로만 응답하세요."
    }
}
