package com.goldpet.infra.google

import com.goldpet.infra.GeocodingService
import com.goldpet.infra.GeocodingService.GeocodeResult
import com.goldpet.infra.GeocodingService.ReverseGeocodeResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class GoogleGeocodingService(
    @Value("\${google.maps.api-key:}") private val apiKey: String
) : GeocodingService {

    private val log = LoggerFactory.getLogger(GoogleGeocodingService::class.java)
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3000)
        setReadTimeout(3000)
    })

    override fun reverseGeocode(lat: Double, lng: Double): String {
        val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey&language=ko"

        return try {
            val body = restTemplate.getForObject(url, Map::class.java) ?: return "알 수 없는 위치"
            val status = body["status"] as? String
            if (status != "OK") {
                log.warn("Google reverse geocode status={} for lat={}, lng={}", status, lat, lng)
                return "알 수 없는 위치"
            }

            val results = (body["results"] as? List<*>)?.firstOrNull() as? Map<*, *>
            val formattedAddress = results?.get("formatted_address") as? String

            // Remove country prefix "대한민국 " for cleaner display
            val address = formattedAddress
                ?.removePrefix("대한민국 ")
                ?.ifBlank { null }
                ?: "알 수 없는 위치"

            log.debug("Reverse geocode ({},{}) = '{}'", lat, lng, address)
            address
        } catch (e: Exception) {
            log.warn("Reverse geocoding failed for lat={}, lng={}: {}", lat, lng, e.message)
            "알 수 없는 위치"
        }
    }

    override fun reverseGeocodeStructured(lat: Double, lng: Double): ReverseGeocodeResult {
        val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey&language=ko"

        return try {
            val body = restTemplate.getForObject(url, Map::class.java)
                ?: return ReverseGeocodeResult("알 수 없는 위치", null)
            val status = body["status"] as? String
            if (status != "OK") {
                log.warn("Google structured reverse geocode status={} for lat={}, lng={}", status, lat, lng)
                return ReverseGeocodeResult("알 수 없는 위치", null)
            }

            val results = (body["results"] as? List<*>)?.firstOrNull() as? Map<*, *>
                ?: return ReverseGeocodeResult("알 수 없는 위치", null)

            val addressComponents = results["address_components"] as? List<*> ?: emptyList<Any>()

            // Parse by types
            var area1 = "" // administrative_area_level_1 (시/도)
            var area2 = "" // sublocality_level_1 (구/군)
            var area3 = "" // sublocality_level_2 (동/읍/면)

            for (component in addressComponents) {
                val comp = component as? Map<*, *> ?: continue
                val types = comp["types"] as? List<*> ?: continue
                val longName = comp["long_name"] as? String ?: continue

                when {
                    types.contains("administrative_area_level_1") -> area1 = longName
                    types.contains("sublocality_level_1") -> area2 = longName
                    types.contains("sublocality_level_2") -> area3 = longName
                }
            }

            val fullAddress = listOf(area1, area2, area3)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "알 수 없는 위치" }

            val province = GeocodingService.abbreviateProvince(area1)
            log.debug("Reverse geocode structured ({},{}) = '{}', province='{}'", lat, lng, fullAddress, province)
            ReverseGeocodeResult(fullAddress, province)
        } catch (e: Exception) {
            log.warn("Structured reverse geocoding failed for lat={}, lng={}: {}", lat, lng, e.message)
            ReverseGeocodeResult("알 수 없는 위치", null)
        }
    }

    override fun geocode(address: String): List<GeocodeResult> {
        val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$address&key=$apiKey&language=ko"

        return try {
            val body = restTemplate.getForObject(url, Map::class.java) ?: return emptyList()
            val status = body["status"] as? String
            if (status != "OK") {
                log.warn("Google geocoding status={} for query: {}", status, address)
                return emptyList()
            }

            val results = (body["results"] as? List<*>) ?: return emptyList()
            if (results.isEmpty()) {
                log.warn("Geocoding returned empty results for query: {}", address)
                return emptyList()
            }

            val geocodeResults = results.mapNotNull { item ->
                val result = item as? Map<*, *> ?: return@mapNotNull null
                val formattedAddress = result["formatted_address"] as? String ?: return@mapNotNull null
                val geometry = result["geometry"] as? Map<*, *> ?: return@mapNotNull null
                val location = geometry["location"] as? Map<*, *> ?: return@mapNotNull null
                val lat = (location["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (location["lng"] as? Number)?.toDouble() ?: return@mapNotNull null

                GeocodeResult(
                    address = formattedAddress.removePrefix("대한민국 "),
                    lat = lat,
                    lng = lng
                )
            }
            log.debug("Geocode '{}' returned {} results", address, geocodeResults.size)
            geocodeResults
        } catch (e: Exception) {
            log.error("Geocoding failed for query '{}': {}", address, e.message)
            emptyList()
        }
    }
}
