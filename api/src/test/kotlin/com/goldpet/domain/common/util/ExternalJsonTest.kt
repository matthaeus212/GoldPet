// 외부 API 응답 안전 파서 검증 (STYLE-002)
package com.goldpet.domain.common.util

import com.goldpet.domain.common.util.ExternalJson.idAt
import com.goldpet.domain.common.util.ExternalJson.intAtOrNull
import com.goldpet.domain.common.util.ExternalJson.listAt
import com.goldpet.domain.common.util.ExternalJson.objectAt
import com.goldpet.domain.common.util.ExternalJson.objectAtOrNull
import com.goldpet.domain.common.util.ExternalJson.objectListAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringAt
import com.goldpet.domain.common.util.ExternalJson.stringAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringListAtOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalJsonTest {

    @Test
    fun `asObject accepts a JSON object`() {
        val obj = ExternalJson.asObject(mapOf("a" to 1), "kakao")
        assertEquals(1, obj["a"])
    }

    // 외부 응답이 배열/문자열/null 로 오면 ClassCastException 대신 원인이 담긴 예외를 던진다.
    @Test
    fun `asObject rejects non-objects with a message that names the source and actual type`() {
        val ex = assertThrows(ExternalJson.ShapeException::class.java) {
            ExternalJson.asObject(listOf(1, 2), "kakao")
        }
        assertTrue(ex.message!!.contains("kakao"), ex.message)
        assertTrue(ex.message!!.contains("array"), ex.message)

        assertThrows(ExternalJson.ShapeException::class.java) { ExternalJson.asObject(null, "naver") }
        assertThrows(ExternalJson.ShapeException::class.java) { ExternalJson.asObject("oops", "naver") }
    }

    @Test
    fun `objectAt returns the nested object and reports which key was wrong`() {
        val root = mapOf("response" to mapOf("id" to "u1"))
        assertEquals("u1", root.objectAt("response", "naver")["id"])

        val bad = mapOf("response" to "not-an-object")
        val ex = assertThrows(ExternalJson.ShapeException::class.java) { bad.objectAt("response", "naver") }
        assertTrue(ex.message!!.contains("response"), ex.message)
    }

    @Test
    fun `optional accessors return null instead of throwing`() {
        val root = mapOf<String, Any?>("a" to "x")
        assertNull(root.objectAtOrNull("missing"))
        assertNull(root.stringAtOrNull("missing"))
        assertNull(root.intAtOrNull("missing"))
        // 타입이 달라도 조용히 null
        assertNull(root.objectAtOrNull("a"))
    }

    @Test
    fun `stringAt rejects non-strings rather than silently stringifying`() {
        val root = mapOf<String, Any?>("id" to 12345)
        val ex = assertThrows(ExternalJson.ShapeException::class.java) { root.stringAt("id", "kakao") }
        assertTrue(ex.message!!.contains("id"), ex.message)
    }

    @Test
    fun `listAt returns the array and rejects non-arrays`() {
        val root = mapOf<String, Any?>("choices" to listOf(1, 2))
        assertEquals(2, root.listAt("choices", "openai").size)

        val bad = mapOf<String, Any?>("choices" to mapOf("a" to 1))
        assertThrows(ExternalJson.ShapeException::class.java) { bad.listAt("choices", "openai") }
    }

    // 외부 배열에 이물질(문자열/숫자)이 섞여 있어도 죽지 않고 유효한 원소만 취한다.
    @Test
    fun `objectListAtOrNull drops non-object elements`() {
        val root = mapOf<String, Any?>(
            "generated_images" to listOf(mapOf("url" to "a"), "junk", 42, mapOf("url" to "b"))
        )
        val images = root.objectListAtOrNull("generated_images")!!
        assertEquals(2, images.size)
        assertEquals("a", images[0]["url"])
        assertEquals("b", images[1]["url"])
    }

    @Test
    fun `stringListAtOrNull drops non-string elements`() {
        val root = mapOf<String, Any?>("health_tips" to listOf("t1", 3, null, "t2"))
        assertEquals(listOf("t1", "t2"), root.stringListAtOrNull("health_tips"))
    }

    @Test
    fun `intAtOrNull accepts any number`() {
        assertEquals(7, mapOf<String, Any?>("n" to 7L).intAtOrNull("n"))
        assertEquals(7, mapOf<String, Any?>("n" to 7.9).intAtOrNull("n"))
        assertNull(mapOf<String, Any?>("n" to "7").intAtOrNull("n"))
    }

    // 키가 String 이 아닌 Map 은 JSON 객체가 아니다(Jackson 이 그런 Map 을 만들 일은 없지만,
    // 헬퍼가 조용히 잘못된 캐스트를 허용하면 안 된다).
    @Test
    fun `objectOrNull rejects maps with non-string keys`() {
        assertNull(ExternalJson.objectOrNull(mapOf(1 to "a")))
    }

    // 회귀: attributes["id"].toString() 은 값이 없을 때 문자열 "null" 을 식별자로 만들어버렸다.
    @Test
    fun `idAt accepts numeric and string ids but never yields the literal null`() {
        assertEquals("12345", mapOf<String, Any?>("id" to 12345L).idAt("id", "kakao"))
        assertEquals("abc", mapOf<String, Any?>("id" to "abc").idAt("id", "naver"))

        val ex = assertThrows(ExternalJson.ShapeException::class.java) {
            mapOf<String, Any?>("id" to null).idAt("id", "kakao")
        }
        assertTrue(ex.message!!.contains("id"), ex.message)

        assertThrows(ExternalJson.ShapeException::class.java) {
            mapOf<String, Any?>("id" to "").idAt("id", "naver")
        }
    }
}
