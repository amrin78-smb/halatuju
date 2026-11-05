package com.gpswalk.simulator

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.simulator.location.AndroidMockLocationAdapter
import com.gpswalk.simulator.location.MockLocationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises the real framework test-provider API on the device. Requires the app to already be
 * selected as the mock location app (the test never changes that setting); otherwise it verifies
 * that initialization fails with a fatal, explanatory error.
 */
@RunWith(AndroidJUnit4::class)
class MockLocationAdapterTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun notSelectedFailsFatally() = runBlocking {
        assumeTrue(!MockLocationStatus.isSelectedMockLocationApp(context))
        val r = AndroidMockLocationAdapter(context).initialize()
        assertTrue(r is PublishResult.Failure && r.fatal)
    }

    @Test fun injectedLocationIsDeliveredAndFlaggedMock() = runBlocking {
        assumeTrue("App must be selected as mock location app", MockLocationStatus.isSelectedMockLocationApp(context))
        // Keep the process in the foreground: Android throttles location delivery to background
        // apps without ACCESS_BACKGROUND_LOCATION, which would starve this test's own listener.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val adapter = AndroidMockLocationAdapter(context)
        val lm = context.getSystemService(LocationManager::class.java)
        val latch = CountDownLatch(1)
        var received: Location? = null
        val listener = LocationListener { l -> if (kotlin.math.abs(l.latitude - 1.2345) < 1e-6) { received = l; latch.countDown() } }
        try {
            assertTrue(adapter.initialize() is PublishResult.Success)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
            val fix = SimulatedFix(1.2345, 103.8, 10.0, 4f, 90f, 1.2f, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
            repeat(5) {
                assertTrue(adapter.publish(fix.copy(timeMillis = System.currentTimeMillis(), elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos())) is PublishResult.Success)
                if (latch.await(1, TimeUnit.SECONDS)) return@repeat
            }
            val l = received ?: throw AssertionError("No location received from test provider")
            assertEquals(103.8, l.longitude, 1e-6)
            assertEquals(1.2f, l.speed, 1e-4f)
            val mock = if (Build.VERSION.SDK_INT >= 31) l.isMock else @Suppress("DEPRECATION") l.isFromMockProvider
            assertTrue("Delivered location must be flagged mock", mock)
        } finally {
            lm.removeUpdates(listener)
            adapter.stop()
            scenario.close()
        }
    }
}
