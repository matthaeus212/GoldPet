// 외부 API JSON 응답을 언체크드 캐스트 없이 안전하게 읽는 헬퍼
package com.goldpet.domain.common.util

/**
 * STYLE-002: 외부 API 클라이언트 4곳(SocialLoginService, LeonardoApiClient, OpenAIStoolVisionClient,
 * DeepLTranslationService)이 `@Suppress("UNCHECKED_CAST")` + `as Map<String, Any>` 를 19회 반복하고
 * 있었다. 외부 응답 형태가 바뀌면 `ClassCastException` 이 그대로 터져 **원인을 알 수 없는 500** 이
 * 된다(어느 필드가 어떻게 달랐는지 로그에 남지 않는다).
 *
 * 여기서는 캐스트를 한 곳에 모으고, 형태가 어긋나면 **어느 경로의 무엇이 기대와 달랐는지** 담은
 * 예외를 던진다. 선택적 필드는 `...OrNull` 로 조용히 넘어간다.
 */
object ExternalJson {

    /** 외부 응답의 형태가 기대와 다를 때. 메시지에 경로와 실제 타입을 남긴다. */
    class ShapeException(message: String) : RuntimeException(message)

    /** 최상위 응답을 객체(Map)로 해석한다. */
    fun asObject(value: Any?, source: String): Map<String, Any?> =
        objectOrNull(value)
            ?: throw ShapeException("$source: 응답이 JSON 객체가 아닙니다 (실제: ${typeName(value)})")

    fun objectOrNull(value: Any?): Map<String, Any?>? {
        if (value !is Map<*, *>) return null
        // 키가 String 이 아닌 Map 은 JSON 객체가 아니다.
        if (value.keys.any { it !is String }) return null
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    fun listOrNull(value: Any?): List<Any?>? = value as? List<Any?>

    /** 필수 하위 객체. 없거나 객체가 아니면 예외. */
    fun Map<String, Any?>.objectAt(key: String, source: String): Map<String, Any?> =
        objectOrNull(this[key])
            ?: throw ShapeException("$source: '$key' 가 객체가 아닙니다 (실제: ${typeName(this[key])})")

    fun Map<String, Any?>.objectAtOrNull(key: String): Map<String, Any?>? = objectOrNull(this[key])

    /** 필수 하위 배열. 없거나 배열이 아니면 예외. */
    fun Map<String, Any?>.listAt(key: String, source: String): List<Any?> =
        listOrNull(this[key])
            ?: throw ShapeException("$source: '$key' 가 배열이 아닙니다 (실제: ${typeName(this[key])})")

    fun Map<String, Any?>.listAtOrNull(key: String): List<Any?>? = listOrNull(this[key])

    /** 객체들의 배열 — 원소 중 객체가 아닌 것은 버린다(외부 응답에 이물질이 섞여도 죽지 않게). */
    fun Map<String, Any?>.objectListAtOrNull(key: String): List<Map<String, Any?>>? =
        listOrNull(this[key])?.mapNotNull { objectOrNull(it) }

    /** 필수 문자열. 없거나 문자열이 아니면 예외. 숫자 등은 toString 하지 않는다(조용한 오해석 방지). */
    fun Map<String, Any?>.stringAt(key: String, source: String): String =
        this[key] as? String
            ?: throw ShapeException("$source: '$key' 가 문자열이 아닙니다 (실제: ${typeName(this[key])})")

    fun Map<String, Any?>.stringAtOrNull(key: String): String? = this[key] as? String

    /** 문자열 배열 — 원소 중 문자열이 아닌 것은 버린다. */
    fun Map<String, Any?>.stringListAtOrNull(key: String): List<String>? =
        listOrNull(this[key])?.filterIsInstance<String>()

    /**
     * 외부 식별자. 카카오는 숫자, 네이버/구글은 문자열로 준다.
     *
     * 기존 코드는 `attributes["id"].toString()` 이었는데, 값이 없으면 **문자열 "null" 이 그대로
     * 사용자 식별자가 된다**(그 ID 로 계정이 만들어지거나 매칭될 수 있다). 없으면 예외를 던진다.
     */
    fun Map<String, Any?>.idAt(key: String, source: String): String = when (val v = this[key]) {
        is String -> v.ifBlank { throw ShapeException("$source: '$key' 가 비어 있습니다") }
        is Number -> v.toString()
        else -> throw ShapeException("$source: '$key' 가 없거나 식별자가 아닙니다 (실제: ${typeName(v)})")
    }

    fun Map<String, Any?>.intAtOrNull(key: String): Int? = when (val v = this[key]) {
        is Int -> v
        is Number -> v.toInt()
        else -> null
    }

    private fun typeName(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> "object"
        is List<*> -> "array"
        else -> value::class.simpleName ?: "unknown"
    }
}
