package com.gpswalk.core

import com.gpswalk.core.engine.DistanceCalculator
import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.MovementProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private val bkk = Coordinate(13.7563, 100.5018)

    @Test fun haversineZeroForSamePoint() {
        assertEquals(0.0, DistanceCalculator.haversineMeters(bkk, bkk), 1e-9)
    }

    @Test fun haversineOneThousandthDegreeLatitude() {
        // 0.001° of latitude ≈ 111.2 m on a sphere of radius 6371.0088 km.
        val d = DistanceCalculator.haversineMeters(bkk, Coordinate(13.7573, 100.5018))
        assertEquals(111.195, d, 0.01)
    }

    @Test fun haversineLongitudeScalesWithCosLatitude() {
        val d = DistanceCalculator.haversineMeters(bkk, Coordinate(13.7563, 100.5118))
        // 0.01° longitude at 13.7563°N ≈ 1111.95 * cos(13.7563°) ≈ 1080.07 m
        assertEquals(1080.07, d, 0.1)
    }

    @Test fun haversineKnownCityPair() {
        // London -> Paris, reference ~343.5 km (spherical).
        val d = DistanceCalculator.haversineMeters(Coordinate(51.5074, -0.1278), Coordinate(48.8566, 2.3522))
        assertEquals(343_560.0, d, 500.0)
    }

    @Test fun bearingsCardinal() {
        assertEquals(0.0, DistanceCalculator.initialBearingDegrees(bkk, Coordinate(13.7573, 100.5018)), 1e-6)
        assertEquals(90.0, DistanceCalculator.initialBearingDegrees(bkk, Coordinate(13.7563, 100.5118)), 0.01)
        assertEquals(180.0, DistanceCalculator.initialBearingDegrees(Coordinate(13.7573, 100.5018), bkk), 1e-6)
        assertEquals(270.0, DistanceCalculator.initialBearingDegrees(Coordinate(13.7563, 100.5118), bkk), 0.01)
    }

    @Test fun intermediatePointHalfwayIsEquidistant() {
        val a = Coordinate(10.0, 20.0, 0.0)
        val b = Coordinate(10.5, 21.0, 100.0)
        val mid = DistanceCalculator.intermediatePoint(a, b, 0.5)
        val da = DistanceCalculator.haversineMeters(a, mid)
        val db = DistanceCalculator.haversineMeters(mid, b)
        assertEquals(da, db, 0.01)
        assertEquals(DistanceCalculator.haversineMeters(a, b), da + db, 0.01)
        assertEquals(50.0, mid.altitudeMeters, 1e-9)
    }

    @Test fun intermediatePointAcrossAntimeridianStaysValid() {
        val a = Coordinate(0.0, 179.9)
        val b = Coordinate(0.0, -179.9)
        val mid = DistanceCalculator.intermediatePoint(a, b, 0.5)
        assertEquals(180.0, kotlin.math.abs(mid.longitude), 1e-6)
        assertTrue(DistanceCalculator.haversineMeters(a, b) < 25_000)
    }

    @Test fun speedConversion() {
        assertEquals(1.1111, MovementProfile.kmhToMps(4.0), 1e-4)
        assertEquals(3.6, MovementProfile.mpsToKmh(1.0), 1e-9)
        assertEquals(5.0 / 3.6, MovementProfile.BRISK.speedMetersPerSecond, 1e-9)
    }
}
