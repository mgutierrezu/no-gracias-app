package com.example.nogracias.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class MyCallBlockerService : CallScreeningService() {

    private val TAG = "MyCallBlockerService"
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("nogracias_prefs", Context.MODE_PRIVATE) // MISMO NOMBRE
        Log.i(TAG, "📞 MyCallBlockerService STARTED - Ready to screen calls!")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Verificar compatibilidad
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "CallScreeningService not supported on this Android version")
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
            return
        }

        try {
            val raw = callDetails.handle?.schemeSpecificPart ?: ""
            val normalized = normalizeToDigits(raw)

            Log.i(TAG, "🔍 INCOMING CALL (MIUI Detection):")
            Log.i(TAG, "   Raw number: '$raw'")
            Log.i(TAG, "   Normalized: '$normalized'")
            Log.i(TAG, "   Call state: ${callDetails.state}")
            Log.i(TAG, "   Device: ${Build.MANUFACTURER} ${Build.MODEL}")

            // Obtener prefijos guardados
            val savedPrefixes = getPrefixesFromStorage()
            Log.i(TAG, "   Checking against prefixes: $savedPrefixes")

            val block = shouldBlock(normalized, savedPrefixes)

            val response = if (block) {
                Log.w(TAG, "🚫🚫🚫 ATTEMPTING TO BLOCK call from: $normalized (raw: $raw)")

                // Respuesta de bloqueo MUY AGRESIVA
                val builder = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)

                // En emuladores, a veces necesitas configuraciones específicas
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setSkipCallLog(true)
                    builder.setSilenceCall(true)
                }

                val response = builder.build()
                Log.w(TAG, "🚫 Block response: disallow=${response.disallowCall}, reject=${response.rejectCall}")
                response
            } else {
                Log.i(TAG, "✅ ALLOWING call from: $normalized")
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .build()
            }

            respondToCall(callDetails, response)

            // Método de bloqueo adicional si el anterior falla
            if (block) {
                try {
                    hangUpCallAlternative()
                } catch (e: Exception) {
                    Log.e(TAG, "Alternative hang up failed", e)
                }
            }

        } catch (ex: Exception) {
            Log.e(TAG, "❌ Error processing call screening", ex)
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
        }
    }

    private fun getPrefixesFromStorage(): List<String> {
        return try {
            // Leer directamente desde el formato simple
            val prefixString = prefs.getString("prefixes", "") ?: ""
            if (prefixString.isNotEmpty()) {
                val simplePrefixes = prefixString.split(",").filter { it.isNotEmpty() }
                Log.d(TAG, "Loaded ${simplePrefixes.size} prefixes: $simplePrefixes")
                return simplePrefixes
            } else {
                Log.d(TAG, "No prefixes found in storage")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading prefixes", e)
            return emptyList()
        }
    }

    private fun shouldBlock(normalized: String, prefixes: List<String>): Boolean {
        if (normalized.isEmpty()) {
            Log.d(TAG, "⚠️ Empty number - allowing call")
            return false
        }

        Log.d(TAG, "🔍 Checking '$normalized' against ${prefixes.size} prefixes")

        // Verificar prefijos
        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                Log.w(TAG, "🎯 MATCH! Prefix '$prefix' found in '$normalized'")
                return true
            }
            Log.d(TAG, "   No match: '$prefix' vs '$normalized'")
        }

        Log.i(TAG, "✓ Number not blocked: '$normalized'")
        return false
    }

    private fun normalizeToDigits(input: String): String {
        val result = input.replace(Regex("\\D"), "")
        Log.d(TAG, "🔄 Normalize: '$input' -> '$result'")
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "📞 MyCallBlockerService STOPPED")
    }

    private fun hangUpCallAlternative() {
        try {
            Log.i(TAG, "🔨 Trying alternative hang up method...")

            // Método 1: TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                if (telecomManager?.isInCall == true) {
                    telecomManager.endCall()
                    Log.i(TAG, "✅ Call ended via TelecomManager")
                    return
                }
            }

            // Método 2: AudioManager - Silenciar
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
            Log.i(TAG, "🔇 Phone silenced")

        } catch (e: SecurityException) {
            Log.w(TAG, "No permission for alternative methods: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Alternative hang up failed", e)
        }
    }
}