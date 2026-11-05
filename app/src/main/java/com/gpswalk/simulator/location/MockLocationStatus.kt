package com.gpswalk.simulator.location

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Read-only checks of the device configuration using public APIs only.
 */
object MockLocationStatus {
    /**
     * True when this app is the one chosen in Developer options > Select mock location app.
     * That selection grants the android:mock_location app-op to this package.
     */
    fun isSelectedMockLocationApp(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return try {
            val mode = if (Build.VERSION.SDK_INT >= 36) {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            } else {
                // API 29–35: the String-op variant is named unsafeCheckOpNoThrow (deprecated in 36).
                @Suppress("DEPRECATION")
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: SecurityException) {
            false
        }
    }

    /** Developer options master switch; null when it cannot be read. */
    fun developerOptionsEnabled(context: Context): Boolean? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
    } catch (e: Settings.SettingNotFoundException) {
        null
    }

    fun locationServicesEnabled(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    fun playServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
