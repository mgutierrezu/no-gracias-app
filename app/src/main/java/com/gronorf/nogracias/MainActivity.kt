package com.gronorf.nogracias

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gronorf.nogracias.model.PrefixItem
import com.gronorf.nogracias.service.PhoneStateService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var etPrefix: EditText
    private lateinit var btnAdd: Button
    private lateinit var switchMain: Switch
    private lateinit var tvStatus: TextView
    private lateinit var lvPrefixes: ListView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: PrefixListAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val prefixList = mutableListOf<PrefixItem>()
    private var isBlockingEnabled = true

    private val callScreeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Call screening permission completed
    }

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }

        if (allGranted) {
            requestCallScreeningRole()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListView()
        loadData()

        checkFirstTimeAndLoadDefaults()

        setupListeners()
        updateUI()

        checkFirstTimeAndShowPermissionDialog()

        startBlockingService()
    }

    private fun checkFirstTimeAndLoadDefaults() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasLoadedDefaults = sharedPrefs.getBoolean("defaults_loaded", false)

        if (!hasLoadedDefaults) {
            sharedPrefs.edit().putBoolean("defaults_loaded", true).apply()
            loadDefaultPrefixes()
        }
    }

    private fun checkFirstTimeAndShowPermissionDialog() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstTime = sharedPrefs.getBoolean("first_time", true)

        if (isFirstTime) {
            sharedPrefs.edit().putBoolean("first_time", false).apply()
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Gracias")
            .setMessage("Para bloquear llamadas spam necesitamos:\n\n" +
                    "1️⃣ Acceso al teléfono\n" +
                    "2️⃣ Administrar llamadas spam\n\n" +
                    "¿Continuar con la configuración?")
            .setPositiveButton("Sí, configurar") { _, _ ->
                requestAllPermissions()
            }
            .setNegativeButton("Más tarde") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestAllPermissions() {
        val requiredPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (requiredPermissions.isNotEmpty()) {
            phonePermissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            requestCallScreeningRole()
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    callScreeningRoleLauncher.launch(intent)
                }
            }
        }
    }

    private fun loadDefaultPrefixes() {
        val defaultPrefixes = listOf("600", "809")

        defaultPrefixes.forEach { prefix ->
            val exists = prefixList.any { it.value == prefix }
            if (!exists) {
                val prefixItem = PrefixItem(prefix, true)
                prefixList.add(prefixItem)
            }
        }

        savePrefixes()
        adapter.notifyDataSetChanged()
        updateUI()
    }

    private fun startBlockingService() {
        try {
            val serviceIntent = Intent(this, PhoneStateService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            showToast("Error starting protection: ${e.message}")
        }
    }

    private fun initViews() {
        prefs = getSharedPreferences("nogracias_prefs", Context.MODE_PRIVATE)

        etPrefix = findViewById(R.id.etPrefix)
        btnAdd = findViewById(R.id.btnAdd)
        switchMain = findViewById(R.id.switchMain)
        tvStatus = findViewById(R.id.tvStatus)
        lvPrefixes = findViewById(R.id.lvPrefixes)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupListView() {
        adapter = PrefixListAdapter(this, prefixList)
        lvPrefixes.adapter = adapter
    }

    private fun setupListeners() {
        btnAdd.setOnClickListener { addPrefix() }

        switchMain.setOnCheckedChangeListener { _, isChecked ->
            isBlockingEnabled = isChecked
            saveBlockingState()
            updateUI()
        }
    }

    private fun addPrefix() {
        val prefixText = etPrefix.text.toString().trim()

        when {
            prefixText.isEmpty() -> {
                showToast("Enter a prefix")
                return
            }
            !prefixText.matches(Regex("^\\+?\\d+$")) -> {
                showToast("Only numbers and + allowed")
                return
            }
            prefixList.any { it.value == prefixText } -> {
                showToast("Prefix already exists")
                return
            }
        }

        val prefixItem = PrefixItem(prefixText, true)
        prefixList.add(prefixItem)

        adapter.notifyDataSetChanged()
        savePrefixes()
        etPrefix.text.clear()
        updateUI()

        showToast("Prefix '$prefixText' added")
    }

    fun removePrefix(position: Int) {
        if (position in 0 until prefixList.size) {
            val removedPrefix = prefixList[position].value
            prefixList.removeAt(position)

            adapter.notifyDataSetChanged()
            savePrefixes()
            updateUI()

            showToast("Prefix '$removedPrefix' removed")
        }
    }

    fun togglePrefix(position: Int, isActive: Boolean) {
        if (position in 0 until prefixList.size) {
            val currentPrefix = prefixList[position]
            currentPrefix.isActive = isActive

            adapter.notifyDataSetChanged()
            savePrefixes()

            val status = if (isActive) "enabled" else "disabled"
            showToast("Prefix ${currentPrefix.value} $status")
        }
    }

    private fun updateUI() {
        val activeCount = prefixList.count { it.isActive }
        val totalCount = prefixList.size

        when {
            !isBlockingEnabled -> {
                tvStatus.text = "Blocking disabled"
                tvStatus.setBackgroundColor(0xFFF5F5F5.toInt())
                tvStatus.setTextColor(0xFF757575.toInt())
            }
            activeCount == 0 -> {
                tvStatus.text = "No active prefixes"
                tvStatus.setBackgroundColor(0xFFFFF3E0.toInt())
                tvStatus.setTextColor(0xFFFF9800.toInt())
            }
            else -> {
                tvStatus.text = "Blocking $activeCount of $totalCount prefixes"
                tvStatus.setBackgroundColor(0xFFF1F8E9.toInt())
                tvStatus.setTextColor(0xFF4CAF50.toInt())
            }
        }

        if (prefixList.isEmpty()) {
            lvPrefixes.visibility = ListView.GONE
            tvEmpty.visibility = TextView.VISIBLE
        } else {
            lvPrefixes.visibility = ListView.VISIBLE
            tvEmpty.visibility = TextView.GONE
        }

        btnAdd.isEnabled = isBlockingEnabled
        etPrefix.isEnabled = isBlockingEnabled

        btnAdd.alpha = if (isBlockingEnabled) 1.0f else 0.5f
        etPrefix.alpha = if (isBlockingEnabled) 1.0f else 0.5f
    }

    private fun savePrefixes() {
        try {
            val json = gson.toJson(prefixList)
            prefs.edit().putString("prefixes_list", json).apply()

            val activePrefixes = prefixList.filter { it.isActive }.map { it.value }
            val simpleJson = activePrefixes.joinToString(",")
            prefs.edit().putString("prefixes", simpleJson).apply()

        } catch (e: Exception) {
            showToast("Error saving: ${e.message}")
        }
    }

    private fun saveBlockingState() {
        prefs.edit().putBoolean("blocking_enabled", isBlockingEnabled).apply()
    }

    private fun loadData() {
        try {
            isBlockingEnabled = prefs.getBoolean("blocking_enabled", true)
            switchMain.isChecked = isBlockingEnabled

            val json = prefs.getString("prefixes_list", null)
            if (json != null) {
                val type = object : TypeToken<List<PrefixItem>>() {}.type
                val loadedPrefixes: List<PrefixItem> = gson.fromJson(json, type) ?: emptyList()
                prefixList.clear()
                prefixList.addAll(loadedPrefixes)
            }

        } catch (e: Exception) {
            showToast("Error loading: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    inner class PrefixListAdapter(
        private val context: Context,
        private val items: MutableList<PrefixItem>
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): PrefixItem = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_prefix, parent, false)

            val item = items[position]

            val tvPrefix = view.findViewById<TextView>(R.id.tvPrefix)
            val switchPrefix = view.findViewById<Switch>(R.id.switchPrefix)
            val btnDelete = view.findViewById<Button>(R.id.btnDelete)

            tvPrefix.text = item.value

            switchPrefix.isChecked = item.isActive

            if (item.isActive) {
                tvPrefix.alpha = 1.0f
                switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            } else {
                tvPrefix.alpha = 0.5f
                switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
            }

            switchPrefix.setOnCheckedChangeListener { _, isChecked ->
                togglePrefix(position, isChecked)
            }

            btnDelete.setOnClickListener {
                removePrefix(position)
            }

            return view
        }
    }
}