package com.goldpet.domain.admin.dto

import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.entity.WalkSpotType
import com.goldpet.domain.walk.service.PhotoUrlSigner
import com.goldpet.domain.user.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class WalkSpotAdminDtoTest {

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    private fun buildSpot(imageKey: String?): WalkSpot {
        val user = User(
            id = 1L,
            email = "a@b.c",
            oauthProvider = "LOCAL",
            oauthId = "x",
            username = "x",
            password = "p",
            nickname = "n",
            name = "n",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
        val walk = Walk(
            id = 1L,
            user = user,
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now(),
            distanceKm = 1.0,
            durationSeconds = 600,
            path = geometryFactory.createLineString(arrayOf(
                Coordinate(126.0, 37.0), Coordinate(126.1, 37.1)
            )),
            caloriesBurned = null,
            notes = null
        )
        return WalkSpot(
            id = 42L,
            walk = walk,
            location = geometryFactory.createPoint(Coordinate(126.05, 37.05)),
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now(),
            imageUrl = imageKey,
            note = null,
            hiddenFromPublic = false
        )
    }

    @Test
    fun `from signs URL via photoUrlSigner and always emits imageKey`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=abc"
        val signer = mock<PhotoUrlSigner>()
        whenever(signer.signedUrlOrNull(key)).thenReturn(signedUrl)

        val dto = WalkSpotAdminDto.from(buildSpot(key), signer)

        assertEquals(signedUrl, dto.imageUrl)
        assertEquals(key, dto.imageKey)
    }

    @Test
    fun `from falls back to raw key when signer returns null (flag off)`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signer = mock<PhotoUrlSigner>()
        whenever(signer.signedUrlOrNull(key)).thenReturn(null)

        val dto = WalkSpotAdminDto.from(buildSpot(key), signer)

        assertEquals(key, dto.imageUrl)
        assertEquals(key, dto.imageKey)
    }

    @Test
    fun `from with null signer returns raw key (backward-compat)`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"

        val dto = WalkSpotAdminDto.from(buildSpot(key), null)

        assertEquals(key, dto.imageUrl)
        assertEquals(key, dto.imageKey)
    }

    @Test
    fun `from handles null imageUrl cleanly`() {
        val signer = mock<PhotoUrlSigner>()
        whenever(signer.signedUrlOrNull(null)).thenReturn(null)

        val dto = WalkSpotAdminDto.from(buildSpot(null), signer)

        assertEquals(null, dto.imageUrl)
        assertEquals(null, dto.imageKey)
    }
}
