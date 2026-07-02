package com.example.adb

import android.util.Log

object JniBridge {
    private const val TAG = "AURASH_JniBridge"
    var isNativeLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("aurash_core")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded native AURASH Rust library (libaurash_core.so)")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.e(TAG, "Native Rust engine not loaded. Running in ultra-optimized Kotlin fallback mode.")
        }
    }

    // Native JNI functions
    external fun pairDevice(host: String, port: Int, pairingCode: String): String
    external fun initSession(host: String, port: Int, safeMode: Boolean): Long
    external fun executeCommand(sessionId: Long, command: String, safeMode: Boolean): Long
    external fun readStream(streamToken: Long): String
    external fun closeSession(sessionId: Long)
}
