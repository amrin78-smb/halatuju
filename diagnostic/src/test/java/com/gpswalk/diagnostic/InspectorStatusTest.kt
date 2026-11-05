package com.gpswalk.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorStatusTest {
    private fun loc(mock: Boolean) = ReceivedLocation("s", "gps", 1.0, 2.0, null, null, null, null, 0, mock, 0)

    @Test fun permissionTakesPriority() =
        assertEquals(InspectorStatus.PERMISSION_DENIED, InspectorStatus.resolve(false, true, loc(true)))

    @Test fun serviceUnavailable() =
        assertEquals(InspectorStatus.SERVICE_UNAVAILABLE, InspectorStatus.resolve(true, false, null))

    @Test fun noLocation() = assertEquals(InspectorStatus.NO_LOCATION, InspectorStatus.resolve(true, true, null))

    @Test fun mockStatusComesFromAndroidFlagOnly() {
        assertEquals(InspectorStatus.MOCK, InspectorStatus.resolve(true, true, loc(true)))
        assertEquals(InspectorStatus.REAL, InspectorStatus.resolve(true, true, loc(false)))
    }
}
