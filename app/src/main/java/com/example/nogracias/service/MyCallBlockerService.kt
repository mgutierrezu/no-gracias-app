package com.example.nogracias.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

class MyCallBlockerService : CallScreeningService() {

    private val TAG = "MyCallBlockerService"
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("prefixes", Context.MODE_PRIVATE)
        Log.i(TAG, "📞 MyCallBlockerService STARTED - Ready to screen calls!")
    }

    @RequiresApi(Build.VERSION_CODES.S)
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
                Log.w(TAG, "🚫 BLOCKING call from: $normalized (raw: $raw)")

                // Respuesta específica para MIUI
                val builder = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)

                // MIUI a veces necesita estas configuraciones
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setSkipCallLog(false) // En MIUI a veces funciona mejor en false
                }

                builder.build()
            } else {
                Log.i(TAG, "✅ ALLOWING call from: $normalized")
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .build()
            }

            respondToCall(callDetails, response)

        } catch (ex: Exception) {
            Log.e(TAG, "❌ Error processing call screening", ex)
            respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
        }
    }

    private fun getPrefixesFromStorage(): List<String> {
        return try {
            val prefixString = prefs.getString("prefixes", "") ?: ""
            if (prefixString.isNotEmpty()) {
                prefixString.split(",").filter { it.isNotEmpty() }
            } else {
                // Prefijos por defecto para testing
                listOf("600", "5699284")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading prefixes", e)
            listOf("600", "5699284")
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

        // Números exactos hardcodeados para testing
        val exactNumbers = listOf("56992849190", "992849190")
        for (num in exactNumbers) {
            if (normalized.endsWith(num)) {
                Log.w(TAG, "🎯 EXACT MATCH! Number '$num' found in '$normalized'")
                return true
            }
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
}