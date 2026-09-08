package com.goldpet.infra

interface GeocodingService {

    data class ReverseGeocodeResult(
        val fullAddress: String,
        val province: String?,
        val district: String? = null
    )

    data class GeocodeResult(
        val address: String,
        val lat: Double,
        val lng: Double
    )

    fun reverseGeocode(lat: Double, lng: Double): String

    fun reverseGeocodeStructured(lat: Double, lng: Double): ReverseGeocodeResult

    fun geocode(address: String): List<GeocodeResult>

    companion object {
        private val PROVINCE_MAP = mapOf(
            "서울특별시" to "서울",
            "경기도" to "경기",
            "강원도" to "강원",
            "강원특별자치도" to "강원",
            "경상북도" to "경북",
            "경상남도" to "경남",
            "광주광역시" to "광주",
            "대구광역시" to "대구",
            "대전광역시" to "대전",
            "부산광역시" to "부산",
            "세종특별자치시" to "세종",
            "울산광역시" to "울산",
            "인천광역시" to "인천",
            "전라남도" to "전남",
            "전라북도" to "전북",
            "전북특별자치도" to "전북",
            "제주특별자치도" to "제주",
            "충청남도" to "충남",
            "충청북도" to "충북",
        )

        fun abbreviateProvince(area1: String): String? {
            return PROVINCE_MAP[area1]
        }
    }
}
