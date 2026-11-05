package com.gpswalk.simulator.util

import android.util.Log

/** Single Logcat tag for the whole app, as required: `adb logcat -s GPSWalkSimulator`. */
object AppLog {
    const val TAG = "GPSWalkSimulator"
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
    fun d(msg: String) { Log.d(TAG, msg) }
}
