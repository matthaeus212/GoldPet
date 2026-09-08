package com.goldpet.infra.google

import com.goldpet.infra.StaticMapService
import org.locationtech.jts.geom.Coordinate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class GoogleStaticMapService(
    @Value("\${google.maps.api-key:}") private val apiKey: String
) : StaticMapService {

    private val log = LoggerFactory.getLogger(GoogleStaticMapService::class.java)
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(5000)
        setReadTimeout(5000)
    })

    companion object {
        private const val STATIC_MAP_URL = "https://maps.googleapis.com/maps/api/staticmap"
        private const val MAX_PATH_POINTS = 50
    }

    override fun getStaticMapImage(coordinates: Array<Coordinate>, width: Int, height: Int): ByteArray? {
        if (coordinates.size < 2) return null

        val simplified = simplifyPath(coordinates, MAX_PATH_POINTS)

        // Build path parameter: color:0xRRGGBB|weight:N|lat1,lng1|lat2,lng2|...
        val pathPoints = simplified.joinToString("|") {
            "%.6f,%.6f".format(it.y, it.x) // Google uses lat,lng order
        }
        val pathParam = "color:0x5A5FFF|weight:4|$pathPoints"

        // Start and end markers
        val startCoord = simplified.first()
        val endCoord = simplified.last()
        val startMarker = "color:green|size:small|%.6f,%.6f".format(startCoord.y, startCoord.x)
        val endMarker = "color:red|size:small|%.6f,%.6f".format(endCoord.y, endCoord.x)

        // Google auto-calculates zoom/center from path — no manual calculation needed
        val url = "$STATIC_MAP_URL?size=${width}x${height}&path=$pathParam&markers=$startMarker&markers=$endMarker&key=$apiKey"

        // Check URL length limit (Google limit: 16384 characters)
        if (url.length > 16000) {
            log.warn("Static map URL too long ({} chars), reducing path points", url.length)
            return getStaticMapImage(coordinates, width, height)
        }

        return try {
            val response = restTemplate.getForObject(url, ByteArray::class.java)
            response
        } catch (e: Exception) {
            log.warn("Static map API call failed: {}", e.message)
            null
        }
    }

    private fun simplifyPath(coordinates: Array<Coordinate>, maxPoints: Int): List<Coordinate> {
        if (coordinates.size <= maxPoints) return coordinates.toList()

        val result = mutableListOf<Coordinate>()
        result.add(coordinates.first())

        val step = (coordinates.size - 1).toDouble() / (maxPoints - 1)
        for (i in 1 until maxPoints - 1) {
            val index = (i * step).toInt().coerceIn(1, coordinates.size - 2)
            result.add(coordinates[index])
        }

        result.add(coordinates.last())
        return result
    }
}
