package com.gronorf.nogracias.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class PhoneStateService : Service() {

    companion object {
        private const val TAG = "PhoneStateService"
        private const val CHANNEL_ID = "call_blocker_channel"
        private const val NOTIFICATION_ID = 1
        private const val PREF_BLOCKED_PREFIXES = "blocked_prefixes"
        private const val PREF_NAME = "nogracias_prefs"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Service started"))
        Log.d(TAG, "Call blocking service created and active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val phoneNumber = it.getStringExtra("phone_number")
            val action = it.getStringExtra("action")

            if (action == "block_call" && phoneNumber != null) {
                handleCallBlocking(phoneNumber)
            }
        }

        return START_STICKY
    }

    private fun handleCallBlocking(phoneNumber: String) {
        Log.d(TAG, "Fast evaluation for: $phoneNumber")

        val cleanNumber = cleanPhoneNumber(phoneNumber)

        if (shouldBlockCall(cleanNumber)) {
            Log.w(TAG, "Blocking immediately: $phoneNumber")
            blockCallMultiple()
            updateNotification("Blocked: $phoneNumber")
            saveBlockedCallLog(phoneNumber)
        } else {
            Log.d(TAG, "Allowing call from: $phoneNumber")
            updateNotification("Protection active - Call allowed")
        }
    }

    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^0-9+]"), "")
    }

    private fun shouldBlockCall(phoneNumber: String): Boolean {
        val blockedPrefixes = getBlockedPrefixes()

        for (prefix in blockedPrefixes) {
            if (phoneNumber.startsWith(prefix)) {
                Log.d(TAG, "Match found with prefix: $prefix")
                return true
            }
        }

        return false
    }

    private fun getBlockedPrefixes(): List<String> {
        return try {
            val prefixString = prefs.getString("prefixes", "") ?: ""
            if (prefixString.isNotEmpty()) {
                val simplePrefixes = prefixString.split(",").filter { it.isNotEmpty() }
                Log.d(TAG, "Loaded ${simplePrefixes.size} prefixes: $simplePrefixes")
                simplePrefixes
            } else {
                Log.d(TAG, "No prefixes found in storage")
                listOf("800", "900", "803", "809")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading prefixes", e)
            emptyList()
        }
    }

    @RequiresPermission(Manifest.permission.ANSWER_PHONE_CALLS)
    private fun blockCallMultiple() {
        Log.i(TAG, "Starting multiple blocking methods")

        var blocked = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    if (telecomManager.isInCall) {
                        telecomManager.endCall()
                        Log.w(TAG, "Blocked with TelecomManager")
                        blocked = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager failed: ${e.message}")
                }
            }

            if (!blocked) {
                try {
                    val telephonyManager =
                        getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    val clazz = Class.forName(telephonyManager.javaClass.name)
                    val method = clazz.getDeclaredMethod("getITelephony")
                    method.isAccessible = true
                    val telephonyService = method.invoke(telephonyManager)

                    telephonyService?.let {
                        val endCallMethod = it.javaClass.getDeclaredMethod("endCall")
                        endCallMethod.invoke(it)
                        Log.w(TAG, "Blocked with ITelephony")
                        blocked = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ITelephony failed: ${e.message}")
                }
            }

            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                Log.i(TAG, "Phone silenced")

                Handler(mainLooper).postDelayed({
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d(TAG, "Sound restored")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error restoring sound: ${e.message}")
                    }
                }, 3000)

            } catch (e: Exception) {
                Log.e(TAG, "Error silencing: ${e.message}")
            }

            if (blocked) {
                Log.w(TAG, "Call blocked successfully")
            } else {
                Log.w(TAG, "Silent blocking applied")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in multiple blocking: ${e.message}", e)
        }
    }

    private fun saveBlockedCallLog(phoneNumber: String) {
        val currentLogs = prefs.getString("blocked_calls_log", "") ?: ""
        val timestamp = java.util.Date().toString()
        val newLog = "$timestamp - $phoneNumber\n$currentLogs"

        val lines = newLog.split("\n")
        val trimmedLog = if (lines.size > 50) {
            lines.take(50).joinToString("\n")
        } else {
            newLog
        }

        prefs.edit().putString("blocked_calls_log", trimmedLog).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Blocking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Call blocker notifications"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("No Gracias - Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}