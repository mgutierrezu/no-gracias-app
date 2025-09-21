package com.example.nogracias.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BlockedNumbersManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("blocked_numbers", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PREFIXES = "blocked_prefixes"
        private const val KEY_EXACT_NUMBERS = "blocked_exact_numbers"
    }

    // ============ PREFIJOS ============
    fun savePrefixes(prefixes: List<String>) {
        val json = gson.toJson(prefixes)
        prefs.edit().putString(KEY_PREFIXES, json).apply()
    }

    fun getPrefixes(): List<String> {
        val json = prefs.getString(KEY_PREFIXES, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============ NÚMEROS EXACTOS ============
    fun saveExactNumbers(numbers: List<String>) {
        val json = gson.toJson(numbers)
        prefs.edit().putString(KEY_EXACT_NUMBERS, json).apply()
    }

    fun getExactNumbers(): List<String> {
        val json = prefs.getString(KEY_EXACT_NUMBERS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============ UTILIDADES ============
    fun addPrefix(prefix: String) {
        val currentPrefixes = getPrefixes().toMutableList()
        if (!currentPrefixes.contains(prefix)) {
            currentPrefixes.add(prefix)
            savePrefixes(currentPrefixes)
        }
    }

    fun removePrefix(prefix: String) {
        val currentPrefixes = getPrefixes().toMutableList()
        currentPrefixes.remove(prefix)
        savePrefixes(currentPrefixes)
    }

    fun addExactNumber(number: String) {
        val currentNumbers = getExactNumbers().toMutableList()
        if (!currentNumbers.contains(number)) {
            currentNumbers.add(number)
            saveExactNumbers(currentNumbers)
        }
    }

    fun removeExactNumber(number: String) {
        val currentNumbers = getExactNumbers().toMutableList()
        currentNumbers.remove(number)
        saveExactNumbers(currentNumbers)
    }

    // ============ VERIFICACIONES ============
    fun isEmpty(): Boolean {
        return getPrefixes().isEmpty() && getExactNumbers().isEmpty()
    }

    fun getTotalBlockedCount(): Int {
        return getPrefixes().size + getExactNumbers().size
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_PREFIXES)
            .remove(KEY_EXACT_NUMBERS)
            .apply()
    }
}