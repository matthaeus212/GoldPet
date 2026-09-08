package com.goldpet.infra

import org.locationtech.jts.geom.Coordinate

interface StaticMapService {
    fun getStaticMapImage(coordinates: Array<Coordinate>, width: Int, height: Int): ByteArray?
}
